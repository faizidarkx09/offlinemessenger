package com.offlinemessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.offlinemessenger.data.local.entity.RelayQueueEntity

@Dao
interface RelayQueueDao {
    @Insert
    suspend fun enqueue(entry: RelayQueueEntity): Long

    @Query("SELECT * FROM relay_queue WHERE expiresAtMs > :nowMs ORDER BY createdAtMs ASC")
    suspend fun pending(nowMs: Long): List<RelayQueueEntity>

    @Query("SELECT * FROM relay_queue WHERE msgId = :msgId LIMIT 1")
    suspend fun byMsgId(msgId: String): RelayQueueEntity?

    @Query("UPDATE relay_queue SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)

    @Query("DELETE FROM relay_queue WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("DELETE FROM relay_queue WHERE msgId = :msgId")
    suspend fun removeByMsgId(msgId: String)

    @Query("DELETE FROM relay_queue WHERE expiresAtMs <= :nowMs")
    suspend fun deleteExpired(nowMs: Long): Int

    @Query("SELECT COUNT(*) FROM relay_queue")
    suspend fun count(): Int
}
