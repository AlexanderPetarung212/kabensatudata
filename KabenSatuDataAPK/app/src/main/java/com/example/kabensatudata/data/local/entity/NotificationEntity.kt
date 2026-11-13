package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long,
    val title: String,
    val message: String,
    val type: String, // "info", "warning", "update", "critical", "kegiatan"
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val targetPoaId: Long? = null,
    val targetMonth: Int? = null
)
