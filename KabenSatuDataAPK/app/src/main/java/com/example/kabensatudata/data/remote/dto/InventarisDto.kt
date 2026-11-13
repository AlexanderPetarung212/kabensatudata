package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Inventaris DTOs ─────────────────────────────────────────────────────────

data class PegawaiRuanganDto(
    val id: Long,
    val nama: String,
    val jabatan: String?,
    @SerializedName("foto_url") val fotoUrl: String?
)

data class RuanganDto(
    val id: Long,
    val uuid: String,
    val nama: String,
    val kode: String?,
    val lantai: String?,
    val deskripsi: String?,
    @SerializedName("foto_url") val fotoUrl: String?,
    @SerializedName("total_barang") val totalBarang: Int?,
    @SerializedName("total_unit") val totalUnit: Int?,
    @SerializedName("kondisi_summary") val kondisiSummary: KondisiSummaryDto?,
    @SerializedName("ar_enabled") val arEnabled: Boolean? = false,
    @SerializedName("penanggung_jawab") val penanggungJawab: PegawaiRuanganDto? = null,
    @SerializedName("pegawai_bertugas") val pegawaiBertugas: List<PegawaiRuanganDto>? = null,
    val barang: List<InventarisBarangDto>?
)

data class InventarisBarangDto(
    val id: Long,
    @SerializedName("ruangan_id") val ruanganId: Long,
    val nama: String,
    @SerializedName("kode_inventaris") val kodeInventaris: String?,
    val merek: String?,
    val jumlah: Int,
    val kondisi: String,
    @SerializedName("tahun_perolehan") val tahunPerolehan: Int?,
    @SerializedName("sumber_dana") val sumberDana: String?,
    @SerializedName("foto_url") val fotoUrl: String?,
    val keterangan: String?,
    @SerializedName("ar_pos_x") val arPosX: Float? = null,
    @SerializedName("ar_pos_y") val arPosY: Float? = null,
    @SerializedName("ar_pos_z") val arPosZ: Float? = null,
    @SerializedName("ar_positioned") val arPositioned: Boolean? = false
)

data class KondisiSummaryDto(
    val baik: Int = 0,
    @SerializedName("rusak_ringan") val rusakRingan: Int = 0,
    @SerializedName("rusak_berat") val rusakBerat: Int = 0,
    @SerializedName("tidak_aktif") val tidakAktif: Int = 0
)

data class InventarisStatsDto(
    @SerializedName("total_ruangan") val totalRuangan: Int,
    @SerializedName("total_jenis") val totalJenis: Int,
    @SerializedName("total_unit") val totalUnit: Int,
    val kondisi: KondisiSummaryDto?
)

// ─── AR DTOs ─────────────────────────────────────────────────────────────────

data class ArPositionItem(
    @SerializedName("barang_id") val barangId: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

data class ArPositionRequest(val positions: List<ArPositionItem>)

data class ArDataResponse(
    @SerializedName("ar_enabled") val arEnabled: Boolean,
    val ruangan: ArRuanganInfo?,
    val barang: List<InventarisBarangDto>
)

data class ArRuanganInfo(
    val id: Long,
    val nama: String,
    val kode: String?
)

// ─── Responses ───────────────────────────────────────────────────────────────

data class RuanganListResponse(
    val data: List<RuanganDto>,
    val stats: InventarisStatsDto?
)

data class RuanganDetailResponse(val data: RuanganDto)
data class InventarisCrudResponse(val data: Any?, val message: String?)
