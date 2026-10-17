package com.offlinemessenger.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

fun UUID.toBytes(): ByteArray =
    ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        .putLong(mostSignificantBits)
        .putLong(leastSignificantBits)
        .array()

fun ByteArray.toUuid(offset: Int = 0): UUID {
    require(size - offset >= 16) { "need 16 bytes for a UUID" }
    val buf = ByteBuffer.wrap(this, offset, 16).order(ByteOrder.BIG_ENDIAN)
    return UUID(buf.long, buf.long)
}
