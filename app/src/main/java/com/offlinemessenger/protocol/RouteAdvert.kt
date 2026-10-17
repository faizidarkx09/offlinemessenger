package com.offlinemessenger.protocol

import com.offlinemessenger.ble.toBytes
import com.offlinemessenger.ble.toUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * ROUTE payload: distance-vector advertisement of known destinations.
 * ```
 *  0     : entry count N (<= 32)
 *  per entry (17 bytes): device id (16) + hops (1)
 * ```
 * Entries with hops >= MAX_TTL are never advertised.
 */
object RouteAdvert {
    const val MAX_ENTRIES = 32

    data class Entry(val deviceId: UUID, val hops: Int)

    fun encode(entries: List<Entry>): ByteArray {
        val safe = entries.filter { it.hops in 1 until Packet.MAX_TTL }.take(MAX_ENTRIES)
        val buf = ByteBuffer.allocate(1 + safe.size * 17).order(ByteOrder.BIG_ENDIAN)
        buf.put(safe.size.toByte())
        safe.forEach {
            buf.put(it.deviceId.toBytes())
            buf.put(it.hops.toByte())
        }
        return buf.array()
    }

    fun decode(raw: ByteArray): List<Entry>? {
        if (raw.isEmpty() || raw.size > 1 + MAX_ENTRIES * 17) return null
        val count = raw[0].toInt() and 0xFF
        if (count > MAX_ENTRIES || raw.size != 1 + count * 17) return null
        return (0 until count).map { i ->
            val off = 1 + i * 17
            val id = raw.toUuid(off)
            val hops = raw[off + 16].toInt() and 0xFF
            if (hops !in 1 until Packet.MAX_TTL) return null
            Entry(id, hops)
        }
    }
}
