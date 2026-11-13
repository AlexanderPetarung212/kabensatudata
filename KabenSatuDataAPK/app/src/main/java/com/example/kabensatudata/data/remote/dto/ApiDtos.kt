package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Request DTOs ───────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_id") val deviceId: String
)

data class RefreshTokenRequest(
    @SerializedName("device_id") val deviceId: String
)

data class SyncPushRequest(
    val changes: List<SyncChangeDto>,
    @SerializedName("device_id") val deviceId: String
)

data class SyncChangeDto(
    val table: String,
    val action: String, // "create", "update", "delete"
    val uuid: String,
    val data: Map<String, Any?>,
    @SerializedName("client_updated_at") val clientUpdatedAt: String
)

// ─── Response DTOs ──────────────────────────────────────────────────────────

data class LoginResponse(
    val token: String,
    val user: UserDto,
    @SerializedName("expires_at") val expiresAt: String
)

data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
    val roles: List<String>
)

data class MessageResponse(val message: String)

data class ProfileResponse(val user: UserDto)

data class RefreshTokenResponse(
    val token: String,
    @SerializedName("expires_at") val expiresAt: String
)

data class ListResponse<T>(val data: List<T>)

// ─── Sync Response DTOs ─────────────────────────────────────────────────────

data class SyncPullResponse(
    val data: SyncPullData,
    @SerializedName("server_time") val serverTime: String,
    val counts: Map<String, Int>
)

data class SyncPullData(
    val villages: List<VillageDto>,
    val buildings: List<BuildingDto>,
    val families: List<FamilyDto>,
    @SerializedName("family_members") val familyMembers: List<FamilyMemberDto>,
    @SerializedName("medical_records") val medicalRecords: List<MedicalRecordDto>
)

data class SyncPushResponse(
    val accepted: List<SyncAcceptedDto>,
    val conflicts: List<SyncConflictDto>,
    val errors: List<SyncErrorDto>,
    @SerializedName("uuid_map") val uuidMap: Map<String, Long>,
    @SerializedName("server_time") val serverTime: String,
    val summary: SyncSummaryDto
)

data class SyncAcceptedDto(
    val index: Int,
    val uuid: String,
    @SerializedName("server_id") val serverId: Long? = null,
    val action: String
)

data class SyncConflictDto(
    val index: Int,
    val uuid: String,
    val action: String,
    @SerializedName("server_data") val serverData: Map<String, Any?>,
    @SerializedName("server_updated_at") val serverUpdatedAt: String,
    val message: String
)

data class SyncErrorDto(
    val index: Int,
    val uuid: String,
    val action: String,
    val error: String
)

data class SyncSummaryDto(
    val total: Int,
    val accepted: Int,
    val conflicts: Int,
    val errors: Int
)

// ─── Entity DTOs ────────────────────────────────────────────────────────────

data class VillageDto(
    val id: Long,
    val uuid: String,
    val name: String,
    val code: String?,
    @SerializedName("sequence_number") val sequenceNumber: String?,
    val district: String?,
    val regency: String?,
    val province: String?,
    @SerializedName("buildings_count") val buildingsCount: Int? = null,
    @SerializedName("updated_at") val updatedAt: String?
)

data class BuildingDto(
    val id: Long,
    val uuid: String,
    @SerializedName("village_id") val villageId: Long,
    @SerializedName("building_number") val buildingNumber: String,
    val latitude: Double?,
    val longitude: Double?,
    val description: String?,
    @SerializedName("families_count") val familiesCount: Int? = null,
    @SerializedName("updated_at") val updatedAt: String?
)

data class FamilyDto(
    val id: Long,
    val uuid: String,
    @SerializedName("building_id") val buildingId: Long,
    @SerializedName("family_number") val familyNumber: String,
    @SerializedName("head_name") val headName: String,
    @SerializedName("sequence_number_in_building") val sequenceNumberInBuilding: Int?,
    @SerializedName("has_clean_water") val hasCleanWater: Boolean?,
    @SerializedName("is_water_protected") val isWaterProtected: Boolean?,
    @SerializedName("has_toilet") val hasToilet: Boolean?,
    @SerializedName("is_toilet_sanitary") val isToiletSanitary: Boolean?,
    @SerializedName("has_mental_illness") val hasMentalIllness: Boolean?,
    @SerializedName("takes_medication_regularly") val takesMedicationRegularly: Boolean?,
    @SerializedName("has_restrained_member") val hasRestrainedMember: Boolean?,
    @SerializedName("members_count") val membersCount: Int? = null,
    @SerializedName("updated_at") val updatedAt: String?
)

