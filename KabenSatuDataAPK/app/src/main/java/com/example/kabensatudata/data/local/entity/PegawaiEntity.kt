package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pegawai_cache")
data class PegawaiEntity(
    @PrimaryKey val id: Long,
    val nama: String,
    val nip: String? = null,
    val nik: String? = null,
    val jenisKelamin: String? = null,
    val jabatan: String? = null,
    val unit: String? = null,
    val pangkatGol: String? = null,
    val pendidikanTerakhir: String? = null,
    val profesi: String? = null,
    val tanggalLahir: String? = null,
    val alamat: String? = null,
    val noHp: String? = null,
    val fotoUrl: String? = null,
    val email: String? = null,
    val dokumenCount: Int? = null,
    val dokumenJson: String? = null,
    val lastSynced: Long = System.currentTimeMillis()
)

@Entity(tableName = "surat_arsip_cache")
data class SuratArsipCacheEntity(
    @PrimaryKey val id: Long,
    val tanggal: String,
    val nomor: String? = null,
    val perihal: String? = null,
    val jenis: String? = null,
    val pegawaiNama: String? = null,
    val hasFile: Boolean = false,
    val fileName: String? = null,
    val fileExtension: String? = null,
    val gdriveUrl: String? = null,
    val downloadUrl: String? = null,
    val isFileCached: Boolean = false,
    val lastSynced: Long = System.currentTimeMillis()
)

@Entity(tableName = "pegawai_stats_cache")
data class PegawaiStatsCacheEntity(
    @PrimaryKey val id: Int = 1,
    val jsonData: String,
    val lastSynced: Long = System.currentTimeMillis()
)
