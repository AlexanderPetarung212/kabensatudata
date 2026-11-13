package com.example.kabensatudata.ui.screen.spm

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.remote.dto.SpmSubIndicatorDto
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpmIndicatorDetailScreen(
    indicatorIndex: Int,
    onBack: () -> Unit,
    onSubClick: (subId: Long) -> Unit,
    viewModel: SpmViewModel = hiltViewModel()
) {
    val state by viewModel.dashboardState.collectAsState()
    val indicator = state.indicators.getOrNull(indicatorIndex)

    // Calculate stats
    val totalN = indicator?.subs?.sumOf { it.numeratorRiil } ?: 0
    val totalD = indicator?.subs?.sumOf { it.denominatorRiil } ?: 0
    val totalDinkes = indicator?.subs?.sumOf { it.denominatorDinkes ?: 0 } ?: 0
    val totalTarget = indicator?.subs?.sumOf { it.targetAbsolute ?: 0 } ?: 0
    val pctDinkes = if (totalDinkes > 0) (totalN.toFloat() / totalDinkes * 100) else 0f
    val pctRiil = if (totalD > 0) (totalN.toFloat() / totalD * 100) else 0f

    Scaffold(
        topBar = {
            WaveTopBar(
                title = indicator?.name ?: "Detail Indikator",
                subtitle = indicator?.code ?: "SPM",
                onBack = onBack
            )
        }
    ) { padding ->
        if (indicator == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Data tidak ditemukan", color = Color.Gray)
            }
            return@Scaffold
        }

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
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, null, Modifier.size(14.dp), tint = Color(0xFFD97706))
                            Spacer(Modifier.width(6.dp))
                            Text("Offline — menampilkan data dari cache", fontSize = 11.sp, color = Color(0xFFD97706))
                        }
                    }
                }
            }

            // ── Stats Summary Cards ──────────────────────────
            item {
                StatsHeader(
                    totalN = totalN, totalD = totalD,
                    totalDinkes = totalDinkes, totalTarget = totalTarget,
                    pctDinkes = pctDinkes, pctRiil = pctRiil
                )
            }

            // ── Overview Bars ────────────────────────────────
            item {
                OverviewBarsCard(totalN, totalD, totalDinkes, totalTarget)
            }

            // ── Section header ───────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.List, null, Modifier.size(18.dp), tint = Color(0xFF0F766E))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sub-Indikator (${indicator.subs.size})",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B)
                    )
                }
            }

            // ── Sub-Indicator Cards (staggered) ──────────────
            itemsIndexed(indicator.subs, key = { _, sub -> sub.id }) { idx, sub ->
                val visible = remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(idx * 80L)
                    visible.value = true
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(250)) + slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                ) {
                    SubIndicatorCard(sub = sub, onClick = { onSubClick(sub.id) })
                }
            }
        }
    }
}

// ─── Stats Header ────────────────────────────────────────────────────────────

@Composable
private fun StatsHeader(
    totalN: Int, totalD: Int, totalDinkes: Int, totalTarget: Int,
    pctDinkes: Float, pctRiil: Float
) {
    Surface(
        Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp, color = Color.White
    ) {
        Column(Modifier.padding(16.dp)) {
            // Animated big percentage
            val animPct by animateFloatAsState(
                targetValue = pctDinkes,
                animationSpec = tween(800, easing = FastOutSlowInEasing),
                label = "headerPct"
            )
            val pctColor = when {
                pctDinkes >= 80f -> Color(0xFF10B981)
                pctDinkes >= 50f -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format("%.1f", animPct),
                    fontSize = 36.sp, fontWeight = FontWeight.Bold, color = pctColor
                )
                Text("%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = pctColor,
                    modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.width(8.dp))
                Text("Capaian", fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(bottom = 6.dp))
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            val animProgress by animateFloatAsState(
                targetValue = (pctDinkes / 100f).coerceIn(0f, 1f),
                animationSpec = tween(800, easing = FastOutSlowInEasing), label = "headerBar"
            )
            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = pctColor, trackColor = Color(0xFFE5E7EB),
            )

            Spacer(Modifier.height(14.dp))

            // Stats grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("Capaian", "$totalN", Color(0xFF10B981))
                MiniStat("Sasaran Dinkes", "$totalDinkes", Color(0xFF3B82F6))
                MiniStat("Sasaran Riil", "$totalD", Color(0xFFF59E0B))
                if (totalTarget > 0) MiniStat("Target", "$totalTarget", Color(0xFF8B5CF6))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
    }
}

// ─── Overview Bars Card ──────────────────────────────────────────────────────

@Composable
private fun OverviewBarsCard(totalN: Int, totalD: Int, totalDinkes: Int, totalTarget: Int) {
    val maxVal = maxOf(totalD, totalDinkes, totalN, totalTarget, 1)

    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp, color = Color.White
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Perbandingan", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF64748B))
            Spacer(Modifier.height(8.dp))
            AnimatedLabelBar("Capaian (N)", totalN, maxVal, Color(0xFF10B981))
            AnimatedLabelBar("Sasaran Dinkes", totalDinkes, maxVal, Color(0xFF3B82F6))
            AnimatedLabelBar("Sasaran Potensial", totalD, maxVal, Color(0xFFF59E0B))
            if (totalTarget > 0) {
                AnimatedLabelBar("Target Absolut", totalTarget, maxVal, Color(0xFF8B5CF6))
            }
        }
    }
}

@Composable
private fun AnimatedLabelBar(label: String, value: Int, maxVal: Int, color: Color) {
    val targetFraction = if (maxVal > 0) value.toFloat() / maxVal else 0f
    val anim by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing), label = "lb"
    )
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 10.sp, color = Color(0xFF64748B))
            Text("$value", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFF1F5F9))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

// ─── Sub-Indicator Card ──────────────────────────────────────────────────────

@Composable
private fun SubIndicatorCard(sub: SpmSubIndicatorDto, onClick: () -> Unit) {
    val pct = sub.percentageRiil.toFloat()
    val pctColor = when {
        pct >= 80f -> Color(0xFF10B981)
        pct >= 50f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    val animProgress by animateFloatAsState(
        targetValue = (pct / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "subProg"
    )

    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp, color = Color.White
    ) {
        Column(Modifier.padding(14.dp)) {
            // Title + percentage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sub.name, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        color = Color(0xFF1E293B), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(sub.code, fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
                Spacer(Modifier.width(8.dp))
                // Circular percentage
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(pctColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${String.format("%.0f", pct)}%",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = pctColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = pctColor, trackColor = Color(0xFFE5E7EB),
            )

            Spacer(Modifier.height(8.dp))

            // Stats row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip("N", "${sub.numeratorRiil}", Color(0xFF10B981))
                StatChip("D", "${sub.denominatorRiil}", Color(0xFF6366F1))
                if (sub.denominatorDinkes != null) {
                    StatChip("Dinkes", "${sub.denominatorDinkes}", Color(0xFF3B82F6))
                }
                if (sub.targetAbsolute != null) {
                    StatChip("Target", "${sub.targetAbsolute}", Color(0xFF8B5CF6))
                }
                if (sub.gap != null) {
                    val gapColor = if (sub.gap >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                    StatChip("Gap", if (sub.gap >= 0) "+${sub.gap}" else "${sub.gap}", gapColor)
                }
            }

            // Lihat pasien
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lihat pasien", fontSize = 11.sp, color = Color(0xFF0F766E), fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = Color(0xFF0F766E))
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.08f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 9.sp, color = color.copy(alpha = 0.7f))
            Spacer(Modifier.width(3.dp))
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
