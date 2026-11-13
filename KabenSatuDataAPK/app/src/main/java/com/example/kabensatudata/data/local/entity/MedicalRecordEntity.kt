package com.example.kabensatudata.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medical_records",
    foreignKeys = [
        ForeignKey(
            entity = FamilyMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyMemberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("familyMemberId")]
)
data class MedicalRecordEntity(
    @PrimaryKey val id: Long = 0,
    val uuid: String,
    @ColumnInfo(name = "familyMemberId") val familyMemberId: Long,
    val visitDate: String, // "YYYY-MM-DD"
    val chiefComplaint: String? = null,
    val anamnesis: String? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val heartRate: Int? = null,
    val bodyTemperature: Double? = null,
    val respiratoryRate: Int? = null,
    val diagnosisCode: String? = null,
    val diagnosisName: String? = null,
    val therapy: String? = null,
    val medication: String? = null,
    val procedure: String? = null,
    val workflowStatus: String? = null,
    val createdBy: Long? = null,
    // Sync
    val updatedAt: String? = null,
    val syncedAt: String? = null,
    val pendingSync: Boolean = false,
    val pendingAction: String? = null
)
