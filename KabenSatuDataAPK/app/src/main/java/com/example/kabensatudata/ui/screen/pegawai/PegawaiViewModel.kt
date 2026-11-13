package com.example.kabensatudata.ui.screen.pegawai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.dao.PegawaiDao
import com.example.kabensatudata.data.local.dao.PegawaiStatsCacheDao
import com.example.kabensatudata.data.local.dao.SuratArsipCacheDao
import com.example.kabensatudata.data.local.entity.PegawaiEntity
import com.example.kabensatudata.data.local.entity.PegawaiStatsCacheEntity
import com.example.kabensatudata.data.local.entity.SuratArsipCacheEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.*
import com.example.kabensatudata.util.ArsipFileInfo
import com.example.kabensatudata.util.ArsipOfflineManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

// ─── States ──────────────────────────────────────────────────────────────────

data class PegawaiListState(
    val list: List<PegawaiDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val stats: PegawaiStatsDto? = null,
    val searchQuery: String = "",
    val isOffline: Boolean = false
)

data class PegawaiDetailState(
    val pegawai: PegawaiDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val isOffline: Boolean = false
)

data class PegawaiFormState(
    val nama: String = "", val nip: String = "", val nik: String = "",
    val jenisKelamin: String = "", val jabatan: String = "", val unit: String = "",
    val pangkatGol: String = "", val pendidikanTerakhir: String = "", val profesi: String = "",
    val tanggalLahir: String = "", val alamat: String = "", val noHp: String = "",
    val fotoUri: Uri? = null, val ktpUri: Uri? = null,
    val isSubmitting: Boolean = false, val submitMessage: String? = null
)

