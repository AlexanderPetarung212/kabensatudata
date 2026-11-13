package com.example.kabensatudata.ui.screen.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush

private val MONTH_LABELS = listOf(
    "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
    "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
)
private val MONTH_NAMES = listOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KelolaBulanScreen(
    initialYear: Int,
    initialMonth: Int,
    onBack: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedMonth by remember { mutableIntStateOf(initialMonth.coerceIn(1, 12)) }

    val activeActivities = remember(uiState.calendarData, selectedMonth) {
        (uiState.calendarData?.activities ?: emptyList()).filter { activity ->
            activity.months[selectedMonth.toString()]?.has_activity == true
        }
    }

    val unfilledCount = remember(activeActivities, selectedMonth, uiState.poaHasPesertaMap) {
        activeActivities.count { activity ->
            // Gunakan poaHasPesertaMap (dari PoaDetail) sebagai sumber utama
            val fromMap = uiState.poaHasPesertaMap[activity.id]
            val monthData = activity.months[selectedMonth.toString()]
            val hasPeserta = fromMap
                ?: (monthData?.participant_ids?.isNotEmpty() == true)
                ?: (monthData?.has_peserta == true)
            !hasPeserta
        }
    }

    // Trigger preload PoaDetail setiap kali bulan berubah atau data kalender berubah
    LaunchedEffect(selectedMonth, uiState.calendarData) {
        if (uiState.calendarData != null) {
            viewModel.preloadPoaDetailsForMonth(
                uiState.calendarData!!.activities, selectedMonth
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadEmployees()
        viewModel.loadCalendar(forceRefresh = true)
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Kelola ${MONTH_NAMES.getOrNull(selectedMonth - 1) ?: ""} ${uiState.selectedYear}",
                subtitle = "${activeActivities.size} kegiatan aktif" +
                    if (unfilledCount > 0) " • $unfilledCount belum lengkap" else "",
                onBack = onBack
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ─── Month Picker ──────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedMonth - 1,
                containerColor = AppColors.Surface,
                contentColor = AppColors.Accent,
                edgePadding = 8.dp
            ) {
                MONTH_LABELS.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedMonth == idx + 1,
                        onClick = { selectedMonth = idx + 1 },
                        text = {
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = if (selectedMonth == idx + 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ─── Summary Bar ───────────────────────────────────────────────────
            if (unfilledCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFEF3C7)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$unfilledCount kegiatan belum ada peserta",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ─── Activity List ─────────────────────────────────────────────────
            if (uiState.isLoading && uiState.calendarData == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(32.dp))
                }
            } else if (activeActivities.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.EventBusy, null, tint = AppColors.TextTertiary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tidak ada kegiatan di ${MONTH_NAMES.getOrNull(selectedMonth - 1) ?: "bulan ini"}",
                            fontSize = 14.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeActivities, key = { it.id }) { activity ->
                        ActivityKelolaBulanCard(
                            activity = activity,
                            month = selectedMonth,
                            // Gunakan poaHasPesertaMap sebagai override utama untuk hasPeserta
                            hasPesertaOverride = uiState.poaHasPesertaMap[activity.id],
                            onEdit = { viewModel.openMonthMetaEdit(activity.id, selectedMonth) }
                        )
                    }
                }
            }
        }

        // ─── PoaMonthMeta Edit Sheet ───────────────────────────────────────────
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
                title = { Text("Memuat data kegiatan...") },
                text = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(32.dp))
                    }
                },
                confirmButton = {}
            )
        }
    }
}

// ─── Activity Card ─────────────────────────────────────────────────────────────

@Composable
private fun ActivityKelolaBulanCard(
    activity: CalendarActivity,
    month: Int,
    hasPesertaOverride: Boolean? = null, // dari poaHasPesertaMap (PoaDetail.participants)
    onEdit: () -> Unit
) {
    val monthData = activity.months[month.toString()]
    val participantIds = monthData?.participant_ids ?: emptyList()
    val participantCount = participantIds.size
    val limit = activity.participant_limit
    val isClaimed = monthData?.claimed == true

    // hasPeserta: gunakan override dari PoaDetail jika tersedia (paling akurat)
    // Fallback: participant_ids dari calendar API, lalu has_peserta boolean
    val hasPeserta = hasPesertaOverride
        ?: (participantIds.isNotEmpty())
        .let { if (it) true else (monthData?.has_peserta == true) }
    val hasNoSurat   = monthData?.has_nomor_surat ?: false
    val hasTanggal   = monthData?.has_tanggal_kegiatan ?: false
    val hasDesa      = monthData?.has_desa ?: false

    // Badge peserta
    val (badgeText, badgeColor, badgeBg) = when {
        limit == 0          -> Triple("Tidak ada limit RAB", AppColors.TextSecondary, AppColors.Secondary.copy(alpha = 0.08f))
        participantCount == 0 -> Triple("0/$limit — Belum diisi", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.08f))
        participantCount < limit -> Triple("$participantCount/$limit — Kurang", Color(0xFFD97706), Color(0xFFFEF3C7))
        participantCount == limit -> Triple("$participantCount/$limit — Sesuai RAB ✓", Color(0xFF16A34A), Color(0xFF22C55E).copy(alpha = 0.08f))
        else -> Triple("$participantCount/$limit — Melebihi!", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.08f))
    }

    val borderColor = when {
        participantCount > limit && limit > 0 -> Color(0xFFEF4444).copy(0.45f)
        participantCount == limit && limit > 0 -> Color(0xFF22C55E).copy(0.38f)
        else -> AppColors.GlossBorder
    }
    val glowColor = when {
        participantCount > limit && limit > 0 -> Color(0xFFEF4444)
        participantCount == limit && limit > 0 -> Color(0xFF22C55E)
        else -> AppColors.Accent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor   = glowColor.copy(0.18f),
                ambientColor = glowColor.copy(0.06f)
            ),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Header: Nama kegiatan + tombol edit / badge klaim ──────────────
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
                    Text(activity.komponen, fontSize = 11.sp, color = AppColors.TextTertiary)
                }

                if (!isClaimed) {
                    FilledTonalButton(
                        onClick = onEdit,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AppColors.Accent.copy(alpha = 0.12f),
                            contentColor = AppColors.Accent
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF22C55E).copy(alpha = 0.1f)) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Klaim ✓", fontSize = 10.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.CardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // ── Status Indicators ✓ / ✗ ───────────────────────────────────────
            StatusIndicatorRow(label = "Nama Pegawai", isDone = hasPeserta)
            Spacer(Modifier.height(4.dp))
            StatusIndicatorRow(label = "Nomor Surat & Tanggal Kegiatan", isDone = hasNoSurat && hasTanggal)
            Spacer(Modifier.height(4.dp))
            StatusIndicatorRow(label = "Desa Tujuan", isDone = hasDesa)

            Spacer(Modifier.height(8.dp))

            // ── Badge Peserta ──────────────────────────────────────────────────
            Surface(shape = RoundedCornerShape(20.dp), color = badgeBg) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👥", fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(badgeText, fontSize = 11.sp, color = badgeColor, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ─── Status Indicator Row ──────────────────────────────────────────────────────

@Composable
private fun StatusIndicatorRow(label: String, isDone: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isDone) Color(0xFF16A34A) else Color(0xFFEF4444),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isDone) AppColors.TextPrimary else AppColors.TextSecondary
        )
    }
}
