package com.offlinemessenger.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * End-to-end encryption — pure-Java implementation on BouncyCastle.
 * Deliberately zero native code / zero JNI: there is no native library to
 * fail loading on any device, which was the #1 crash source of v1.
 *
 * Design (NaCl/libsodium semantics, self-consistent protocol):
 * - Each device owns a long-term X25519 keypair, generated once with the OS
 *   CSPRNG and stored in [SecureStore] (Keystore-backed).
 * - Peers exchange public keys inside HELLO packets; trust is
 *   trust-on-first-use, upgradeable to "verified" by comparing key
 *   fingerprints out-of-band (see Contacts screen).
 * - Per-peer session keys: X25519 Diffie-Hellman shared secret, then
 *   Blake2b-512 keyed by the shared secret over
 *   ("OM-KX-v1" || client_pk || server_pk). The 64-byte output splits into
 *   rx/tx; the client (smaller device id) takes rx=first half / tx=second
 *   half, the server takes them swapped — both sides derive identical keys
 *   with zero extra round trips.
 * - Message payloads use XSalsa20-Poly1305 secretbox (exact NaCl
 *   construction) with a fresh random 24-byte nonce per message.
 *   Output: nonce(24) || mac(16) || ciphertext.
 *
 * Relay nodes only ever see opaque ciphertext + routing headers. They learn
 * sender/destination ids (routing metadata) but never plaintext.
 */
class CryptoManager(private val store: IdentityStore) {

    data class SessionKeys(val rx: ByteArray, val tx: ByteArray)

    companion object {
        const val KX_PUBLIC_KEY_BYTES = 32
        const val KX_SECRET_KEY_BYTES = 32
        const val SESSION_KEY_BYTES = 32
        const val SECRETBOX_NONCE_BYTES = 24
        const val SECRETBOX_MAC_BYTES = 16
        private val KDF_DOMAIN = "OM-KX-v1".toByteArray(Charsets.UTF_8)
        private val secureRandom = SecureRandom()
    }

    /** This device's long-term X25519 public key (shared in HELLO). */
    val publicKey: ByteArray by lazy {
        val existing = store.keyPair()
        if (existing != null) {
            require(existing.first.size == KX_PUBLIC_KEY_BYTES) { "stored public key has wrong size" }
            existing.first
        } else {
            val (pk, sk) = generateKeypair()
            store.storeKeyPair(pk, sk)
            pk
        }
    }

    private val secretKey: ByteArray by lazy {
        store.keyPair()?.second
            ?: error("keypair missing after generation")
    }

    private fun generateKeypair(): Pair<ByteArray, ByteArray> {
        val priv = X25519PrivateKeyParameters(secureRandom)
        val pub = priv.generatePublicKey()
        return pub.encoded to priv.encoded
    }

    /** Human-comparable fingerprint of our public key: SHA-256, grouped hex. */
    fun fingerprint(): String = fingerprintOf(publicKey)