data class SuratArsipState(
    val list: List<SuratArsipDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedFilter: String? = null,
    val actionMessage: String? = null,
    val isOffline: Boolean = false,
    val isSyncingFiles: Boolean = false,
    val cachedFileIds: Set<Long> = emptySet()
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class PegawaiViewModel @Inject constructor(
    private val api: ApiService,
    private val pegawaiDao: PegawaiDao,
    private val arsipCacheDao: SuratArsipCacheDao,
    private val statsCacheDao: PegawaiStatsCacheDao,
    private val arsipOfflineManager: ArsipOfflineManager,
    private val tokenManager: AuthTokenManager
) : ViewModel() {

    private val gson = Gson()

    private val _listState = MutableStateFlow(PegawaiListState())
    val listState = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(PegawaiDetailState())
    val detailState = _detailState.asStateFlow()

    private val _formState = MutableStateFlow(PegawaiFormState())
    val formState = _formState.asStateFlow()

    private val _arsipState = MutableStateFlow(SuratArsipState())
    val arsipState = _arsipState.asStateFlow()

    // ─── Pegawai List (cache-first) ───────────────────────────────────

    fun loadPegawaiList(search: String? = null) {
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true, error = null) }

            // 1. Load from cache first (instant display)
            try {
                val cached = if (search.isNullOrBlank()) pegawaiDao.getAll() else pegawaiDao.search(search)
                if (cached.isNotEmpty()) {
                    _listState.update { it.copy(list = cached.map { e -> e.toDto() }, isLoading = true) }
                }
            } catch (_: Exception) {}

            // 2. Try fetch from API
            try {
                val res = api.getPegawaiList(search)
                if (res.isSuccessful) {
                    val apiData = res.body()?.data ?: emptyList()
                    _listState.update { it.copy(list = apiData, isLoading = false, isOffline = false) }

                    // Cache if full list (no search filter)
                    if (search.isNullOrBlank()) {
                        pegawaiDao.deleteAll()
                        pegawaiDao.insertAll(apiData.map { it.toEntity() })
                    }
                } else {
                    // API error but we have cache → show cached data
                    _listState.update { it.copy(isLoading = false, isOffline = true) }
                }
            } catch (e: Exception) {
                // Network error → show cached data with offline indicator
                val cached = if (search.isNullOrBlank()) pegawaiDao.getAll() else pegawaiDao.search(search)
                _listState.update {
                    it.copy(
                        list = cached.map { e -> e.toDto() },
                        isLoading = false,
                        isOffline = true,
                        error = if (cached.isEmpty()) "Tidak ada koneksi & cache kosong" else null
                    )
                }
            }
        }
    }

    fun updateSearch(query: String) {
        _listState.update { it.copy(searchQuery = query) }
        loadPegawaiList(query.ifBlank { null })
    }

    // ─── Stats (cache-first) ──────────────────────────────────────────

    fun loadStats() {
        viewModelScope.launch {
            // 1. Load from cache first
            try {
                statsCacheDao.get()?.let { cached ->
                    val stats = gson.fromJson(cached.jsonData, PegawaiStatsDto::class.java)
                    _listState.update { it.copy(stats = stats) }
                }
            } catch (_: Exception) {}

            // 2. Try API
            try {
                val res = api.getPegawaiStats()
                if (res.isSuccessful) {
                    val data = res.body()?.data
                    _listState.update { it.copy(stats = data) }
                    data?.let {
                        statsCacheDao.insertOrUpdate(
                            PegawaiStatsCacheEntity(jsonData = gson.toJson(it))
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ─── Detail ───────────────────────────────────────────────────────

    fun loadDetail(id: Long) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, error = null, isOffline = false) }

            // 1. Cache-first: show cached data instantly
            try {
                val cached = pegawaiDao.getById(id)
                if (cached != null) {
                    _detailState.update { it.copy(pegawai = cached.toDto(gson), isLoading = true) }
                }
            } catch (_: Exception) {}

            // 2. Try API
            try {
                val res = api.getPegawaiDetail(id)
                if (res.isSuccessful) {
                    val apiData = res.body()?.data
                    _detailState.update { it.copy(pegawai = apiData, isLoading = false, isOffline = false) }

                    // Cache detail with dokumen JSON
                    if (apiData != null) {
                        val dokJson = if (!apiData.dokumen.isNullOrEmpty()) gson.toJson(apiData.dokumen) else null
                        try {
                            pegawaiDao.updateDokumenJson(id, dokJson ?: "")
                        } catch (_: Exception) {
                            // If no row exists yet, insert full entity
                            pegawaiDao.insertAll(listOf(apiData.toEntity(gson)))
                        }
                    }
                } else {
                    // API error → keep cache
                    val cached = pegawaiDao.getById(id)
                    _detailState.update {
                        it.copy(
                            pegawai = cached?.toDto(gson) ?: it.pegawai,
                            isLoading = false,
                            isOffline = true,
                            error = if (cached == null && it.pegawai == null) "Data tidak ditemukan" else null
                        )
                    }
                }
            } catch (e: Exception) {
                val cached = pegawaiDao.getById(id)
                _detailState.update {
                    it.copy(
                        pegawai = cached?.toDto(gson) ?: it.pegawai,
                        isLoading = false,
                        isOffline = true,
                        error = if (cached == null && it.pegawai == null) e.message else null
                    )
                }
            }
        }
    }

    fun clearActionMessage() { _detailState.update { it.copy(actionMessage = null) } }

    // ─── Delete Pegawai ───────────────────────────────────────────────

    fun deletePegawai(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.deletePegawai(id)
                if (res.isSuccessful) onSuccess()
                else _detailState.update { it.copy(actionMessage = "Gagal menghapus") }
            } catch (e: Exception) {
                _detailState.update { it.copy(actionMessage = e.message) }
            }
        }
    }

    // ─── Delete Document ──────────────────────────────────────────────

    fun deleteDocument(pegawaiId: Long, docId: Long) {
        viewModelScope.launch {
            try {
                val res = api.deletePegawaiDocument(pegawaiId, docId)
                if (res.isSuccessful) {
                    _detailState.update { it.copy(actionMessage = "Dokumen dihapus") }
                    loadDetail(pegawaiId)
                } else {
                    _detailState.update { it.copy(actionMessage = "Gagal menghapus dokumen") }
                }
            } catch (e: Exception) {
                _detailState.update { it.copy(actionMessage = e.message) }
            }
        }
    }

    // ─── Form ─────────────────────────────────────────────────────────

    fun updateFormField(updater: PegawaiFormState.() -> PegawaiFormState) {
        _formState.update { it.updater() }
    }

    fun resetForm() { _formState.value = PegawaiFormState() }

    fun populateFormFromDetail() {
        _detailState.value.pegawai?.let { p ->
            _formState.value = PegawaiFormState(
                nama = p.nama, nip = p.nip ?: "", nik = p.nik ?: "",
                jenisKelamin = p.jenisKelamin ?: "", jabatan = p.jabatan ?: "",
                unit = p.unit ?: "", pangkatGol = p.pangkatGol ?: "",
                pendidikanTerakhir = p.pendidikanTerakhir ?: "", profesi = p.profesi ?: "",
                tanggalLahir = p.tanggalLahir ?: "", alamat = p.alamat ?: "", noHp = p.noHp ?: ""
            )
        }
    }

    fun submitPegawai(editId: Long? = null, fotoFile: File? = null, ktpFile: File? = null) {
        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true, submitMessage = null) }
            try {
                val f = _formState.value
                val nama = f.nama.toRequestBody("text/plain".toMediaType())
                val nip = f.nip.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val nik = f.nik.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val jk = f.jenisKelamin.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val jabatan = f.jabatan.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val unit = f.unit.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val gol = f.pangkatGol.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val pend = f.pendidikanTerakhir.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val prof = f.profesi.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val tl = f.tanggalLahir.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val alm = f.alamat.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                val hp = f.noHp.ifBlank { null }?.toRequestBody("text/plain".toMediaType())

                val fotoPart = fotoFile?.let {
                    MultipartBody.Part.createFormData("foto", it.name, it.asRequestBody("image/*".toMediaType()))
                }
                val ktpPart = ktpFile?.let {
                    MultipartBody.Part.createFormData("ktp", it.name, it.asRequestBody("image/*".toMediaType()))
                }

                val res = if (editId != null) {
                    api.updatePegawai(editId, nama, nip, nik, jk, jabatan, unit, gol, pend, prof, tl, alm, hp, fotoPart, ktpPart)
                } else {
                    api.createPegawai(nama, nip, nik, jk, jabatan, unit, gol, pend, prof, tl, alm, hp, null, fotoPart, ktpPart)
                }

                if (res.isSuccessful) {
                    _formState.update { it.copy(isSubmitting = false, submitMessage = "Berhasil!") }
                } else {
                    _formState.update { it.copy(isSubmitting = false, submitMessage = "Gagal: ${res.code()}") }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isSubmitting = false, submitMessage = e.message) }
            }
        }
    }

    // ─── Surat Arsip (cache-first) ────────────────────────────────────

    fun loadSuratArsip(jenis: String? = null, search: String? = null) {
        viewModelScope.launch {
            _arsipState.update { it.copy(isLoading = true, error = null) }

            // 1. Load from cache first + check which files are cached locally
            try {
                val cached = arsipCacheDao.getFiltered(jenis, search)
                if (cached.isNotEmpty()) {
                    val cachedIds = cached.filter {
                        arsipOfflineManager.isFileCached(it.id, it.fileExtension)
                    }.map { it.id }.toSet()
                    _arsipState.update {
                        it.copy(list = cached.map { e -> e.toDto() }, isLoading = true, cachedFileIds = cachedIds)
                    }
                }
            } catch (_: Exception) {}

            // 2. Try API
            try {
                val res = api.getSuratArsip(jenis, search)
                if (res.isSuccessful) {
                    val apiData = res.body()?.data ?: emptyList()

                    // Check cached files
                    val cachedIds = apiData.filter {
                        arsipOfflineManager.isFileCached(it.id, it.fileExtension)
                    }.map { it.id }.toSet()

                    _arsipState.update {
                        it.copy(list = apiData, isLoading = false, isOffline = false, cachedFileIds = cachedIds)
                    }

                    // Cache full list
                    if (jenis == null && search.isNullOrBlank()) {
                        arsipCacheDao.deleteAll()
                        arsipCacheDao.insertAll(apiData.map {
                            it.toEntity().copy(isFileCached = it.id in cachedIds)
                        })
                    }

                    // 3. Proactive file sync: download all uncached files
                    syncArsipFiles(apiData)
                } else {
                    _arsipState.update { it.copy(isLoading = false, isOffline = true) }
                }
            } catch (e: Exception) {
                val cached = arsipCacheDao.getFiltered(jenis, search)
                val cachedIds = cached.filter {
                    arsipOfflineManager.isFileCached(it.id, it.fileExtension)
                }.map { it.id }.toSet()
                _arsipState.update {
                    it.copy(
                        list = cached.map { e -> e.toDto() },
                        isLoading = false,
                        isOffline = true,
                        cachedFileIds = cachedIds,
                        error = if (cached.isEmpty()) "Tidak ada koneksi & cache kosong" else null
                    )
                }
            }
        }
    }

    /**
     * Background sync: download all arsip files that haven't been cached yet.
     */
    private fun syncArsipFiles(items: List<SuratArsipDto>) {
        viewModelScope.launch {
            val uncached = items.filter {
                it.hasFile == true && !arsipOfflineManager.isFileCached(it.id, it.fileExtension)
            }
            if (uncached.isEmpty()) return@launch

            _arsipState.update { it.copy(isSyncingFiles = true) }

            val token = tokenManager.getToken()
            val fileInfos = uncached.map {
                ArsipFileInfo(
                    id = it.id,
                    downloadUrl = it.downloadUrl,
                    fileExtension = it.fileExtension,
                    isGDrive = it.gdriveUrl != null
                )
            }

            val count = arsipOfflineManager.syncAllFiles(fileInfos, token)

            // Update cached IDs
            val allCachedIds = items.filter {
                arsipOfflineManager.isFileCached(it.id, it.fileExtension)
            }.map { it.id }.toSet()

            _arsipState.update {
                it.copy(isSyncingFiles = false, cachedFileIds = allCachedIds)
            }

            // Update Room cache
            allCachedIds.forEach { id ->
                try { arsipCacheDao.updateFileCached(id, true) } catch (_: Exception) {}
            }

            if (count > 0) {
                _arsipState.update {
                    it.copy(actionMessage = "$count file arsip berhasil di-download untuk offline")
                }
            }
        }
    }

    fun updateArsipSearch(query: String) {
        _arsipState.update { it.copy(searchQuery = query) }
        loadSuratArsip(_arsipState.value.selectedFilter, query.ifBlank { null })
    }

    fun updateArsipFilter(jenis: String?) {
        _arsipState.update { it.copy(selectedFilter = jenis) }
        loadSuratArsip(jenis, _arsipState.value.searchQuery.ifBlank { null })
    }

    fun clearArsipActionMessage() { _arsipState.update { it.copy(actionMessage = null) } }

    fun deleteSuratArsip(id: Long) {
        viewModelScope.launch {
            try {
                val res = api.deleteSuratArsip(id)
                if (res.isSuccessful) {
                    _arsipState.update { it.copy(actionMessage = "Arsip berhasil dihapus") }
                    loadSuratArsip(_arsipState.value.selectedFilter, _arsipState.value.searchQuery.ifBlank { null })
                } else {
                    _arsipState.update { it.copy(actionMessage = "Gagal menghapus arsip") }
                }
            } catch (e: Exception) {
                _arsipState.update { it.copy(actionMessage = e.message) }
            }
        }
    }

    fun uploadSuratArsip(jenis: String, perihal: String, nomorSurat: String?, file: File?) {
        viewModelScope.launch {
            _arsipState.update { it.copy(isLoading = true) }
            try {
                val jenisPart = jenis.toRequestBody("text/plain".toMediaType())
                val perihalPart = perihal.toRequestBody("text/plain".toMediaType())
                val nomorPart = nomorSurat?.toRequestBody("text/plain".toMediaType())
                val filePart = file?.let {
                    MultipartBody.Part.createFormData("file", it.name, it.asRequestBody("application/octet-stream".toMediaType()))
                }

                val res = api.uploadSuratArsip(jenisPart, nomorPart, perihalPart, null, filePart)
                if (res.isSuccessful) {
                    _arsipState.update { it.copy(actionMessage = "Arsip berhasil ditambahkan", isLoading = false) }
                    loadSuratArsip(_arsipState.value.selectedFilter, _arsipState.value.searchQuery.ifBlank { null })
                } else {
                    _arsipState.update { it.copy(actionMessage = "Gagal: ${res.code()}", isLoading = false) }
                }
            } catch (e: Exception) {
                _arsipState.update { it.copy(actionMessage = e.message, isLoading = false) }
            }
        }
    }

    fun updateSuratArsip(id: Long, jenis: String?, perihal: String?, nomorSurat: String?) {
        viewModelScope.launch {
            _arsipState.update { it.copy(isLoading = true) }
            try {
                val jenisPart = jenis?.toRequestBody("text/plain".toMediaType())
                val perihalPart = perihal?.toRequestBody("text/plain".toMediaType())
                val nomorPart = nomorSurat?.toRequestBody("text/plain".toMediaType())

                val res = api.updateSuratArsip(id, jenisPart, nomorPart, perihalPart)
                if (res.isSuccessful) {
                    _arsipState.update { it.copy(actionMessage = "Arsip berhasil diperbarui", isLoading = false) }
                    loadSuratArsip(_arsipState.value.selectedFilter, _arsipState.value.searchQuery.ifBlank { null })
                } else {
                    _arsipState.update { it.copy(actionMessage = "Gagal: ${res.code()}", isLoading = false) }
                }
            } catch (e: Exception) {
                _arsipState.update { it.copy(actionMessage = e.message, isLoading = false) }
            }
        }
    }

    // ─── Surat Tugas ──────────────────────────────────────────────────

    fun createSuratTugas(
        pegawaiId: Long, nomorSurat: String?, dasar: String?,
        maksudTugas: String, tanggalMulai: String, tanggalSelesai: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true) }
            try {
                val res = api.createSuratTugas(pegawaiId, nomorSurat, dasar, maksudTugas, tanggalMulai, tanggalSelesai)
                if (res.isSuccessful) {
                    _formState.update { it.copy(isSubmitting = false, submitMessage = "Surat tugas berhasil dibuat!") }
                    onSuccess()
                } else {
                    _formState.update { it.copy(isSubmitting = false, submitMessage = "Gagal: ${res.code()}") }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isSubmitting = false, submitMessage = e.message) }
            }
        }
    }
}

