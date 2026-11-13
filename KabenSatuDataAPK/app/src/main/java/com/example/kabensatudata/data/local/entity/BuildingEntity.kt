package com.example.kabensatudata.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "buildings",
    foreignKeys = [
        ForeignKey(
            entity = VillageEntity::class,
            parentColumns = ["id"],
            childColumns = ["villageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("villageId")]
)
data class BuildingEntity(
    @PrimaryKey val id: Long = 0,
    val uuid: String,
    @ColumnInfo(name = "villageId") val villageId: Long,
    val buildingNumber: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val updatedAt: String? = null,
    val syncedAt: String? = null,
    // Pending sync flags
    val pendingSync: Boolean = false,
    val pendingAction: String? = null // "create", "update", "delete"
)
