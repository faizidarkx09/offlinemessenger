package com.offlinemessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    /** IGNORE keeps the unique msgId a hard dedup barrier at the DB level. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE msgId = :msgId")
    suspend fun setStatus(msgId: String, status: MessageStatus)

    @Query("SELECT * FROM messages WHERE msgId = :msgId LIMIT 1")
    suspend fun getById(msgId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMs ASC")
    fun conversation(conversationId: String): Flow<List<MessageEntity>>

    /** Chat list: latest message per conversation. */
    @Query(
        """
        SELECT * FROM messages m1 WHERE timestampMs =
          (SELECT MAX(timestampMs) FROM messages m2 WHERE m2.conversationId = m1.conversationId)
        ORDER BY timestampMs DESC
        """,
    )
    fun latestPerConversation(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND isMine = 0 AND isRead = 0")
    fun unreadCount(conversationId: String): Flow<Int>

    @Query("UPDATE messages SET isRead = 1 WHERE conversationId = :conversationId AND isMine = 0")
    suspend fun markRead(conversationId: String)

    @Query("DELETE FROM messages WHERE timestampMs < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long): Int

    @Query("UPDATE messages SET reactionsJson = :json WHERE msgId = :msgId")
    suspend fun setReactions(msgId: String, json: String)

    @Query("UPDATE messages SET status = 'READ' WHERE msgId = :msgId AND isMine = 1")
    suspend fun markMyMessageRead(msgId: String)

    /** Global message search across all conversations. */
    @Query("SELECT * FROM messages WHERE body LIKE '%' || :q || '%' ORDER BY timestampMs DESC LIMIT 50")
    suspend fun search(q: String): List<MessageEntity>

    /** Unread incoming messages, for read-receipt fan-out on markRead. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isMine = 0 AND isRead = 0 AND status = 'RECEIVED'")
    suspend fun unreadReceived(conversationId: String): List<MessageEntity>
}
