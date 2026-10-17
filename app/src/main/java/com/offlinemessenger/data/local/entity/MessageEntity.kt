package com.offlinemessenger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Delivery lifecycle of a single message. Never DELIVERED without a real ACK. */
enum class MessageStatus { PENDING, SENT, DELIVERED, FAILED, RECEIVED }

@Entity(
    tableName = "messages",
    indices = [Index("conversationId"), Index("timestampMs")],
)
data class MessageEntity(
    /** UUID string of the packet message id — global dedup key. */
    @PrimaryKey val msgId: String,
    /** Conversation partner's device-id string (mine for notes-to-self). */
    val conversationId: String,
    val senderId: String,
    val destId: String,
    /** Decrypted plaintext. Only stored for messages we sent or received. */
    val body: String,
    val timestampMs: Long,
    val status: MessageStatus,
    val isMine: Boolean,
    val ttl: Int,
    val hops: Int,
    val isRead: Boolean = true,
)
