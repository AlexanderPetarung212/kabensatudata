package com.example.kabensatudata.data.remote.dto

// ─── POA DTOs ───────────────────────────────────────────────────────────────

// POA List
data class PoaListResponse(
    val data: List<PoaListItem>,
    val current_page: Int,
    val last_page: Int,
    val total: Int,
    val available_komponen: List<String>,
    val available_years: List<Int>
)

data class PoaListItem(
    val id: Long,
    val kegiatan: String,
    val komponen: String,
    val rincian_menu: String?,
    val planned_total: Double,
    val year: Int,
    val months: Map<String, PoaMonthSummary>,
    val participants: List<PoaParticipantDto>
)

data class PoaMonthSummary(
    val has_activity: Boolean = false,
    val count: Int = 0,
    val marked: Boolean = false,
    val claimed: Boolean = false,
    val locked: Boolean = false,
    val is_mine: Boolean = false,
    val tahap: Int? = null
)

data class PoaParticipantDto(
    val employee_id: Long,
    val nama: String,
    val role: String? = null,
    val borrowed_employee_id: Long? = null
)

// POA Detail
data class PoaDetailResponse(
    val id: Long,
    val kegiatan: String,
    val komponen: String,
    val rincian_menu: String?,
    val year: Int,
    val planned_total: Double,
    val nomor_surat: String?,
    val output_target: String?,
    val rab_id: Long?,
    val participants: List<PoaParticipantDto>,
    val claim_status: Map<String, PoaClaimStatus>,
    val rab_targets: Map<String, Int>,
    val item_progress: Map<String, Any?>?,
    val rab_items: List<RabItemDto>,
    val villages: List<PoaVillageDto>,
    val is_admin: Boolean
)

data class PoaClaimStatus(
    val participant_ids: List<Long> = emptyList(),
    val assigned: Boolean = false,
    val claimed: Boolean = false,
    val claimed_hidden: Boolean = false,
    val locked: Boolean = false,
    val claimed_at: String? = null,
    val claimed_by: Long? = null,
    val claimed_by_name: String? = null,
    val sppt_lpj_id: Long? = null,
    val sppd_lpj_id: Long? = null,
    val tiba_berangkat_id: Long? = null,
    val allowed: Boolean = false,
    val no_surat_sppt: String? = null,
    val no_surat_sppd: String? = null,
    val tanggal_kegiatan_sppt: String? = null,
    val tanggal_kegiatan_sppd: String? = null,
    val tanggal_surat_sppt: String? = null,
    val tanggal_surat_sppd: String? = null,
    val holidays: String? = null,
    val darat_village_ids: List<Long> = emptyList(),
    val seberang_village_ids: List<Long> = emptyList(),
    val desa_tujuan_darat: String? = null,
    val desa_tujuan_seberang: String? = null,
    val borrowed_map: Map<String, Long> = emptyMap(),
    val marked: Boolean = false,
    val count: Int = 0,
    val amount: Double = 0.0,
    val item_opsional: List<ItemOpsionalDto> = emptyList()
)

data class RabItemDto(
    val id: Long,
    val label: String?,
    val type: String? = null,
    val subtotal: Double = 0.0,
    val unit_price: Double? = null,
    val factors: List<Map<String, Any?>>? = null
)

data class PoaVillageDto(
    val id: Long,
    val nama: String,
    val akses: String
)

// POA Form
data class RabOption(
    val id: Long,
    val komponen: String?,
    val rincian_menu: String?,
    val kegiatan: String?,
    val total: Double
)

data class EmployeeDto(
    val id: Long,
    val nama: String,
    val nip: String?,
    val jabatan: String?
)

// POA Actions
data class PoaCreateRequest(
    val year: Int,
    val rab_id: Long,
    val nomor_surat: String? = null,
    val kegiatan: String,
    val output_target: String? = null,
    val planned_total: Double,
    val participants: List<ParticipantInput>? = null
)

data class ParticipantInput(
    val employee_id: Long,
    val role: String? = null
)

data class MonthMetaRequest(
    val month: Int,
    val no_surat_sppt: String? = null,
    val no_surat_sppd: String? = null,
    val tanggal_kegiatan_sppt: String? = null,
    val tanggal_kegiatan_sppd: String? = null,
    val tanggal_surat_sppt: String? = null,
    val tanggal_surat_sppd: String? = null,
    val darat_village_ids: String? = null,
    val seberang_village_ids: String? = null,
    val participant_ids: String? = null,
    val holidays: String? = null,
    val borrowed_map: String? = null
)

data class ItemProgressRequest(
    val month: Int,
    val rab_item_id: Long,
    val completed: Boolean,
    val absorbed_amount: Double? = null
)

data class ToggleRequest(val month: Int, val marked: Boolean? = null, val locked: Boolean? = null, val hidden: Boolean? = null)
data class CarryOverRequest(val month: Int, val realized: Double? = null)
data class BulkLockRequest(val year: Int, val month: Int, val locked: Boolean)
data class GenericResponse(val ok: Boolean? = null, val success: Boolean? = null, val message: String? = null, val id: Long? = null, val error: String? = null)
