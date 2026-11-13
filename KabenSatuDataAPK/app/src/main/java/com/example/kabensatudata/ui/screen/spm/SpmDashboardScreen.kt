package com.example.kabensatudata.ui.screen.spm

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.kabensatudata.ui.components.StaggeredItem
import com.example.kabensatudata.ui.theme.WaveTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpmDashboardScreen(
    onBack: () -> Unit,
    onSubClick: (subId: Long, year: Int, month: Int?, villageId: Long?) -> Unit,
    onIndicatorClick: (indicatorIndex: Int, year: Int, month: Int?, villageId: Long?) -> Unit = { _, _, _, _ -> },
    viewModel: SpmViewModel = hiltViewModel()
) {
    val state by viewModel.dashboardState.collectAsState()

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Data SPM",
                subtitle = "Standar Pelayanan Minimal",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF0FDFA)),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Offline Indicator ────────────────────────────
            if (state.isOffline) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp), color = Color(0xFFFEF3C7)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, null, Modifier.size(16.dp), tint = Color(0xFFD97706))
                            Spacer(Modifier.width(8.dp))
                            Text("Offline — menampilkan data dari cache", fontSize = 12.sp, color = Color(0xFFD97706))
                        }
                    }
                }
            }

            // ── Filter Row ───────────────────────────────────
            item {
                FilterSection(
                    year = state.year,
                    month = state.month,
                    villageId = state.villageId,
                    villages = state.villages.map { it.id to it.name },
                    onYearChange = viewModel::setYear,
                    onMonthChange = viewModel::setMonth,
                    onVillageChange = viewModel::setVillage
                )
            }

            // ── Overview Chart ───────────────────────────────
            if (state.overview != null && state.indicators.isNotEmpty()) {
                item {
                    OverviewChartCard(
                        overview = state.overview!!,
                        indicators = state.indicators,
                        expandedIndex = state.expandedChartIndex,
                        onToggle = viewModel::toggleChartIndicator,
                        onIndicatorDetail = { idx -> onIndicatorClick(idx, state.year, state.month, state.villageId) }
                    )
                }
            }

            // ── Loading ──────────────────────────────────────
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF0F766E))
                    }
                }
            }

            // ── Error ────────────────────────────────────────
            if (state.error != null && !state.isOffline) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(12.dp), color = Color(0xFFFEE2E2)
                    ) {
                        Text(state.error ?: "", Modifier.padding(16.dp), color = Color(0xFFDC2626), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ─── Filter Section ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    year: Int, month: Int?, villageId: Long?,
    villages: List<Pair<Long, String>>,
    onYearChange: (Int) -> Unit, onMonthChange: (Int?) -> Unit, onVillageChange: (Long?) -> Unit
) {
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val years = (currentYear downTo currentYear - 5).toList()
    val months = listOf(null to "Semua Bulan") + (1..12).map { it to java.text.DateFormatSymbols(java.util.Locale("id")).months[it-1].replaceFirstChar { c -> c.uppercase() } }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Filter Data", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF0F766E))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var yearExp by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(yearExp, { yearExp = it }, Modifier.weight(1f)) {
                OutlinedTextField(
                    value = "$year", onValueChange = {}, readOnly = true,
                    label = { Text("Tahun", fontSize = 11.sp) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(yearExp) },
                    singleLine = true
                )
                ExposedDropdownMenu(yearExp, { yearExp = false }) {
                    years.forEach { y -> DropdownMenuItem(text = { Text("$y") }, onClick = { onYearChange(y); yearExp = false }) }
                }
            }
            var monthExp by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(monthExp, { monthExp = it }, Modifier.weight(1.4f)) {
                OutlinedTextField(
                    value = months.find { it.first == month }?.second ?: "Semua",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Bulan", fontSize = 11.sp) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(monthExp) },
                    singleLine = true
                )
                ExposedDropdownMenu(monthExp, { monthExp = false }) {
                    months.forEach { (m, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { onMonthChange(m); monthExp = false }) }
                }
            }
        }
        if (villages.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            var vilExp by remember { mutableStateOf(false) }
            val allVillages = listOf(null to "Semua Desa") + villages
            ExposedDropdownMenuBox(vilExp, { vilExp = it }) {
                OutlinedTextField(
                    value = allVillages.find { it.first == villageId }?.second ?: "Semua Desa",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Desa", fontSize = 11.sp) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(vilExp) },
                    singleLine = true
                )
                ExposedDropdownMenu(vilExp, { vilExp = false }) {
                    allVillages.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onVillageChange(id); vilExp = false }) }
                }
            }
        }
    }
}

