package com.example.kabensatudata.ui.screen.calendar

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.dao.BokCacheDao
import com.example.kabensatudata.data.local.dao.CalendarCacheDao
import com.example.kabensatudata.data.local.dao.NotificationDao
import com.example.kabensatudata.data.local.entity.BokCacheEntity
import com.example.kabensatudata.data.local.entity.CalendarCacheEntity
import com.example.kabensatudata.data.local.entity.NotificationEntity
import com.example.kabensatudata.data.remote.BokApiService
import com.example.kabensatudata.data.remote.dto.*
import com.example.kabensatudata.fcm.FCMService
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

data class CalendarUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val calendarData: CalendarResponse? = null,
    val filteredActivities: List<CalendarActivity> = emptyList(),
    val selectedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val searchQuery: String = "",
    val selectedKomponen: String = "",
    val lastSynced: Long? = null,
    val isAdmin: Boolean = false,

    // Detail bottom sheet
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    val detailData: CalendarDetailResponse? = null,
    val showDetail: Boolean = false,
    val detailPoaId: Long = 0,
    val detailMonth: Int = 0,

    // BOK auth
    val bokAuthError: String? = null,
    val isBokAuthing: Boolean = false,

    // Claim
    val claimPrep: ClaimPrepResponse? = null,
    val isClaimPrepLoading: Boolean = false,
    val isClaimExecuting: Boolean = false,
    val claimError: String? = null,
    val claimSuccess: String? = null,
    val claimResult: ClaimResponse? = null,

    // Download
    val downloadingFile: String? = null,
    val downloadError: String? = null,
    val downloadSuccess: String? = null,

    // RAB Detail Sheet (ℹ️ button on card — read-only all users)
    val showRabDetail: Boolean = false,
    val rabDetailPoaId: Long = 0L,
    val rabDetailData: PoaDetailResponse? = null,
    val isRabDetailLoading: Boolean = false,

    // PoaMonthMeta edit (admin only — from CalendarDetailSheet or KelolaBulan)
    val showMonthMeta: Boolean = false,
    val monthMetaPoaDetail: PoaDetailResponse? = null,
    val monthMetaMonth: Int = 0,
    val isMonthMetaLoading: Boolean = false,
    val isSavingMonthMeta: Boolean = false,
    val monthMetaSaveError: String? = null,
    val monthMetaSaveSuccess: Boolean = false,

    // Employees list (needed for PoaMonthMetaSheet)
    val employees: List<EmployeeDto> = emptyList(),
    val isEmployeesLoading: Boolean = false,

    // Peserta per POA — key: poaId, value: jumlah peserta (dari PoaDetail.participants)
    // Diisi oleh preloadPoaDetailsForMonth agar KelolaBulanScreen tidak perlu fetch per-card
    val poaHasPesertaMap: Map<Long, Boolean> = emptyMap()
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    application: Application,
    private val bokApiService: BokApiService,
    private val tokenManager: AuthTokenManager,
    private val calendarCacheDao: CalendarCacheDao,
    private val bokCacheDao: BokCacheDao,
    private val notificationDao: NotificationDao,
    private val gson: Gson
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState

    // Cache: poaId → PoaDetailResponse (avoid repeated API calls)
    private val poaDetailCache = mutableMapOf<Long, PoaDetailResponse>()

    init {
        // Determine admin role from token
        val roles = tokenManager.getUserRoles()
        val isAdmin = roles.any { it.equals("admin", true) || it.equals("super_admin", true) || it.equals("superadmin", true) }
        _uiState.update { it.copy(isAdmin = isAdmin) }
        loadCalendar()
        if (isAdmin) loadEmployees()
    }

    // ─── Calendar Data ──────────────────────────────────────────────────────────

    fun loadCalendar(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            if (!tokenManager.isBokLoggedIn()) {
                val authOk = loginToBok()
                if (!authOk) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
            }

            val year = _uiState.value.selectedYear

            if (!forceRefresh) {
                val cached = calendarCacheDao.getByYear(year)
                if (cached != null) {
                    try {
                        val data = gson.fromJson(cached.jsonData, CalendarResponse::class.java)
                        _uiState.update {
                            it.copy(calendarData = data, lastSynced = cached.lastSynced, isLoading = false)
                        }
                        applyFilters()
                        fetchFromApi(year, background = true)
                        return@launch
                    } catch (_: Exception) { }
                }
            }

            fetchFromApi(year, background = false)
        }
    }

    private suspend fun fetchFromApi(year: Int, background: Boolean) {
        if (!background) _uiState.update { it.copy(isLoading = true) }
        try {
            val response = bokApiService.getCalendar(year)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                calendarCacheDao.insertOrUpdate(
                    CalendarCacheEntity(year = year, jsonData = gson.toJson(data), lastSynced = System.currentTimeMillis())
                )
                _uiState.update {
                    it.copy(calendarData = data, lastSynced = System.currentTimeMillis(), isLoading = false, error = null)
                }
                applyFilters()
                // Pre-fetch all details in background
                prefetchAllDetails(data)
                // Check for assigned activities and notify
                checkAndNotifyAssignedActivities(data)
            } else if (response.code() == 401) {
                val authOk = loginToBok()
                if (authOk) fetchFromApi(year, background) else if (!background) _uiState.update { it.copy(isLoading = false) }
            } else {
                if (!background) _uiState.update { it.copy(isLoading = false, error = "Gagal memuat kalender (${response.code()})") }
            }
        } catch (e: Exception) {
            if (!background) _uiState.update { it.copy(isLoading = false, error = "Tidak dapat terhubung ke server BOK") }
        }
    }

    /**
     * Pre-fetch all calendar detail data in background.
     * Iterates every activity + month with has_activity and caches the detail response.
     * Skips details already in cache.
     */
    private fun prefetchAllDetails(data: CalendarResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            var fetched = 0
            var skipped = 0
            for (activity in data.activities) {
                for ((monthStr, monthData) in activity.months) {
                    if (monthData.has_activity) {
                        val month = monthStr.toIntOrNull() ?: continue
                        val cacheKey = "calendar_detail_${activity.id}_${month}"
                        // Skip if already cached
                        val existing = bokCacheDao.get(cacheKey)
                        if (existing != null) { skipped++; continue }

                        try {
                            val resp = bokApiService.getCalendarDetail(activity.id, month)
                            if (resp.isSuccessful && resp.body() != null) {
                                bokCacheDao.insertOrUpdate(BokCacheEntity(cacheKey, gson.toJson(resp.body())))
                                fetched++
                            }
                        } catch (_: Exception) { /* skip on error, will retry next time */ }

                        // Small delay to avoid overloading server
                        kotlinx.coroutines.delay(200)
                    }
                }
            }
            android.util.Log.d("CalendarVM", "Pre-fetch complete: $fetched fetched, $skipped already cached")
        }
    }

    /**
     * Check for activities assigned to current user (is_mine) for current + next month.
     * Shows system notification + saves to NotificationEntity.
     * Uses deterministic serverId for dedup to avoid repeat notifications.
     */
    private fun checkAndNotifyAssignedActivities(data: CalendarResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = java.util.Calendar.getInstance()
            val currentMonth = cal.get(java.util.Calendar.MONTH) + 1 // 1-based
            val currentYear = cal.get(java.util.Calendar.YEAR)
            val nextMonth = if (currentMonth == 12) 1 else currentMonth + 1

            val monthsToCheck = listOf(currentMonth, nextMonth)
            val monthNames = mapOf(
                1 to "Januari", 2 to "Februari", 3 to "Maret", 4 to "April",
                5 to "Mei", 6 to "Juni", 7 to "Juli", 8 to "Agustus",
                9 to "September", 10 to "Oktober", 11 to "November", 12 to "Desember"
            )

            val newActivities = mutableListOf<Triple<Long, Int, String>>() // (poaId, month, kegiatan)

            for (activity in data.activities) {
                for (month in monthsToCheck) {
                    val monthData = activity.months[month.toString()] ?: continue
                    if (monthData.has_activity && monthData.is_mine) {
                        // Deterministic serverId: activityId * 10000 + year * 100 + month
                        val dedupId = activity.id * 10000 + currentYear * 100 + month
                        val alreadyExists = notificationDao.existsByServerId(dedupId)
                        if (!alreadyExists) {
                            val monthName = monthNames[month] ?: "$month"
                            val count = monthData.count ?: 1
                            val title = "📋 Kegiatan $monthName"
                            val body = "Anda ditugaskan: ${activity.kegiatan} (${count}x)"

                            // Save to DB with deep link data
                            notificationDao.insert(NotificationEntity(
                                serverId = dedupId,
                                title = title,
                                message = body,
                                type = "kegiatan",
                                targetPoaId = activity.id,
                                targetMonth = month
                            ))

                            newActivities.add(Triple(activity.id, month, activity.kegiatan))
                        }
                    }
                }
            }

            // Show single system notification summarizing all new assignments
            if (newActivities.isNotEmpty()) {
                val app = getApplication<Application>()
                val grouped = newActivities.groupBy { monthNames[it.second] ?: "${it.second}" }
                val bodyLines = grouped.entries.joinToString("\n") { (bulan, items) ->
                    "📌 $bulan: ${items.joinToString(", ") { it.third }}"
                }

                val title = "📋 ${newActivities.size} kegiatan ditugaskan"
                val body = bodyLines

                // Deep link: open calendar (if single activity, pass poaId+month)
                val intent = android.content.Intent(app, com.example.kabensatudata.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("open_calendar", true)
                    if (newActivities.size == 1) {
                        putExtra("poa_id", newActivities[0].first)
                        putExtra("poa_month", newActivities[0].second)
                    }
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    app, 99_000 + currentMonth, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

                val notification = androidx.core.app.NotificationCompat.Builder(app, FCMService.CHANNEL_ID)
                    .setSmallIcon(com.example.kabensatudata.R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setSound(soundUri)
                    .setVibrate(longArrayOf(0, 300, 200, 300))
                    .build()

                val manager = app.getSystemService(android.app.NotificationManager::class.java)
                manager.notify(99_000 + currentMonth, notification)

                android.util.Log.d("CalendarVM", "Notified ${newActivities.size} assigned activities")
            }
        }
    }

    private suspend fun loginToBok(): Boolean {
        _uiState.update { it.copy(isBokAuthing = true, bokAuthError = null) }
        val email = tokenManager.getUserEmail()
        val password = tokenManager.getPassword()

        if (email.isBlank() || password.isNullOrBlank()) {
            _uiState.update { it.copy(isBokAuthing = false, bokAuthError = "Kredensial tidak tersedia. Silakan login ulang.") }
            return false
        }

        return try {
            val response = bokApiService.login(BokLoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                tokenManager.saveBokToken(response.body()!!.token)
                response.body()!!.user.role?.let { tokenManager.saveBokRole(it) }
                _uiState.update { it.copy(isBokAuthing = false, bokAuthError = null) }
                true
            } else {
                _uiState.update { it.copy(isBokAuthing = false, bokAuthError = "Login BOK gagal.") }
                false
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isBokAuthing = false, bokAuthError = "Tidak dapat terhubung ke server BOK") }
            false
        }
    }

    fun setYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year) }
        loadCalendar()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setKomponen(komponen: String) {
        _uiState.update { it.copy(selectedKomponen = komponen) }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val data = state.calendarData ?: return
        var filtered = data.activities

        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            filtered = filtered.filter {
                it.kegiatan.lowercase().contains(q) ||
                it.komponen.lowercase().contains(q) ||
                (it.rincian_menu?.lowercase()?.contains(q) == true)
            }
        }
        if (state.selectedKomponen.isNotBlank()) {
            filtered = filtered.filter { it.komponen == state.selectedKomponen }
        }

        filtered = filtered.sortedWith(compareByDescending<CalendarActivity> { activity ->
            activity.months.values.any { it.is_mine }
        }.thenBy { it.kegiatan })

        _uiState.update { it.copy(filteredActivities = filtered) }
    }

    // ─── Detail ─────────────────────────────────────────────────────────────────

    fun loadDetail(poaId: Long, month: Int) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDetailLoading = true, detailError = null, detailData = null,
                    showDetail = true, detailPoaId = poaId, detailMonth = month,
                    claimPrep = null, claimError = null, claimSuccess = null, claimResult = null,
                    downloadError = null, downloadSuccess = null
                )
            }

            // Try cache first
            val cacheKey = "calendar_detail_${poaId}_${month}"
            val cached = bokCacheDao.get(cacheKey)
            if (cached != null) {
                try {
                    val data = gson.fromJson(cached.jsonData, CalendarDetailResponse::class.java)
                    _uiState.update { it.copy(isDetailLoading = false, detailData = data) }
                } catch (_: Exception) { }
            }

            // Fetch fresh from API (background if cache exists)
            try {
                val response = bokApiService.getCalendarDetail(poaId, month)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    bokCacheDao.insertOrUpdate(BokCacheEntity(cacheKey, gson.toJson(data)))
                    _uiState.update { it.copy(isDetailLoading = false, detailData = data) }
                } else if (response.code() == 401) {
                    val authOk = loginToBok()
                    if (authOk) loadDetail(poaId, month)
                    else if (cached == null) _uiState.update { it.copy(isDetailLoading = false, detailError = "Sesi berakhir") }
                } else {
                    if (cached == null) _uiState.update { it.copy(isDetailLoading = false, detailError = "Gagal memuat detail (${response.code()})") }
                }
            } catch (e: Exception) {
                if (cached == null) _uiState.update { it.copy(isDetailLoading = false, detailError = "Tidak dapat terhubung ke server") }
                else _uiState.update { it.copy(isDetailLoading = false) }
            }
        }
    }

    fun dismissDetail() {
        _uiState.update {
            it.copy(showDetail = false, detailData = null, detailError = null,
                claimPrep = null, claimError = null, claimSuccess = null, claimResult = null,
                downloadError = null, downloadSuccess = null)
        }
    }

    // ─── Employees ──────────────────────────────────────────────────────────────

    fun loadEmployees() {
        if (_uiState.value.employees.isNotEmpty()) return // already loaded
        viewModelScope.launch {
            _uiState.update { it.copy(isEmployeesLoading = true) }
            try {
                val resp = bokApiService.getEmployees()
                if (resp.isSuccessful && resp.body() != null) {
                    _uiState.update { it.copy(employees = resp.body()!!, isEmployeesLoading = false) }
                } else {
                    _uiState.update { it.copy(isEmployeesLoading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isEmployeesLoading = false) }
            }
        }
    }

    /**
     * Pre-load POA detail untuk semua aktivitas aktif di bulan tertentu.
     * PoaDetail berisi 'participants' (general POA participants dari tabel poa_participants).
     * Ini yang digunakan KelolaBulanScreen untuk menentukan has_peserta secara akurat
     * tanpa bergantung pada calendar API yang mungkin tidak ter-update di server.
     */
    fun preloadPoaDetailsForMonth(activities: List<CalendarActivity>, month: Int) {
        val activePoaIds = activities
            .filter { it.months[month.toString()]?.has_activity == true }
            .map { it.id }

        if (activePoaIds.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val newMap = _uiState.value.poaHasPesertaMap.toMutableMap()
            for (poaId in activePoaIds) {
                // Jika sudah ada di cache, gunakan langsung
                val cached = poaDetailCache[poaId]
                if (cached != null) {
                    newMap[poaId] = cached.participants.isNotEmpty()
                    continue
                }
                // Fetch dari API
                try {
                    val resp = bokApiService.getPoaDetail(poaId)
                    if (resp.isSuccessful && resp.body() != null) {
                        val detail = resp.body()!!
                        poaDetailCache[poaId] = detail
                        // has_peserta: ada general participants ATAU participant_ids di bulan ini
                        val monthStatus = detail.claim_status[month.toString()]
                        val hasInMonth = (monthStatus?.participant_ids?.isNotEmpty() == true)
                        val hasGeneral = detail.participants.isNotEmpty()
                        newMap[poaId] = hasInMonth || hasGeneral
                    }
                } catch (_: Exception) { /* skip, fallback ke false */ }
                kotlinx.coroutines.delay(150) // hindari overload server
            }
            _uiState.update { it.copy(poaHasPesertaMap = newMap) }
        }
    }

    // ─── RAB Detail Sheet (ℹ️ — read-only all users) ────────────────────────────

    fun openRabDetail(poaId: Long) {
        _uiState.update { it.copy(showRabDetail = true, rabDetailPoaId = poaId, rabDetailData = poaDetailCache[poaId]) }
        if (poaDetailCache.containsKey(poaId)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRabDetailLoading = true) }
            try {
                val resp = bokApiService.getPoaDetail(poaId)
                if (resp.isSuccessful && resp.body() != null) {
                    poaDetailCache[poaId] = resp.body()!!
                    _uiState.update { it.copy(isRabDetailLoading = false, rabDetailData = resp.body()) }
                } else {
                    _uiState.update { it.copy(isRabDetailLoading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isRabDetailLoading = false) }
            }
        }
    }

    fun dismissRabDetail() {
        _uiState.update { it.copy(showRabDetail = false) }
    }

    // ─── PoaMonthMeta Edit (admin) ───────────────────────────────────────────────

    fun openMonthMetaEdit(poaId: Long, month: Int) {
        _uiState.update {
            it.copy(
                showMonthMeta = true,
                monthMetaMonth = month,
                monthMetaPoaDetail = poaDetailCache[poaId],
                isMonthMetaLoading = !poaDetailCache.containsKey(poaId),
                monthMetaSaveError = null,
                monthMetaSaveSuccess = false
            )
        }
        if (poaDetailCache.containsKey(poaId)) return
        viewModelScope.launch {
            try {
                val resp = bokApiService.getPoaDetail(poaId)
                if (resp.isSuccessful && resp.body() != null) {
                    poaDetailCache[poaId] = resp.body()!!
                    _uiState.update { it.copy(isMonthMetaLoading = false, monthMetaPoaDetail = resp.body()) }
                } else {
                    _uiState.update { it.copy(isMonthMetaLoading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isMonthMetaLoading = false) }
            }
        }
    }

    fun dismissMonthMeta() {
        _uiState.update { it.copy(showMonthMeta = false, monthMetaPoaDetail = null, monthMetaSaveError = null, monthMetaSaveSuccess = false) }
    }

    fun saveMonthMeta(poaId: Long, request: MonthMetaRequest) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingMonthMeta = true, monthMetaSaveError = null) }
            try {
                val resp = bokApiService.poaUpsertMonth(poaId, request)
                if (resp.isSuccessful) {
                    // Invalidate cache so next open reloads fresh data
                    poaDetailCache.remove(poaId)
                    _uiState.update { it.copy(isSavingMonthMeta = false, monthMetaSaveSuccess = true, showMonthMeta = false) }
                    // Refresh calendar grid to reflect participant changes
                    loadCalendar(forceRefresh = true)
                } else {
                    val errBody = resp.errorBody()?.string()
                    val errMsg = try { gson.fromJson(errBody, Map::class.java)?.get("message")?.toString() ?: "Gagal menyimpan" } catch (_: Exception) { "Gagal menyimpan" }
                    _uiState.update { it.copy(isSavingMonthMeta = false, monthMetaSaveError = errMsg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingMonthMeta = false, monthMetaSaveError = e.message ?: "Error tidak diketahui") }
            }
        }
    }

    // ─── Claim ──────────────────────────────────────────────────────────────────


    fun loadClaimPrep() {
        val poaId = _uiState.value.detailPoaId
        val month = _uiState.value.detailMonth
        if (poaId <= 0 || month <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isClaimPrepLoading = true, claimError = null) }
            try {
                val response = bokApiService.getClaimPrep(poaId, month)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.update { it.copy(isClaimPrepLoading = false, claimPrep = response.body()) }
                } else {
                    _uiState.update { it.copy(isClaimPrepLoading = false, claimError = "Gagal memeriksa klaim") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isClaimPrepLoading = false, claimError = "Tidak dapat terhubung ke server") }
            }
        }
    }

    fun executeClaim() {
        val poaId = _uiState.value.detailPoaId
        val month = _uiState.value.detailMonth
        val prep = _uiState.value.claimPrep ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isClaimExecuting = true, claimError = null) }
            try {
                val request = ClaimRequest(
                    month = month,
                    jumlah_desa_darat = if (prep.target_darat > 0) prep.target_darat else null,
                    desa_tujuan_darat = prep.auto_desa_darat,
                    jumlah_desa_seberang = if (prep.target_seberang > 0) prep.target_seberang else null,
                    desa_tujuan_seberang = prep.auto_desa_seberang
                )
                val response = bokApiService.executeClaim(poaId, request)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    _uiState.update {
                        it.copy(
                            isClaimExecuting = false,
                            claimSuccess = result.message ?: "Klaim berhasil!",
                            claimResult = result,
                            claimPrep = null
                        )
                    }
                    // Refresh detail to show updated download buttons
                    loadDetail(poaId, month)
                    // Refresh calendar grid
                    loadCalendar(forceRefresh = true)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errMsg = try {
                        gson.fromJson(errorBody, Map::class.java)?.get("error")?.toString()
                            ?: gson.fromJson(errorBody, Map::class.java)?.get("message")?.toString()
                            ?: "Gagal klaim (${response.code()})"
                    } catch (_: Exception) { "Gagal klaim (${response.code()})" }
                    _uiState.update { it.copy(isClaimExecuting = false, claimError = errMsg) }
                }
            } catch (e: Exception) {
                // Log the actual error for debugging
                android.util.Log.e("CalendarVM", "Claim error: ${e::class.simpleName}: ${e.message}")
                val errorDetail = when {
                    e.message?.contains("Expected BEGIN") == true ->
                        "Server mengembalikan respons yang bukan JSON. Pastikan server BOK sudah diupdate."
                    e.message?.contains("Unable to resolve host") == true ->
                        "Tidak bisa terhubung ke server. Periksa koneksi internet."
                    else -> e.message ?: "Error tidak diketahui"
                }
                _uiState.update { it.copy(isClaimExecuting = false, claimError = errorDetail) }
            }
        }
    }

    // ─── Download ───────────────────────────────────────────────────────────────

    fun downloadLpj(lpjId: Long, type: String) {
        val kegiatan = _uiState.value.detailData?.kegiatan?.replace(Regex("[^A-Za-z0-9_\\- ]"), "_") ?: ""
        downloadFile("$type $kegiatan.docx") {
            bokApiService.downloadLpj(lpjId)
        }
    }

    fun downloadTibaBerangkat(tbId: Long) {
        val kegiatan = _uiState.value.detailData?.kegiatan?.replace(Regex("[^A-Za-z0-9_\\- ]"), "_") ?: ""
        downloadFile("Tiba_Berangkat $kegiatan.docx") {
            bokApiService.downloadTibaBerangkat(tbId)
        }
    }

    fun downloadItemOpsional(itemId: Long, label: String) {
        val kegiatan = _uiState.value.detailData?.kegiatan?.replace(Regex("[^A-Za-z0-9_\\- ]"), "_") ?: ""
        val safeName = label.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
        downloadFile("Kwitansi $safeName $kegiatan.docx") {
            bokApiService.downloadItemOpsional(itemId)
        }
    }

    private fun downloadFile(fileName: String, apiCall: suspend () -> retrofit2.Response<okhttp3.ResponseBody>) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingFile = fileName, downloadError = null, downloadSuccess = null) }
            try {
                val response = apiCall()
                if (response.isSuccessful && response.body() != null) {
                    withContext(Dispatchers.IO) {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadsDir.exists()) downloadsDir.mkdirs()
                        val file = File(downloadsDir, fileName)
                        response.body()!!.byteStream().use { input ->
                            FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    _uiState.update { it.copy(downloadingFile = null, downloadSuccess = "📄 $fileName disimpan di Downloads") }
                } else {
                    _uiState.update { it.copy(downloadingFile = null, downloadError = "Gagal download (${response.code()})") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(downloadingFile = null, downloadError = "Gagal download: ${e.message}") }
            }
        }
    }

    fun clearDownloadMessages() {
        _uiState.update { it.copy(downloadError = null, downloadSuccess = null) }
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

    // ─── HomeScreen Preview ─────────────────────────────────────────────────────

    fun getCurrentMonthActivities(): List<CalendarActivity> {
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val data = _uiState.value.calendarData ?: return emptyList()
        return data.activities.filter { activity ->
            val month = activity.months[currentMonth.toString()]
            month != null && month.has_activity
        }.sortedWith(compareByDescending<CalendarActivity> { activity ->
            activity.months.values.any { it.is_mine }
        }.thenByDescending { activity ->
            val m = activity.months[currentMonth.toString()]
            m != null && !m.claimed
        }.thenBy { it.kegiatan })
    }
}
