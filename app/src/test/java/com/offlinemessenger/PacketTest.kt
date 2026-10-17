package com.offlinemessenger

import com.offlinemessenger.protocol.Packet
import com.offlinemessenger.protocol.PacketType
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class PacketTest {

    private fun samplePacket(ttl: Byte = Packet.DEFAULT_TTL): ByteArray =
        Packet.encode(
            type = PacketType.MSG,
            ttl = ttl,
            messageId = UUID.randomUUID(),
            senderId = UUID.randomUUID(),
            destId = UUID.randomUUID(),
            timestampMs = System.currentTimeMillis(),
            payload = "hello".toByteArray(),
        )

    @Test
    fun `encode decode roundtrip preserves every field`() {
        val msgId = UUID.randomUUID()
        val sender = UUID.randomUUID()
        val dest = UUID.randomUUID()
        val now = System.currentTimeMillis()
        val payload = ByteArray(512) { it.toByte() }
        val raw = Packet.encode(PacketType.MSG, 3, msgId, sender, dest, now, payload, relayed = true)
        val d = Packet.decode(raw)!!
        assertEquals(PacketType.MSG, d.type)
        assertEquals(3.toByte(), d.ttl)
        assertTrue(d.relayed)
        assertEquals(msgId, d.messageId)
        assertEquals(sender, d.senderId)
        assertEquals(dest, d.destId)
        assertEquals(now, d.timestampMs)
        assertArrayEquals(payload, d.payload)
    }

    @Test
    fun `rejects wrong version`() {
        val raw = samplePacket()
        raw[0] = 0x02
        assertNull(Packet.decode(raw))
    }

    @Test
    fun `rejects unknown type`() {
        val raw = samplePacket()
        raw[1] = 0x7F
        assertNull(Packet.decode(raw))
    }

    @Test
    fun `rejects ttl above max`() {
        val raw = samplePacket()
        raw[2] = (Packet.MAX_TTL + 1).toByte()
        assertNull(Packet.decode(raw))
    }

    @Test
    fun `rejects truncated packet`() {
        val raw = samplePacket()
        assertNull(Packet.decode(raw.copyOf(raw.size - 3)))
    }

    @Test
    fun `rejects length mismatch`() {
        val raw = samplePacket()
        // Corrupt the declared payload length (+1) without changing the bytes.
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
        val declared = buf.getInt(60)
        buf.putInt(60, declared + 1)
        assertNull(Packet.decode(raw))
    }

    @Test
    fun `rejects far-future timestamp as replay protection`() {
        val raw = Packet.encode(
            PacketType.MSG, Packet.DEFAULT_TTL, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), System.currentTimeMillis() + Packet.MAX_CLOCK_SKEW_MS + 1,
            ByteArray(0),
        )
        assertNull(Packet.decode(raw))
    }

    @Test
    fun `rejects ancient timestamp`() {
        val raw = Packet.encode(
            PacketType.MSG, Packet.DEFAULT_TTL, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), System.currentTimeMillis() - Packet.MAX_CLOCK_SKEW_MS - 1,
            ByteArray(0),
        )
        assertNull(Packet.decode(raw))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode refuses oversize payload`() {
        Packet.encode(
            PacketType.MSG, Packet.DEFAULT_TTL, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), System.currentTimeMillis(),
            ByteArray(Packet.MAX_PAYLOAD_SIZE + 1),
        )
    }

    @Test
    fun `broadcast id is all zeros`() {
        assertEquals(UUID(0L, 0L), Packet.BROADCAST_ID)
    }
}
