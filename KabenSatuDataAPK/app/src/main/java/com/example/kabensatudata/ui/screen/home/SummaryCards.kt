package com.example.kabensatudata.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.theme.AppColors

// ─── Summary Card (Tappable with Bottom Sheet) ──────────

@Composable
fun SummaryCardRow(
    kesehatanCount: Int,
    sanitasiCount: Int,
    chartDataAvailable: Boolean,
    onKesehatanClick: () -> Unit,
    onSanitasiClick: () -> Unit,
    onGrafikClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Ringkasan Data",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // Row 1: Kesehatan + Sanitasi — dengan animated count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard(
                title = "Kasus Kesehatan",
                count = kesehatanCount,
                subtitle = "kasus terdeteksi",
                icon = Icons.Default.Favorite,
                iconColor = AppColors.ServiceCoral,
                onClick = onKesehatanClick,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Air & Sanitasi",
                count = sanitasiCount,
                subtitle = "data",
                icon = Icons.Default.WaterDrop,
                iconColor = AppColors.ServiceBlue,
                onClick = onSanitasiClick,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Grafik Data (full width)
        SummaryCard(
            title = "Grafik Data",
            count = null,
            subtitle = "Jenis kelamin, usia, pendidikan, kesehatan & kunjungan",
            icon = Icons.Default.BarChart,
            iconColor = AppColors.ServicePurple,
            onClick = onGrafikClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    count: Int?,       // null = tidak pakai count-up (misal Grafik)
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // G7: Animated count-up
    val animatedCount by animateIntAsState(
        targetValue = count ?: 0,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "countUp"
    )

    // G4: Glossy card dengan shadow berwarna
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = iconColor.copy(alpha = AppColors.GlowAlpha),
                ambientColor = iconColor.copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.GlossBorder),
        shadowElevation = 0.dp  // handled by Modifier.shadow untuk spotColor
    ) {
        Column {
            // Glossy stripe di atas card (specular)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(0.70f),
                                iconColor.copy(0.55f),
                                Color.White.copy(0.55f),
                                iconColor.copy(0.20f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular gradient icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(iconColor.copy(alpha = 0.18f), iconColor.copy(alpha = 0.04f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary
                    )
                    if (count != null) {
                        // Count-up animasi
                        Text(
                            "$animatedCount $subtitle",
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary,
                            maxLines = 2
                        )
                    } else {
                        Text(
                            subtitle,
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary,
                            maxLines = 2
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight, null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Bottom Sheet for Kasus Kesehatan ───────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KesehatanBottomSheet(
    onDismiss: () -> Unit,
    tbcCount: Int,
    hypertensionCount: Int,
    healthData: List<ChartItem>
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppColors.Secondary.copy(alpha = 0.4f))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Kasus Kesehatan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HealthCaseCard("Pernah TBC", tbcCount, Icons.Default.Warning, Color(0xFFEF4444), Modifier.weight(1f))
                HealthCaseCard("Hipertensi", hypertensionCount, Icons.Default.Bolt, Color(0xFFF59E0B), Modifier.weight(1f))
            }

            if (healthData.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HomeCard(Modifier.fillMaxWidth()) {
                    Text("Detail Masalah Kesehatan", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    HorizontalBarChart(healthData)
                }
            }
        }
    }
}

// ─── Bottom Sheet for Air & Sanitasi ────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanitasiBottomSheet(
    onDismiss: () -> Unit,
    sanitasiStats: List<SanitasiStat>
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppColors.Secondary.copy(alpha = 0.4f))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Air Bersih & Sanitasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sanitasiStats.size >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sanitasiStats.take(2).forEach { stat ->
                        SanitasiCard(stat, Modifier.weight(1f))
                    }
                }
            }
            if (sanitasiStats.size > 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sanitasiStats.drop(2).forEach { stat ->
                        SanitasiCard(stat, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─── Bottom Sheet for Grafik Data ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrafikBottomSheet(
    onDismiss: () -> Unit,
    genderData: List<ChartItem>,
    ageData: List<ChartItem>,
    educationData: List<ChartItem>,
    healthData: List<ChartItem>,
    visitCounts: List<Pair<String, Int>>,
    totalVisitsThisWeek: Int
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppColors.Secondary.copy(alpha = 0.4f))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Grafik Data",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gender Chart
            HomeCard(Modifier.fillMaxWidth()) {
                Text("Jenis Kelamin", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                Spacer(Modifier.height(12.dp))
                HorizontalBarChart(genderData)
            }

            // Age Chart
            HomeCard(Modifier.fillMaxWidth()) {
                Text("Distribusi Usia", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                Spacer(Modifier.height(12.dp))
                HorizontalBarChart(ageData)
            }

            // Education Chart
            HomeCard(Modifier.fillMaxWidth()) {
                Text("Tingkat Pendidikan", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                Spacer(Modifier.height(12.dp))
                if (educationData.isEmpty()) {
                    Text("Belum ada data", fontSize = 13.sp, color = AppColors.TextTertiary)
                } else {
                    HorizontalBarChart(educationData)
                }
            }

            // Health Issues Chart
            HomeCard(Modifier.fillMaxWidth()) {
                Text("Masalah Kesehatan", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                Spacer(Modifier.height(12.dp))
                HorizontalBarChart(healthData)
            }

            // Visit Line Chart
            HomeCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kunjungan Pasien", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                    Text("7 hari terakhir", fontSize = 11.sp, color = AppColors.TextTertiary)
                }
                Spacer(Modifier.height(4.dp))
                Text("Total $totalVisitsThisWeek kunjungan", fontSize = 12.sp, color = AppColors.Accent)
                Spacer(Modifier.height(12.dp))
                VisitLineChart(data = visitCounts)
            }
        }
    }
}
