package com.example.kabensatudata.ui.screen.medical

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.remote.dto.DailyVisitDto
import com.example.kabensatudata.data.remote.dto.DiseaseCountDto
import com.example.kabensatudata.data.remote.dto.MedicineUsageCountDto
import com.example.kabensatudata.ui.theme.AppColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalHubScreen(
    onNavigateToRecords: () -> Unit,
    onNavigateToMedicines: () -> Unit,
    isAdminOrNakes: Boolean = false,
    viewModel: MedicineViewModel = hiltViewModel()
) {
    val state by viewModel.dashboardState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadDashboard() }

    // Date range picker state
    var showDatePicker by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf("") }
    var customTo by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Header ──
            item {
                Surface(Modifier.fillMaxWidth(), color = AppColors.PrimaryDark) {
                    Column(
                        Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text("Medis", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Dashboard kesehatan & manajemen obat",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // ── Date Filter ──
            item {
                DateFilterSection(
                    currentLabel = state.periodLabel,
                    onPreset = { from, to, label -> viewModel.setDateRange(from, to, label) },
                    onCustom = { showDatePicker = true }
                )
            }

            // ── Summary Cards ──
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryCard("Kunjungan", state.totalVisits, Icons.Default.Assignment, Color(0xFF3B82F6), Modifier.weight(1f))
                    SummaryCard("Pasien", state.totalPatients, Icons.Default.Person, Color(0xFF10B981), Modifier.weight(1f))
                }
            }

            // ── Navigation Cards ──
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NavCard(
                        title = "Rekam Medis",
                        subtitle = "Riwayat kunjungan pasien",
                        icon = Icons.Default.Description,
                        color = Color(0xFF6366F1),
                        onClick = onNavigateToRecords,
                        modifier = Modifier.weight(1f)
                    )
                    if (isAdminOrNakes) {
                        NavCard(
                            title = "Manajemen Obat",
                            subtitle = "Stok & data obat",
                            icon = Icons.Default.Medication,
                            color = Color(0xFFF59E0B),
                            onClick = onNavigateToMedicines,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Loading ──
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                    }
                }
            }

            // ── Error ──
            if (state.error != null) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp), color = Color(0xFFFEE2E2)
                    ) {
                        Text(state.error!!, Modifier.padding(12.dp), fontSize = 12.sp, color = Color(0xFFDC2626))
                    }
                }
            }

            // ── Top 10 Diseases ──
            if (state.topDiseases.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { ChartSection("📊 10 Penyakit Terbesar", state.periodLabel) }
                item { DiseaseBarChart(state.topDiseases) }
            }

            // ── Top 10 Medicines ──
            if (state.topMedicines.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { ChartSection("💊 10 Obat Terbanyak Dipakai", state.periodLabel) }
                item { MedicineBarChart(state.topMedicines) }
            }

            // ── Daily Visits Chart ──
            if (state.dailyVisits.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { ChartSection("📈 Kunjungan Pasien", "Per hari — ${state.periodLabel}") }
                item { VisitLineChart(state.dailyVisits) }
            }

            // ── Empty State ──
            if (!state.isLoading && state.topDiseases.isEmpty() && state.topMedicines.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BarChart, null, tint = AppColors.TextTertiary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Belum ada data untuk periode ini", fontSize = 14.sp, color = AppColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }

    // ── Custom Date Picker Dialog ──
    if (showDatePicker) {
        CustomDateRangeDialog(
            initialFrom = customFrom,
            initialTo = customTo,
            onDismiss = { showDatePicker = false },
            onConfirm = { from, to ->
                customFrom = from
                customTo = to
                val label = "${formatShortDate(from)} — ${formatShortDate(to)}"
                viewModel.setDateRange(from, to, label)
                showDatePicker = false
            }
        )
    }
}

// ─── Date Filter Section ────────────────────────────────

