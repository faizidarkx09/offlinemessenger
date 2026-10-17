package com.offlinemessenger

import com.offlinemessenger.protocol.DecodedPacket
import com.offlinemessenger.protocol.Packet
import com.offlinemessenger.protocol.PacketType
import com.offlinemessenger.relay.RelayEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DuplicateSuppressionTest {

    private val me = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()

    private fun msg(msgId: UUID) = DecodedPacket(
        type = PacketType.MSG,
        ttl = Packet.DEFAULT_TTL,
        relayed = false,
        messageId = msgId,
        senderId = alice,
        destId = carol,
        timestampMs = System.currentTimeMillis(),
        payload = ByteArray(4),
    )

    @Test
    fun `second sighting of same message is dropped`() {
        val engine = RelayEngine()
        val id = UUID.randomUUID()
        val first = engine.decide(msg(id), bob, me, setOf(bob), emptyMap())
        assertNotEquals(RelayEngine.Action.DROP, first.action)
        val second = engine.decide(msg(id), bob, me, setOf(bob), emptyMap())
        assertEquals(RelayEngine.Action.DROP, second.action)
    }

    @Test
    fun `haveSeen and markSeen agree`() {
        val engine = RelayEngine()
        val id = UUID.randomUUID()
        assertFalse(engine.haveSeen(id))
        engine.markSeen(id)
        assertTrue(engine.haveSeen(id))
    }

    @Test
    fun `seen cache evicts oldest entries past capacity`() {
        val engine = RelayEngine(maxSeen = 10)
        val ids = (0 until 12).map { UUID.randomUUID() }
        ids.forEach { engine.markSeen(it) }
        assertEquals(10, engine.seenCount())
        // The two oldest were evicted.
        assertFalse(engine.haveSeen(ids[0]))
        assertFalse(engine.haveSeen(ids[1]))
        assertTrue(engine.haveSeen(ids[11]))
    }
}
