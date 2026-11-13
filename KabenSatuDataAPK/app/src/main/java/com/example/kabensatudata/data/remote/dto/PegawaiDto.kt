package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Pegawai DTOs ────────────────────────────────────────────────────────────

data class PegawaiDto(
    val id: Long,
    @SerializedName("user_id") val userId: Long?,
    val nama: String,
    val nip: String?,
    val nik: String?,
    @SerializedName("jenis_kelamin") val jenisKelamin: String?,
    val jabatan: String?,
    val unit: String?,
    @SerializedName("pangkat_gol") val pangkatGol: String?,
    @SerializedName("pendidikan_terakhir") val pendidikanTerakhir: String?,
    val profesi: String?,
    @SerializedName("tanggal_lahir") val tanggalLahir: String?,
    val alamat: String?,
    @SerializedName("no_hp") val noHp: String?,
    @SerializedName("foto_url") val fotoUrl: String?,
    @SerializedName("ktp_url") val ktpUrl: String?,
    val email: String?,
    @SerializedName("dokumen_count") val dokumenCount: Int?,
    val dokumen: List<DokumenDto>?
)

data class DokumenDto(
    val id: Long,
    val jenis: String,
    val judul: String,
    @SerializedName("issued_at") val issuedAt: String?,
    val keterangan: String?,
    @SerializedName("file_name") val fileName: String?,
    @SerializedName("file_extension") val fileExtension: String?,
    @SerializedName("gdrive_url") val gdriveUrl: String?,
    @SerializedName("download_url") val downloadUrl: String?
)

data class SuratArsipDto(
    val id: Long,
    val tanggal: String,
    val nomor: String?,
    val perihal: String?,
    val jenis: String?,
    @SerializedName("pegawai_nama") val pegawaiNama: String?,
    @SerializedName("has_file") val hasFile: Boolean?,
    @SerializedName("file_name") val fileName: String?,
    @SerializedName("file_extension") val fileExtension: String?,
    @SerializedName("gdrive_url") val gdriveUrl: String?,
    @SerializedName("download_url") val downloadUrl: String?
)

data class ChartItemDto(
    val label: String,
    val value: Int
)

data class PegawaiChartsDto(
    val gender: List<ChartItemDto>?,
    val education: List<ChartItemDto>?,
    val profession: List<ChartItemDto>?,
    val golongan: List<ChartItemDto>?
)

data class PegawaiStatsDto(
    @SerializedName("total_pegawai") val totalPegawai: Int,
    @SerializedName("total_dokumen") val totalDokumen: Int,
    @SerializedName("total_surat_tugas") val totalSuratTugas: Int,
    val charts: PegawaiChartsDto?
)

// ─── Responses ───────────────────────────────────────────────────────────────

data class PegawaiListResponse(val data: List<PegawaiDto>)
data class PegawaiDetailResponse(val data: PegawaiDto)
data class PegawaiStatsResponse(val data: PegawaiStatsDto)
data class DokumenListResponse(val data: List<DokumenDto>)
data class SuratArsipListResponse(val data: List<SuratArsipDto>)
data class PegawaiCreateResponse(val data: PegawaiDto?, val message: String?)
data class SuratTugasCreateResponse(val data: Any?, val message: String?)
data class SuratArsipCrudResponse(val data: Any?, val message: String?)
