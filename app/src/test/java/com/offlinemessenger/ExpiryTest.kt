package com.offlinemessenger

import com.offlinemessenger.protocol.Packet
import com.offlinemessenger.protocol.PacketType
import com.offlinemessenger.repository.ChatRepository
import com.offlinemessenger.repository.PeerRepository
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Expiry semantics:
 * - Packets carry a timestamp; anything outside ±24h is rejected on decode
 *   (replay protection), so stale traffic can never be re-injected.
 * - Relay-queue rows expire after [ChatRepository.MESSAGE_TTL_MS] (24h).
 * - Learned routes expire after [PeerRepository.ROUTE_TTL_MS] (2 min).
 */
class ExpiryTest {

    private fun packetAt(ts: Long) = Packet.encode(
        PacketType.MSG, Packet.DEFAULT_TTL, UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), ts, ByteArray(4),
    )

    @Test
    fun `packet exactly at skew boundary is accepted`() {
        val now = System.currentTimeMillis()
        assertNotNull(Packet.decode(packetAt(now - Packet.MAX_CLOCK_SKEW_MS), now))
        assertNotNull(Packet.decode(packetAt(now + Packet.MAX_CLOCK_SKEW_MS), now))
    }

    @Test
    fun `packet beyond skew boundary is rejected`() {
        val now = System.currentTimeMillis()
        assertNull(Packet.decode(packetAt(now - Packet.MAX_CLOCK_SKEW_MS - 1), now))
        assertNull(Packet.decode(packetAt(now + Packet.MAX_CLOCK_SKEW_MS + 1), now))
    }

    @Test
    fun `relay queue ttl is 24 hours`() {
        assertEquals(24 * 60 * 60 * 1000L, ChatRepository.MESSAGE_TTL_MS)
    }

    @Test
    fun `route ttl is 2 minutes`() {
        assertEquals(120_000L, PeerRepository.ROUTE_TTL_MS)
    }

    @Test
    fun `max ttl bounds hop count`() {
        assertTrue(Packet.MAX_TTL <= 5)
        assertTrue(Packet.DEFAULT_TTL <= Packet.MAX_TTL)
    }
}
