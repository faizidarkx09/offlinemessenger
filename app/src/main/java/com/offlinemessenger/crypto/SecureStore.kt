package com.offlinemessenger.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Keystore-backed local storage for identity material and settings.
 * The secret key never leaves this encrypted store in plaintext.
 */
class SecureStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "om_secure_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Stable 128-bit routing identity for this device. Generated once. */
    fun deviceId(): UUID {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return UUID.fromString(existing)
        val fresh = UUID.randomUUID()
        prefs.edit().putString(KEY_DEVICE_ID, fresh.toString()).apply()
        return fresh
    }

    fun displayName(): String = prefs.getString(KEY_DISPLAY_NAME, null) ?: defaultName()

    fun setDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name.take(40)).apply()
    }

    private fun defaultName(): String {
        val id = deviceId().toString().take(8).uppercase()
        return "User-$id"
    }

    /** X25519 key-exchange keypair, base64-encoded. Generated once via libsodium. */
    fun keyPair(): Pair<ByteArray, ByteArray>? {
        val pk = prefs.getString(KEY_PUB, null) ?: return null
        val sk = prefs.getString(KEY_SEC, null) ?: return null
        return Base64.decode(pk, Base64.NO_WRAP) to Base64.decode(sk, Base64.NO_WRAP)
    }

    fun storeKeyPair(publicKey: ByteArray, secretKey: ByteArray) {
        prefs.edit()
            .putString(KEY_PUB, Base64.encodeToString(publicKey, Base64.NO_WRAP))
            .putString(KEY_SEC, Base64.encodeToString(secretKey, Base64.NO_WRAP))
            .apply()
    }

    var relayOptIn: Boolean
        get() = prefs.getBoolean(KEY_RELAY_OPT_IN, false)
        set(v) = prefs.edit().putBoolean(KEY_RELAY_OPT_IN, v).apply()

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PUB = "kx_public_key"
        private const val KEY_SEC = "kx_secret_key"
        private const val KEY_RELAY_OPT_IN = "relay_opt_in"
    }
}
