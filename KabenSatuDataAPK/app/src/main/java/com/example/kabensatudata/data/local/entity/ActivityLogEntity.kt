package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache for device activity logs.
 * Logs are stored locally and periodically synced to the server.
 * This ensures no data is lost during offline periods.
 */
@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val deviceId: String,
    val eventType: String,   // login, logout, screen_on, screen_off, navigate, app_switch, app_usage
    val eventData: String,   // JSON: {"screen":"MedicalHub"} or {"package":"com.whatsapp","duration":120}
    val timestamp: Long,     // epoch millis
    val synced: Boolean = false
)
