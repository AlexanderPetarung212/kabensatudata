package com.example.kabensatudata.ui.screen.calendar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.remote.dto.CalendarActivity
import com.example.kabensatudata.ui.screen.poa.PoaMonthMetaSheet
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import java.util.Calendar

private val TahapGreen = Color(0xFF22C55E)
private val TahapYellow = Color(0xFFEAB308)
private val TahapRed = Color(0xFFEF4444)
private val TahapGreenBg = Color(0xFF166534)
private val TahapYellowBg = Color(0xFF854D0E)
private val TahapRedBg = Color(0xFF991B1B)

private val MonthLabels = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

private fun tahapColor(tahap: Int?): Color = when (tahap) {
    1 -> TahapGreen; 2 -> TahapYellow; 3 -> TahapRed; else -> Color.Gray
}

private fun tahapHeaderColor(monthIndex: Int): Color = when {
    monthIndex < 4 -> TahapGreenBg
    monthIndex < 8 -> TahapYellowBg
    else -> TahapRedBg
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onNavigateKelolaBulan: (year: Int, month: Int) -> Unit = { _, _ -> },
    viewModel: CalendarViewModel = hiltViewModel(),
    deepLinkPoaId: Long = 0L,
    deepLinkMonth: Int = 0
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Auto open detail from deep link
    LaunchedEffect(deepLinkPoaId, deepLinkMonth, uiState.calendarData) {
        if (deepLinkPoaId > 0 && deepLinkMonth > 0 && uiState.calendarData != null && !uiState.showDetail) {
            viewModel.loadDetail(deepLinkPoaId, deepLinkMonth)
        }
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Kalender Kegiatan BOK",
                subtitle = if (uiState.lastSynced != null) {
                    val ago = (System.currentTimeMillis() - uiState.lastSynced!!) / 60000
                    if (ago < 1) "Baru saja disinkronkan" else "Disinkronkan ${ago} menit lalu"
                } else "Program Pengelolaan BOK",
                onBack = onBack,
                actions = {
                    // Year dropdown
                    var expanded by remember { mutableStateOf(false) }
                    val years = uiState.calendarData?.available_years ?: listOf(uiState.selectedYear)
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text("${uiState.selectedYear}", color = Color.White, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            years.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text("$year") },
                                    onClick = { viewModel.setYear(year); expanded = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.loadCalendar(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                }
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Search + filter
            SearchFilterBar(
                searchQuery = uiState.searchQuery,
                onSearchChange = viewModel::setSearchQuery,
                availableKomponen = uiState.calendarData?.available_komponen ?: emptyList(),
                selectedKomponen = uiState.selectedKomponen,
                onKomponenChange = viewModel::setKomponen
            )

            if (uiState.isLoading && uiState.calendarData == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Memuat kalender...", fontSize = 13.sp, color = AppColors.TextTertiary)
                    }
                }
            } else if (uiState.error != null && uiState.calendarData == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.CloudOff, null, tint = AppColors.Error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(uiState.error!!, fontSize = 14.sp, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadCalendar(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Text("Coba Lagi")
                        }
                    }
                }
            } else {
                // Calendar grid
                val activities = uiState.filteredActivities
                val totalCount = uiState.calendarData?.activities?.size ?: 0

                Text(
                    "${activities.size} kegiatan" + if (uiState.searchQuery.isNotBlank() || uiState.selectedKomponen.isNotBlank()) " (difilter dari $totalCount)" else "",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Grid
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(activities, key = { it.id }) { activity ->
                        CalendarActivityCard(
                            activity = activity,
                            onCellClick = { month -> viewModel.loadDetail(activity.id, month) },
                            onInfoClick = { viewModel.openRabDetail(activity.id) }
                        )
                    }

                    if (activities.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, null, tint = AppColors.TextTertiary, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Tidak ada kegiatan yang sesuai filter", fontSize = 13.sp, color = AppColors.TextTertiary)
                                }
                            }
                        }
                    }
                }

                // Legend
                CalendarLegend()
            } // end else

        } // end Column

        // ─── RAB Detail Bottom Sheet ────────────────────────────────────────────
        if (uiState.showRabDetail) {
            RabDetailSheet(
                isLoading = uiState.isRabDetailLoading,
                poaDetail = uiState.rabDetailData,
                onDismiss = viewModel::dismissRabDetail
            )
        }

        // ─── PoaMonthMeta Edit (Admin) ──────────────────────────────────────────
        if (uiState.showMonthMeta && uiState.monthMetaPoaDetail != null) {
            PoaMonthMetaSheet(
                month = uiState.monthMetaMonth,
                detail = uiState.monthMetaPoaDetail!!,
                employees = uiState.employees,
                onDismiss = viewModel::dismissMonthMeta,
                onSave = { req ->
                    viewModel.saveMonthMeta(uiState.monthMetaPoaDetail!!.id, req)
                }
            )
        } else if (uiState.showMonthMeta && uiState.isMonthMetaLoading) {
            AlertDialog(
                onDismissRequest = viewModel::dismissMonthMeta,
                title = { Text("Memuat data...") },
                text = { CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(32.dp)) },
                confirmButton = {}
            )
        }

        // ─── FAB Kelola Bulan (Admin only) ─────────────────────────────────────
        if (uiState.isAdmin) {
            val currentMonth = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 }
            androidx.compose.material3.FloatingActionButton(
                onClick = { onNavigateKelolaBulan(uiState.selectedYear, currentMonth) },
                containerColor = AppColors.Accent,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EditCalendar, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kelola Bulan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        // Detail Bottom Sheet
        if (uiState.showDetail) {
            CalendarDetailSheet(
                isLoading = uiState.isDetailLoading,
                error = uiState.detailError,
                data = uiState.detailData,
                onDismiss = viewModel::dismissDetail,
                claimPrep = uiState.claimPrep,
                isClaimPrepLoading = uiState.isClaimPrepLoading,
                isClaimExecuting = uiState.isClaimExecuting,
                claimError = uiState.claimError,
                claimSuccess = uiState.claimSuccess,
                claimResult = uiState.claimResult,
                onLoadClaimPrep = viewModel::loadClaimPrep,
                onExecuteClaim = viewModel::executeClaim,
                downloadingFile = uiState.downloadingFile,
                downloadError = uiState.downloadError,
                downloadSuccess = uiState.downloadSuccess,
                onDownloadLpj = viewModel::downloadLpj,
                onDownloadTibaBerangkat = viewModel::downloadTibaBerangkat,
                onDownloadItemOpsional = viewModel::downloadItemOpsional,
                onClearDownloadMessages = viewModel::clearDownloadMessages,
                isFileDownloaded = viewModel::isFileDownloaded,
                onOpenFile = viewModel::openDownloadedFile,
                isAdmin = uiState.isAdmin,
                onArturPeserta = { _, _ ->
                    viewModel.openMonthMetaEdit(uiState.detailPoaId, uiState.detailMonth)
                }
            )
        }
    } // end Box
    } // end Scaffold lambda
} // end CalendarScreen


