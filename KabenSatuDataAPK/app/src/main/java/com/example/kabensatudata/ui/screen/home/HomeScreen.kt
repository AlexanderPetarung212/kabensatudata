package com.example.kabensatudata.ui.screen.home

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.dao.*
import com.example.kabensatudata.data.local.entity.FamilyMemberEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.AnnouncementDto
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.appTextFieldColors
import com.example.kabensatudata.util.ExcelExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import com.example.kabensatudata.ui.screen.calendar.CalendarSection
import com.example.kabensatudata.ui.screen.calendar.CalendarDetailSheet
import com.example.kabensatudata.ui.screen.calendar.CalendarViewModel
import com.example.kabensatudata.ui.screen.poa.PoaSection
import com.example.kabensatudata.ui.screen.poa.PoaViewModel

// ─── Data models ────────────────────────────────────────

data class ChartItem(
    val label: String,
    val value: Int,
    val color: Color
)

data class SanitasiStat(
    val label: String,
    val count: Int,
    val percentage: Float,
    val color: Color
)

data class SearchResult(
    val member: FamilyMemberEntity,
    val familyHeadName: String,
    val familyNumber: String
)

data class HomeUiState(
    val userName: String = "",
    val pendingSyncCount: Int = 0,
    // Quick counts
    val villageCount: Int = 0,
    val buildingCount: Int = 0,
    val familyCount: Int = 0,
    val memberCount: Int = 0,
    // Health cases
    val tbcCount: Int = 0,
    val hypertensionCount: Int = 0,
    // Sanitasi
    val sanitasiStats: List<SanitasiStat> = emptyList(),
    // Distribution charts
    val genderData: List<ChartItem> = emptyList(),
    val ageData: List<ChartItem> = emptyList(),
    val educationData: List<ChartItem> = emptyList(),
    val healthData: List<ChartItem> = emptyList(),
    // Visit chart
    val visitCounts: List<Pair<String, Int>> = emptyList(),
    val totalVisitsThisWeek: Int = 0,
    // Search
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = true,
    val announcement: AnnouncementDto? = null,
    // Analysis
    val analysisState: AnalysisState = AnalysisState()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tokenManager: AuthTokenManager,
    private val villageDao: VillageDao,
    private val buildingDao: BuildingDao,
    private val familyDao: FamilyDao,
    private val memberDao: FamilyMemberDao,
    private val medicalRecordDao: MedicalRecordDao,
    private val syncQueueDao: SyncQueueDao,
    private val apiService: ApiService,
    private val notificationDao: NotificationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    val unreadNotificationCount = notificationDao.getUnreadCount()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadDashboard()
        setupSearch()
        fetchAnnouncement()
        loadVillagesForAnalysis()
    }

    @OptIn(FlowPreview::class)
    private fun setupSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length >= 2) {
                        _uiState.update { it.copy(isSearching = true) }
                        try {
                            val results = memberDao.search(query).first()
                            val mapped = results.map { m ->
                                val family = familyDao.getById(m.familyId)
                                SearchResult(m, family?.headName ?: "-", family?.familyNumber ?: "-")
                            }
                            _uiState.update { it.copy(searchResults = mapped, isSearching = false) }
                        } catch (_: Exception) {
                            _uiState.update { it.copy(isSearching = false) }
                        }
                    } else {
                        _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (!active) "" else it.searchQuery,
                searchResults = if (!active) emptyList() else it.searchResults
            )
        }
        if (!active) _searchQuery.value = ""
    }

    fun logout() {
        tokenManager.clear()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            val userName = tokenManager.getUserName().ifEmpty { "Operator" }
            val villageCount = villageDao.countAll()
            val buildingCount = buildingDao.countAll()
            val familyCount = familyDao.countAll()
            val memberCount = memberDao.countAll()
            val pendingSync = syncQueueDao.getPendingCount().first()

            // ── Health case counts ──
            val tbcCount = try { memberDao.countWithTuberculosis() } catch (_: Exception) { 0 }
            val hypertensionCount = try { memberDao.countWithHypertension() } catch (_: Exception) { 0 }

            // ── Sanitasi stats ──
            val totalFamilies = familyCount.coerceAtLeast(1)
            val cleanWater = try { familyDao.countWithCleanWater() } catch (_: Exception) { 0 }
            val protectedWater = try { familyDao.countWithProtectedWater() } catch (_: Exception) { 0 }
            val toilet = try { familyDao.countWithToilet() } catch (_: Exception) { 0 }
            val sanitaryToilet = try { familyDao.countWithSanitaryToilet() } catch (_: Exception) { 0 }
            val sanitasiStats = listOf(
                SanitasiStat("Air Bersih", cleanWater, cleanWater.toFloat() / totalFamilies * 100, Color(0xFF3B82F6)),
                SanitasiStat("Air Terlindungi", protectedWater,
                    if (cleanWater > 0) protectedWater.toFloat() / cleanWater * 100 else 0f,
                    Color(0xFF10B981)),
                SanitasiStat("Jamban", toilet, toilet.toFloat() / totalFamilies * 100, Color(0xFF6366F1)),
                SanitasiStat("Jamban Saniter", sanitaryToilet,
                    if (toilet > 0) sanitaryToilet.toFloat() / toilet * 100 else 0f,
                    Color(0xFF8B5CF6))
            )

            // ── Gender distribution ──
            val male = try { memberDao.countByGender("Laki-laki") } catch (_: Exception) { 0 }
            val female = try { memberDao.countByGender("Perempuan") } catch (_: Exception) { 0 }
            val genderData = listOf(
                ChartItem("Laki-laki", male, Color(0xFF3B82F6)),
                ChartItem("Perempuan", female, Color(0xFFEC4899))
            )

            // ── Age distribution ──
            val today = LocalDate.now()
            val birthDates = try { memberDao.getAllBirthDates() } catch (_: Exception) { emptyList() }
            val ageGroups = mutableMapOf(
                "0-5" to 0, "6-17" to 0, "18-45" to 0, "46-60" to 0, ">60" to 0
            )
            birthDates.forEach { bd ->
                try {
                    val birth = LocalDate.parse(bd.take(10))
                    val age = Period.between(birth, today).years
                    when {
                        age <= 5 -> ageGroups["0-5"] = ageGroups["0-5"]!! + 1
                        age <= 17 -> ageGroups["6-17"] = ageGroups["6-17"]!! + 1
                        age <= 45 -> ageGroups["18-45"] = ageGroups["18-45"]!! + 1
                        age <= 60 -> ageGroups["46-60"] = ageGroups["46-60"]!! + 1
                        else -> ageGroups[">60"] = ageGroups[">60"]!! + 1
                    }
                } catch (_: Exception) { /* skip invalid dates */ }
            }
            val ageColors = listOf(Color(0xFF10B981), Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6))
            val ageData = ageGroups.entries.mapIndexed { i, (label, count) ->
                ChartItem(label, count, ageColors[i])
            }

            // ── Education distribution ──
            val eduRaw = try { memberDao.getEducationDistribution() } catch (_: Exception) { emptyList() }
            val eduColors = listOf(
                Color(0xFF6366F1), Color(0xFF3B82F6), Color(0xFF10B981),
                Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6),
                Color(0xFFEC4899), Color(0xFF14B8A6)
            )
            val educationData = eduRaw.mapIndexed { i, ec ->
                ChartItem(ec.education, ec.cnt, eduColors[i % eduColors.size])
            }

            // ── Health issues ──
            val smokers = try { memberDao.countSmokers() } catch (_: Exception) { 0 }
            val pregnant = try { memberDao.countPregnant() } catch (_: Exception) { 0 }
            val chronicCough = try { memberDao.countWithChronicCough() } catch (_: Exception) { 0 }
            val healthData = listOf(
                ChartItem("TBC", tbcCount, Color(0xFFEF4444)),
                ChartItem("Hipertensi", hypertensionCount, Color(0xFFF59E0B)),
                ChartItem("Perokok", smokers, Color(0xFF6B7280)),
                ChartItem("Hamil", pregnant, Color(0xFFEC4899)),
                ChartItem("Batuk Kronis", chronicCough, Color(0xFF8B5CF6))
            )

            // ── Visit counts (7 days) ──
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val dayFormatter = DateTimeFormatter.ofPattern("EEE")
            val visitCounts = (6 downTo 0).map { daysAgo ->
                val date = today.minusDays(daysAgo.toLong())
                val label = date.format(dayFormatter)
                val count = try { medicalRecordDao.countByDate(date.format(formatter)) } catch (_: Exception) { 0 }
                label to count
            }
            val totalVisits = visitCounts.sumOf { it.second }

            _uiState.update {
                it.copy(
                    userName = userName, villageCount = villageCount, buildingCount = buildingCount,
                    familyCount = familyCount, memberCount = memberCount, pendingSyncCount = pendingSync,
                    tbcCount = tbcCount, hypertensionCount = hypertensionCount,
                    sanitasiStats = sanitasiStats, genderData = genderData,
                    ageData = ageData, educationData = educationData, healthData = healthData,
                    visitCounts = visitCounts, totalVisitsThisWeek = totalVisits,
                    isLoading = false
                )
            }
        }
    }

    private fun fetchAnnouncement() {
        viewModelScope.launch {
            try {
                val response = apiService.getAnnouncement()
                if (response.isSuccessful) {
                    val ann = response.body()?.announcement
                    _uiState.update { it.copy(announcement = ann) }
                    // Save to local notification DB if new
                    if (ann != null) {
                        val exists = notificationDao.existsByServerId(ann.id)
                        if (!exists) {
                            notificationDao.insert(
                                com.example.kabensatudata.data.local.entity.NotificationEntity(
                                    serverId = ann.id,
                                    title = ann.title,
                                    message = ann.message,
                                    type = ann.type
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Silently fail if offline — announcement is optional
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // ─── Analysis Methods ─────────────────────────────────
    // ═══════════════════════════════════════════════════════

    // Cached raw data for analysis (avoid re-loading)
    private var cachedAllRows: List<AnalysisRow>? = null

    private fun loadVillagesForAnalysis() {
        viewModelScope.launch {
            try {
                val villages = villageDao.getAllSuspend()
                val villagePairs = villages.map { it.id to it.name }
                _uiState.update {
                    it.copy(analysisState = it.analysisState.copy(villages = villagePairs))
                }
            } catch (_: Exception) { /* ok */ }
        }
    }

    fun toggleAnalysisGroup(group: String) {
        _uiState.update {
            val current = it.analysisState.expandedGroups
            val updated = if (group in current) current - group else current + group
            it.copy(analysisState = it.analysisState.copy(expandedGroups = updated))
        }
    }

    fun updateAnalysisFilter(key: String, value: String) {
        _uiState.update {
            val newFilters = it.analysisState.filters.toMutableMap()
            if (value.isBlank()) newFilters.remove(key) else newFilters[key] = value
            it.copy(analysisState = it.analysisState.copy(filters = newFilters))
        }
    }

    fun selectAnalysisVillage(villageId: Long?) {
        _uiState.update {
            it.copy(analysisState = it.analysisState.copy(selectedVillageId = villageId))
        }
    }

    fun runAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(analysisState = it.analysisState.copy(isAnalyzing = true, showSuccessNotif = false)) }
            try {
                // Load all data if not cached
                if (cachedAllRows == null) {
                    val villages = villageDao.getAllSuspend()
                    val buildings = buildingDao.getAllSuspend()
                    val families = familyDao.getAllSuspend()
                    val members = memberDao.getAllSuspend()
                    cachedAllRows = AnalysisEngine.buildRows(villages, buildings, families, members)
                }

                var rows = cachedAllRows!!

                // Village filter
                val villageId = _uiState.value.analysisState.selectedVillageId
                if (villageId != null) {
                    val village = villageDao.getById(villageId)
                    val vName = village?.name ?: ""
                    rows = rows.filter { it.rawVillageName == vName }
                }

                // Apply user filters
                val filters = _uiState.value.analysisState.filters
                val filtered = AnalysisEngine.applyFilters(rows, filters)

                _uiState.update {
                    it.copy(analysisState = it.analysisState.copy(
                        results = filtered,
                        resultCount = filtered.size,
                        isAnalyzing = false,
                        hasAnalyzed = true,
                        currentPage = 0,
                        showSuccessNotif = true
                    ))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(analysisState = it.analysisState.copy(
                        results = emptyList(), resultCount = 0,
                        isAnalyzing = false, hasAnalyzed = true,
                        currentPage = 0
                    ))
                }
            }
        }
    }

    fun clearAnalysis() {
        _uiState.update {
            it.copy(analysisState = AnalysisState(villages = it.analysisState.villages))
        }
    }

    fun toggleCrossTab() {
        _uiState.update {
            it.copy(analysisState = it.analysisState.copy(showCrossTab = !it.analysisState.showCrossTab))
        }
    }

    fun setCrossTabVar1(v: String) {
        _uiState.update { it.copy(analysisState = it.analysisState.copy(crossTabVar1 = v)) }
    }

    fun setCrossTabVar2(v: String) {
        _uiState.update { it.copy(analysisState = it.analysisState.copy(crossTabVar2 = v)) }
    }

    fun runCrossTab() {
        viewModelScope.launch {
            try {
                // Use analysis results if available, otherwise all data
                val data = if (_uiState.value.analysisState.hasAnalyzed) {
                    _uiState.value.analysisState.results
                } else {
                    // Build all rows
                    if (cachedAllRows == null) {
                        val villages = villageDao.getAllSuspend()
                        val buildings = buildingDao.getAllSuspend()
                        val families = familyDao.getAllSuspend()
                        val members = memberDao.getAllSuspend()
                        cachedAllRows = AnalysisEngine.buildRows(villages, buildings, families, members)
                    }
                    cachedAllRows!!
                }

                val var1 = _uiState.value.analysisState.crossTabVar1
                val var2 = _uiState.value.analysisState.crossTabVar2
                val (headers, rows) = AnalysisEngine.computeCrossTab(data, var1, var2)

                _uiState.update {
                    it.copy(analysisState = it.analysisState.copy(
                        crossTabHeaders = headers, crossTabRows = rows
                    ))
                }
            } catch (_: Exception) { /* ok */ }
        }
    }

    fun setAnalysisPage(page: Int) {
        _uiState.update {
            it.copy(analysisState = it.analysisState.copy(currentPage = page))
        }
    }

    fun dismissSuccessNotif() {
        _uiState.update {
            it.copy(analysisState = it.analysisState.copy(showSuccessNotif = false))
        }
    }

    // ─── Export Analisis ─────────────────────────────────────────────────

    fun exportAnalysis(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value.analysisState
            if (state.results.isEmpty()) return@launch

            _uiState.update { it.copy(analysisState = it.analysisState.copy(isExporting = true)) }
            try {
                val villageName = state.selectedVillageId?.let { id ->
                    state.villages.find { it.first == id }?.second
                }
                val fileUri = ExcelExportHelper.exportAnalysis(
                    context = context,
                    rows = state.results,
                    crossTabHeaders = state.crossTabHeaders,
                    crossTabRows = state.crossTabRows,
                    filters = state.filters,
                    villageName = villageName
                )
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Buka File Excel"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(analysisState = it.analysisState.copy(isExporting = false)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── HomeScreen Composable ────────────────────────────
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToVillages: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToPoaList: () -> Unit = {},
    onNavigateToPoaDetail: (Long) -> Unit = {},
    onNavigateToAnalysis: () -> Unit = {},
    onNavigateToMedicalHub: () -> Unit = {},
    onNavigateToDataHub: () -> Unit = {},
    onMemberClick: ((Long) -> Unit)? = null,
    onOpenNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    calendarViewModel: CalendarViewModel = hiltViewModel(),
    poaViewModel: PoaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val calendarUiState by calendarViewModel.uiState.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState(initial = 0)
    var showAnnouncement by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Bottom sheet states
    var showKesehatanSheet by remember { mutableStateOf(false) }
    var showSanitasiSheet by remember { mutableStateOf(false) }
    var showGrafikSheet by remember { mutableStateOf(false) }

    // ── Announcement Popup ────────────────────────────
    if (showAnnouncement && uiState.announcement != null) {
        val ann = uiState.announcement!!
        val (annIcon, annColor) = when (ann.type) {
            "warning"  -> Icons.Default.Warning to Color(0xFFF59E0B)
            "update"   -> Icons.Default.SystemUpdate to Color(0xFF10B981)
            "critical" -> Icons.Default.Error to Color(0xFFEF4444)
            else       -> Icons.Default.Info to Color(0xFF3B82F6)
        }
        AlertDialog(
            onDismissRequest = { showAnnouncement = false },
            icon = { Icon(annIcon, null, tint = annColor, modifier = Modifier.size(36.dp)) },
            title = { Text(ann.title, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary) },
            text = { Text(ann.message, color = AppColors.TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { showAnnouncement = false }) {
                    Text("Tutup", color = annColor, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = AppColors.Surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Gradient Header dengan Wave ────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    // Wave Canvas background dengan shimmer
                    val homeShimmer = rememberInfiniteTransition(label = "homeShimmer")
                    val homeShimmerOff by homeShimmer.animateFloat(
                        initialValue = -1.5f, targetValue = 2.5f,
                        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
                        label = "homeShimmerOff"
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        val w = size.width
                        val h = size.height

                        // Gradient lebih dalam
                        val gradientBrush = Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color(0xFF00C49A),
                                androidx.compose.ui.graphics.Color(0xFF006B5E)
                            )
                        )
                        drawRect(brush = gradientBrush)

                        // Wave path
                        val wavePath = Path().apply {
                            moveTo(0f, h * 0.80f)
                            cubicTo(
                                w * 0.20f, h * 0.65f,
                                w * 0.55f, h * 0.98f,
                                w * 0.80f, h * 0.82f
                            )
                            cubicTo(
                                w * 0.90f, h * 0.74f,
                                w * 0.95f, h * 0.72f,
                                w, h * 0.78f
                            )
                            lineTo(w, 0f)
                            lineTo(0f, 0f)
                            close()
                        }
                        drawPath(wavePath, gradientBrush)

                        // Shimmer overlay
                        val shimmerBrush = Brush.linearGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f),
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f),
                                androidx.compose.ui.graphics.Color.Transparent
                            ),
                            start = Offset(homeShimmerOff * w, 0f),
                            end = Offset((homeShimmerOff + 0.45f) * w, h)
                        )
                        drawRect(brush = shimmerBrush)
                    }

                    // Konten di atas wave
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(top = 16.dp, bottom = 32.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Halo,",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        uiState.userName.ifEmpty { "Operator" },
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("👋", fontSize = 20.sp)
                                }
                            }
                            // Notification bell
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f))
                                        .clickable { onOpenNotifications() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        "Notifikasi",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                if (unreadCount > 0) {
                                    Badge(
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        containerColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
                                    ) {
                                        Text(
                                            if (unreadCount > 9) "9+" else "$unreadCount",
                                            color = androidx.compose.ui.graphics.Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = {
                                viewModel.onSearchQueryChanged(it)
                                if (!uiState.isSearchActive && it.isNotEmpty()) viewModel.setSearchActive(true)
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            placeholder = { Text("Cari nama atau NIK…", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchActive(false); focusManager.clearFocus() }) {
                                        Icon(Icons.Default.Close, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f),
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f),
                                focusedBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                                cursorColor = androidx.compose.ui.graphics.Color.White,
                                focusedTextColor = androidx.compose.ui.graphics.Color.White,
                                unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
                                focusedPlaceholderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                    }
                }
            }

            // ── Search Results or Dashboard ─────────────
            if (uiState.isSearchActive || uiState.searchQuery.length >= 2) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Hasil Pencarian", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                        Text("${uiState.searchResults.size} ditemukan", fontSize = 12.sp, color = AppColors.TextTertiary)
                    }
                }
                if (uiState.isSearching) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                        }
                    }
                } else if (uiState.searchResults.isEmpty() && uiState.searchQuery.length >= 2) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null, tint = AppColors.TextTertiary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Tidak ditemukan", fontSize = 13.sp, color = AppColors.TextTertiary)
                            }
                        }
                    }
                } else {
                    items(uiState.searchResults, key = { it.member.id }) { result ->
                        SearchResultCard(result = result, onClick = {
                            focusManager.clearFocus()
                            onMemberClick?.invoke(result.member.id)
                        })
                    }
                }
            } else {
                // ── Service Grid (2×4) ──────────────────
                item {
                    Spacer(Modifier.height(16.dp))
                    ServiceGrid(
                        onNavigateToVillages = onNavigateToVillages,
                        onNavigateToMap = onNavigateToMap,
                        onNavigateToCalendar = onNavigateToCalendar,
                        onNavigateToAnalysis = onNavigateToAnalysis,
                        onNavigateToMedicalHub = onNavigateToMedicalHub,
                        onNavigateToSync = onNavigateToSync,
                        onNavigateToPoaList = onNavigateToPoaList,
                        onNavigateToDataHub = onNavigateToDataHub
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // ── Quick Stats Row ─────────────────────
                item {
                    if (uiState.isLoading) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(4) { ShimmerBox(Modifier.weight(1f).height(80.dp)) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStatCard("Desa", uiState.villageCount, Icons.Default.LocationCity, Modifier.weight(1f))
                            MiniStatCard("Bangunan", uiState.buildingCount, Icons.Default.Home, Modifier.weight(1f))
                            MiniStatCard("Keluarga", uiState.familyCount, Icons.Default.People, Modifier.weight(1f))
                            MiniStatCard("Penduduk", uiState.memberCount, Icons.Default.Person, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Summary Cards (Tappable → Bottom Sheet) ──
                item {
                    SummaryCardRow(
                        kesehatanCount = uiState.tbcCount + uiState.hypertensionCount,
                        sanitasiCount = uiState.sanitasiStats.size,
                        chartDataAvailable = uiState.genderData.isNotEmpty(),
                        onKesehatanClick = { showKesehatanSheet = true },
                        onSanitasiClick = { showSanitasiSheet = true },
                        onGrafikClick = { showGrafikSheet = true }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // ── Kalender Kegiatan BOK ────────────────
                item {
                    val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
                    CalendarSection(
                        activities = calendarViewModel.getCurrentMonthActivities(),
                        currentMonth = currentMonth,
                        isLoading = calendarUiState.isLoading,
                        error = calendarUiState.error ?: calendarUiState.bokAuthError,
                        onNavigateToCalendar = onNavigateToCalendar,
                        onActivityClick = { poaId, month ->
                            calendarViewModel.loadDetail(poaId, month)
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // ── POA Section ─────────────────────────
                item {
                    PoaSection(
                        viewModel = poaViewModel,
                        onViewAll = onNavigateToPoaList,
                        onPoaClick = onNavigateToPoaDetail
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // ── Bottom Sheets ────────────────────────────────
        if (showKesehatanSheet) {
            KesehatanBottomSheet(
                onDismiss = { showKesehatanSheet = false },
                tbcCount = uiState.tbcCount,
                hypertensionCount = uiState.hypertensionCount,
                healthData = uiState.healthData
            )
        }

        if (showSanitasiSheet) {
            SanitasiBottomSheet(
                onDismiss = { showSanitasiSheet = false },
                sanitasiStats = uiState.sanitasiStats
            )
        }

        if (showGrafikSheet) {
            GrafikBottomSheet(
                onDismiss = { showGrafikSheet = false },
                genderData = uiState.genderData,
                ageData = uiState.ageData,
                educationData = uiState.educationData,
                healthData = uiState.healthData,
                visitCounts = uiState.visitCounts,
                totalVisitsThisWeek = uiState.totalVisitsThisWeek
            )
        }

        // Calendar Detail Bottom Sheet
        if (calendarUiState.showDetail) {
            CalendarDetailSheet(
                isLoading = calendarUiState.isDetailLoading,
                error = calendarUiState.detailError,
                data = calendarUiState.detailData,
                onDismiss = calendarViewModel::dismissDetail,
                claimPrep = calendarUiState.claimPrep,
                isClaimPrepLoading = calendarUiState.isClaimPrepLoading,
                isClaimExecuting = calendarUiState.isClaimExecuting,
                claimError = calendarUiState.claimError,
                claimSuccess = calendarUiState.claimSuccess,
                claimResult = calendarUiState.claimResult,
                onLoadClaimPrep = calendarViewModel::loadClaimPrep,
                onExecuteClaim = calendarViewModel::executeClaim,
                downloadingFile = calendarUiState.downloadingFile,
                downloadError = calendarUiState.downloadError,
                downloadSuccess = calendarUiState.downloadSuccess,
                onDownloadLpj = calendarViewModel::downloadLpj,
                onDownloadTibaBerangkat = calendarViewModel::downloadTibaBerangkat,
                onDownloadItemOpsional = calendarViewModel::downloadItemOpsional,
                onClearDownloadMessages = calendarViewModel::clearDownloadMessages
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Component Composables ────────────────────────────
// ═══════════════════════════════════════════════════════

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = AppColors.TextPrimary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    accentColor: Color = AppColors.Accent,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor   = accentColor.copy(0.22f),
                ambientColor = accentColor.copy(0.08f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.GlossBorder),
        shadowElevation = 0.dp
    ) {
        Column {
            // Glossy stripe putih-teal di atas card
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(0.70f),
                                accentColor.copy(0.55f),
                                Color.White.copy(0.45f),
                                accentColor.copy(0.20f)
                            )
                        )
                    )
            )
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun MiniStatCard(label: String, count: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    val animatedCount = animateCountUp(count)
    Surface(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor   = AppColors.Accent.copy(0.28f),
                ambientColor = AppColors.Accent.copy(0.10f)
            ),
        shape = RoundedCornerShape(18.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.GlossBorder),
        shadowElevation = 0.dp
    ) {
        Column {
            // Glossy stripe di atas mini card
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(0.65f),
                                AppColors.Accent.copy(0.50f),
                                Color.White.copy(0.40f)
                            )
                        )
                    )
            )
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(AppColors.Accent.copy(alpha = 0.20f), AppColors.Accent.copy(alpha = 0.05f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, modifier = Modifier.size(18.dp), tint = AppColors.Accent)
                }
                Spacer(Modifier.height(4.dp))
                Text("$animatedCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Accent)
                Text(label, fontSize = 10.sp, color = AppColors.TextSecondary)
            }
        }
    }
}

// ─── Health Case Card ───────────────────────────────────

@Composable
fun HealthCaseCard(
    label: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedCount = animateCountUp(count)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = AppColors.Surface,
        shadowElevation = 4.dp
    ) {
        Column {
            // Gradient accent strip
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(color, color.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    )
            )
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular gradient icon
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.04f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.TextSecondary)
                    Text("$animatedCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                }
            }
        }
    }
}

// ─── Sanitasi Card ──────────────────────────────────────

@Composable
fun SanitasiCard(stat: SanitasiStat, modifier: Modifier = Modifier) {
    val animatedCount = animateCountUp(stat.count)
    val animatedPct by animateFloatAsState(
        targetValue = stat.percentage,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "pct"
    )
    val animatedBar by animateFloatAsState(
        targetValue = (stat.percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "bar"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = AppColors.Surface,
        shadowElevation = 4.dp
    ) {
        Column {
            // Gradient accent strip
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(stat.color, stat.color.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    )
            )
            Column(Modifier.padding(14.dp)) {
            Text(stat.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text("$animatedCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "${String.format("%.1f", animatedPct)}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = stat.color
            )
            Spacer(Modifier.height(6.dp))
            // progress bar
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.Secondary.copy(alpha = 0.2f))
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(animatedBar).clip(RoundedCornerShape(3.dp)).background(stat.color)
                )
            }
        }
        }
    }
}

// ─── Horizontal Bar Chart ───────────────────────────────

@Composable
fun HorizontalBarChart(data: List<ChartItem>) {
    if (data.isEmpty()) return
    val maxVal = data.maxOf { it.value }.coerceAtLeast(1)
    val total = data.sumOf { it.value }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.forEach { item ->
            val animatedFraction by animateFloatAsState(
                targetValue = item.value.toFloat() / maxVal,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                label = "bar_${item.label}"
            )
            val animatedValue = animateCountUp(item.value, durationMs = 800)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label
                Text(
                    item.label,
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.width(90.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Bar
                Box(
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.Secondary.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(item.color)
                    )
                }
                // Value
                Text(
                    "$animatedValue",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.width(48.dp).padding(start = 8.dp)
                )
            }
        }
        // Total
        if (total > 0) {
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.CardBorder)
            val animatedTotal = animateCountUp(total, durationMs = 800)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Text("$animatedTotal", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Accent)
            }
        }
    }
}

