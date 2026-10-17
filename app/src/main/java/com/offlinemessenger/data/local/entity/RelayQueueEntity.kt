package com.offlinemessenger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted store-and-forward queue. A row exists only while a message is
 * genuinely waiting for a suitable peer; it is deleted on forward, ACK or
 * expiry — never held indefinitely.
 */
@Entity(
    tableName = "relay_queue",
    indices = [Index("expiresAtMs"), Index("destId")],
)
data class RelayQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val msgId: String,
    val destId: String,
    /** Complete encoded packet (header + ciphertext). Relays never see plaintext. */
    val packet: ByteArray,
    val attempts: Int = 0,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    /** Preferred next hop, if a route is known; null = flood candidates. */
    val nextHopId: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RelayQueueEntity) return false
        return id == other.id && msgId == other.msgId && packet.contentEquals(other.packet)
    }

    override fun hashCode(): Int = 31 * id.hashCode() + msgId.hashCode()
}
