package com.offlinemessenger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Delivery lifecycle of a single message. Never DELIVERED without a real ACK. */
enum class MessageStatus { PENDING, SENT, DELIVERED, READ, FAILED, RECEIVED }

@Entity(
    tableName = "messages",
    indices = [Index("conversationId"), Index("timestampMs")],
)
data class MessageEntity(
    /** UUID string of the packet message id — global dedup key. */
    @PrimaryKey val msgId: String,
    /** Conversation partner's device-id string, or "group:<uuid>" for groups. */
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
    // ---- v2: replies / reactions / forwarding ----
    /** msgId of the message being replied to (null when not a reply). */
    val replyToId: String? = null,
    /** Denormalized snippet of the replied-to message for fast display. */
    val replyToBody: String? = null,
    /** Display name of the original sender of the replied-to message. */
    val replyToSender: String? = null,
    /** JSON: {"❤️":["deviceId1","deviceId2"], ...} — who reacted with what. */
    val reactionsJson: String = "{}",
    /** True when this message was forwarded from another chat. */
    val forwarded: Boolean = false,
)

/** Parses reactionsJson into emoji -> set of device ids. Never throws. */
fun MessageEntity.reactions(): Map<String, Set<String>> {
    if (reactionsJson.isBlank() || reactionsJson == "{}") return emptyMap()
    return runCatching {
        val out = mutableMapOf<String, MutableSet<String>>()
        // Minimal JSON parse: {"emoji":["id1","id2"],"emoji2":[...]}
        val s = reactionsJson.trim().removePrefix("{").removeSuffix("}")
        if (s.isBlank()) return emptyMap()
        var i = 0
        while (i < s.length) {
            val kStart = s.indexOf('"', i); if (kStart < 0) break
            val kEnd = s.indexOf('"', kStart + 1); if (kEnd < 0) break
            val key = s.substring(kStart + 1, kEnd)
            val aStart = s.indexOf('[', kEnd); if (aStart < 0) break
            val aEnd = s.indexOf(']', aStart); if (aEnd < 0) break
            val ids = s.substring(aStart + 1, aEnd).split(',')
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }.toSet()
            out[key] = ids.toMutableSet()
            i = aEnd + 1
        }
        out
    }.getOrDefault(emptyMap())
}

/** Serializes emoji -> device-id sets back to JSON. */
fun reactionsToJson(map: Map<String, Set<String>>): String {
    if (map.isEmpty()) return "{}"
    return buildString {
        append('{')
        map.entries.forEachIndexed { idx, (emoji, ids) ->
            if (idx > 0) append(',')
            append('"').append(emoji.replace("\"", "")).append("\":[")
            ids.forEachIndexed { j, id ->
                if (j > 0) append(',')
                append('"').append(id).append('"')
            }
            append(']')
        }
        append('}')
    }
}
