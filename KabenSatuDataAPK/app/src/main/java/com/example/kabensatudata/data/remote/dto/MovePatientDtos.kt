package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /move-patient/{id}
 */
data class MovePatientRequest(
    @SerializedName("family_id") val familyId: Long
)

/**
 * Response from POST /move-patient/{id}
 */
data class MovePatientResponse(
    val message: String,
    val data: MovePatientData
)

data class MovePatientData(
    val id: Long,
    val name: String,
    @SerializedName("old_rm_number") val oldRmNumber: String,
    @SerializedName("new_rm_number") val newRmNumber: String,
    @SerializedName("old_family_id") val oldFamilyId: Long,
    @SerializedName("new_family_id") val newFamilyId: Long,
    @SerializedName("new_family_name") val newFamilyName: String?,
    @SerializedName("medical_records_updated") val medicalRecordsUpdated: Int
)
