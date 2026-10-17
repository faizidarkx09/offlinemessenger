package com.offlinemessenger

import com.offlinemessenger.protocol.MessageEnvelope
import com.offlinemessenger.protocol.packMembers
import com.offlinemessenger.protocol.unpackMembers
import org.junit.Assert.*
import org.junit.Test

/** Round-trip tests for the encrypted message envelope (JSON). */
class EnvelopeTest {

    @Test
    fun `chat round trip with reply and forward flag`() {
        val env = MessageEnvelope(
            kind = "chat",
            text = "hello \"world\"\nnew line",
            replyTo = "abc-123",
            replyBody = "original",
            replySender = "Ali",
            forwarded = true,
        )
        val back = MessageEnvelope.decode(env.encode())
        assertEquals("chat", back.kind)
        assertEquals("hello \"world\"\nnew line", back.text)
        assertEquals("abc-123", back.replyTo)
        assertEquals("original", back.replyBody)
        assertEquals("Ali", back.replySender)
        assertTrue(back.forwarded)
    }

    @Test
    fun `react round trip`() {
        val env = MessageEnvelope(kind = "react", target = "msg-1", emoji = "❤️", add = false)
        val back = MessageEnvelope.decode(env.encode())
        assertEquals("react", back.kind)
        assertEquals("msg-1", back.target)
        assertEquals("❤️", back.emoji)
        assertFalse(back.add)
    }

    @Test
    fun `legacy plaintext falls back to chat`() {
        val back = MessageEnvelope.decode("just plain text".toByteArray())
        assertEquals("chat", back.kind)
        assertEquals("just plain text", back.text)
    }

    @Test
    fun `group chat envelope carries group id`() {
        val env = MessageEnvelope(kind = "chat", text = "hi all", group = "g1", groupName = "Team")
        val back = MessageEnvelope.decode(env.encode())
        assertEquals("g1", back.group)
        assertEquals("Team", back.groupName)
    }

    @Test
    fun `member pack unpack round trip`() {
        val packed = packMembers(
            listOf(Triple("id1", "Ali", "keyA"), Triple("id2", "Sara", "keyB")),
            admins = setOf("id1"),
        )
        val specs = unpackMembers(packed)
        assertEquals(2, specs.size)
        assertEquals("id1", specs[0].deviceId)
        assertEquals("Ali", specs[0].displayName)
        assertEquals("keyA", specs[0].publicKeyB64)
        assertTrue(specs[0].isAdmin)
        assertFalse(specs[1].isAdmin)
    }

    @Test
    fun `decode never throws on garbage`() {
        val back = MessageEnvelope.decode(byteArrayOf(0x01, 0x02, 0x7F, 0x00))
        assertEquals("chat", back.kind)
    }
}