// ─── Overview Chart Card ─────────────────────────────────────────────────────

@Composable
private fun OverviewChartCard(
    overview: com.example.kabensatudata.data.remote.dto.SpmOverview,
    indicators: List<com.example.kabensatudata.data.remote.dto.SpmIndicatorDto>,
    expandedIndex: Int?,
    onToggle: (Int) -> Unit,
    onIndicatorDetail: (Int) -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp, color = Color.White
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Ringkasan Capaian SPM", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F766E))
            Spacer(Modifier.height(4.dp))
            Text("Tap indikator untuk lihat detail", fontSize = 10.sp, color = Color(0xFF94A3B8))
            Spacer(Modifier.height(12.dp))

            val maxVal = maxOf(
                overview.denoms.maxOrNull() ?: 1,
                overview.potentials.maxOrNull() ?: 1,
                overview.actuals.maxOrNull() ?: 1,
                1
            )

            overview.labels.forEachIndexed { i, label ->
                val isExpanded = expandedIndex == i
                val denom = overview.denoms.getOrNull(i) ?: 0
                val potential = overview.potentials.getOrNull(i) ?: 0
                val actual = overview.actuals.getOrNull(i) ?: 0

                // Calculate percentage (Capaian / Sasaran Dinkes × 100%)
                val pct = if (denom > 0) (actual.toFloat() / denom * 100) else 0f
                val pctColor = when {
                    pct >= 80f -> Color(0xFF10B981)
                    pct >= 50f -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }

                Surface(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggle(i) },
                    color = if (isExpanded) Color(0xFFF0FDFA) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        // Label row with PERCENTAGE badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label, fontSize = 11.sp,
                                color = if (isExpanded) Color(0xFF0F766E) else Color(0xFF64748B),
                                fontWeight = if (isExpanded) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(4.dp))
                            // Percentage badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = pctColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "${String.format("%.1f", pct)}%",
                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = pctColor
                                )
                            }
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, Modifier.size(16.dp), tint = Color(0xFFCBD5E1)
                            )
                        }

                        // Animated expansion
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeIn(tween(200)),
                            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeOut(tween(150))
                        ) {
                            Column(Modifier.padding(top = 6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LegendDot(Color(0xFF3B82F6), "Sasaran Dinkes")
                                    LegendDot(Color(0xFFF59E0B), "Sasaran Potensial")
                                    LegendDot(Color(0xFF10B981), "Capaian")
                                }
                                Spacer(Modifier.height(6.dp))
                                AnimatedBarRow(value = denom, maxVal = maxVal, color = Color(0xFF3B82F6))
                                AnimatedBarRow(value = potential, maxVal = maxVal, color = Color(0xFFF59E0B))
                                AnimatedBarRow(value = actual, maxVal = maxVal, color = Color(0xFF10B981))

                                // Lihat Lebih Lanjut button
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onIndicatorDetail(i) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F766E).copy(alpha = 0.08f)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Visibility, null, Modifier.size(14.dp), tint = Color(0xFF0F766E))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Lihat Lebih Lanjut", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F766E))
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowForward, null, Modifier.size(14.dp), tint = Color(0xFF0F766E))
                                    }
                                }
                            }
                        }
                    }
                }

                if (i < overview.labels.size - 1 && !isExpanded) {
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp), color = Color(0xFFF0F0F0))
                }
            }
        }
    }
}

@Composable
private fun AnimatedBarRow(value: Int, maxVal: Int, color: Color) {
    val targetFraction = if (maxVal > 0) value.toFloat() / maxVal else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing), label = "barAnim"
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF1F5F9))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animatedFraction).clip(RoundedCornerShape(6.dp)).background(color))
        }
        Spacer(Modifier.width(8.dp))
        Text("$value", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569), modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 9.sp, color = Color.Gray)
    }
}
