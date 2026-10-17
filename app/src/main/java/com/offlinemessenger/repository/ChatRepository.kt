package com.offlinemessenger.repository

import android.content.Context
import android.util.Base64
import com.offlinemessenger.ble.BleTransport
import com.offlinemessenger.crypto.CryptoManager
import com.offlinemessenger.crypto.SecureStore
import com.offlinemessenger.data.local.dao.ContactDao
import com.offlinemessenger.data.local.dao.ConversationDao
import com.offlinemessenger.data.local.dao.GroupDao
import com.offlinemessenger.data.local.dao.MessageDao
import com.offlinemessenger.data.local.dao.RelayQueueDao
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.data.local.entity.ConversationEntity
import com.offlinemessenger.data.local.entity.GroupEntity
import com.offlinemessenger.data.local.entity.GroupMemberEntity
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.MessageStatus
import com.offlinemessenger.data.local.entity.RelayQueueEntity
import com.offlinemessenger.data.local.entity.reactions
import com.offlinemessenger.data.local.entity.reactionsToJson
import com.offlinemessenger.protocol.DecodedPacket
import com.offlinemessenger.protocol.GroupMemberSpec
import com.offlinemessenger.protocol.HelloPayload
import com.offlinemessenger.protocol.MessageEnvelope
import com.offlinemessenger.protocol.Packet
import com.offlinemessenger.protocol.PacketType
import com.offlinemessenger.protocol.packMembers
import com.offlinemessenger.protocol.unpackMembers
import com.offlinemessenger.relay.AckTracker
import com.offlinemessenger.relay.RelayEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** Structured event for system notifications (new message arrived). */
data class NotifyEvent(
    val title: String,
    val body: String,
    val conversationId: String,
)

/** An incoming chat-request handshake from a nearby peer. */
data class ConnRequest(val peerId: String, val timestampMs: Long)

/**
 * Messaging orchestration: send/receive, ACKs, relaying, store-and-forward,
 * replies, reactions, read receipts, groups.
 *
 * Delivery truth: a message becomes DELIVERED only when the destination's
 * ACK packet arrives. Everything else is SENT (transmitted) or PENDING.
 */
