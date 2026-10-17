package com.offlinemessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offlinemessenger.data.local.entity.RouteEntity

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(route: RouteEntity)

    @Query("SELECT * FROM routes WHERE destId = :destId AND expiresAtMs > :nowMs LIMIT 1")
    suspend fun get(destId: String, nowMs: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE expiresAtMs > :nowMs ORDER BY hops ASC")
    suspend fun allValid(nowMs: Long): List<RouteEntity>

    @Query("DELETE FROM routes WHERE expiresAtMs <= :nowMs")
    suspend fun deleteExpired(nowMs: Long): Int

    @Query("DELETE FROM routes WHERE nextHopId = :nextHopId")
    suspend fun deleteVia(nextHopId: String)
}
