package com.offlinemessenger.relay

import com.offlinemessenger.protocol.DecodedPacket
import com.offlinemessenger.protocol.Packet
import java.util.UUID

/**
 * Pure-Kotlin relay decision engine (no Android dependencies — unit tested).
 *
 * Rules:
 * - Never forward a packet we've already seen (duplicate suppression).
 * - Never forward when TTL is exhausted.
 * - Never send a packet back to the peer it came from (loop prevention).
 * - Prefer a direct link, then the best learned route, else bounded flood
 *   (TTL still applies, so flooding cannot run forever).
 * - Per-peer rate limiting protects the radio from storms.
 */
class RelayEngine(
    private val maxSeen: Int = 1_000,
    private val maxForwardsPerSecond: Int = 20,
) {
    enum class Action { DROP, FORWARD_DIRECT, FORWARD_ROUTE, FLOOD }

    data class Decision(val action: Action, val nextHopId: UUID? = null)

    data class Route(val nextHopId: UUID, val hops: Int)

    // Access-ordered LRU of seen message ids.
    private val seen: LinkedHashMap<UUID, Long> = object : LinkedHashMap<UUID, Long>(maxSeen, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Long>): Boolean =
            size > maxSeen
    }

    // Per-peer sliding-window timestamps for rate limiting.
    private val forwardTimes = mutableMapOf<UUID, ArrayDeque<Long>>()

    @Synchronized
    fun haveSeen(msgId: UUID): Boolean = seen.containsKey(msgId)

    @Synchronized
    fun markSeen(msgId: UUID) {
        seen[msgId] = System.currentTimeMillis()
    }

    /**
     * Decides how to handle a packet addressed to someone else.
     * Applies duplicate suppression, then delegates to [routeFor].
     */
    @Synchronized
    fun decide(
        packet: DecodedPacket,
        fromPeer: UUID,
        myId: UUID,
        directPeers: Set<UUID>,
        routes: Map<UUID, Route>,
    ): Decision {
        if (packet.destId == myId || packet.destId == Packet.BROADCAST_ID) return Decision(Action.DROP)
        if (haveSeen(packet.messageId)) return Decision(Action.DROP)
        markSeen(packet.messageId)
        return routeFor(
            destId = packet.destId,
            ttl = packet.ttl,
            fromPeer = fromPeer,
            directPeers = directPeers,
            routes = routes,
        )
    }

    /**
     * Pure next-hop selection without duplicate bookkeeping. Used for our own
     * outbound packets and for store-and-forward drains (the seen-cache must
     * NOT swallow those).
     */
    @Synchronized
    fun routeFor(
        destId: UUID,
        ttl: Byte,
        fromPeer: UUID?,
        directPeers: Set<UUID>,
        routes: Map<UUID, Route>,
    ): Decision {
        if (ttl <= 0) return Decision(Action.DROP)
        if (directPeers.contains(destId)) {
            return Decision(Action.FORWARD_DIRECT, destId)
        }
        val route = routes[destId]
        if (route != null && route.nextHopId != fromPeer && directPeers.contains(route.nextHopId)) {
            return Decision(Action.FORWARD_ROUTE, route.nextHopId)
        }
        // Bounded flood: TTL decrements at each hop, duplicates are suppressed.
        return if (directPeers.isEmpty()) Decision(Action.DROP) else Decision(Action.FLOOD)
    }

    /** Returns the packet with TTL decremented and the relayed flag set. */
    fun aged(packet: DecodedPacket): DecodedPacket =
        packet.copy(ttl = (packet.ttl - 1).toByte(), relayed = true)

    /** Sliding-window rate limit: at most [maxForwardsPerSecond] forwards/sec per peer. */
    @Synchronized
    fun canForward(peerId: UUID, nowMs: Long = System.currentTimeMillis()): Boolean {
        val window = forwardTimes.getOrPut(peerId) { ArrayDeque() }
        while (window.isNotEmpty() && nowMs - window.first() > 1_000) window.removeFirst()
        if (window.size >= maxForwardsPerSecond) return false
        window.addLast(nowMs)
        return true
    }

    @Synchronized
    fun seenCount(): Int = seen.size
}
