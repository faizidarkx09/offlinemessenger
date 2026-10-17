package com.offlinemessenger.relay

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks outbound messages awaiting acknowledgement.
 *
 * A message is NEVER reported delivered before the destination's ACK
 * arrives. Timeouts trigger bounded retries; exhaustion marks failure.
 */
class AckTracker(
    val ackTimeoutMs: Long = 10_000L,
    val maxAttempts: Int = 3,
) {
    data class Pending(
        val msgId: UUID,
        val destId: UUID,
        val packet: ByteArray,
        val attempts: Int,
        val lastSentMs: Long,
    )

    private val pending = ConcurrentHashMap<UUID, Pending>()

    fun track(msgId: UUID, destId: UUID, packet: ByteArray, nowMs: Long = System.currentTimeMillis()) {
        pending[msgId] = Pending(msgId, destId, packet, attempts = 1, lastSentMs = nowMs)
    }

    /** Returns the destination id if this ACK resolves a pending message. */
    fun onAck(msgId: UUID): UUID? = pending.remove(msgId)?.destId

    fun isPending(msgId: UUID): Boolean = pending.containsKey(msgId)

    fun pendingCount(): Int = pending.size

    /** Entries whose timeout elapsed and still have attempts left. */
    fun dueRetries(nowMs: Long = System.currentTimeMillis()): List<Pending> =
        pending.values.filter { nowMs - it.lastSentMs >= ackTimeoutMs && it.attempts < maxAttempts }

    /** Entries that exhausted all attempts — the caller marks them failed. */
    fun exhausted(nowMs: Long = System.currentTimeMillis()): List<Pending> =
        pending.values.filter { nowMs - it.lastSentMs >= ackTimeoutMs && it.attempts >= maxAttempts }
            .also { list -> list.forEach { pending.remove(it.msgId) } }

    fun recordAttempt(msgId: UUID, nowMs: Long = System.currentTimeMillis()) {
        pending.computeIfPresent(msgId) { _, p -> p.copy(attempts = p.attempts + 1, lastSentMs = nowMs) }
    }

    fun cancel(msgId: UUID) {
        pending.remove(msgId)
    }
}