@Composable
private fun DateFilterSection(
    currentLabel: String,
    onPreset: (String?, String?, String) -> Unit,
    onCustom: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val thisMonth = remember { YearMonth.now() }
    val lastMonth = remember { thisMonth.minusMonths(1) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp), tint = AppColors.Accent)
            Spacer(Modifier.width(6.dp))
            Text("Periode: ", fontSize = 12.sp, color = AppColors.TextSecondary)
            Text(currentLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 30 hari
            FilterChip(
                selected = currentLabel == "30 hari terakhir",
                onClick = { onPreset(null, null, "30 hari terakhir") },
                label = { Text("30 Hari", fontSize = 11.sp) },
                modifier = Modifier.height(30.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Accent, selectedLabelColor = Color.White
                )
            )
            // 7 hari
            FilterChip(
                selected = currentLabel == "7 hari terakhir",
                onClick = {
                    val from = today.minusDays(7).toString()
                    val to = today.toString()
                    onPreset(from, to, "7 hari terakhir")
                },
                label = { Text("7 Hari", fontSize = 11.sp) },
                modifier = Modifier.height(30.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Accent, selectedLabelColor = Color.White
                )
            )
            // Bulan ini
            val thisMonthLabel = "${thisMonth.month.getDisplayName(JavaTextStyle.FULL, Locale("id"))} ${thisMonth.year}"
            FilterChip(
                selected = currentLabel == thisMonthLabel,
                onClick = {
                    val from = thisMonth.atDay(1).toString()
                    val to = thisMonth.atEndOfMonth().toString()
                    onPreset(from, to, thisMonthLabel)
                },
                label = { Text("Bulan Ini", fontSize = 11.sp) },
                modifier = Modifier.height(30.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Accent, selectedLabelColor = Color.White
                )
            )
            // Bulan lalu
            val lastMonthLabel = "${lastMonth.month.getDisplayName(JavaTextStyle.FULL, Locale("id"))} ${lastMonth.year}"
            FilterChip(
                selected = currentLabel == lastMonthLabel,
                onClick = {
                    val from = lastMonth.atDay(1).toString()
                    val to = lastMonth.atEndOfMonth().toString()
                    onPreset(from, to, lastMonthLabel)
                },
                label = { Text("Bulan Lalu", fontSize = 11.sp) },
                modifier = Modifier.height(30.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Accent, selectedLabelColor = Color.White
                )
            )
            // Custom
            FilterChip(
                selected = currentLabel.contains("—"),
                onClick = onCustom,
                label = { Text("Kustom", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(14.dp)) },
                modifier = Modifier.height(30.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF8B5CF6), selectedLabelColor = Color.White
                )
            )
        }
    }
}

// ─── Custom Date Range Dialog ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateRangeDialog(
    initialFrom: String,
    initialTo: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var fromDate by remember { mutableStateOf(initialFrom.ifBlank { LocalDate.now().minusDays(7).toString() }) }
    var toDate by remember { mutableStateOf(initialTo.ifBlank { LocalDate.now().toString() }) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Rentang Tanggal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // From
                OutlinedCard(
                    Modifier.fillMaxWidth().clickable { showFromPicker = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp), tint = AppColors.Accent)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Dari", fontSize = 11.sp, color = AppColors.TextTertiary)
                            Text(formatShortDate(fromDate), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        }
                    }
                }
                // To
                OutlinedCard(
                    Modifier.fillMaxWidth().clickable { showToPicker = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, null, Modifier.size(18.dp), tint = AppColors.Accent)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Sampai", fontSize = 11.sp, color = AppColors.TextTertiary)
                            Text(formatShortDate(toDate), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fromDate, toDate) },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )

    // Material3 DatePicker for "from"
    if (showFromPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                LocalDate.parse(fromDate).toEpochDay() * 86400000L
            } catch (_: Exception) { System.currentTimeMillis() }
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        fromDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                    }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Batal") } }
        ) { DatePicker(state = datePickerState) }
    }

    // Material3 DatePicker for "to"
    if (showToPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                LocalDate.parse(toDate).toEpochDay() * 86400000L
            } catch (_: Exception) { System.currentTimeMillis() }
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        toDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                    }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Batal") } }
        ) { DatePicker(state = datePickerState) }
    }
}

// ─── Components ──────────────────────────────────────────

