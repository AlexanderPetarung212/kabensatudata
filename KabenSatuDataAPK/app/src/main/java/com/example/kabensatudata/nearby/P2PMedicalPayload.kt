package com.example.kabensatudata.nearby

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/**
 * Payload for transferring medical record data between devices.
 * Carries data progressively through 3 stages.
 */
data class P2PMedicalPayload(
    @SerializedName("record_uuid")
    val recordUuid: String = "",
    val stage: String = STAGE_REGISTRATION,     // REGISTRATION, DIAGNOSIS, DISPENSED
    // Patient info
    @SerializedName("patient_name")
    val patientName: String = "",
    @SerializedName("patient_id")
    val patientId: Long = 0,
    @SerializedName("patient_gender")
    val patientGender: String = "",
    // Stage 1: Registration
    @SerializedName("visit_date")
    val visitDate: String = "",
    @SerializedName("chief_complaint")
    val chiefComplaint: String = "",
    val anamnesis: String = "",
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val weight: Double? = null,
    val height: Double? = null,
    @SerializedName("heart_rate")
    val heartRate: Int? = null,
    @SerializedName("body_temperature")
    val bodyTemperature: Double? = null,
    @SerializedName("respiratory_rate")
    val respiratoryRate: Int? = null,
    // Stage 2: Diagnosis
    @SerializedName("diagnosis_code")
    val diagnosisCode: String? = null,
    @SerializedName("diagnosis_name")
    val diagnosisName: String? = null,
    val therapy: String? = null,
    val procedure: String? = null,
    val prescriptions: List<PrescriptionItem> = emptyList(),
    // Timestamp
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        const val STAGE_REGISTRATION = "REGISTRATION"
        const val STAGE_DIAGNOSIS = "DIAGNOSIS"
        const val STAGE_DISPENSED = "DISPENSED"

        private val gson = Gson()

        fun fromJson(json: String): P2PMedicalPayload? = try {
            gson.fromJson(json, P2PMedicalPayload::class.java)
        } catch (_: Exception) { null }
    }
}

data class PrescriptionItem(
    @SerializedName("medicine_id")
    val medicineId: Long = 0,
    @SerializedName("medicine_name")
    val medicineName: String = "",
    @SerializedName("medicine_unit")
    val medicineUnit: String = "",
    val quantity: Int = 0,
    val instruction: String = "",
    val frequency: String = "",
    val dosage: String = "",
    val notes: String = "",
    val dispensed: Boolean = false
) {
    fun toJson(): String = Gson().toJson(this)
}
