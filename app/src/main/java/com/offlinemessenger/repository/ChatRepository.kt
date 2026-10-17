package com.offlinemessenger.repository

import android.content.Context
import android.util.Base64
import com.offlinemessenger.ble.BleTransport
import com.offlinemessenger.crypto.CryptoManager
import com.offlinemessenger.crypto.SecureStore
import com.offlinemessenger.data.local.dao.ContactDao
import com.offlinemessenger.data.local.dao.MessageDao
import com.offlinemessenger.data.local.dao.RelayQueueDao
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.MessageStatus
import com.offlinemessenger.data.local.entity.RelayQueueEntity
import com.offlinemessenger.protocol.DecodedPacket
import com.offlinemessenger.protocol.HelloPayload
import com.offlinemessenger.protocol.Packet
import com.offlinemessenger.protocol.PacketType
import com.offlinemessenger.relay.AckTracker
import com.offlinemessenger.relay.RelayEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Messaging orchestration: send/receive, ACKs, relaying, store-and-forward.
 *
 * Delivery truth: a message becomes DELIVERED only when the destination's
 * ACK packet arrives. Everything else is SENT (transmitted) or PENDING.
 */
class ChatRepository(
    private val appContext: Context,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val relayQueueDao: RelayQueueDao,
    private val crypto: CryptoManager,
    private val store: SecureStore,
    private val transport: BleTransport,
    private val peers: PeerRepository,
    private val relayEngine: RelayEngine,
    private val ackTracker: AckTracker,
    private val scope: CoroutineScope,
) {
    companion object {
        /** Relayed messages live at most 24h in the store-and-forward queue. */
        const val MESSAGE_TTL_MS = 24 * 60 * 60 * 1000L
        private const val ACK_CHECK_MS = 2_000L
        private const val MAINTENANCE_MS = 60_000L
        private const val ROUTE_ADVERT_MS = 30_000L
    }

    val myDeviceId: UUID get() = transport.myDeviceId

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    fun conversation(id: String): Flow<List<MessageEntity>> = messageDao.conversation(id)
    fun latestPerConversation(): Flow<List<MessageEntity>> = messageDao.latestPerConversation()
    fun unreadCount(id: String): Flow<Int> = messageDao.unreadCount(id)
    suspend fun markRead(id: String) = messageDao.markRead(id)
    suspend fun relayQueueSize(): Int = relayQueueDao.count()

    fun start() {
        if (!started.compareAndSet(false, true)) return // service restarts must not double-wire
        val peerListener = peers.transportListener
        transport.eventListener = object : BleTransport.EventListener {
            override fun onHello(deviceId: UUID, hello: HelloPayload.Hello, address: String) {
                peerListener.onHello(deviceId, hello, address)
            }

            override fun onPeerConnected(deviceId: UUID) {
                peerListener.onPeerConnected(deviceId)
                scope.launch { drainQueue() }
            }

            override fun onPeerDisconnected(deviceId: UUID) {
                peerListener.onPeerDisconnected(deviceId)
            }
        }
        scope.launch { transport.incoming.collect { handleIncoming(it) } }
        scope.launch { ackLoop() }
        scope.launch { maintenanceLoop() }
        scope.launch { routeAdvertLoop() }
    }

    // ------------------------------------------------------------ sending

    /**
     * Sends a text message. Requires a prior HELLO key exchange with the
     * destination (trust-on-first-use): without the peer's public key there
     * is nothing to encrypt to, so the send is refused rather than sent
     * insecurely.
     */
    suspend fun sendMessage(destId: UUID, text: String): Result<Unit> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty message"))
        if (trimmed.toByteArray(Charsets.UTF_8).size > 4 * 1024) {
            return Result.failure(IllegalArgumentException("message too long (4 KB max)"))
        }
        val contact = contactDao.get(destId.toString())
            ?: return Result.failure(
                IllegalStateException("No key exchanged with this contact yet — meet in Bluetooth range first."),
            )
        val peerKey = try {
            Base64.decode(contact.publicKeyB64, Base64.NO_WRAP)
        } catch (_: Exception) {
            return Result.failure(IllegalStateException("Stored key is corrupt; re-exchange HELLO."))
        }
        val keys = crypto.sessionKeys(destId, peerKey)
            ?: return Result.failure(IllegalStateException("Key agreement failed."))
        val sealed = crypto.encrypt(keys, trimmed.toByteArray(Charsets.UTF_8))
            ?: return Result.failure(IllegalStateException("Encryption failed."))

        val msgId = UUID.randomUUID()
        val now = System.currentTimeMillis()
        val packet = Packet.encode(
            type = PacketType.MSG,
            ttl = Packet.DEFAULT_TTL,
            messageId = msgId,
            senderId = myDeviceId,
            destId = destId,
            timestampMs = now,
            payload = sealed,
        )
        messageDao.insert(
            MessageEntity(
                msgId = msgId.toString(),
                conversationId = destId.toString(),
                senderId = myDeviceId.toString(),
                destId = destId.toString(),
                body = trimmed,
                timestampMs = now,
                status = MessageStatus.PENDING,
                isMine = true,
                ttl = Packet.DEFAULT_TTL.toInt(),
                hops = 0,
            ),
        )
        return if (sendRouted(destId, null, packet)) {
            messageDao.setStatus(msgId.toString(), MessageStatus.SENT)
            ackTracker.track(msgId, destId, packet, now)
            Result.success(Unit)
        } else {
            enqueueRelay(msgId, destId, packet, now, nextHopId = null)
            // stays PENDING: queued for store-and-forward, NOT delivered
            _events.tryEmit("queued for later delivery")
            Result.success(Unit)
        }
    }

    // ------------------------------------------------------------ receiving

    private suspend fun handleIncoming(inc: BleTransport.Incoming) {
        val p = inc.packet
        when {
            p.destId == myDeviceId -> handleForMe(p)
            p.destId == Packet.BROADCAST_ID -> handleBroadcast(p)
            else -> handleRelay(p, inc.fromDeviceId)
        }
    }

    private suspend fun handleForMe(p: DecodedPacket) {
        when (p.type) {
            PacketType.MSG -> {
                if (relayEngine.haveSeen(p.messageId)) return // duplicate
                relayEngine.markSeen(p.messageId)
                val contact = contactDao.get(p.senderId.toString()) ?: return
                val peerKey = runCatching {
                    Base64.decode(contact.publicKeyB64, Base64.NO_WRAP)
                }.getOrNull() ?: return
                val keys = crypto.sessionKeys(p.senderId, peerKey) ?: return
                val plain = crypto.decrypt(keys, p.payload) ?: return // auth failed: drop
                val body = runCatching { String(plain, Charsets.UTF_8) }.getOrNull() ?: return
                messageDao.insert(
                    MessageEntity(
                        msgId = p.messageId.toString(),
                        conversationId = p.senderId.toString(),
                        senderId = p.senderId.toString(),
                        destId = myDeviceId.toString(),
                        body = body,
                        timestampMs = p.timestampMs,
                        status = MessageStatus.RECEIVED,
                        isMine = false,
                        ttl = p.ttl.toInt(),
                        hops = 0,
                        isRead = false,
                    ),
                )
                sendAck(p.senderId, p.messageId)
            }
            PacketType.ACK -> {
                // ACKs carry the original message id in the header.
                val destId = ackTracker.onAck(p.messageId)
                if (destId != null) {
                    messageDao.setStatus(p.messageId.toString(), MessageStatus.DELIVERED)
                    relayQueueDao.removeByMsgId(p.messageId.toString())
                    _events.tryEmit("delivered ✓✓")
                }
            }
            else -> Unit // HELLO/BYE/ROUTE never addressed to us here
        }
    }

    private suspend fun handleBroadcast(p: DecodedPacket) {
        if (p.type == PacketType.ROUTE) peers.handleRouteAdvert(p)
    }

    private suspend fun handleRelay(p: DecodedPacket, fromDeviceId: UUID?) {
        if (p.type != PacketType.MSG && p.type != PacketType.ACK) return
        if (fromDeviceId == null) return
        val decision = relayEngine.decide(
            packet = p,
            fromPeer = fromDeviceId,
            myId = myDeviceId,
            directPeers = transport.connectedDeviceIds(),
            routes = learnedRoutes(),
        )
        if (decision.action == RelayEngine.Action.DROP) return
        if (!store.relayOptIn) return // not our message + relaying disabled = drop
        val aged = relayEngine.aged(p)
        val bytes = Packet.encode(
            type = aged.type,
            ttl = aged.ttl,
            messageId = aged.messageId,
            senderId = aged.senderId,
            destId = aged.destId,
            timestampMs = aged.timestampMs,
            payload = aged.payload,
            relayed = true,
        )
        val targets = when (decision.action) {
            RelayEngine.Action.FORWARD_DIRECT, RelayEngine.Action.FORWARD_ROUTE ->
                listOfNotNull(decision.nextHopId)
            RelayEngine.Action.FLOOD ->
                (transport.connectedDeviceIds() - fromDeviceId).toList()
            else -> emptyList()
        }
        var forwarded = 0
        var preferred: UUID? = null
        for (t in targets) {
            if (!relayEngine.canForward(t)) continue
            if (transport.sendPacket(t, bytes)) {
                forwarded++
                if (preferred == null) preferred = t
            }
        }
        if (forwarded == 0) {
            // Nobody to hand it to right now: store-and-forward (only if we
            // actually store it — this row IS the store).
            enqueueRelay(p.messageId, p.destId, bytes, System.currentTimeMillis(), preferred)
            _events.tryEmit("stored for forwarding")
        }
    }

    // ------------------------------------------------------------ routing

    private suspend fun learnedRoutes(): Map<UUID, RelayEngine.Route> =
        peers.validRoutes().associate {
            UUID.fromString(it.destId) to
                RelayEngine.Route(UUID.fromString(it.nextHopId), it.hops)
        }

    /**
     * Best-effort routed send for our own packets (MSG, ACK).
     * Returns true if at least one peer accepted the packet.
     */
    private suspend fun sendRouted(destId: UUID, fromPeer: UUID?, packet: ByteArray): Boolean {
        val decoded = Packet.decode(packet) ?: return false
        val direct = transport.connectedDeviceIds()
        val decision = relayEngine.routeFor(
            destId = destId,
            ttl = decoded.ttl,
            fromPeer = fromPeer,
            directPeers = direct,
            routes = learnedRoutes(),
        )
        val targets = when (decision.action) {
            RelayEngine.Action.FORWARD_DIRECT, RelayEngine.Action.FORWARD_ROUTE ->
                listOfNotNull(decision.nextHopId)
            RelayEngine.Action.FLOOD -> (direct - setOfNotNull(fromPeer)).toList()
            else -> emptyList()
        }
        var ok = false
        for (t in targets) {
            if (transport.sendPacket(t, packet)) ok = true
        }
        return ok
    }

    private fun sendAck(to: UUID, msgId: UUID) {
        val ack = Packet.encode(
            type = PacketType.ACK,
            ttl = Packet.DEFAULT_TTL,
            messageId = msgId, // original message id: the sender matches on it
            senderId = myDeviceId,
            destId = to,
            timestampMs = System.currentTimeMillis(),
            payload = ByteArray(0),
        )
        scope.launch { sendRouted(to, null, ack) }
    }

    private suspend fun enqueueRelay(
        msgId: UUID,
        destId: UUID,
        packet: ByteArray,
        now: Long,
        nextHopId: UUID?,
    ) {
        if (relayQueueDao.byMsgId(msgId.toString()) != null) return
        relayQueueDao.enqueue(
            RelayQueueEntity(
                msgId = msgId.toString(),
                destId = destId.toString(),
                packet = packet,
                createdAtMs = now,
                expiresAtMs = now + MESSAGE_TTL_MS,
                nextHopId = nextHopId?.toString(),
            ),
        )
    }

    /** Store-and-forward drain: a queued packet leaves only via a live peer. */
    private suspend fun drainQueue() {
        val now = System.currentTimeMillis()
        relayQueueDao.deleteExpired(now)
        for (entry in relayQueueDao.pending(now)) {
            val decoded = Packet.decode(entry.packet) ?: run {
                relayQueueDao.remove(entry.id)
                continue
            }
            if (now - decoded.timestampMs > MESSAGE_TTL_MS) {
                relayQueueDao.remove(entry.id)
                continue
            }
            val destId = UUID.fromString(entry.destId)
            if (sendRouted(destId, null, entry.packet)) {
                relayQueueDao.remove(entry.id)
                _events.tryEmit("forwarded stored message")
            } else {
                relayQueueDao.bumpAttempts(entry.id)
            }
        }
    }

    // ------------------------------------------------------------ loops

    private suspend fun ackLoop() {
        while (scope.isActive) {
            delay(ACK_CHECK_MS)
            val now = System.currentTimeMillis()
            for (pending in ackTracker.dueRetries(now)) {
                if (sendRouted(pending.destId, null, pending.packet)) {
                    ackTracker.recordAttempt(pending.msgId, now)
                }
            }
            for (dead in ackTracker.exhausted(now)) {
                messageDao.setStatus(dead.msgId.toString(), MessageStatus.FAILED)
                relayQueueDao.removeByMsgId(dead.msgId.toString())
                _events.tryEmit("message failed — no route / no ACK")
            }
        }
    }

    private suspend fun maintenanceLoop() {
        while (scope.isActive) {
            delay(MAINTENANCE_MS)
            relayQueueDao.deleteExpired(System.currentTimeMillis())
            peers.pruneExpired()
        }
    }

    private suspend fun routeAdvertLoop() {
        while (scope.isActive) {
            delay(ROUTE_ADVERT_MS)
            val connected = transport.connectedDeviceIds()
            if (connected.isEmpty()) continue
            val payload = peers.buildRouteAdvert()
            val packet = Packet.encode(
                type = PacketType.ROUTE,
                ttl = 1, // direct neighbours only; each node re-advertises
                messageId = UUID.randomUUID(),
                senderId = myDeviceId,
                destId = Packet.BROADCAST_ID,
                timestampMs = System.currentTimeMillis(),
                payload = payload,
            )
            connected.forEach { transport.sendPacket(it, packet) }
        }
    }
}