    fun fingerprintOf(pubKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pubKey)
        return digest.take(20).joinToString("") { "%02x".format(it) }
            .chunked(4).joinToString(" ").uppercase()
    }

    /**
     * Derives the authenticated session keys for [peerDeviceId].
     * Returns null if the peer public key is malformed.
     */
    fun sessionKeys(peerDeviceId: UUID, peerPublicKey: ByteArray): SessionKeys? {
        if (peerPublicKey.size != KX_PUBLIC_KEY_BYTES) return null
        return try {
            val agreement = X25519Agreement()
            agreement.init(X25519PrivateKeyParameters(secretKey, 0))
            val shared = ByteArray(agreement.agreementSize)
            agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), shared, 0)
            val keys = deriveSessionKeys(peerDeviceId, peerPublicKey, shared)
            shared.fill(0)
            keys
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveSessionKeys(
        peerDeviceId: UUID,
        peerPublicKey: ByteArray,
        shared: ByteArray,
    ): SessionKeys {
        val iAmClient = isClientRole(store.deviceId(), peerDeviceId)
        // Fixed public-key order so both sides hash identical input.
        val (clientPk, serverPk) =
            if (iAmClient) publicKey to peerPublicKey else peerPublicKey to publicKey
        val digest = Blake2bDigest(shared) // keyed Blake2b-512
        digest.update(KDF_DOMAIN, 0, KDF_DOMAIN.size)
        digest.update(clientPk, 0, clientPk.size)
        digest.update(serverPk, 0, serverPk.size)
        val okm = ByteArray(2 * SESSION_KEY_BYTES)
        digest.doFinal(okm, 0)
        val keys = if (iAmClient) {
            SessionKeys(
                rx = okm.copyOfRange(0, SESSION_KEY_BYTES),
                tx = okm.copyOfRange(SESSION_KEY_BYTES, 2 * SESSION_KEY_BYTES),
            )
        } else {
            SessionKeys(
                rx = okm.copyOfRange(SESSION_KEY_BYTES, 2 * SESSION_KEY_BYTES),
                tx = okm.copyOfRange(0, SESSION_KEY_BYTES),
            )
        }
        okm.fill(0)
        return keys
    }

    /** Encrypts [plaintext] for the peer. Output: nonce(24) || mac(16) || ciphertext. */
    fun encrypt(keys: SessionKeys, plaintext: ByteArray): ByteArray? = try {
        secretBoxSeal(keys.tx, plaintext)
    } catch (_: Exception) {
        null
    }

    /** Decrypts output of [encrypt]. Returns null on authentication failure. */
    fun decrypt(keys: SessionKeys, sealed: ByteArray): ByteArray? = try {
        if (sealed.size < SECRETBOX_NONCE_BYTES + SECRETBOX_MAC_BYTES) return null
        secretBoxOpen(keys.rx, sealed)
    } catch (_: Exception) {
        null
    }

    /**
     * NaCl crypto_secretbox construction: XSalsa20 stream cipher, the first 32
     * keystream bytes used as the Poly1305 one-time key, MAC computed over the
     * ciphertext and prepended.
     */
    internal fun secretBoxSeal(key: ByteArray, message: ByteArray): ByteArray {
        require(key.size == SESSION_KEY_BYTES) { "bad session key size" }
        val nonce = ByteArray(SECRETBOX_NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        val polyKey = ByteArray(32)
        engine.processBytes(ByteArray(32), 0, 32, polyKey, 0)
        val cipher = ByteArray(message.size)
        engine.processBytes(message, 0, message.size, cipher, 0)
        val mac = ByteArray(SECRETBOX_MAC_BYTES)
        Poly1305().apply {
            init(KeyParameter(polyKey))
            update(cipher, 0, cipher.size)
            doFinal(mac, 0)
        }
        polyKey.fill(0)
        return nonce + mac + cipher
    }

    internal fun secretBoxOpen(key: ByteArray, sealed: ByteArray): ByteArray? {
        require(key.size == SESSION_KEY_BYTES) { "bad session key size" }
        val nonce = sealed.copyOfRange(0, SECRETBOX_NONCE_BYTES)
        val mac = sealed.copyOfRange(SECRETBOX_NONCE_BYTES, SECRETBOX_NONCE_BYTES + SECRETBOX_MAC_BYTES)
        val cipher = sealed.copyOfRange(SECRETBOX_NONCE_BYTES + SECRETBOX_MAC_BYTES, sealed.size)
        val engine = XSalsa20Engine()
        engine.init(false, ParametersWithIV(KeyParameter(key), nonce))
        val polyKey = ByteArray(32)
        engine.processBytes(ByteArray(32), 0, 32, polyKey, 0)
        val expected = ByteArray(SECRETBOX_MAC_BYTES)
        Poly1305().apply {
            init(KeyParameter(polyKey))
            update(cipher, 0, cipher.size)
            doFinal(expected, 0)
        }
        polyKey.fill(0)
        if (!MessageDigest.isEqual(mac, expected)) return null
        val plain = ByteArray(cipher.size)
        engine.processBytes(cipher, 0, cipher.size, plain, 0)
        return plain
    }

    /**
     * Deterministic client/server role assignment so both peers derive the
     * same session keys without negotiating. Smaller device id = client.
     */
    internal fun isClientRole(a: UUID, b: UUID): Boolean {
        val msb = a.mostSignificantBits.compareTo(b.mostSignificantBits)
        if (msb != 0) return msb < 0
        return a.leastSignificantBits < b.leastSignificantBits
    }
}
