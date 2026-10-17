package com.offlinemessenger.protocol

import com.offlinemessenger.ble.toBytes
import com.offlinemessenger.ble.toUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * HELLO payload: identity + key-exchange announcement + profile.
 * ```
 *  0..15  : device id (16 bytes)
 * 16..47  : X25519 public key (32 bytes)
 * 48..49  : display name length (uint16 BE)
 * 50..    : display name (UTF-8, <= 40 bytes)
 *  ..+4   : photo length (uint32 BE)
 *  ..     : profile photo JPEG thumbnail (<= MAX_PHOTO_BYTES)
 * ```
 * Old builds sent the name as raw trailing bytes; decode() falls back to
 * that layout when the v2 framing doesn't parse.
 */
object HelloPayload {
    const val MIN_SIZE = 48
    const val MAX_NAME_BYTES = 40

    /** Profile photo cap: 96x96 JPEG is ~3-6 KB; 8 KB leaves headroom. */
    const val MAX_PHOTO_BYTES = 8 * 1024

    data class Hello(
        val deviceId: UUID,
        val publicKey: ByteArray,
        val displayName: String,
        val photo: ByteArray = ByteArray(0),
    )

    fun encode(
        deviceId: UUID,
        publicKey: ByteArray,
        displayName: String,
        photo: ByteArray = ByteArray(0),
    ): ByteArray {
        require(publicKey.size == 32) { "public key must be 32 bytes" }
        require(photo.size <= MAX_PHOTO_BYTES) { "photo too large" }
        val nameBytes = displayName.toByteArray(Charsets.UTF_8).take(MAX_NAME_BYTES).toByteArray()
        return ByteBuffer.allocate(48 + 2 + nameBytes.size + 4 + photo.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(deviceId.toBytes())
            .put(publicKey)
            .putShort(nameBytes.size.toShort())
            .put(nameBytes)
            .putInt(photo.size)
            .put(photo)
            .array()
    }

    fun decode(raw: ByteArray): Hello? {
        if (raw.size < MIN_SIZE) return null
        val deviceId = raw.toUuid(0)
        val publicKey = raw.copyOfRange(16, 48)
        // Try v2 framing first.
        decodeV2(raw, deviceId, publicKey)?.let { return it }
        // Fallback: legacy layout (name = trailing bytes).
        val nameBytes = raw.copyOfRange(48, raw.size).take(MAX_NAME_BYTES).toByteArray()
        val name = try {
            String(nameBytes, Charsets.UTF_8).trim().ifEmpty { "Unknown" }
        } catch (_: Exception) {
            return null
        }
        return Hello(deviceId, publicKey, name)
    }

    private fun decodeV2(raw: ByteArray, deviceId: UUID, publicKey: ByteArray): Hello? {
        return runCatching {
            val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
            buf.position(48)
            val nameLen = buf.short.toInt() and 0xFFFF
            if (nameLen < 0 || nameLen > MAX_NAME_BYTES) return null
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            val photoLen = buf.int
            if (photoLen < 0 || photoLen > MAX_PHOTO_BYTES) return null
            if (buf.remaining() != photoLen) return null
            val photo = ByteArray(photoLen)
            buf.get(photo)
            val name = String(nameBytes, Charsets.UTF_8).trim().ifEmpty { "Unknown" }
            Hello(deviceId, publicKey, name, photo)
        }.getOrNull()
    }
}
