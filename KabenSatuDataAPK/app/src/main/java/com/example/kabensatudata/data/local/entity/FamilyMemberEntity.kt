package com.example.kabensatudata.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "family_members",
    foreignKeys = [
        ForeignKey(
            entity = FamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("familyId")]
)
data class FamilyMemberEntity(
    @PrimaryKey val id: Long = 0,
    val uuid: String,
    @ColumnInfo(name = "familyId") val familyId: Long,
    val name: String,
    val nik: String? = null,
    val rmNumber: String? = null,
    val sequenceNumberInFamily: Int? = null,
    val relationship: String,
    val birthPlace: String? = null,
    val birthDate: String? = null, // ISO date
    val gender: String, // "Laki-laki" | "Perempuan"
    // Health fields
    val isPregnant: Boolean? = null,
    val religion: String? = null,
    val education: String? = null,
    val maritalStatus: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val hasJkn: Boolean? = null,
    val isSmoker: Boolean? = null,
    val useWater: Boolean? = null,
    val useToilet: Boolean? = null,
    val hasTuberculosis: Boolean? = null,
    val takesTbMedicationRegularly: Boolean? = null,
    val hasChronicCough: Boolean? = null,
    val hasHypertension: Boolean? = null,
    val takesHypertensionMedicationRegularly: Boolean? = null,
    val usesContraception: Boolean? = null,
    val gaveBirthInHealthFacility: Boolean? = null,
    val exclusiveBreastfeeding: Boolean? = null,
    val completeImmunization: Boolean? = null,
    val growthMonitoring: Boolean? = null,
    // ── NIK Verification (SATUSEHAT) ──────────────────────────
    val ihsNumber: String? = null,
    val nikVerifiedStatus: String? = null,  // "verified" | "unverified" | "ihs_only"
    val nikVerifiedAt: String? = null,
    val nikVerifiedSource: String? = null,  // "satusehat"
    // ──────────────────────────────────────────────────────────
    // Sync
    val updatedAt: String? = null,
    val syncedAt: String? = null,
    val pendingSync: Boolean = false,
    val pendingAction: String? = null
)