data class FamilyMemberDto(
    val id: Long,
    val uuid: String,
    @SerializedName("family_id") val familyId: Long,
    val name: String,
    val nik: String?,
    @SerializedName("rm_number") val rmNumber: String?,
    @SerializedName("sequence_number_in_family") val sequenceNumberInFamily: Int?,
    val relationship: String,
    @SerializedName("birth_place") val birthPlace: String?,
    @SerializedName("birth_date") val birthDate: String?,
    val age: Int?,
    val gender: String,
    @SerializedName("is_pregnant") val isPregnant: Boolean?,
    val religion: String?,
    val education: String?,
    @SerializedName("marital_status") val maritalStatus: String?,
    val occupation: String?,
    val address: String?,
    @SerializedName("has_jkn") val hasJkn: Boolean?,
    @SerializedName("is_smoker") val isSmoker: Boolean?,
    @SerializedName("use_water") val useWater: Boolean?,
    @SerializedName("use_toilet") val useToilet: Boolean?,
    @SerializedName("has_tuberculosis") val hasTuberculosis: Boolean?,
    @SerializedName("takes_tb_medication_regularly") val takesTbMedicationRegularly: Boolean?,
    @SerializedName("has_chronic_cough") val hasChronicCough: Boolean?,
    @SerializedName("has_hypertension") val hasHypertension: Boolean?,
    @SerializedName("takes_hypertension_medication_regularly") val takesHypertensionMedicationRegularly: Boolean?,
    @SerializedName("uses_contraception") val usesContraception: Boolean?,
    @SerializedName("gave_birth_in_health_facility") val gaveBirthInHealthFacility: Boolean?,
    @SerializedName("exclusive_breastfeeding") val exclusiveBreastfeeding: Boolean?,
    @field:SerializedName("complete_immunization") val completeImmunization: Boolean?,
    @field:SerializedName("growth_monitoring") val growthMonitoring: Boolean?,
    // ── NIK Verification (SATUSEHAT) ──────────────────────────
    @field:SerializedName("ihs_number") val ihsNumber: String?,
    @field:SerializedName("nik_verified_status") val nikVerifiedStatus: String?,
    @field:SerializedName("nik_verified_at") val nikVerifiedAt: String?,
    @field:SerializedName("nik_verified_source") val nikVerifiedSource: String?,
    // ──────────────────────────────────────────────────────────
    @field:SerializedName("updated_at") val updatedAt: String?
)

data class MedicalRecordDto(
    val id: Long,
    val uuid: String,
    @SerializedName("family_member_id") val familyMemberId: Long,
    @SerializedName("visit_date") val visitDate: String,
    @SerializedName("chief_complaint") val chiefComplaint: String?,
    val anamnesis: String?,
    val systolic: Int?,
    val diastolic: Int?,
    val weight: Double?,
    val height: Double?,
    @SerializedName("heart_rate") val heartRate: Int?,
    @SerializedName("body_temperature") val bodyTemperature: Double?,
    @SerializedName("respiratory_rate") val respiratoryRate: Int?,
    @SerializedName("diagnosis_code") val diagnosisCode: String?,
    @SerializedName("diagnosis_name") val diagnosisName: String?,
    val therapy: String?,
    val medication: String?,
    val procedure: String?,
    @SerializedName("workflow_status") val workflowStatus: String?,
    @SerializedName("created_by") val createdBy: Long?,
    val bmi: Double?,
    @SerializedName("bmi_category") val bmiCategory: String?,
    @SerializedName("blood_pressure_category") val bloodPressureCategory: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

// ─── Medicine DTO ───────────────────────────────────────────────────────────

data class MedicineDto(
    val id: Long,
    val name: String,
    @SerializedName("generic_name") val genericName: String?,
    val strength: String?,
    @SerializedName("stock_quantity") val stockQuantity: Int,
    val unit: String,
    @SerializedName("minimum_stock") val minimumStock: Int,
    @SerializedName("is_active") val isActive: Boolean
)

// ─── Announcement DTO ───────────────────────────────────────────────────────

data class AnnouncementResponse(
    val announcement: AnnouncementDto?
)

data class AnnouncementDto(
    val id: Long,
    val title: String,
    val message: String,
    val type: String // "info", "warning", "update", "critical"
)
