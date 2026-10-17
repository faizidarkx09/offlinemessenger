package com.offlinemessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offlinemessenger.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE deviceId = :deviceId LIMIT 1")
    suspend fun get(deviceId: String): ContactEntity?

    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    fun all(): Flow<List<ContactEntity>>

    @Query("UPDATE contacts SET verified = 1 WHERE deviceId = :deviceId")
    suspend fun setVerified(deviceId: String)

    @Query("UPDATE contacts SET lastSeenMs = :seenMs, displayName = :name WHERE deviceId = :deviceId")
    suspend fun touch(deviceId: String, name: String, seenMs: Long)

    @Query("DELETE FROM contacts WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)
}
