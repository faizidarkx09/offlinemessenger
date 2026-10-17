package com.offlinemessenger

import com.offlinemessenger.protocol.DecodedPacket
import com.offlinemessenger.protocol.Packet
import com.offlinemessenger.protocol.PacketType
import com.offlinemessenger.relay.RelayEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class RoutingTest {

    private lateinit var engine: RelayEngine
    private val me = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()

    @Before
    fun setup() {
        engine = RelayEngine()
    }

    private fun msg(
        msgId: UUID = UUID.randomUUID(),
        dest: UUID = carol,
        ttl: Byte = Packet.DEFAULT_TTL,
    ) = DecodedPacket(
        type = PacketType.MSG,
        ttl = ttl,
        relayed = false,
        messageId = msgId,
        senderId = alice,
        destId = dest,
        timestampMs = System.currentTimeMillis(),
        payload = ByteArray(10),
    )

    @Test
    fun `direct peer wins over learned route`() {
        val routes = mapOf(carol to RelayEngine.Route(bob, 2))
        val d = engine.decide(msg(), fromPeer = bob, myId = me, directPeers = setOf(carol), routes = routes)
        assertEquals(RelayEngine.Action.FORWARD_DIRECT, d.action)
        assertEquals(carol, d.nextHopId)
    }

    @Test
    fun `learned route used when destination not direct`() {
        val routes = mapOf(carol to RelayEngine.Route(bob, 2))
        val d = engine.decide(msg(), fromPeer = alice, myId = me, directPeers = setOf(bob), routes = routes)
        assertEquals(RelayEngine.Action.FORWARD_ROUTE, d.action)
        assertEquals(bob, d.nextHopId)
    }

    @Test
    fun `route via the peer it came from is ignored to prevent loops`() {
        // Only route to carol goes back through bob, who sent it to us.
        val routes = mapOf(carol to RelayEngine.Route(bob, 2))
        val d = engine.decide(msg(), fromPeer = bob, myId = me, directPeers = setOf(bob), routes = routes)
        assertEquals(RelayEngine.Action.FLOOD, d.action)
    }

    @Test
    fun `floods when no route known`() {
        val d = engine.decide(msg(), fromPeer = alice, myId = me, directPeers = setOf(bob), routes = emptyMap())
        assertEquals(RelayEngine.Action.FLOOD, d.action)
    }

    @Test
    fun `drops when ttl exhausted`() {
        val d = engine.decide(
            msg(ttl = 0), fromPeer = alice, myId = me,
            directPeers = setOf(carol), routes = emptyMap(),
        )
        assertEquals(RelayEngine.Action.DROP, d.action)
    }

    @Test
    fun `drops when no peers at all`() {
        val d = engine.routeFor(carol, Packet.DEFAULT_TTL, null, emptySet(), emptyMap())
        assertEquals(RelayEngine.Action.DROP, d.action)
    }

    @Test
    fun `aged decrements ttl and sets relayed flag`() {
        val aged = engine.aged(msg(ttl = 3))
        assertEquals(2.toByte(), aged.ttl)
        assertTrue(aged.relayed)
    }

    @Test
    fun `rate limiter caps forwards per peer per second`() {
        val now = 1_000_000L
        repeat(20) { assertTrue(engine.canForward(bob, now + it)) }
        assertFalse(engine.canForward(bob, now + 20))
        // Next window allows again.
        assertTrue(engine.canForward(bob, now + 1_001))
    }
}
