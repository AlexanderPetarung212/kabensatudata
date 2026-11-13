package com.example.kabensatudata.ui.screen.poa

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.dao.BokCacheDao
import com.example.kabensatudata.data.local.entity.BokCacheEntity
import com.example.kabensatudata.data.remote.BokApiService
import com.example.kabensatudata.data.remote.dto.*
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class PoaUiState(
    // List
    val isLoading: Boolean = false,
    val error: String? = null,
    val poaList: List<PoaListItem> = emptyList(),
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val searchQuery: String = "",
    val selectedKomponen: String = "",
    val selectedMonth: Int = 0,
    val availableKomponen: List<String> = emptyList(),
    val availableYears: List<Int> = emptyList(),

    // Detail
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    val detailData: PoaDetailResponse? = null,

    // Form
    val isFormLoading: Boolean = false,
    val formError: String? = null,
    val formSuccess: String? = null,
    val availableRabs: List<RabOption> = emptyList(),
    val employees: List<EmployeeDto> = emptyList(),

    // Actions
    val actionLoading: String? = null,
    val actionError: String? = null,
    val actionSuccess: String? = null,

    // Download
    val downloadingFile: String? = null,
    val downloadError: String? = null,
    val downloadSuccess: String? = null,

    // Admin
    val isBokAdmin: Boolean = false
)