@Composable
private fun SummaryCard(label: String, count: Int, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("$count", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text(label, fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun NavCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface, shadowElevation = 2.dp,
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = AppColors.TextTertiary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ChartSection(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Text(subtitle, fontSize = 11.sp, color = AppColors.TextTertiary)
    }
}

// ─── Interactive Disease Bar Chart ──────────────────────

@Composable
private fun DiseaseBarChart(data: List<DiseaseCountDto>) {
    val maxVal = data.maxOfOrNull { it.count } ?: 1
    var selectedIdx by remember { mutableIntStateOf(-1) }
    val colors = listOf(
        Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B), Color(0xFFEAB308),
        Color(0xFF84CC16), Color(0xFF22C55E), Color(0xFF14B8A6), Color(0xFF06B6D4),
        Color(0xFF3B82F6), Color(0xFF8B5CF6)
    )

    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            data.forEachIndexed { idx, item ->
                val fraction = item.count.toFloat() / maxVal
                val barColor = colors[idx % colors.size]
                val isSelected = selectedIdx == idx

                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) barColor.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { selectedIdx = if (isSelected) -1 else idx }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            item.name, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${item.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = barColor)
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF1F5F9))) {
                        Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                    }
                    if (isSelected) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${item.name}: ${item.count} kasus",
                            fontSize = 11.sp, color = barColor, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (idx < data.lastIndex) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

// ─── Interactive Medicine Usage Bar Chart ────────────────

@Composable
private fun MedicineBarChart(data: List<MedicineUsageCountDto>) {
    val maxVal = data.maxOfOrNull { it.totalUsed } ?: 1
    var selectedIdx by remember { mutableIntStateOf(-1) }
    val colors = listOf(
        Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFD946EF),
        Color(0xFFF43F5E), Color(0xFFF97316), Color(0xFFF59E0B), Color(0xFF22C55E),
        Color(0xFF14B8A6), Color(0xFF6366F1)
    )

    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            data.forEachIndexed { idx, item ->
                val fraction = item.totalUsed.toFloat() / maxVal
                val barColor = colors[idx % colors.size]
                val isSelected = selectedIdx == idx

                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) barColor.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { selectedIdx = if (isSelected) -1 else idx }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            item.name, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${item.totalUsed} ${item.unit}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = barColor)
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF1F5F9))) {
                        Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                    }
                    if (isSelected) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${item.name}: ${item.totalUsed} ${item.unit} digunakan",
                            fontSize = 11.sp, color = barColor, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (idx < data.lastIndex) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

// ─── Interactive Daily Visit Chart ──────────────────────

@Composable
private fun VisitLineChart(data: List<DailyVisitDto>) {
    val maxVal = (data.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    var selectedIdx by remember { mutableIntStateOf(-1) }
    val barColor = Color(0xFF6366F1)

    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            // Chart area
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEachIndexed { idx, item ->
                    val fraction = item.count.toFloat() / maxVal
                    val isSelected = selectedIdx == idx

                    Column(
                        Modifier
                            .width(24.dp)
                            .fillMaxHeight()
                            .clickable { selectedIdx = if (isSelected) -1 else idx },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (isSelected || item.count > 0) {
                            Text(
                                "${item.count}", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                color = if (isSelected) barColor else AppColors.TextTertiary
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(
                                    if (isSelected) barColor
                                    else barColor.copy(alpha = 0.5f + fraction * 0.5f)
                                )
                        )
                    }
                }
            }

            // Selected detail
            if (selectedIdx in data.indices) {
                Spacer(Modifier.height(8.dp))
                val sel = data[selectedIdx]
                Surface(shape = RoundedCornerShape(8.dp), color = barColor.copy(alpha = 0.1f)) {
                    Text(
                        "📅 ${formatDateLabel(sel.date)}: ${sel.count} kunjungan",
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = barColor
                    )
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────

private val BULAN_SHORT = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
    "Jul", "Agt", "Sep", "Okt", "Nov", "Des"
)

private val BULAN_FULL = arrayOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

private fun formatDateLabel(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        val day = parts[2].toInt()
        val month = parts[1].toInt()
        "$day ${BULAN_SHORT[month - 1]}"
    } catch (_: Exception) { dateStr }
}

private fun formatShortDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        val day = parts[2].toInt()
        val month = parts[1].toInt()
        val year = parts[0].toInt()
        "$day ${BULAN_FULL[month - 1]} $year"
    } catch (_: Exception) { dateStr }
}
