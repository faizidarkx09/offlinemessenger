package com.offlinemessenger.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import java.security.MessageDigest
import java.util.UUID

/**
 * End-to-end encryption built on libsodium (via LazySodium-Android), a
 * reviewed, widely-audited cryptographic library. No custom algorithms,
 * no hardcoded shared secrets.
 *
 * Design:
 * - Each device owns a long-term X25519 keypair (crypto_kx), generated once
 *   with the OS CSPRNG and stored in [SecureStore] (Keystore-backed).
 * - Peers exchange public keys inside signed-by-nothing HELLO packets;
 *   trust is trust-on-first-use, upgradeable to "verified" by comparing
 *   key fingerprints out-of-band (see Contacts screen).
 * - Per-peer session keys are derived with crypto_kx (authenticated key
 *   exchange). Roles are deterministic: the peer with the smaller device id
 *   acts as "client", so both sides derive identical rx/tx keys without
 *   extra round trips.
 * - Message payloads use XSalsa20-Poly1305 (crypto_secretbox) with a fresh
 *   random 24-byte nonce per message.
 *
 * Relay nodes only ever see opaque ciphertext + routing headers. They learn
 * sender/destination ids (routing metadata) but never plaintext.
 */
class CryptoManager(
    private val store: SecureStore,
    private val sodium: LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid()),
) {
    data class SessionKeys(val rx: ByteArray, val tx: ByteArray)

    /** libsodium documented constant sizes (stable ABI). */
    companion object {
        const val KX_PUBLIC_KEY_BYTES = 32
        const val KX_SECRET_KEY_BYTES = 32
        const val SESSION_KEY_BYTES = 32
        const val SECRETBOX_NONCE_BYTES = 24
        const val SECRETBOX_MAC_BYTES = 16
    }

    /** This device's long-term X25519 public key (shared in HELLO). */
    val publicKey: ByteArray by lazy {
        val existing = store.keyPair()
        if (existing != null) {
            require(existing.first.size == KX_PUBLIC_KEY_BYTES) { "stored public key has wrong size" }
            existing.first
        } else {
            val pk = ByteArray(KX_PUBLIC_KEY_BYTES)
            val sk = ByteArray(KX_SECRET_KEY_BYTES)
            check(sodium.cryptoKxKeypair(pk, sk) == 0) { "libsodium keypair generation failed" }
            store.storeKeyPair(pk, sk)
            pk
        }
    }

    private val secretKey: ByteArray by lazy {
        store.keyPair()?.second
            ?: error("keypair missing after generation")
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
        val rx = ByteArray(SESSION_KEY_BYTES)
        val tx = ByteArray(SESSION_KEY_BYTES)
        val ok = if (isClientRole(store.deviceId(), peerDeviceId)) {
            sodium.cryptoKxClientSessionKeys(rx, tx, publicKey, secretKey, peerPublicKey)
        } else {
            sodium.cryptoKxServerSessionKeys(rx, tx, publicKey, secretKey, peerPublicKey)
        }
        if (ok != 0) return null
        return SessionKeys(rx, tx)
    }

    /** Encrypts [plaintext] for the peer. Output: nonce(24) || ciphertext. */
    fun encrypt(keys: SessionKeys, plaintext: ByteArray): ByteArray? {
        val nonce = ByteArray(SECRETBOX_NONCE_BYTES)
        sodium.randombytesBuf(nonce, nonce.size)
        val cipher = ByteArray(plaintext.size + SECRETBOX_MAC_BYTES)
        val ok = sodium.cryptoSecretBoxEasy(cipher, plaintext, plaintext.size.toLong(), nonce, keys.tx)
        if (ok != 0) return null
        return nonce + cipher
    }

    /** Decrypts output of [encrypt]. Returns null on authentication failure. */
    fun decrypt(keys: SessionKeys, sealed: ByteArray): ByteArray? {
        if (sealed.size < SECRETBOX_NONCE_BYTES + SECRETBOX_MAC_BYTES) return null
        val nonce = sealed.copyOfRange(0, SECRETBOX_NONCE_BYTES)
        val cipher = sealed.copyOfRange(SECRETBOX_NONCE_BYTES, sealed.size)
        val plain = ByteArray(cipher.size - SECRETBOX_MAC_BYTES)
        val ok = sodium.cryptoSecretBoxOpenEasy(plain, cipher, cipher.size.toLong(), nonce, keys.rx)
        if (ok != 0) return null
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