// ─── Mapping Extensions ──────────────────────────────────────────────────────

private fun PegawaiEntity.toDto(gson: Gson? = null) = PegawaiDto(
    id = id, userId = null, nama = nama, nip = nip, nik = nik,
    jenisKelamin = jenisKelamin, jabatan = jabatan, unit = unit,
    pangkatGol = pangkatGol, pendidikanTerakhir = pendidikanTerakhir,
    profesi = profesi, tanggalLahir = tanggalLahir, alamat = alamat,
    noHp = noHp, fotoUrl = fotoUrl, ktpUrl = null, email = email,
    dokumenCount = dokumenCount,
    dokumen = if (gson != null && !dokumenJson.isNullOrBlank()) {
        try {
            gson.fromJson(dokumenJson, Array<DokumenDto>::class.java).toList()
        } catch (_: Exception) { null }
    } else null
)

private fun PegawaiDto.toEntity(gson: Gson? = null) = PegawaiEntity(
    id = id, nama = nama, nip = nip, nik = nik,
    jenisKelamin = jenisKelamin, jabatan = jabatan, unit = unit,
    pangkatGol = pangkatGol, pendidikanTerakhir = pendidikanTerakhir,
    profesi = profesi, tanggalLahir = tanggalLahir, alamat = alamat,
    noHp = noHp, fotoUrl = fotoUrl, email = email, dokumenCount = dokumenCount,
    dokumenJson = if (gson != null && !dokumen.isNullOrEmpty()) gson.toJson(dokumen) else null
)

private fun SuratArsipCacheEntity.toDto() = SuratArsipDto(
    id = id, tanggal = tanggal, nomor = nomor, perihal = perihal,
    jenis = jenis, pegawaiNama = pegawaiNama, hasFile = hasFile,
    fileName = fileName, fileExtension = fileExtension,
    gdriveUrl = gdriveUrl, downloadUrl = downloadUrl
)

private fun SuratArsipDto.toEntity() = SuratArsipCacheEntity(
    id = id, tanggal = tanggal, nomor = nomor, perihal = perihal,
    jenis = jenis, pegawaiNama = pegawaiNama, hasFile = hasFile ?: false,
    fileName = fileName, fileExtension = fileExtension,
    gdriveUrl = gdriveUrl, downloadUrl = downloadUrl
)