// ─── Search & Filter Bar ────────────────────────────────────────────────────

@Composable
private fun SearchFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    availableKomponen: List<String>,
    selectedKomponen: String,
    onKomponenChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Cari kegiatan...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppColors.Surface,
                unfocusedContainerColor = AppColors.Surface,
                focusedBorderColor = AppColors.Accent,
                unfocusedBorderColor = AppColors.CardBorder
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )

        // Filter chips
        if (availableKomponen.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = selectedKomponen.isBlank(),
                        onClick = { onKomponenChange("") },
                        label = { Text("Semua", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Accent,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
                items(availableKomponen) { komponen ->
                    FilterChip(
                        selected = selectedKomponen == komponen,
                        onClick = { onKomponenChange(if (selectedKomponen == komponen) "" else komponen) },
                        label = { Text(komponen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Accent,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
    }
}

// ─── Activity Card with Month Grid ──────────────────────────────────────────

@Composable
private fun CalendarActivityCard(
    activity: CalendarActivity,
    onCellClick: (Int) -> Unit,
    onInfoClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Activity name row with ℹ️
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        activity.kegiatan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        activity.komponen,
                        fontSize = 11.sp,
                        color = AppColors.TextTertiary
                    )
                }
                // ℹ️ RAB Detail button
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(28.dp).padding(2.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Detail RAB",
                        tint = AppColors.Accent.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // Month grid: 2 rows x 6 columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (m in 1..6) {
                    MonthCell(
                        label = MonthLabels[m - 1],
                        monthData = activity.months[m.toString()],
                        month = m,
                        onCellClick = onCellClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (m in 7..12) {
                    MonthCell(
                        label = MonthLabels[m - 1],
                        monthData = activity.months[m.toString()],
                        month = m,
                        onCellClick = onCellClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    label: String,
    monthData: com.example.kabensatudata.data.remote.dto.CalendarMonth?,
    month: Int,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActivity = monthData?.has_activity == true
    val isClaimed = monthData?.claimed == true
    val color = if (hasActivity) tahapColor(monthData?.tahap) else Color.Gray

    Column(
        modifier = modifier.padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 9.sp, color = AppColors.TextTertiary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isClaimed -> Color(0xFF22C55E).copy(alpha = 0.18f)
                        hasActivity -> color.copy(alpha = 0.12f)
                        else -> AppColors.Secondary.copy(alpha = 0.1f)
                    }
                )
                .then(
                    when {
                        isClaimed -> Modifier.border(2.dp, Color(0xFF22C55E), CircleShape)
                        hasActivity -> Modifier.border(1.5.dp, color.copy(alpha = 0.5f), CircleShape)
                        else -> Modifier
                    }
                )
                .clickable(enabled = hasActivity) { onCellClick(month) },
            contentAlignment = Alignment.Center
        ) {
            when {
                isClaimed -> Text("✅", fontSize = 10.sp)
                hasActivity -> {
                    val text = if ((monthData?.count ?: 0) > 0) "${monthData?.count}x" else "⏳"
                    Text(text, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
                }
                else -> Text("—", fontSize = 8.sp, color = AppColors.TextTertiary)
            }
        }
        if (monthData?.is_mine == true) {
            Text("👤", fontSize = 8.sp)
        }
    }
}

// ─── Legend ──────────────────────────────────────────────────────────────────

@Composable
private fun CalendarLegend() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem(TahapGreen, "Tahap I (Jan-Apr)")
            LegendItem(TahapYellow, "Tahap II (Mei-Agu)")
            LegendItem(TahapRed, "Tahap III (Sep-Des)")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = AppColors.TextSecondary)
    }
}
