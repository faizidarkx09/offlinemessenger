package com.offlinemessenger

import com.offlinemessenger.crypto.CryptoManager
import com.offlinemessenger.crypto.IdentityStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.UUID

/**
 * End-to-end crypto round trip (X25519 key exchange + XSalsa20-Poly1305),
 * running on the plain JVM against the REAL BouncyCastle implementation.
 *
 * Pure-Java crypto means these tests exercise the exact code paths the app
 * uses on-device — no native library, no emulator needed.
 */
class CryptoRoundTripTest {

    private class FakeStore(val id: UUID = UUID.randomUUID()) : IdentityStore {
        private var kp: Pair<ByteArray, ByteArray>? = null
        override fun deviceId(): UUID = id
        override fun keyPair(): Pair<ByteArray, ByteArray>? = kp
        override fun storeKeyPair(publicKey: ByteArray, secretKey: ByteArray) {
            kp = publicKey to secretKey
        }
    }

    private fun twoDevices(): Triple<CryptoManager, CryptoManager, Pair<FakeStore, FakeStore>> {
        val storeA = FakeStore()
        val storeB = FakeStore()
        return Triple(CryptoManager(storeA), CryptoManager(storeB), storeA to storeB)
    }

    @Test
    fun `two parties derive matching session keys`() {
        val (a, b, stores) = twoDevices()
        val (storeA, storeB) = stores
        val keysA = assertNotNull(a.sessionKeys(storeB.id, b.publicKey))
        val keysB = assertNotNull(b.sessionKeys(storeA.id, a.publicKey))
        // A's transmit key is B's receive key and vice versa.
        assertArrayEquals(keysA.tx, keysB.rx)
        assertArrayEquals(keysA.rx, keysB.tx)
        assertFalse(keysA.rx.contentEquals(keysA.tx))
    }

    @Test
    fun `session keys agree regardless of device id order`() {
        val low = FakeStore(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val high = FakeStore(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
        val a = CryptoManager(low)
        val b = CryptoManager(high)
        val keysA = assertNotNull(a.sessionKeys(high.id, b.publicKey))
        val keysB = assertNotNull(b.sessionKeys(low.id, a.publicKey))
        assertArrayEquals(keysA.tx, keysB.rx)
        assertArrayEquals(keysA.rx, keysB.tx)
    }

    @Test
    fun `message round trip in both directions`() {
        val (a, b, stores) = twoDevices()
        val (storeA, storeB) = stores
        val keysA = assertNotNull(a.sessionKeys(storeB.id, b.publicKey))
        val keysB = assertNotNull(b.sessionKeys(storeA.id, a.publicKey))
        val msg = "Assalam-o-Alaikum, offline test 123".toByteArray()

        val sealedAB = assertNotNull(a.encrypt(keysA, msg))
        assertEquals(24 + 16 + msg.size, sealedAB.size)
        assertArrayEquals(msg, assertNotNull(b.decrypt(keysB, sealedAB)))

        val sealedBA = assertNotNull(b.encrypt(keysB, msg))
        assertArrayEquals(msg, assertNotNull(a.decrypt(keysA, sealedBA)))
    }

    @Test
    fun `tampered or truncated messages are rejected`() {
        val (a, b, stores) = twoDevices()
        val (storeA, storeB) = stores
        val keysA = assertNotNull(a.sessionKeys(storeB.id, b.publicKey))
        val keysB = assertNotNull(b.sessionKeys(storeA.id, a.publicKey))
        val sealed = assertNotNull(a.encrypt(keysA, "secret".toByteArray()))

        val tamperedMac = sealed.copyOf().also { it[30] = (it[30] + 1).toByte() }
        assertNull(b.decrypt(keysB, tamperedMac))

        val tamperedCipher = sealed.copyOf().also { it[50] = (it[50] + 1).toByte() }
        assertNull(b.decrypt(keysB, tamperedCipher))

        val tamperedNonce = sealed.copyOf().also { it[5] = (it[5] + 1).toByte() }
        assertNull(b.decrypt(keysB, tamperedNonce))

        assertNull(b.decrypt(keysB, sealed.copyOf(sealed.size - 1)))
        assertNull(b.decrypt(keysB, ByteArray(10)))
    }

    @Test
    fun `wrong key cannot decrypt`() {
        val (a, b, stores) = twoDevices()
        val (_, storeB) = stores
        val keysA = assertNotNull(a.sessionKeys(storeB.id, b.publicKey))
        val sealed = assertNotNull(a.encrypt(keysA, "secret".toByteArray()))

        val stranger = CryptoManager(FakeStore())
        val wrongKeys = assertNotNull(stranger.sessionKeys(storeB.id, b.publicKey))
        assertNull(stranger.decrypt(wrongKeys, sealed))
    }

    @Test
    fun `malformed peer key is rejected`() {
        val (a, _, _) = twoDevices()
        assertNull(a.sessionKeys(UUID.randomUUID(), ByteArray(16)))
        assertNull(a.sessionKeys(UUID.randomUUID(), ByteArray(0)))
    }

    @Test
    fun `keypair is generated once and reused`() {
        val store = FakeStore()
        val cm = CryptoManager(store)
        val first = cm.publicKey
        val second = cm.publicKey
        assertArrayEquals(first, second)
        assertNotNull(store.keyPair())
        assertEquals(32, first.size)
    }
}
