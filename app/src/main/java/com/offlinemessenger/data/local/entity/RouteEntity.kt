package com.offlinemessenger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Learned route to a destination: forward via [nextHopId] in [hops] hops.
 * Routes expire ([expiresAtMs]) — stale topology is worse than none.
 * Direct (1-hop) neighbours are tracked separately at runtime, not here.
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val destId: String,
    val nextHopId: String,
    val hops: Int,
    val expiresAtMs: Long,
)
