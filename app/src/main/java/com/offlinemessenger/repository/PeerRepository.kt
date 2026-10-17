package com.offlinemessenger.repository

import android.util.Base64
import com.offlinemessenger.ble.BleTransport
import com.offlinemessenger.crypto.CryptoManager
import com.offlinemessenger.data.local.dao.ContactDao
import com.offlinemessenger.data.local.dao.RouteDao
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.data.local.entity.RouteEntity
import com.offlinemessenger.protocol.DecodedPacket
import com.offlinemessenger.protocol.HelloPayload
import com.offlinemessenger.protocol.Packet
import com.offlinemessenger.protocol.RouteAdvert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Peer identity + topology. Handles HELLO (trust-on-first-use contact
 * creation) and ROUTE advertisements (distance-vector learning).
 */
class PeerRepository(
    private val contactDao: ContactDao,
    private val routeDao: RouteDao,
    private val transport: BleTransport,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope,
) {
    val contacts: Flow<List<ContactEntity>> = contactDao.all()

    companion object {
        /** Learned routes expire after 2 minutes without refresh. */
        const val ROUTE_TTL_MS = 120_000L
    }

    /**
     * Transport events this repository cares about. The transport supports a
     * single listener, so [ChatRepository] composes this with its own.
     */
    val transportListener = object : BleTransport.EventListener {
        override fun onHello(deviceId: UUID, hello: HelloPayload.Hello, address: String) {
            scope.launch { upsertFromHello(hello) }
        }

        override fun onPeerConnected(deviceId: UUID) {
            // Route invalidation + queue drain are handled by ChatRepository.
        }

        override fun onPeerDisconnected(deviceId: UUID) {
            scope.launch { routeDao.deleteVia(deviceId.toString()) }
        }
    }

    private suspend fun upsertFromHello(hello: HelloPayload.Hello) {
        if (hello.publicKey.size != CryptoManager.KX_PUBLIC_KEY_BYTES) return
        val id = hello.deviceId.toString()
        val existing = contactDao.get(id)
        val fingerprint = crypto.fingerprintOf(hello.publicKey)
        if (existing == null) {
            contactDao.upsert(
                ContactEntity(
                    deviceId = id,
                    displayName = hello.displayName,
                    publicKeyB64 = Base64.encodeToString(hello.publicKey, Base64.NO_WRAP),
                    fingerprint = fingerprint,
                    verified = false,
                    lastSeenMs = System.currentTimeMillis(),
                ),
            )
        } else {
            // Key rotation: accept the new key but drop verification (TOFU).
            val verified = existing.verified && existing.publicKeyB64 ==
                Base64.encodeToString(hello.publicKey, Base64.NO_WRAP)
            contactDao.upsert(
                existing.copy(
                    displayName = hello.displayName,
                    publicKeyB64 = Base64.encodeToString(hello.publicKey, Base64.NO_WRAP),
                    fingerprint = fingerprint,
                    verified = verified,
                    lastSeenMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun setVerified(deviceId: String) = contactDao.setVerified(deviceId)

    suspend fun deleteContact(deviceId: String) = contactDao.delete(deviceId)

    /** Learns routes from a neighbour's ROUTE advertisement. */
    suspend fun handleRouteAdvert(packet: DecodedPacket) {
        val entries = RouteAdvert.decode(packet.payload) ?: return
        val now = System.currentTimeMillis()
        val via = packet.senderId.toString()
        for (e in entries) {
            val dest = e.deviceId
            if (dest == transport.myDeviceId) continue
            if (transport.connectedDeviceIds().contains(dest)) continue // direct beats learned
            val hops = e.hops + 1
            if (hops > Packet.MAX_TTL) continue
            val current = routeDao.get(dest.toString(), now)
            if (current == null || hops < current.hops) {
                routeDao.upsert(
                    RouteEntity(
                        destId = dest.toString(),
                        nextHopId = via,
                        hops = hops,
                        expiresAtMs = now + ROUTE_TTL_MS,
                    ),
                )
            }
        }
    }

    /** Builds our ROUTE advertisement: direct peers + best learned routes. */
    suspend fun buildRouteAdvert(): ByteArray {
        val now = System.currentTimeMillis()
        val entries = mutableListOf<RouteAdvert.Entry>()
        transport.connectedDeviceIds()
            .filter { it != transport.myDeviceId }
            .forEach { entries.add(RouteAdvert.Entry(it, 1)) }
        routeDao.allValid(now).forEach { r ->
            entries.add(RouteAdvert.Entry(UUID.fromString(r.destId), r.hops))
        }
        return RouteAdvert.encode(entries)
    }

    suspend fun validRoutes(): List<RouteEntity> =
        routeDao.allValid(System.currentTimeMillis())

    suspend fun pruneExpired() {
        val now = System.currentTimeMillis()
        routeDao.deleteExpired(now)
    }
}
