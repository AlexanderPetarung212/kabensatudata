package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Quick Patient Registration DTOs ────────────────────────────────────────

data class QuickRegisterRequest(
    val name: String,
    val gender: String,
    @SerializedName("birth_date") val birthDate: String? = null,
    val nik: String? = null,
    @SerializedName("birth_place") val birthPlace: String? = null,
    val address: String? = null,
    val occupation: String? = null,
    val religion: String? = null,
    @SerializedName("has_jkn") val hasJkn: Boolean? = null
)

data class QuickRegisterResponse(
    val message: String,
    val data: QuickRegisterPatientData,
    @SerializedName("is_existing") val isExisting: Boolean
)

data class QuickRegisterPatientData(
    val id: Long,
    val uuid: String,
    @SerializedName("family_id") val familyId: Long,
    val name: String,
    @SerializedName("rm_number") val rmNumber: String?,
    val gender: String,
    @SerializedName("birth_date") val birthDate: String?,
    val age: Int?,
    val nik: String?,
    val address: String?,
    @SerializedName("has_jkn") val hasJkn: Boolean?,
    @SerializedName("birth_place") val birthPlace: String?,
    val occupation: String?,
    val religion: String?,
    val relationship: String?,
    @SerializedName("sequence_number_in_family") val sequenceNumberInFamily: Int?,
    @SerializedName("updated_at") val updatedAt: String?
)
