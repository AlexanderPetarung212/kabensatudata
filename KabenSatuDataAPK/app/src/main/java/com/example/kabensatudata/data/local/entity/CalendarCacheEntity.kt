package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for BOK calendar data.
 * Stores the full JSON response per year so we can serve offline.
 */
@Entity(tableName = "calendar_cache")
data class CalendarCacheEntity(
    @PrimaryKey
    val year: Int,
    val jsonData: String,       // Full CalendarResponse JSON
    val lastSynced: Long        // System.currentTimeMillis()
)
