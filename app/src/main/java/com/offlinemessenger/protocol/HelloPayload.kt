package com.offlinemessenger.protocol

import com.offlinemessenger.ble.toBytes
import com.offlinemessenger.ble.toUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * HELLO payload: identity + key-exchange announcement.
 * ```
 *  0..15  : device id (16 bytes)
 * 16..47  : X25519 public key (32 bytes)
 * 48..    : display name (UTF-8, <= 40 bytes)
 * ```
 */
object HelloPayload {
    const val MIN_SIZE = 48
    const val MAX_NAME_BYTES = 40

    data class Hello(val deviceId: UUID, val publicKey: ByteArray, val displayName: String)

    fun encode(deviceId: UUID, publicKey: ByteArray, displayName: String): ByteArray {
        require(publicKey.size == 32) { "public key must be 32 bytes" }
        val nameBytes = displayName.toByteArray(Charsets.UTF_8).take(MAX_NAME_BYTES).toByteArray()
        return ByteBuffer.allocate(48 + nameBytes.size).order(ByteOrder.BIG_ENDIAN)
            .put(deviceId.toBytes())
            .put(publicKey)
            .put(nameBytes)
            .array()
    }

    fun decode(raw: ByteArray): Hello? {
        if (raw.size < MIN_SIZE) return null
        val deviceId = raw.toUuid(0)
        val publicKey = raw.copyOfRange(16, 48)
        val nameBytes = raw.copyOfRange(48, raw.size).take(MAX_NAME_BYTES).toByteArray()
        val name = try {
            String(nameBytes, Charsets.UTF_8).trim().ifEmpty { "Unknown" }
        } catch (_: Exception) {
            return null
        }
        return Hello(deviceId, publicKey, name)
    }
}
