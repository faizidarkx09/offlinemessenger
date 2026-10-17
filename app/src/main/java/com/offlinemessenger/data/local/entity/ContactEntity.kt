package com.offlinemessenger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A known app peer. Created on first HELLO (trust-on-first-use); the user
 * can upgrade it to verified after comparing key fingerprints out-of-band.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    /** Base64 X25519 public key from the peer's HELLO. */
    val publicKeyB64: String,
    val fingerprint: String,
    val verified: Boolean = false,
    val lastSeenMs: Long = 0L,
)
