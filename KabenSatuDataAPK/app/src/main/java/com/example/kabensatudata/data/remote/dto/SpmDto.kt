package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── SPM Dashboard Response ──────────────────────────────────────────────────

data class SpmDashboardWrapper(val data: SpmDashboardData?)

data class SpmDashboardData(
    val year: Int,
    val month: Int?,
    @SerializedName("village_id") val villageId: Long?,
    val overview: SpmOverview,
    val indicators: List<SpmIndicatorDto>,
    val villages: List<SpmVillageOption>
)

data class SpmOverview(
    val labels: List<String>,
    val actuals: List<Int>,
    val denoms: List<Int>,
    val potentials: List<Int>
)

data class SpmIndicatorDto(
    val code: String,
    val name: String,
    val subs: List<SpmSubIndicatorDto>
)

data class SpmSubIndicatorDto(
    val id: Long,
    val code: String,
    val name: String,
    @SerializedName("numerator_riil") val numeratorRiil: Int,
    @SerializedName("denominator_riil") val denominatorRiil: Int,
    @SerializedName("percentage_riil") val percentageRiil: Double,
    @SerializedName("denominator_dinkes") val denominatorDinkes: Int?,
    @SerializedName("target_percentage") val targetPercentage: Double?,
    @SerializedName("target_absolute") val targetAbsolute: Int?,
    val gap: Int?,
    @SerializedName("is_overridden") val isOverridden: Boolean
)

data class SpmVillageOption(val id: Long, val name: String)

// ─── SPM Sub-Detail Response ─────────────────────────────────────────────────

data class SpmSubDetailWrapper(val data: SpmSubDetailData?)

data class SpmSubDetailData(
    @SerializedName("sub_indicator") val subIndicator: SpmSubInfo,
    val year: Int,
    val month: Int?,
    @SerializedName("achieved_count") val achievedCount: Int,
    @SerializedName("potential_count") val potentialCount: Int,
    val rows: List<SpmPatientRow>,
    val potential: List<SpmPatientRow>
)

data class SpmSubInfo(val id: Long, val code: String, val name: String)

data class SpmPatientRow(
    val id: Long,
    val name: String?,
    val gender: String?,
    val age: Int?,
    val nik: String?,
    val village: String?,
    @SerializedName("last_visit") val lastVisit: String?,
    val status: String?
)
