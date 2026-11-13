package com.example.kabensatudata.ui.screen.spm

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.BuildingDao
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.dao.SpmCacheDao
import com.example.kabensatudata.data.local.entity.SpmCacheEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.*
import com.example.kabensatudata.util.EnrichedSpmRow
import com.example.kabensatudata.util.ExcelExportHelper
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─── States ──────────────────────────────────────────────────────────────────

data class SpmDashboardState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
    val year: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val month: Int? = null,
    val villageId: Long? = null,
    val overview: SpmOverview? = null,
    val indicators: List<SpmIndicatorDto> = emptyList(),
    val villages: List<SpmVillageOption> = emptyList(),
    val expandedIndicators: Set<String> = emptySet(),
    val expandedChartIndex: Int? = null
)

data class SpmSubDetailState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
    val subIndicator: SpmSubInfo? = null,
    val achievedCount: Int = 0,
    val potentialCount: Int = 0,
    val rows: List<SpmPatientRow> = emptyList(),
    val potential: List<SpmPatientRow> = emptyList(),
    val selectedTab: Int = 0,  // 0=Tercatat, 1=Sasaran
    val isExporting: Boolean = false
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class SpmViewModel @Inject constructor(
    private val apiService: ApiService,
    private val spmCacheDao: SpmCacheDao,
    private val memberDao: FamilyMemberDao,
    private val familyDao: FamilyDao,
    private val buildingDao: BuildingDao
) : ViewModel() {

    private val gson = Gson()

    private val _dashboardState = MutableStateFlow(SpmDashboardState())
    val dashboardState = _dashboardState.asStateFlow()

    private val _detailState = MutableStateFlow(SpmSubDetailState())
    val detailState = _detailState.asStateFlow()

    init { loadDashboard() }

    // ─── Cache key helpers ───────────────────────────────────────────────

    private fun dashboardCacheKey(year: Int, month: Int?, villageId: Long?): String =
        "spm_dashboard_${year}_${month ?: "all"}_${villageId ?: "all"}"

    private fun subDetailCacheKey(subId: Long, year: Int, month: Int?, villageId: Long?): String =
        "spm_sub_detail_${subId}_${year}_${month ?: "all"}_${villageId ?: "all"}"

    // ─── Dashboard ───────────────────────────────────────────────────────

    fun loadDashboard() {
        viewModelScope.launch {
            val s = _dashboardState.value
            _dashboardState.update { it.copy(isLoading = true, error = null, isOffline = false) }

            // 1. Cache-first: show cached data instantly
            val cacheKey = dashboardCacheKey(s.year, s.month, s.villageId)
            try {
                val cached = spmCacheDao.get(cacheKey)
                if (cached != null) {
                    val d = gson.fromJson(cached.jsonData, SpmDashboardData::class.java)
                    _dashboardState.update {
                        it.copy(
                            isLoading = true,
                            overview = d.overview,
                            indicators = d.indicators,
                            villages = d.villages,
                            year = d.year,
                            month = d.month,
                            villageId = d.villageId
                        )
                    }
                }
            } catch (_: Exception) {}

            // 2. Try API
            try {
                val resp = apiService.getSpmDashboard(s.year, s.month, s.villageId)
                if (resp.isSuccessful) {
                    val d = resp.body()?.data
                    if (d != null) {
                        _dashboardState.update {
                            it.copy(
                                isLoading = false,
                                isOffline = false,
                                overview = d.overview,
                                indicators = d.indicators,
                                villages = d.villages,
                                year = d.year,
                                month = d.month,
                                villageId = d.villageId
                            )
                        }
                        // Save to cache
                        val key = dashboardCacheKey(d.year, d.month, d.villageId)
                        val json = gson.toJson(d)
                        spmCacheDao.insertOrUpdate(SpmCacheEntity(key, json))
                    } else {
                        _dashboardState.update { it.copy(isLoading = false, error = "Data kosong") }
                    }
                } else {
                    _dashboardState.update { it.copy(isLoading = false, isOffline = true) }
                }
            } catch (e: Exception) {
                _dashboardState.update {
                    it.copy(
                        isLoading = false,
                        isOffline = true,
                        error = if (it.overview == null) e.message else null
                    )
                }
            }
        }
    }

    // loadDashboardFromCache is no longer needed — cache-first is built into loadDashboard()

    // ─── Filters ─────────────────────────────────────────────────────────

    fun setYear(year: Int) { _dashboardState.update { it.copy(year = year) }; loadDashboard() }
    fun setMonth(month: Int?) { _dashboardState.update { it.copy(month = month) }; loadDashboard() }
    fun setVillage(vid: Long?) { _dashboardState.update { it.copy(villageId = vid) }; loadDashboard() }

    fun toggleIndicator(code: String) {
        _dashboardState.update {
            val set = it.expandedIndicators.toMutableSet()
            if (set.contains(code)) set.remove(code) else set.add(code)
            it.copy(expandedIndicators = set)
        }
    }

    fun toggleChartIndicator(index: Int) {
        _dashboardState.update {
            it.copy(expandedChartIndex = if (it.expandedChartIndex == index) null else index)
        }
    }

    // ─── Sub Detail ──────────────────────────────────────────────────────

    fun loadSubDetail(subId: Long, year: Int, month: Int? = null, villageId: Long? = null) {
        viewModelScope.launch {
            _detailState.update { SpmSubDetailState(isLoading = true) }

            // 1. Cache-first
            val cacheKey = subDetailCacheKey(subId, year, month, villageId)
            try {
                val cached = spmCacheDao.get(cacheKey)
                if (cached != null) {
                    val d = gson.fromJson(cached.jsonData, SpmSubDetailData::class.java)
                    _detailState.update {
                        SpmSubDetailState(
                            isLoading = true,
                            subIndicator = d.subIndicator,
                            achievedCount = d.achievedCount,
                            potentialCount = d.potentialCount,
                            rows = d.rows,
                            potential = d.potential
                        )
                    }
                }
            } catch (_: Exception) {}

            // 2. Try API
            try {
                val resp = apiService.getSpmSubDetail(subId, year, month, villageId)
                if (resp.isSuccessful) {
                    val d = resp.body()?.data
                    if (d != null) {
                        _detailState.update {
                            SpmSubDetailState(
                                subIndicator = d.subIndicator,
                                achievedCount = d.achievedCount,
                                potentialCount = d.potentialCount,
                                rows = d.rows,
                                potential = d.potential
                            )
                        }
                        // Save to cache
                        val key = subDetailCacheKey(subId, year, month, villageId)
                        val json = gson.toJson(d)
                        spmCacheDao.insertOrUpdate(SpmCacheEntity(key, json))
                    } else {
                        _detailState.update { it.copy(isLoading = false, error = "Data kosong") }
                    }
                } else {
                    _detailState.update { it.copy(isLoading = false, isOffline = true) }
                }
            } catch (e: Exception) {
                _detailState.update {
                    it.copy(
                        isLoading = false,
                        isOffline = true,
                        error = if (it.subIndicator == null) e.message else null
                    )
                }
            }
        }
    }

    fun setDetailTab(tab: Int) { _detailState.update { it.copy(selectedTab = tab) } }

    // ─── Export SPM Sub-Detail ───────────────────────────────────────────

    fun exportSubDetail(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _detailState.value
            if (state.rows.isEmpty() && state.potential.isEmpty()) return@launch

            _detailState.update { it.copy(isExporting = true) }
            try {
                // Kumpulkan semua NIK dari rows + potential
                val allNiks = (state.rows + state.potential)
                    .mapNotNull { it.nik }
                    .filter { it.isNotBlank() }
                    .distinct()

                // Query Room DB sekali untuk semua NIK
                val localMembers = if (allNiks.isNotEmpty())
                    memberDao.getByNiks(allNiks).associateBy { it.nik }
                else emptyMap()

                val localFamilies = familyDao.getAllSuspend().associateBy { it.id }
                val localBuildings = buildingDao.getAllSuspend().associateBy { it.id }

                fun enrich(row: SpmPatientRow, statusOverride: String? = null): EnrichedSpmRow {
                    val local = row.nik?.let { localMembers[it] }
                    val family = local?.familyId?.let { localFamilies[it] }
                    val building = family?.buildingId?.let { localBuildings[it] }
                    return EnrichedSpmRow(
                        id = row.id, name = row.name, gender = row.gender,
                        age = row.age, nik = row.nik, village = row.village,
                        lastVisit = row.lastVisit,
                        status = statusOverride ?: row.status,
                        birthDate = local?.birthDate?.take(10),
                        education = local?.education,
                        occupation = local?.occupation,
                        maritalStatus = local?.maritalStatus,
                        religion = local?.religion,
                        hasJkn = local?.hasJkn,
                        isSmoker = local?.isSmoker,
                        hasTbc = local?.hasTuberculosis,
                        hasHypertension = local?.hasHypertension,
                        usesContraception = local?.usesContraception,
                        isPregnant = local?.isPregnant,
                        familyNumber = family?.familyNumber?.let { "KK-${it.toString().padStart(3, '0')}" },
                        buildingNumber = building?.buildingNumber?.let { "B-${it.toString().padStart(3, '0')}" }
                    )
                }

                val enrichedAchieved = state.rows.map { enrich(it, "Tercatat") }
                val enrichedPotential = state.potential.map { enrich(it) }

                val year = _dashboardState.value.year
                val month = _dashboardState.value.month

                val fileUri = ExcelExportHelper.exportSpmSubDetail(
                    context = context,
                    subName = state.subIndicator?.name ?: "SPM",
                    subCode = state.subIndicator?.code ?: "",
                    year = year,
                    month = month,
                    achieved = enrichedAchieved,
                    potential = enrichedPotential
                )

                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Buka File Excel"))
                }
            } catch (e: Exception) {
                // Gagal export — silent fail, tidak crash app
                e.printStackTrace()
            } finally {
                _detailState.update { it.copy(isExporting = false) }
            }
        }
    }
}
