package com.example.kabensatudata.data.remote.dto

// ─── BOK Calendar DTOs ──────────────────────────────────────────────────────

// Login
data class BokLoginRequest(val email: String, val password: String)
data class BokLoginResponse(val token: String, val user: BokUser)
data class BokUser(
    val id: Long,
    val name: String,
    val email: String,
    val role: String?,
    val employee_id: Long?
)

// Calendar Grid
data class CalendarResponse(
    val year: Int,
    val available_years: List<Int>,
    val available_komponen: List<String>,
    val activities: List<CalendarActivity>
)

data class CalendarActivity(
    val id: Long,
    val kegiatan: String,
    val komponen: String,
    val rincian_menu: String?,
    val participant_limit: Int = 0,   // Maks peserta dari faktor "orang" di RAB
    val months: Map<String, CalendarMonth>
)

data class CalendarMonth(
    val has_activity: Boolean,
    val count: Int,
    val marked: Boolean,
    val claimed: Boolean,
    val tahap: Int?,
    val is_mine: Boolean,
    val participant_ids: List<Int> = emptyList(),
    // Status indicators untuk KelolaBulan
    val has_peserta: Boolean = false,
    val has_nomor_surat: Boolean = false,
    val has_tanggal_kegiatan: Boolean = false,
    val has_desa: Boolean = false
)

// Calendar Detail
data class CalendarDetailResponse(
    val kegiatan: String,
    val komponen: String,
    val bulan: String,
    val tahun: Int,
    val tahap: Int,
    val participants: List<CalendarParticipant>,
    val no_participants: Boolean,
    val schedules: List<CalendarSchedule>,
    val estimated_budget: Double,
    val planned_total: Double,
    val monthly_count: Int,
    val claimed: Boolean,
    val marked: Boolean,
    // Download IDs (available when claimed)
    val sppt_lpj_id: Long? = null,
    val sppd_lpj_id: Long? = null,
    val tiba_berangkat_id: Long? = null,
    val item_opsional: List<ItemOpsionalDto> = emptyList()
)

data class CalendarParticipant(
    val nama: String,
    val role: String?
)

data class CalendarSchedule(
    val type: String,
    val nomor_surat: String?,
    val tanggal_surat: String?,
    val tanggal_kegiatan: String?
)

data class ItemOpsionalDto(
    val id: Long,
    val label: String,
    val type: String?,
    val amount: Double
)

// ─── Claim DTOs ─────────────────────────────────────────────────────────────

data class ClaimPrepResponse(
    val can_claim: Boolean,
    val is_reclaim: Boolean,
    val reason: String?,
    val target_darat: Int,
    val target_seberang: Int,
    val auto_desa_darat: String?,
    val auto_desa_seberang: String?
)

data class ClaimRequest(
    val month: Int,
    val no_surat_sppt: String? = null,
    val tanggal_surat_sppt: String? = null,
    val jumlah_desa_darat: Int? = null,
    val desa_tujuan_darat: String? = null,
    val no_surat_sppd: String? = null,
    val tanggal_surat_sppd: String? = null,
    val jumlah_desa_seberang: Int? = null,
    val desa_tujuan_seberang: String? = null
)

data class ClaimResponse(
    val success: Boolean,
    val message: String?,
    val sppt_lpj_id: Long?,
    val sppd_lpj_id: Long?,
    val tiba_berangkat_id: Long?,
    val item_opsional: List<ItemOpsionalDto>?
)
