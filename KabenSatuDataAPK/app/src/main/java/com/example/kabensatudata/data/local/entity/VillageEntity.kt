package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "villages")
data class VillageEntity(
    @PrimaryKey val id: Long = 0,
    val uuid: String,
    val name: String,
    val code: String? = null,
    val sequenceNumber: String? = null,
    val district: String? = null,
    val regency: String? = null,
    val province: String? = null,
    val updatedAt: String? = null,
    val syncedAt: String? = null
)
