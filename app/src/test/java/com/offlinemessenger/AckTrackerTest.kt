package com.offlinemessenger

import com.offlinemessenger.relay.AckTracker
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AckTrackerTest {

    @Test
    fun `ack resolves pending and returns destination`() {
        val tracker = AckTracker(ackTimeoutMs = 1_000, maxAttempts = 3)
        val id = UUID.randomUUID()
        val dest = UUID.randomUUID()
        tracker.track(id, dest, ByteArray(8), nowMs = 0)
        assertTrue(tracker.isPending(id))
        assertEquals(dest, tracker.onAck(id))
        assertFalse(tracker.isPending(id))
    }

    @Test
    fun `unknown ack resolves nothing`() {
        val tracker = AckTracker()
        assertNull(tracker.onAck(UUID.randomUUID()))
    }

    @Test
    fun `no retry before timeout`() {
        val tracker = AckTracker(ackTimeoutMs = 10_000, maxAttempts = 3)
        tracker.track(UUID.randomUUID(), UUID.randomUUID(), ByteArray(8), nowMs = 0)
        assertTrue(tracker.dueRetries(nowMs = 9_999).isEmpty())
        assertEquals(1, tracker.dueRetries(nowMs = 10_000).size)
    }

    @Test
    fun `retries bounded by max attempts then exhausted`() {
        val tracker = AckTracker(ackTimeoutMs = 1_000, maxAttempts = 2)
        val id = UUID.randomUUID()
        tracker.track(id, UUID.randomUUID(), ByteArray(8), nowMs = 0)
        // First timeout: one retry due.
        val due = tracker.dueRetries(nowMs = 1_000)
        assertEquals(1, due.size)
        tracker.recordAttempt(id, nowMs = 1_000)
        // Second timeout: attempts exhausted, not "due" anymore.
        assertTrue(tracker.dueRetries(nowMs = 2_000).isEmpty())
        val dead = tracker.exhausted(nowMs = 2_000)
        assertEquals(1, dead.size)
        assertEquals(id, dead[0].msgId)
        assertFalse(tracker.isPending(id))
    }

    @Test
    fun `cancel removes pending`() {
        val tracker = AckTracker()
        val id = UUID.randomUUID()
        tracker.track(id, UUID.randomUUID(), ByteArray(8))
        tracker.cancel(id)
        assertFalse(tracker.isPending(id))
        assertEquals(0, tracker.pendingCount())
    }
}