@HiltViewModel
class PoaViewModel @Inject constructor(
    application: Application,
    private val bokApiService: BokApiService,
    private val tokenManager: AuthTokenManager,
    private val bokCacheDao: BokCacheDao,
    private val gson: Gson
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PoaUiState())
    val uiState: StateFlow<PoaUiState> = _uiState

    init {
        initializeAndLoad()
    }

    private fun initializeAndLoad() {
        viewModelScope.launch {
            // If BOK role is not saved yet (e.g. cached token from before role-saving was added),
            // force a fresh login to get the role
            val bokRole = tokenManager.getBokRole()
            if (bokRole.isBlank()) {
                // Force fresh login to get role
                loginToBok()
            }
            checkAdminRole()
            loadPoaList()
        }
    }

    private fun checkAdminRole() {
        val bokRole = tokenManager.getBokRole()
        val userEmail = tokenManager.getUserEmail()
        // Match bokbaru's isAdmin() logic: role check OR email fallback
        val isAdmin = bokRole.equals("super_admin", ignoreCase = true) ||
                bokRole.equals("superadmin", ignoreCase = true) ||
                bokRole.equals("admin", ignoreCase = true) ||
                userEmail.equals("admin@admin.com", ignoreCase = true)
        _uiState.update { it.copy(isBokAdmin = isAdmin) }
    }

    // ─── List ───────────────────────────────────────────────────────────────────

    fun loadPoaList(page: Int = 1) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (!tokenManager.isBokLoggedIn()) {
                val ok = loginToBok()
                if (!ok) { _uiState.update { it.copy(isLoading = false) }; return@launch }
            }

            val state = _uiState.value
            val cacheKey = "poa_list_${state.selectedYear}_${page}_${state.selectedKomponen}_${state.selectedMonth}"

            // Cache first (only for page 1 with no search)
            if (page == 1 && state.searchQuery.isBlank()) {
                val cached = bokCacheDao.get(cacheKey)
                if (cached != null) {
                    try {
                        val data = gson.fromJson(cached.jsonData, PoaListResponse::class.java)
                        _uiState.update {
                            it.copy(
                                isLoading = false, poaList = data.data,
                                currentPage = data.current_page, lastPage = data.last_page,
                                total = data.total, availableKomponen = data.available_komponen,
                                availableYears = data.available_years
                            )
                        }
                    } catch (_: Exception) { }
                }
            }

            try {
                val response = bokApiService.getPoaList(
                    year = state.selectedYear,
                    q = state.searchQuery.ifBlank { null },
                    komponen = state.selectedKomponen.ifBlank { null },
                    month = if (state.selectedMonth > 0) state.selectedMonth else null,
                    page = page
                )
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    // Cache page 1 with no search
                    if (page == 1 && state.searchQuery.isBlank()) {
                        bokCacheDao.insertOrUpdate(BokCacheEntity(cacheKey, gson.toJson(data)))
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            poaList = if (page == 1) data.data else it.poaList + data.data,
                            currentPage = data.current_page,
                            lastPage = data.last_page,
                            total = data.total,
                            availableKomponen = data.available_komponen,
                            availableYears = data.available_years
                        )
                    }
                } else if (response.code() == 401) {
                    if (loginToBok()) loadPoaList(page)
                    else _uiState.update { it.copy(isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Gagal memuat POA (${response.code()})") }
                }
            } catch (e: Exception) {
                val cached = bokCacheDao.get(cacheKey)
                if (cached == null) _uiState.update { it.copy(isLoading = false, error = "Tidak dapat terhubung ke server") }
                else _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.currentPage < state.lastPage && !state.isLoading) {
            loadPoaList(state.currentPage + 1)
        }
    }

    fun setYear(year: Int) { _uiState.update { it.copy(selectedYear = year) }; loadPoaList() }
    fun setSearchQuery(q: String) { _uiState.update { it.copy(searchQuery = q) }; loadPoaList() }
    fun setKomponen(k: String) { _uiState.update { it.copy(selectedKomponen = k) }; loadPoaList() }
    fun setMonth(m: Int) { _uiState.update { it.copy(selectedMonth = m) }; loadPoaList() }

    // ─── Detail ─────────────────────────────────────────────────────────────────

    fun loadPoaDetail(poaId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, detailError = null, detailData = null) }

            // Cache first
            val cacheKey = "poa_detail_${poaId}"
            val cached = bokCacheDao.get(cacheKey)
            if (cached != null) {
                try {
                    val data = gson.fromJson(cached.jsonData, PoaDetailResponse::class.java)
                    _uiState.update { it.copy(isDetailLoading = false, detailData = data) }
                } catch (_: Exception) { }
            }

            // Fetch fresh
            try {
                val response = bokApiService.getPoaDetail(poaId)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    bokCacheDao.insertOrUpdate(BokCacheEntity(cacheKey, gson.toJson(data)))
                    _uiState.update { it.copy(isDetailLoading = false, detailData = data) }
                } else {
                    if (cached == null) _uiState.update { it.copy(isDetailLoading = false, detailError = "Gagal memuat detail (${response.code()})") }
                }
            } catch (e: Exception) {
                if (cached == null) _uiState.update { it.copy(isDetailLoading = false, detailError = "Tidak dapat terhubung") }
                else _uiState.update { it.copy(isDetailLoading = false) }
            }
        }
    }

    // ─── CRUD ───────────────────────────────────────────────────────────────────

    fun loadFormData(year: Int, excludePoaId: Long? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFormLoading = true) }

            // Cache employees
            val empCacheKey = "employees"
            val empCached = bokCacheDao.get(empCacheKey)
            if (empCached != null) {
                try {
                    val emps = gson.fromJson(empCached.jsonData, Array<EmployeeDto>::class.java).toList()
                    _uiState.update { it.copy(employees = emps) }
                } catch (_: Exception) { }
            }

            try {
                val rabRes = bokApiService.getAvailableRabs(year, excludePoaId)
                val empRes = bokApiService.getEmployees()
                if (empRes.isSuccessful && empRes.body() != null) {
                    bokCacheDao.insertOrUpdate(BokCacheEntity(empCacheKey, gson.toJson(empRes.body())))
                }
                _uiState.update {
                    it.copy(
                        isFormLoading = false,
                        availableRabs = rabRes.body() ?: emptyList(),
                        employees = empRes.body() ?: it.employees
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFormLoading = false, formError = "Gagal memuat data form") }
            }
        }
    }

    fun createPoa(request: PoaCreateRequest, onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFormLoading = true, formError = null) }
            try {
                val response = bokApiService.createPoa(request)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    _uiState.update { it.copy(isFormLoading = false, formSuccess = result.message) }
                    result.id?.let { onSuccess(it) }
                    loadPoaList()
                } else {
                    val err = parseError(response)
                    _uiState.update { it.copy(isFormLoading = false, formError = err) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFormLoading = false, formError = "Gagal: ${e.message}") }
            }
        }
    }

    fun updatePoa(poaId: Long, request: PoaCreateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFormLoading = true, formError = null) }
            try {
                val response = bokApiService.updatePoa(poaId, request)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isFormLoading = false, formSuccess = "POA berhasil diperbarui.") }
                    onSuccess()
                    loadPoaList()
                } else {
                    _uiState.update { it.copy(isFormLoading = false, formError = parseError(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isFormLoading = false, formError = "Gagal: ${e.message}") }
            }
        }
    }

    fun deletePoa(poaId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = "delete") }
            try {
                val response = bokApiService.deletePoa(poaId)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(actionLoading = null, actionSuccess = "POA dihapus.") }
                    onSuccess()
                    loadPoaList()
                } else {
                    _uiState.update { it.copy(actionLoading = null, actionError = parseError(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = null, actionError = "Gagal: ${e.message}") }
            }
        }
    }

    // ─── Schedule Actions ───────────────────────────────────────────────────────

    fun toggleMark(poaId: Long, month: Int, marked: Boolean) {
        doAction("mark") { bokApiService.poaToggleMark(poaId, ToggleRequest(month, marked = marked)) }
        reloadDetail(poaId)
    }

    fun toggleClaimLock(poaId: Long, month: Int, locked: Boolean) {
        doAction("lock") { bokApiService.poaToggleClaimLock(poaId, ToggleRequest(month, locked = locked)) }
        reloadDetail(poaId)
    }

    fun toggleClaimLabel(poaId: Long, month: Int, hidden: Boolean) {
        doAction("label") { bokApiService.poaToggleClaimLabel(poaId, ToggleRequest(month, hidden = hidden)) }
        reloadDetail(poaId)
    }

    fun upsertMonth(poaId: Long, request: MonthMetaRequest) {
        doAction("upsert") { bokApiService.poaUpsertMonth(poaId, request) }
        reloadDetail(poaId)
    }

    fun updateItemProgress(poaId: Long, request: ItemProgressRequest) {
        doAction("progress") { bokApiService.poaItemProgress(poaId, request) }
        reloadDetail(poaId)
    }

    fun carryOver(poaId: Long, month: Int, realized: Double) {
        doAction("carry") { bokApiService.poaCarryOver(poaId, CarryOverRequest(month, realized)) }
        reloadDetail(poaId)
    }

    fun bulkLock(year: Int, month: Int, locked: Boolean) {
        doAction("bulk") { bokApiService.poaBulkLock(BulkLockRequest(year, month, locked)) }
        loadPoaList()
    }

    private fun doAction(tag: String, call: suspend () -> retrofit2.Response<GenericResponse>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionLoading = tag, actionError = null, actionSuccess = null) }
            try {
                val resp = call()
                if (resp.isSuccessful) {
                    _uiState.update { it.copy(actionLoading = null, actionSuccess = resp.body()?.message ?: "Berhasil") }
                } else {
                    _uiState.update { it.copy(actionLoading = null, actionError = parseError(resp)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(actionLoading = null, actionError = "Gagal: ${e.message}") }
            }
        }
    }

    private fun reloadDetail(poaId: Long) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            loadPoaDetail(poaId)
        }
    }

    // ─── Download ───────────────────────────────────────────────────────────────

    fun downloadLpj(lpjId: Long, type: String, kegiatan: String) {
        val safeName = kegiatan.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
        downloadFile("$type $safeName.docx") { bokApiService.downloadLpj(lpjId) }
    }

    fun downloadTibaBerangkat(tbId: Long, kegiatan: String) {
        val safeName = kegiatan.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
        downloadFile("Tiba_Berangkat $safeName.docx") { bokApiService.downloadTibaBerangkat(tbId) }
    }

    fun downloadItemOpsional(itemId: Long, label: String, kegiatan: String) {
        val safeName = kegiatan.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
        val safeLabel = label.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
        downloadFile("Kwitansi $safeLabel $safeName.docx") { bokApiService.downloadItemOpsional(itemId) }
    }

    private fun downloadFile(fileName: String, apiCall: suspend () -> retrofit2.Response<okhttp3.ResponseBody>) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingFile = fileName, downloadError = null, downloadSuccess = null) }
            try {
                val response = apiCall()
                if (response.isSuccessful && response.body() != null) {
                    withContext(Dispatchers.IO) {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, fileName)
                        response.body()!!.byteStream().use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
                    }
                    _uiState.update { it.copy(downloadingFile = null, downloadSuccess = "📄 $fileName tersimpan di Downloads") }
                } else {
                    _uiState.update { it.copy(downloadingFile = null, downloadError = "Gagal download (${response.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(downloadingFile = null, downloadError = "Gagal: ${e.message}") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(actionError = null, actionSuccess = null, downloadError = null, downloadSuccess = null, formError = null, formSuccess = null) }
    }

    /** Check if a downloaded file already exists in Downloads folder */
    fun isFileDownloaded(fileName: String): Boolean {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, fileName).exists()
    }

    /** Open an already downloaded file */
    fun openDownloadedFile(fileName: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        if (!file.exists()) {
            _uiState.update { it.copy(downloadError = "File tidak ditemukan") }
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                getApplication(), "${getApplication<Application>().packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            _uiState.update { it.copy(downloadError = "Tidak ada aplikasi untuk membuka file") }
        }
    }

    // ─── Auth ───────────────────────────────────────────────────────────────────

    private suspend fun loginToBok(): Boolean {
        val email = tokenManager.getUserEmail()
        val password = tokenManager.getPassword()
        if (email.isBlank() || password.isNullOrBlank()) return false
        return try {
            val resp = bokApiService.login(BokLoginRequest(email, password))
            if (resp.isSuccessful && resp.body() != null) {
                tokenManager.saveBokToken(resp.body()!!.token)
                resp.body()!!.user.role?.let { tokenManager.saveBokRole(it) }
                checkAdminRole()
                true
            } else false
        } catch (_: Exception) { false }
    }

    private fun <T> parseError(response: retrofit2.Response<T>): String {
        return try {
            val body = response.errorBody()?.string()
            val map = gson.fromJson(body, Map::class.java)
            map?.get("error")?.toString() ?: map?.get("message")?.toString() ?: "Gagal (${response.code()})"
        } catch (_: Exception) { "Gagal (${response.code()})" }
    }
}
