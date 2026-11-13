package com.example.kabensatudata.ui.screen.inventaris

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.InventarisCacheDao
import com.example.kabensatudata.data.local.entity.InventarisCacheEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

data class RuanganListState(
    val list: List<RuanganDto> = emptyList(),
    val stats: InventarisStatsDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isOffline: Boolean = false
)

data class RuanganDetailState(
    val ruangan: RuanganDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val isOffline: Boolean = false
)

data class RuanganFormState(
    val nama: String = "", val kode: String = "", val lantai: String = "",
    val deskripsi: String = "", val fotoUri: Uri? = null,
    val isSubmitting: Boolean = false, val submitMessage: String? = null
)

data class BarangFormState(
    val nama: String = "", val kodeInventaris: String = "", val merek: String = "",
    val jumlah: String = "1", val kondisi: String = "baik",
    val tahunPerolehan: String = "", val sumberDana: String = "",
    val keterangan: String = "", val fotoUri: Uri? = null,
    val isSubmitting: Boolean = false, val submitMessage: String? = null,
    val editBarang: InventarisBarangDto? = null
)

data class ArDataState(
    val isLoading: Boolean = false,
    val barangList: List<InventarisBarangDto> = emptyList(),
    val ruanganNama: String? = null,
    val arEnabled: Boolean = false,
    val saveMessage: String? = null
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class InventarisViewModel @Inject constructor(
    private val api: ApiService,
    private val cacheDao: InventarisCacheDao,
    private val app: Application,
    private val gson: Gson
) : ViewModel() {

    private val _listState = MutableStateFlow(RuanganListState())
    val listState = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(RuanganDetailState())
    val detailState = _detailState.asStateFlow()

    private val _ruanganFormState = MutableStateFlow(RuanganFormState())
    val ruanganFormState = _ruanganFormState.asStateFlow()

    private val _barangFormState = MutableStateFlow(BarangFormState())
    val barangFormState = _barangFormState.asStateFlow()

    private val _arDataState = MutableStateFlow(ArDataState())
    val arDataState = _arDataState.asStateFlow()

    // ─── List ────────────────────────────────────────────────────────────────

    fun loadRuanganList() {
        if (_listState.value.isLoading) return
        _listState.update { it.copy(isLoading = true, error = null, isOffline = false) }
        viewModelScope.launch {
            try {
                val resp = api.getInventarisRuangan()
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    _listState.update { it.copy(
                        list = body.data, stats = body.stats,
                        isLoading = false, isOffline = false
                    ) }
                    // Cache
                    cacheDao.upsert(InventarisCacheEntity(
                        cacheKey = "ruangan_list",
                        jsonData = gson.toJson(body)
                    ))
                } else {
                    loadFromCache()
                }
            } catch (e: Exception) {
                loadFromCache()
            }
        }
    }

    private suspend fun loadFromCache() {
        val cached = cacheDao.get("ruangan_list")
        if (cached != null) {
            try {
                val body = gson.fromJson(cached.jsonData, RuanganListResponse::class.java)
                _listState.update { it.copy(
                    list = body.data, stats = body.stats,
                    isLoading = false, isOffline = true, error = null
                ) }
            } catch (_: Exception) {
                _listState.update { it.copy(isLoading = false, error = "Gagal memuat data") }
            }
        } else {
            _listState.update { it.copy(isLoading = false, error = "Tidak ada koneksi & cache kosong") }
        }
    }

    fun updateSearchQuery(query: String) {
        _listState.update { it.copy(searchQuery = query) }
    }

    // ─── Detail ──────────────────────────────────────────────────────────────

    fun loadDetail(ruanganId: Long) {
        _detailState.update { it.copy(isLoading = true, error = null, isOffline = false, actionMessage = null) }
        viewModelScope.launch {
            try {
                val resp = api.getInventarisRuanganDetail(ruanganId)
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!.data
                    _detailState.update { it.copy(ruangan = data, isLoading = false, isOffline = false) }
                    cacheDao.upsert(InventarisCacheEntity(
                        cacheKey = "ruangan_detail_$ruanganId",
                        jsonData = gson.toJson(data)
                    ))
                } else {
                    loadDetailFromCache(ruanganId)
                }
            } catch (_: Exception) {
                loadDetailFromCache(ruanganId)
            }
        }
    }

    fun loadDetailByUuid(uuid: String) {
        _detailState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val resp = api.getInventarisRuanganByUuid(uuid)
                if (resp.isSuccessful && resp.body() != null) {
                    val data = resp.body()!!.data
                    _detailState.update { it.copy(ruangan = data, isLoading = false) }
                } else {
                    _detailState.update { it.copy(isLoading = false, error = "Ruangan tidak ditemukan") }
                }
            } catch (e: Exception) {
                _detailState.update { it.copy(isLoading = false, error = e.message ?: "Error") }
            }
        }
    }

    private suspend fun loadDetailFromCache(ruanganId: Long) {
        val cached = cacheDao.get("ruangan_detail_$ruanganId")
        if (cached != null) {
            try {
                val data = gson.fromJson(cached.jsonData, RuanganDto::class.java)
                _detailState.update { it.copy(ruangan = data, isLoading = false, isOffline = true) }
            } catch (_: Exception) {
                _detailState.update { it.copy(isLoading = false, error = "Gagal memuat cache") }
            }
        } else {
            _detailState.update { it.copy(isLoading = false, error = "Tidak ada koneksi & cache kosong") }
        }
    }

    fun clearActionMessage() { _detailState.update { it.copy(actionMessage = null) } }

    // ─── Ruangan CRUD ────────────────────────────────────────────────────────

    fun updateRuanganForm(block: RuanganFormState.() -> RuanganFormState) {
        _ruanganFormState.update { it.block() }
    }

    fun resetRuanganForm() { _ruanganFormState.value = RuanganFormState() }

    fun populateRuanganForm(r: RuanganDto) {
        _ruanganFormState.value = RuanganFormState(
            nama = r.nama, kode = r.kode ?: "", lantai = r.lantai ?: "",
            deskripsi = r.deskripsi ?: ""
        )
    }

    fun submitRuangan(editId: Long? = null) {
        val f = _ruanganFormState.value
        if (f.nama.isBlank()) {
            _ruanganFormState.update { it.copy(submitMessage = "Nama ruangan wajib diisi") }
            return
        }
        _ruanganFormState.update { it.copy(isSubmitting = true, submitMessage = null) }
        viewModelScope.launch {
            try {
                val nama = f.nama.toRequestBody("text/plain".toMediaType())
                val kode = f.kode.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
                val lantai = f.lantai.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
                val deskripsi = f.deskripsi.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
                val fotoPart = f.fotoUri?.let { buildImagePart("foto", it) }

                val resp = if (editId != null) {
                    api.updateRuangan(editId, nama, kode, lantai, deskripsi, fotoPart)
                } else {
                    api.createRuangan(nama, kode, lantai, deskripsi, fotoPart)
                }

                if (resp.isSuccessful) {
                    _ruanganFormState.update { it.copy(
                        isSubmitting = false,
                        submitMessage = resp.body()?.message ?: "Berhasil"
                    ) }
                    loadRuanganList()
                } else {
                    _ruanganFormState.update { it.copy(
                        isSubmitting = false,
                        submitMessage = "Gagal: ${resp.code()}"
                    ) }
                }
            } catch (e: Exception) {
                _ruanganFormState.update { it.copy(
                    isSubmitting = false,
                    submitMessage = "Error: ${e.message}"
                ) }
            }
        }
    }

    fun deleteRuangan(ruanganId: Long) {
        _detailState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val resp = api.deleteRuangan(ruanganId)
                if (resp.isSuccessful) {
                    _detailState.update { it.copy(
                        isLoading = false,
                        actionMessage = "Ruangan berhasil dihapus"
                    ) }
                    loadRuanganList()
                } else {
                    _detailState.update { it.copy(isLoading = false, actionMessage = "Gagal menghapus") }
                }
            } catch (e: Exception) {
                _detailState.update { it.copy(isLoading = false, actionMessage = "Error: ${e.message}") }
            }
        }
    }

    // ─── Barang CRUD ─────────────────────────────────────────────────────────

    fun updateBarangForm(block: BarangFormState.() -> BarangFormState) {
        _barangFormState.update { it.block() }
    }

    fun resetBarangForm() { _barangFormState.value = BarangFormState() }

    fun populateBarangForm(b: InventarisBarangDto) {
        _barangFormState.value = BarangFormState(
            nama = b.nama, kodeInventaris = b.kodeInventaris ?: "",
            merek = b.merek ?: "", jumlah = b.jumlah.toString(),
            kondisi = b.kondisi, tahunPerolehan = b.tahunPerolehan?.toString() ?: "",
            sumberDana = b.sumberDana ?: "", keterangan = b.keterangan ?: "",
            editBarang = b
        )
    }

    fun submitBarang(ruanganId: Long, editBarangId: Long? = null) {
        val f = _barangFormState.value
        if (f.nama.isBlank()) {
            _barangFormState.update { it.copy(submitMessage = "Nama barang wajib diisi") }
            return
        }
        _barangFormState.update { it.copy(isSubmitting = true, submitMessage = null) }
        viewModelScope.launch {
            try {
                val nama = f.nama.toRequestBody("text/plain".toMediaType())
                val kodeInv = f.kodeInventaris.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
                val merek = f.merek.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
                val jumlah = f.jumlah.toRequestBody("text/plain".toMediaType())
                val kondisi = f.kondisi.toRequestBody("text/plain".toMediaType())
                val tahun = f.tahunPerolehan.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
                val sumberDana = f.sumberDana.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
                val keterangan = f.keterangan.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType())
                val fotoPart = f.fotoUri?.let { buildImagePart("foto", it) }

                val resp = if (editBarangId != null) {
                    api.updateBarang(ruanganId, editBarangId, nama, kodeInv, merek, jumlah, kondisi, tahun, sumberDana, keterangan, fotoPart)
                } else {
                    api.createBarang(ruanganId, nama, kodeInv, merek, jumlah, kondisi, tahun, sumberDana, keterangan, fotoPart)
                }

                if (resp.isSuccessful) {
                    _barangFormState.update { it.copy(
                        isSubmitting = false,
                        submitMessage = resp.body()?.message ?: "Berhasil"
                    ) }
                    loadDetail(ruanganId)
                } else {
                    _barangFormState.update { it.copy(isSubmitting = false, submitMessage = "Gagal: ${resp.code()}") }
                }
            } catch (e: Exception) {
                _barangFormState.update { it.copy(isSubmitting = false, submitMessage = "Error: ${e.message}") }
            }
        }
    }

    fun deleteBarang(ruanganId: Long, barangId: Long) {
        viewModelScope.launch {
            try {
                val resp = api.deleteBarang(ruanganId, barangId)
                if (resp.isSuccessful) {
                    _detailState.update { it.copy(actionMessage = "Barang berhasil dihapus") }
                    loadDetail(ruanganId)
                } else {
                    _detailState.update { it.copy(actionMessage = "Gagal menghapus barang") }
                }
            } catch (e: Exception) {
                _detailState.update { it.copy(actionMessage = "Error: ${e.message}") }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun buildImagePart(name: String, uri: Uri): MultipartBody.Part? {
        return try {
            val contentResolver = app.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File.createTempFile("upload_", ".jpg", app.cacheDir)
            file.outputStream().use { inputStream.copyTo(it) }
            val requestBody = file.asRequestBody("image/*".toMediaType())
            MultipartBody.Part.createFormData(name, file.name, requestBody)
        } catch (_: Exception) { null }
    }

    // ─── AR ──────────────────────────────────────────────────────────────────

    fun loadArData(ruanganId: Long) {
        _arDataState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                android.util.Log.d("AR_DEBUG", "loadArData called for ruanganId=$ruanganId")
                val resp = api.getArData(ruanganId)
                android.util.Log.d("AR_DEBUG", "Response code=${resp.code()}, isSuccessful=${resp.isSuccessful}")
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    android.util.Log.d("AR_DEBUG", "AR data loaded: ${body.barang.size} barang, arEnabled=${body.arEnabled}, ruangan=${body.ruangan?.nama}")
                    body.barang.forEach { b ->
                        android.util.Log.d("AR_DEBUG", "  Barang: ${b.nama} pos=(${b.arPosX}, ${b.arPosY}, ${b.arPosZ})")
                    }
                    _arDataState.update { it.copy(
                        isLoading = false,
                        barangList = body.barang,
                        ruanganNama = body.ruangan?.nama,
                        arEnabled = body.arEnabled
                    ) }
                } else {
                    val errorBody = resp.errorBody()?.string() ?: "unknown"
                    android.util.Log.e("AR_DEBUG", "API failed: code=${resp.code()}, error=$errorBody")
                    _arDataState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                android.util.Log.e("AR_DEBUG", "Exception loading AR data: ${e.message}", e)
                _arDataState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveArPositions(ruanganId: Long, positions: List<ArPositionItem>) {
        viewModelScope.launch {
            try {
                val resp = api.saveArPositions(ruanganId, ArPositionRequest(positions))
                if (resp.isSuccessful) {
                    _arDataState.update { it.copy(
                        saveMessage = resp.body()?.message ?: "Posisi AR berhasil disimpan"
                    ) }
                } else {
                    _arDataState.update { it.copy(saveMessage = "Gagal menyimpan: ${resp.code()}") }
                }
            } catch (e: Exception) {
                _arDataState.update { it.copy(saveMessage = "Error: ${e.message}") }
            }
        }
    }

    fun clearArSaveMessage() {
        _arDataState.update { it.copy(saveMessage = null) }
    }
}
