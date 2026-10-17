package com.offlinemessenger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A chat group. Messages fan out to members, each encrypted pairwise. */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val createdBy: String,
    val createdAtMs: Long,
    /** True when I am an admin of this group. */
    val iAmAdmin: Boolean = false,
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "deviceId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["groupId"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class GroupMemberEntity(
    val groupId: String,
    val deviceId: String,
    val displayName: String,
    /** Base64 X25519 public key — needed for pairwise fan-out encryption. */
    val publicKeyB64: String,
    val isAdmin: Boolean = false,
)

/** Per-conversation UI state: pins, mutes. Derived chat list stays in messages. */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val pinned: Boolean = false,
    val muted: Boolean = false,
)
