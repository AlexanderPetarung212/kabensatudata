package com.example.kabensatudata.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medicine_usages",
    foreignKeys = [
        ForeignKey(
            entity = MedicalRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicalRecordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicalRecordId"), Index("medicineId")]
)
data class MedicineUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicalRecordId") val medicalRecordId: Long,
    @ColumnInfo(name = "medicineId") val medicineId: Long,
    val quantityUsed: Int = 0,
    val instructionText: String? = null,  // e.g. "3dd1"
    val frequency: String? = null,        // e.g. "3x1"
    val dosage: String? = null,           // e.g. "1 tablet"
    val notes: String? = null,
    // Sync
    val pendingSync: Boolean = false,
    val pendingAction: String? = null
)
