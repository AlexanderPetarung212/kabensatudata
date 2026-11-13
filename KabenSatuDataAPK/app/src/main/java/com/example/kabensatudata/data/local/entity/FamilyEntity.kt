package com.example.kabensatudata.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "families",
    foreignKeys = [
        ForeignKey(
            entity = BuildingEntity::class,
            parentColumns = ["id"],
            childColumns = ["buildingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("buildingId")]
)
data class FamilyEntity(
    @PrimaryKey val id: Long = 0,
    val uuid: String,
    @ColumnInfo(name = "buildingId") val buildingId: Long,
    val familyNumber: String,
    val headName: String,
    val sequenceNumberInBuilding: Int? = null,
    // Health indicators
    val hasCleanWater: Boolean? = null,
    val isWaterProtected: Boolean? = null,
    val hasToilet: Boolean? = null,
    val isToiletSanitary: Boolean? = null,
    val hasMentalIllness: Boolean? = null,
    val takesMedicationRegularly: Boolean? = null,
    val hasRestrainedMember: Boolean? = null,
    // Sync
    val updatedAt: String? = null,
    val syncedAt: String? = null,
    val pendingSync: Boolean = false,
    val pendingAction: String? = null
)