class ChatRepository(
    private val appContext: Context,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val relayQueueDao: RelayQueueDao,
    private val groupDao: GroupDao,
    private val conversationDao: ConversationDao,
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
        /** Group conversation ids are prefixed so 1:1 and group chats never collide. */
        const val GROUP_PREFIX = "group:"
    }

    val myDeviceId: UUID get() = transport.myDeviceId

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events

    private val _notifications = MutableSharedFlow<NotifyEvent>(extraBufferCapacity = 32)
    val notifications: SharedFlow<NotifyEvent> = _notifications

    /** Incoming "X wants to chat" requests (receiver side). */
    private val _incomingRequests = MutableSharedFlow<ConnRequest>(extraBufferCapacity = 16)
    val incomingRequests: SharedFlow<ConnRequest> = _incomingRequests.asSharedFlow()

    /** Peer ids that accepted OUR request (requester side) — open the chat. */
    private val _requestAccepted = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val requestAccepted: SharedFlow<String> = _requestAccepted.asSharedFlow()

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    fun conversation(id: String): Flow<List<MessageEntity>> = messageDao.conversation(id)
    fun latestPerConversation(): Flow<List<MessageEntity>> = messageDao.latestPerConversation()
    fun unreadCount(id: String): Flow<Int> = messageDao.unreadCount(id)
    fun conversationPrefs(): Flow<List<ConversationEntity>> = conversationDao.all()
    suspend fun relayQueueSize(): Int = relayQueueDao.count()
    suspend fun searchMessages(q: String): List<MessageEntity> =
        if (q.isBlank()) emptyList() else messageDao.search(q)

    suspend fun setPinned(conversationId: String, pinned: Boolean) {
        conversationDao.insert(ConversationEntity(conversationId))
        conversationDao.setPinned(conversationId, pinned)
    }

    suspend fun isPinned(conversationId: String): Boolean =
        conversationDao.get(conversationId)?.pinned == true

    /** Marks messages read AND sends read receipts to their senders. */
    suspend fun markRead(conversationId: String) {
        val unread = messageDao.unreadReceived(conversationId)
        messageDao.markRead(conversationId)
        for (m in unread) sendReadReceipt(m)
    }

    fun group(groupId: String): Flow<GroupEntity?> = groupDao.groupFlow(groupId)

    suspend fun getGroupOnce(groupId: String): GroupEntity? = groupDao.getGroup(groupId)

    fun groupMembers(groupId: String): Flow<List<GroupMemberEntity>> =
        groupDao.members(groupId)

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

    // ------------------------------------------------------------ crypto helpers

    private fun sessionKeysFor(deviceId: UUID, keyB64: String): CryptoManager.SessionKeys? {
        val peerKey = runCatching { Base64.decode(keyB64, Base64.NO_WRAP) }.getOrNull()
            ?: return null
        return crypto.sessionKeys(deviceId, peerKey)
    }

    private suspend fun displayNameOf(deviceId: String): String =
        contactDao.get(deviceId)?.displayName ?: "Unknown"

    // ------------------------------------------------------------ sending

    /**
     * Sends a text message. Requires a prior HELLO key exchange with the
     * destination (trust-on-first-use).
     */
    suspend fun sendMessage(
        destId: UUID,
        text: String,
        replyTo: MessageEntity? = null,
        forwarded: Boolean = false,
    ): Result<Unit> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty message"))
        if (trimmed.toByteArray(Charsets.UTF_8).size > 4 * 1024) {
            return Result.failure(IllegalArgumentException("message too long (4 KB max)"))
        }
        val contact = contactDao.get(destId.toString())
            ?: return Result.failure(
                IllegalStateException("No key exchanged with this contact yet — meet in Bluetooth range first."),
            )
        val keys = sessionKeysFor(destId, contact.publicKeyB64)
            ?: return Result.failure(IllegalStateException("Key agreement failed."))
        val env = MessageEnvelope(
            kind = "chat",
            text = trimmed,
            replyTo = replyTo?.msgId,
            replyBody = replyTo?.body?.take(120),
            replySender = replyTo?.let {
                if (it.isMine) "You" else runCatching {
                    displayNameOf(it.senderId)
                }.getOrDefault("Unknown")
            },
            forwarded = forwarded,
        )
        val sealed = crypto.encrypt(keys, env.encode())
            ?: return Result.failure(IllegalStateException("Encryption failed."))

        val msgId = UUID.randomUUID()
        val now = System.currentTimeMillis()
        val packet = Packet.encode(
            type = PacketType.MSG, ttl = Packet.DEFAULT_TTL, messageId = msgId,
            senderId = myDeviceId, destId = destId, timestampMs = now, payload = sealed,
        )
        messageDao.insert(
            MessageEntity(
                msgId = msgId.toString(), conversationId = destId.toString(),
                senderId = myDeviceId.toString(), destId = destId.toString(),
                body = trimmed, timestampMs = now, status = MessageStatus.PENDING,
                isMine = true, ttl = Packet.DEFAULT_TTL.toInt(), hops = 0,
                replyToId = replyTo?.msgId,
                replyToBody = replyTo?.body?.take(120),
                replyToSender = replyTo?.let { if (it.isMine) "You" else contact.displayName },
                forwarded = forwarded,
            ),
        )
        return if (sendRouted(destId, null, packet)) {
            messageDao.setStatus(msgId.toString(), MessageStatus.SENT)
            ackTracker.track(msgId, destId, packet, now)
            Result.success(Unit)
        } else {
            enqueueRelay(msgId, destId, packet, now, nextHopId = null)
            _events.tryEmit("queued for later delivery")
            Result.success(Unit)
        }
    }

    /**
     * Sends a reaction. Idempotent (explicit add/remove), so ACK retries are safe.
     */
    suspend fun sendReaction(target: MessageEntity, emoji: String): Result<Unit> {
        val current = target.reactions()
        val reactors = current[emoji] ?: emptySet()
        val add = myDeviceId.toString() !in reactors
        val env = MessageEnvelope(kind = "react", target = target.msgId, emoji = emoji, add = add)
        return if (target.conversationId.startsWith(GROUP_PREFIX)) {
            val groupId = target.conversationId.removePrefix(GROUP_PREFIX)
            sendToGroupMembers(groupId, env, trackMsgId = UUID.randomUUID(), trackAck = false)
            applyReactionLocal(target.msgId, emoji, myDeviceId.toString(), add)
            Result.success(Unit)
        } else {
            val destId = runCatching { UUID.fromString(target.conversationId) }.getOrNull()
                ?: return Result.failure(IllegalStateException("bad conversation"))
            sendControlMessage(destId, env)
            applyReactionLocal(target.msgId, emoji, myDeviceId.toString(), add)
            Result.success(Unit)
        }
    }

    /** Sends an encrypted control message (react/read/gsync) to one peer. */
    // ------------------------------------------------- connection request handshake

    /** "I want to chat" — sent after tapping Connect in Nearby. */
    suspend fun sendConnectionRequest(peerId: UUID): Boolean =
        sendControlMessage(peerId, MessageEnvelope(kind = "connreq"))

    /** Accept an incoming request — the chat opens on both sides. */
    suspend fun acceptConnectionRequest(peerId: UUID): Boolean =
        sendControlMessage(peerId, MessageEnvelope(kind = "connacc"))

    /** Decline an incoming request (best-effort, no retry). */
    suspend fun declineConnectionRequest(peerId: UUID) {
        runCatching { sendControlMessage(peerId, MessageEnvelope(kind = "conndec")) }
    }

    private suspend fun handleConnRequest(p: DecodedPacket) {
        _incomingRequests.emit(ConnRequest(p.senderId.toString(), System.currentTimeMillis()))
    }

    private suspend fun handleConnAccepted(p: DecodedPacket, contact: ContactEntity) {
        val peerId = p.senderId.toString()
        _requestAccepted.emit(peerId)
        _notifications.emit(
            NotifyEvent(
                title = "Request accepted",
                body = "${contact.displayName} accepted your chat request — tap to open.",
                conversationId = peerId,
            ),
        )
    }

    private suspend fun sendControlMessage(destId: UUID, env: MessageEnvelope): Boolean {
        val contact = contactDao.get(destId.toString()) ?: return false
        val keys = sessionKeysFor(destId, contact.publicKeyB64) ?: return false
        val sealed = crypto.encrypt(keys, env.encode()) ?: return false
        val packet = Packet.encode(
            type = PacketType.MSG, ttl = Packet.DEFAULT_TTL,
            messageId = UUID.randomUUID(), senderId = myDeviceId, destId = destId,
            timestampMs = System.currentTimeMillis(), payload = sealed,
        )
        return sendRouted(destId, null, packet)
    }

    private suspend fun applyReactionLocal(msgId: String, emoji: String, deviceId: String, add: Boolean) {
        val msg = messageDao.getById(msgId) ?: return
        val map = msg.reactions().mapValues { it.value.toMutableSet() }.toMutableMap()
        if (add) map.getOrPut(emoji) { mutableSetOf() }.add(deviceId)
        else {
            map[emoji]?.remove(deviceId)
            if (map[emoji].isNullOrEmpty()) map.remove(emoji)
        }
        messageDao.setReactions(msgId, reactionsToJson(map.mapValues { it.value.toSet() }))
    }

    private suspend fun sendReadReceipt(m: MessageEntity) {
        val destId = runCatching { UUID.fromString(m.senderId) }.getOrNull() ?: return
        // Don't send receipts for our own messages or non-chat traffic.
        if (m.isMine) return
        scope.launch {
            sendControlMessage(destId, MessageEnvelope(kind = "read", target = m.msgId))
        }
    }

    // ------------------------------------------------------------ groups

    suspend fun createGroup(name: String, memberDeviceIds: List<String>): Result<String> {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Group name needed"))
        if (memberDeviceIds.isEmpty()) return Result.failure(IllegalArgumentException("Add at least one member"))
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        groupDao.upsertGroup(
            GroupEntity(groupId, clean, myDeviceId.toString(), now, iAmAdmin = true),
        )
        val members = mutableListOf<GroupMemberEntity>()
        members += GroupMemberEntity(groupId, myDeviceId.toString(), "You", "", isAdmin = true)
        for (id in memberDeviceIds.distinct()) {
            val c = contactDao.get(id) ?: continue
            members += GroupMemberEntity(groupId, id, c.displayName, c.publicKeyB64, isAdmin = false)
        }
        groupDao.upsertMembers(members)
        // Tell members about the group (they create it locally on receipt).
        syncGroup(groupId)
        return Result.success(groupId)
    }

    /** Admin pushes the current member list to all members. */
    suspend fun syncGroup(groupId: String) {
        val group = groupDao.getGroup(groupId) ?: return
        val members = groupDao.membersOnce(groupId).filter { it.deviceId != myDeviceId.toString() }
        if (members.isEmpty()) return
        val packed = packMembers(
            members.map { Triple(it.deviceId, it.displayName, it.publicKeyB64) } +
                Triple(myDeviceId.toString(), "You", ""),
            admins = members.filter { it.isAdmin }.map { it.deviceId }.toSet() +
                myDeviceId.toString(),
        )
        val env = MessageEnvelope(kind = "gsync", group = groupId, groupName = group.name, members = packed)
        sendToGroupMembers(groupId, env, trackMsgId = UUID.randomUUID())
    }

    suspend fun addGroupMember(groupId: String, deviceId: String): Boolean {
        val group = groupDao.getGroup(groupId) ?: return false
        if (!group.iAmAdmin) return false
        val c = contactDao.get(deviceId) ?: return false
        groupDao.upsertMember(GroupMemberEntity(groupId, deviceId, c.displayName, c.publicKeyB64))
        syncGroup(groupId)
        return true
    }

    suspend fun removeGroupMember(groupId: String, deviceId: String): Boolean {
        val group = groupDao.getGroup(groupId) ?: return false
        if (!group.iAmAdmin || deviceId == myDeviceId.toString()) return false
        groupDao.removeMember(groupId, deviceId)
        syncGroup(groupId)
        return true
    }

    suspend fun sendGroupMessage(
        groupId: String,
        text: String,
        replyTo: MessageEntity? = null,
        forwarded: Boolean = false,
    ): Result<Unit> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty message"))
        val group = groupDao.getGroup(groupId)
            ?: return Result.failure(IllegalStateException("Group not found"))
        val env = MessageEnvelope(
            kind = "chat", text = trimmed,
            replyTo = replyTo?.msgId,
            replyBody = replyTo?.body?.take(120),
            replySender = replyTo?.let {
                if (it.isMine) "You" else runCatching { displayNameOf(it.senderId) }.getOrDefault("?")
            },
            forwarded = forwarded, group = groupId, groupName = group.name,
        )
        val msgId = UUID.randomUUID()
        val now = System.currentTimeMillis()
        messageDao.insert(
            MessageEntity(
                msgId = msgId.toString(), conversationId = GROUP_PREFIX + groupId,
                senderId = myDeviceId.toString(), destId = groupId,
                body = trimmed, timestampMs = now, status = MessageStatus.PENDING,
                isMine = true, ttl = Packet.DEFAULT_TTL.toInt(), hops = 0,
                replyToId = replyTo?.msgId, replyToBody = replyTo?.body?.take(120),
                replyToSender = replyTo?.let { if (it.isMine) "You" else "?" },
                forwarded = forwarded,
            ),
        )
        val sent = sendToGroupMembers(groupId, env, trackMsgId = msgId)
        if (sent > 0) {
            messageDao.setStatus(msgId.toString(), MessageStatus.SENT)
            Result.success(Unit)
        } else {
            _events.tryEmit("no members reachable — will retry")
            Result.success(Unit)
        }
        return Result.success(Unit)
    }

    /**
     * Fan-out: encrypts the envelope separately for every member (except me)
     * and sends. Returns how many members accepted the packet.
     */
    private suspend fun sendToGroupMembers(
        groupId: String,
        env: MessageEnvelope,
        trackMsgId: UUID,
        trackAck: Boolean = true,
    ): Int {
        val members = groupDao.membersOnce(groupId).filter { it.deviceId != myDeviceId.toString() }
        var sent = 0
        var firstPacket: ByteArray? = null
        var firstDest: UUID? = null
        val now = System.currentTimeMillis()
        for (m in members) {
            val destId = runCatching { UUID.fromString(m.deviceId) }.getOrNull() ?: continue
            val keys = sessionKeysFor(destId, m.publicKeyB64) ?: continue
            val sealed = crypto.encrypt(keys, env.encode()) ?: continue
            val packet = Packet.encode(
                type = PacketType.MSG, ttl = Packet.DEFAULT_TTL,
                messageId = trackMsgId, senderId = myDeviceId, destId = destId,
                timestampMs = now, payload = sealed,
            )
            if (sendRouted(destId, null, packet)) {
                sent++
                if (firstPacket == null) {
                    firstPacket = packet
                    firstDest = destId
                }
            }
        }
        // Track one representative for ACK/retry; first ACK marks DELIVERED.
        if (trackAck && firstPacket != null && firstDest != null) {
            ackTracker.track(trackMsgId, firstDest, firstPacket, now)
        }
        return sent
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
                val keys = sessionKeysFor(p.senderId, contact.publicKeyB64) ?: return
                val plain = crypto.decrypt(keys, p.payload) ?: return // auth failed: drop
                val env = MessageEnvelope.decode(plain)
                when (env.kind) {
                    "chat" -> handleChatMessage(p, env, contact.displayName)
                    "react" -> handleReaction(p, env)
                    "read" -> handleReadReceipt(p, env)
                    "gsync" -> handleGroupSync(p, env, contact.displayName)
                    "connreq" -> handleConnRequest(p)
                    "connacc" -> handleConnAccepted(p, contact)
                    "conndec" -> Unit // declined: stay silent
                }
                sendAck(p.senderId, p.messageId)
            }
            PacketType.ACK -> {
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

    private suspend fun handleChatMessage(p: DecodedPacket, env: MessageEnvelope, senderName: String) {
        val groupId = env.group
        if (groupId != null) {
            // Ensure the group exists locally.
            if (groupDao.getGroup(groupId) == null) {
                groupDao.upsertGroup(
                    GroupEntity(groupId, env.groupName ?: "Group", p.senderId.toString(), p.timestampMs),
                )
                val contact = contactDao.get(p.senderId.toString())
                groupDao.upsertMember(
                    GroupMemberEntity(
                        groupId, p.senderId.toString(), senderName,
                        contact?.publicKeyB64 ?: "", isAdmin = true,
                    ),
                )
            }
            messageDao.insert(
                MessageEntity(
                    msgId = p.messageId.toString(), conversationId = GROUP_PREFIX + groupId,
                    senderId = p.senderId.toString(), destId = groupId,
                    body = env.text, timestampMs = p.timestampMs,
                    status = MessageStatus.RECEIVED, isMine = false,
                    ttl = p.ttl.toInt(), hops = 0, isRead = false,
                    replyToId = env.replyTo, replyToBody = env.replyBody,
                    replyToSender = env.replySender, forwarded = env.forwarded,
                ),
            )
            _notifications.tryEmit(
                NotifyEvent(env.groupName ?: "Group", "$senderName: ${env.text.take(80)}", GROUP_PREFIX + groupId),
            )
        } else {
            messageDao.insert(
                MessageEntity(
                    msgId = p.messageId.toString(), conversationId = p.senderId.toString(),
                    senderId = p.senderId.toString(), destId = myDeviceId.toString(),
                    body = env.text, timestampMs = p.timestampMs,
                    status = MessageStatus.RECEIVED, isMine = false,
                    ttl = p.ttl.toInt(), hops = 0, isRead = false,
                    replyToId = env.replyTo, replyToBody = env.replyBody,
                    replyToSender = env.replySender, forwarded = env.forwarded,
                ),
            )
            _notifications.tryEmit(
                NotifyEvent(senderName, env.text.take(120), p.senderId.toString()),
            )
        }
    }

    private suspend fun handleReaction(p: DecodedPacket, env: MessageEnvelope) {
        val target = env.target ?: return
        val emoji = env.emoji ?: return
        applyReactionLocal(target, emoji, p.senderId.toString(), env.add)
    }

    private suspend fun handleReadReceipt(p: DecodedPacket, env: MessageEnvelope) {
        val target = env.target ?: return
        // Only my sent messages can be marked read by someone else's receipt.
        val msg = messageDao.getById(target) ?: return
        if (!msg.isMine || msg.senderId != myDeviceId.toString()) return
        messageDao.markMyMessageRead(target)
    }

    private suspend fun handleGroupSync(p: DecodedPacket, env: MessageEnvelope, senderName: String) {
        val groupId = env.group ?: return
        val packed = env.members ?: return
        val specs: List<GroupMemberSpec> = unpackMembers(packed)
        if (specs.isEmpty()) return
        val existing = groupDao.getGroup(groupId)
        groupDao.upsertGroup(
            GroupEntity(
                groupId, env.groupName ?: existing?.name ?: "Group",
                p.senderId.toString(), p.timestampMs,
                iAmAdmin = existing?.iAmAdmin == true,
            ),
        )
        // Replace member list with the admin's authoritative copy.
        val current = groupDao.membersOnce(groupId)
        for (c in current) groupDao.removeMember(groupId, c.deviceId)
        groupDao.upsertMembers(
            specs.map {
                GroupMemberEntity(
                    groupId, it.deviceId,
                    it.displayName.ifBlank { senderName },
                    it.publicKeyB64, it.isAdmin,
                )
            },
        )
        _events.tryEmit("group updated: ${env.groupName}")
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
            messageId = msgId,
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

    private suspend fun drainQueue() {
        val now = System.currentTimeMillis()
        relayQueueDao.deleteExpired(now)
        for (entry in relayQueueDao.pending(now)) {
            val decoded = Packet.decode(entry.packet)
            if (decoded == null) {
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
                ttl = 1,
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
