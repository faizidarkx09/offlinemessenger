package com.offlinemessenger.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Versioned binary packet format (v1) carried over BLE GATT.
 *
 * ```
 *  0      : version            (0x01)
 *  1      : type               (PacketType)
 *  2      : ttl                (hops remaining, 0 = do not forward)
 *  3      : flags              (bit0 = relayed)
 *  4..19  : message id         (16 bytes, UUID)
 * 20..35  : sender id          (16 bytes, UUID)
 * 36..51  : destination id     (16 bytes, UUID; all-zero = broadcast)
 * 52..59  : timestamp millis   (8 bytes, big-endian)
 * 60..63  : payload length     (4 bytes, big-endian)
 * 64..    : payload            (<= MAX_PAYLOAD_SIZE)
 * ```
 *
 * Every packet is validated on decode: version, known type, TTL bound,
 * payload size bound and exact length match. Anything else is dropped and
 * counted — never processed.
 */
object Packet {
    const val VERSION: Byte = 0x01
    const val HEADER_SIZE = 64
    const val MAX_PAYLOAD_SIZE = 8 * 1024
    const val MAX_TTL: Byte = 5
    const val DEFAULT_TTL: Byte = 3

    /** All-zero UUID marks a broadcast packet (route advertisements). */
    val BROADCAST_ID: UUID = UUID(0L, 0L)

    /** Max clock skew accepted for replay protection (24h). */
    const val MAX_CLOCK_SKEW_MS = 24 * 60 * 60 * 1000L

    fun encode(
        type: PacketType,
        ttl: Byte,
        messageId: UUID,
        senderId: UUID,
        destId: UUID,
        timestampMs: Long,
        payload: ByteArray,
        relayed: Boolean = false,
    ): ByteArray {
        require(payload.size <= MAX_PAYLOAD_SIZE) { "payload too large: ${payload.size}" }
        require(ttl <= MAX_TTL) { "ttl out of range: $ttl" }
        val flags: Byte = if (relayed) 0x01 else 0x00
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(VERSION)
        buf.put(type.code)
        buf.put(ttl)
        buf.put(flags)
        buf.putUuid(messageId)
        buf.putUuid(senderId)
        buf.putUuid(destId)
        buf.putLong(timestampMs)
        buf.putInt(payload.size)
        buf.put(payload)
        return buf.array()
    }

    /**
     * Decodes and validates. Returns null for any malformed packet —
     * callers must drop it (and may bump a corrupt-packet counter).
     */
    fun decode(raw: ByteArray, nowMs: Long = System.currentTimeMillis()): DecodedPacket? {
        if (raw.size < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
        if (buf.get() != VERSION) return null
        val type = PacketType.fromCode(buf.get()) ?: return null
        val ttl = buf.get()
        if (ttl < 0 || ttl > MAX_TTL) return null
        val flags = buf.get()
        val messageId = buf.getUuid()
        val senderId = buf.getUuid()
        val destId = buf.getUuid()
        val timestampMs = buf.long
        // Replay/freshness window: reject far-future and ancient packets.
        if (timestampMs > nowMs + MAX_CLOCK_SKEW_MS) return null
        if (timestampMs < nowMs - MAX_CLOCK_SKEW_MS) return null
        val payloadLen = buf.int
        if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_SIZE) return null
        if (raw.size != HEADER_SIZE + payloadLen) return null
        val payload = ByteArray(payloadLen)
        buf.get(payload)
        return DecodedPacket(
            type = type,
            ttl = ttl,
            relayed = (flags.toInt() and 0x01) != 0,
            messageId = messageId,
            senderId = senderId,
            destId = destId,
            timestampMs = timestampMs,
            payload = payload,
        )
    }

    private fun ByteBuffer.putUuid(u: UUID) {
        putLong(u.mostSignificantBits)
        putLong(u.leastSignificantBits)
    }

    private fun ByteBuffer.getUuid(): UUID = UUID(long, long)
}

/** Packet types on the wire. */
enum class PacketType(val code: Byte) {
    /** Identity + key-exchange announcement. Payload: HelloPayload (see protocol doc). */
    HELLO(0x01),

    /** End-to-end encrypted application message. Payload: nonce(24) || secretbox ciphertext. */
    MSG(0x02),

    /** Delivery acknowledgement. Payload: none (message id is in the header). */
    ACK(0x03),

    /** Distance-vector route advertisement. Payload: RouteAdvert encoding. */
    ROUTE(0x04),

    /** Graceful disconnect notice. Payload: none. */
    BYE(0x05);

    companion object {
        fun fromCode(code: Byte): PacketType? = values().firstOrNull { it.code == code }
    }
}

/** A validated, decoded packet. */
data class DecodedPacket(
    val type: PacketType,
    val ttl: Byte,
    val relayed: Boolean,
    val messageId: UUID,
    val senderId: UUID,
    val destId: UUID,
    val timestampMs: Long,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedPacket) return false
        return type == other.type && ttl == other.ttl && relayed == other.relayed &&
            messageId == other.messageId && senderId == other.senderId &&
            destId == other.destId && timestampMs == other.timestampMs &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var r = type.hashCode()
        r = 31 * r + messageId.hashCode()
        r = 31 * r + payload.contentHashCode()
        return r
    }
}
