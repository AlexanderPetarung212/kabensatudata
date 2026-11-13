package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks pending sync operations to be pushed to the server when online.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,      // "buildings", "families", etc.
    val action: String,         // "create", "update", "delete"
    val uuid: String,           // UUID of the record
    val dataJson: String,       // JSON of the data to sync
    val clientUpdatedAt: String, // ISO timestamp when change was made locally
    val createdAt: String,      // When this queue entry was created
    val retryCount: Int = 0,    // How many times we've tried to sync
    val lastError: String? = null // Last error message if sync failed
)
