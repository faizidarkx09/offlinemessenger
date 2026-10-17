package com.offlinemessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offlinemessenger.data.local.entity.ConversationEntity
import com.offlinemessenger.data.local.entity.GroupEntity
import com.offlinemessenger.data.local.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<GroupMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: GroupMemberEntity)

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    fun groupFlow(groupId: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun allGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY displayName ASC")
    fun members(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun membersOnce(groupId: String): List<GroupMemberEntity>

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND deviceId = :deviceId")
    suspend fun removeMember(groupId: String, deviceId: String)

    @Query("DELETE FROM groups WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conv: ConversationEntity)

    @Query("UPDATE conversations SET pinned = :pinned WHERE conversationId = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET muted = :muted WHERE conversationId = :id")
    suspend fun setMuted(id: String, muted: Boolean)

    @Query("SELECT * FROM conversations")
    fun all(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE conversationId = :id LIMIT 1")
    suspend fun get(id: String): ConversationEntity?
}