// ─── Visit Line Chart ───────────────────────────────────

@Composable
fun VisitLineChart(data: List<Pair<String, Int>>) {
    if (data.isEmpty()) return
    val maxVal = data.maxOf { it.second }.coerceAtLeast(1)

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val w = size.width
            val h = size.height

            val points = data.mapIndexed { i, (_, v) ->
                val x = w * i / (data.size - 1).coerceAtLeast(1)
                val y = h - (v.toFloat() / maxVal) * (h * 0.85f)
                Offset(x, y)
            }

            if (points.size >= 2) {
                val fillPath = Path().apply {
                    moveTo(points.first().x, h)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, h)
                    close()
                }
                drawPath(fillPath, AppColors.Accent.copy(alpha = 0.08f))

                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                }
                drawPath(linePath, AppColors.Accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }

            points.forEach { p ->
                drawCircle(AppColors.Accent, radius = 5f, center = p)
                drawCircle(Color.White, radius = 3f, center = p)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach { (label, _) -> Text(label, fontSize = 9.sp, color = AppColors.TextTertiary) }
        }
    }
}

// ─── Search Result Card ─────────────────────────────────

@Composable
fun SearchResultCard(result: SearchResult, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(AppColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.member.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("NIK: ${result.member.nik ?: "-"}", fontSize = 11.sp, color = AppColors.TextTertiary)
                Text("KK: ${result.familyHeadName}", fontSize = 11.sp, color = AppColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.Secondary, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Animation Helpers ──────────────────────────────────

@Composable
fun animateCountUp(target: Int, durationMs: Int = 700): Int {
    var animatedValue by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        if (target == 0) { animatedValue = 0; return@LaunchedEffect }
        val startTime = withFrameNanos { it }
        val durationNanos = durationMs * 1_000_000L
        do {
            val elapsed = withFrameNanos { it } - startTime
            val progress = (elapsed.toFloat() / durationNanos).coerceIn(0f, 1f)
            // ease-out cubic
            val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
            animatedValue = (target * eased).toInt()
        } while (progress < 1f)
        animatedValue = target
    }
    return animatedValue
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val shimmerTranslate = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by shimmerTranslate.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1200, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            AppColors.CardBorder.copy(alpha = 0.3f),
            AppColors.CardBorder.copy(alpha = 0.6f),
            AppColors.CardBorder.copy(alpha = 0.3f)
        ),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim + 200f, 0f)
    )
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
    )
}
