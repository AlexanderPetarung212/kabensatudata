package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Dashboard DTOs ──────────────────────────────────────────────────────────

data class MedicalDashboardResponse(
    @SerializedName("top_diseases") val topDiseases: List<DiseaseCountDto>,
    @SerializedName("top_medicines") val topMedicines: List<MedicineUsageCountDto>,
    @SerializedName("daily_visits") val dailyVisits: List<DailyVisitDto>,
    val summary: DashboardSummaryDto
)

data class DiseaseCountDto(
    val name: String,
    val count: Int
)

data class MedicineUsageCountDto(
    val name: String,
    val unit: String,
    @SerializedName("total_used") val totalUsed: Int
)

data class DailyVisitDto(
    val date: String,
    val count: Int
)

data class DashboardSummaryDto(
    @SerializedName("total_visits") val totalVisits: Int,
    @SerializedName("total_patients") val totalPatients: Int,
    @SerializedName("period_days") val periodDays: Int
)

// ─── Medicine CRUD DTOs ──────────────────────────────────────────────────────

data class MedicineFullDto(
    val id: Long,
    val name: String,
    @SerializedName("generic_name") val genericName: String?,
    val strength: String?,
    @SerializedName("stock_quantity") val stockQuantity: Int,
    @SerializedName("stock_initial") val stockInitial: Int,
    val unit: String,
    @SerializedName("minimum_stock") val minimumStock: Int,
    val description: String?,
    @SerializedName("is_active") val isActive: Boolean
)

data class MedicineCreateRequest(
    val name: String,
    @SerializedName("generic_name") val genericName: String?,
    val strength: String?,
    val unit: String,
    @SerializedName("stock_quantity") val stockQuantity: Int,
    @SerializedName("minimum_stock") val minimumStock: Int,
    val description: String?,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class StockAdjustRequest(
    val adjustment: Int,
    val reason: String
)

data class MedicineResponse(
    val message: String,
    val data: MedicineFullDto
)

data class MessageOnlyResponse(
    val message: String
)
