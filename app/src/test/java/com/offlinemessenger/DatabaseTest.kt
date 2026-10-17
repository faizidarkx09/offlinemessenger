package com.offlinemessenger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.offlinemessenger.data.local.AppDatabase
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.MessageStatus
import com.offlinemessenger.data.local.entity.RelayQueueEntity
import com.offlinemessenger.data.local.entity.RouteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room DAO tests against an in-memory database (Robolectric provides the
 * Android framework pieces Room needs at runtime).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun message(id: String, convo: String) = MessageEntity(
        msgId = id,
        conversationId = convo,
        senderId = "sender",
        destId = "dest",
        body = "hello $id",
        timestampMs = System.currentTimeMillis(),
        status = MessageStatus.RECEIVED,
        isMine = false,
        ttl = 3,
        hops = 0,
        isRead = false,
    )

    @Test
    fun `insert and read message`() = runTest {
        val dao = db.messageDao()
        dao.insert(message("m1", "c1"))
        assertEquals("hello m1", dao.getById("m1")!!.body)
    }

    @Test
    fun `duplicate message id is ignored at db level`() = runTest {
        val dao = db.messageDao()
        dao.insert(message("dup", "c1"))
        dao.insert(message("dup", "c1").copy(body = "second"))
        assertEquals("hello dup", dao.getById("dup")!!.body)
    }

    @Test
    fun `status update works`() = runTest {
        val dao = db.messageDao()
        dao.insert(message("s1", "c1").copy(isMine = true, status = MessageStatus.SENT))
        dao.setStatus("s1", MessageStatus.DELIVERED)
        assertEquals(MessageStatus.DELIVERED, dao.getById("s1")!!.status)
    }

    @Test
    fun `unread count and mark read`() = runTest {
        val dao = db.messageDao()
        dao.insert(message("u1", "c1"))
        dao.insert(message("u2", "c1"))
        assertEquals(2, dao.unreadCount("c1").first())
        dao.markRead("c1")
        assertEquals(0, dao.unreadCount("c1").first())
    }

    @Test
    fun `relay queue pending filters expired rows`() = runTest {
        val dao = db.relayQueueDao()
        val now = System.currentTimeMillis()
        dao.enqueue(RelayQueueEntity(msgId = "live", destId = "d", packet = ByteArray(4), createdAtMs = now, expiresAtMs = now + 60_000))
        dao.enqueue(RelayQueueEntity(msgId = "dead", destId = "d", packet = ByteArray(4), createdAtMs = now - 120_000, expiresAtMs = now - 1))
        val pending = dao.pending(now)
        assertEquals(1, pending.size)
        assertEquals("live", pending[0].msgId)
        assertEquals(1, dao.deleteExpired(now))
        assertEquals(1, dao.count())
    }

    @Test
    fun `route upsert replaces and get respects expiry`() = runTest {
        val dao = db.routeDao()
        val now = System.currentTimeMillis()
        dao.upsert(RouteEntity("dest", "via", 2, now + 60_000))
        assertEquals(2, dao.get("dest", now)!!.hops)
        dao.upsert(RouteEntity("dest", "via2", 1, now + 60_000))
        assertEquals("via2", dao.get("dest", now)!!.nextHopId)
        assertNull(dao.get("dest", now + 61_000))
    }

    @Test
    fun `contact upsert and verify`() = runTest {
        val dao = db.contactDao()
        dao.upsert(ContactEntity("d1", "Alice", "aGVsbG8=", "fp", verified = false))
        assertFalse(dao.get("d1")!!.verified)
        dao.setVerified("d1")
        assertTrue(dao.get("d1")!!.verified)
    }
}
