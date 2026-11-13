package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── SPM Relevance Check ─────────────────────────────────────────────────────

data class SpmRelevanceResponse(
    val data: SpmRelevanceData
)

data class SpmRelevanceData(
    @SerializedName("family_member_id") val familyMemberId: Long,
    @SerializedName("member_name") val memberName: String,
    val relevant: Boolean,
    @SerializedName("sub_indicators") val subIndicators: List<SpmRelevantSubIndicator>
)

data class SpmRelevantSubIndicator(
    val id: Long,
    val code: String,
    val name: String,
    @SerializedName("indicator_name") val indicatorName: String,
    @SerializedName("already_recorded") val alreadyRecorded: Boolean
)

// ─── SPM Record Link ─────────────────────────────────────────────────────────

data class SpmRecordLinkRequest(
    @SerializedName("medical_record_id") val medicalRecordId: Long,
    @SerializedName("spm_sub_indicator_ids") val spmSubIndicatorIds: List<Long>
)

data class SpmRecordLinkResponse(
    val message: String,
    val linked: Int
)
