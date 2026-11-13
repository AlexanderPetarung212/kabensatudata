package com.example.kabensatudata.ui.screen.data

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.remote.dto.ChartItemDto
import com.example.kabensatudata.ui.screen.pegawai.PegawaiViewModel
import com.example.kabensatudata.ui.theme.AppColors
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import com.example.kabensatudata.data.local.AuthTokenManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

private val chartPalette = listOf(
    Color(0xFF6366F1), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFFEF4444),
    Color(0xFF3B82F6), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFF8B5CF6)
)

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface DataHubAuthEntryPoint {
    fun authTokenManager(): AuthTokenManager
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataHubScreen(
    onNavigateToVillages: () -> Unit,
    onNavigateToPegawai: () -> Unit,
    onNavigateToSuratArsip: () -> Unit,
    onNavigateToSpm: () -> Unit = {},
    onNavigateToNearby: () -> Unit = {},
    onNavigateToDeviceMonitor: () -> Unit = {},
    onNavigateToCms: () -> Unit = {},
    onNavigateToInventaris: () -> Unit = {},
    viewModel: PegawaiViewModel = hiltViewModel()
) {
    val listState by viewModel.listState.collectAsState()
    val context = LocalContext.current
    val isAdmin = remember {
        val ep = EntryPointAccessors.fromApplication(context, DataHubAuthEntryPoint::class.java)
        ep.authTokenManager().getUserRoles().any {
            it.equals("admin", true) || it.equals("super_admin", true) || it.equals("superadmin", true)
        }
    }

    LaunchedEffect(Unit) { viewModel.loadStats() }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Header
            Surface(Modifier.fillMaxWidth(), color = AppColors.PrimaryDark) {
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text("Data", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("Kelola data desa, kepegawaian & SPM", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Nav Cards
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HubNavCard(
                    title = "Daftar Desa", subtitle = "Data wilayah & penduduk",
                    icon = Icons.Default.LocationCity, color = Color(0xFF3B82F6),
                    onClick = onNavigateToVillages, modifier = Modifier.weight(1f)
                )
                HubNavCard(
                    title = "Kepegawaian", subtitle = "Data pegawai & dokumen",
                    icon = Icons.Default.Badge, color = Color(0xFF8B5CF6),
                    onClick = onNavigateToPegawai, modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // SPM + Surat Row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Data SPM Card
                Surface(
                    Modifier.weight(1f).clickable(onClick = onNavigateToSpm),
                    shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.2f))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF0F766E).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Assessment, null, tint = Color(0xFF0F766E), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Data SPM", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Text("Capaian kesehatan", fontSize = 10.sp, color = AppColors.TextTertiary)
                        }
                    }
                }
                // E-Arsip Surat Card
                Surface(
                    Modifier.weight(1f).clickable(onClick = onNavigateToSuratArsip),
                    shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF59E0B).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Folder, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("E-Arsip", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Text("Surat & dokumen", fontSize = 10.sp, color = AppColors.TextTertiary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Inventaris Row
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onNavigateToInventaris),
                shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.2f))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF06B6D4).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Inventory2, null, tint = Color(0xFF06B6D4), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Inventaris", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Text("Kelola barang & ruangan", fontSize = 10.sp, color = AppColors.TextTertiary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Rekam Medis Offline 3 Meja Card
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onNavigateToNearby),
                shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.2f))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF7C3AED).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bluetooth, null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Rekam Medis Offline 3 Meja", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Text("Antrian pasien & transfer data offline", fontSize = 10.sp, color = AppColors.TextTertiary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // CMS Puskesmas Card
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onNavigateToCms),
                shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.2f))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF0EA5E9).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Article, null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CMS Puskesmas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Text("Kelola Berita, Galeri & Pesan", fontSize = 10.sp, color = AppColors.TextTertiary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
                }
            }

            // Stats Summary Cards
            listState.stats?.let { s ->
                Spacer(Modifier.height(16.dp))
                Text("Statistik", Modifier.padding(horizontal = 20.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard("Pegawai", s.totalPegawai, Icons.Default.People, Color(0xFF6366F1), Modifier.weight(1f))
                    StatCard("Dokumen", s.totalDokumen, Icons.Default.Description, Color(0xFF10B981), Modifier.weight(1f))
                    StatCard("Surat", s.totalSuratTugas, Icons.Default.Email, Color(0xFFF59E0B), Modifier.weight(1f))
                }

                // Charts
                s.charts?.let { charts ->
                    Spacer(Modifier.height(20.dp))
                    Text("Grafik Pegawai", Modifier.padding(horizontal = 20.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(12.dp))

                    // Gender Donut Chart
                    charts.gender?.let { data ->
                        if (data.isNotEmpty()) {
                            ChartSection("Jenis Kelamin") {
                                DonutChart(data = data, modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // Education Bar Chart
                    charts.education?.let { data ->
                        if (data.isNotEmpty()) {
                            ChartSection("Pendidikan") {
                                HorizontalBarChart(data = data)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // Profession Bar Chart
                    charts.profession?.let { data ->
                        if (data.isNotEmpty()) {
                            ChartSection("Profesi") {
                                HorizontalBarChart(data = data)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // Golongan Chart
                    charts.golongan?.let { data ->
                        if (data.isNotEmpty()) {
                            ChartSection("Pangkat/Golongan") {
                                HorizontalBarChart(data = data)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ─── Components ──────────────────────────────────────────────────────────────

@Composable
private fun HubNavCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier) {
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
private fun StatCard(label: String, count: Int, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text("$count", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text(label, fontSize = 10.sp, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun ChartSection(title: String, content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize(),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DonutChart(data: List<ChartItemDto>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.value }.toFloat()
    if (total <= 0f) return

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Donut
        Box(
            Modifier.size(120.dp).drawBehind {
                val strokeWidth = 24.dp.toPx()
                var startAngle = -90f
                data.forEachIndexed { idx, item ->
                    val sweep = (item.value / total) * 360f
                    drawArc(
                        color = chartPalette[idx % chartPalette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth)
                    )
                    startAngle += sweep
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Text("${total.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        }

        Spacer(Modifier.width(16.dp))

        // Legend
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            data.forEachIndexed { idx, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(chartPalette[idx % chartPalette.size]))
                    Spacer(Modifier.width(6.dp))
                    Text(item.label, fontSize = 11.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.value}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun HorizontalBarChart(data: List<ChartItemDto>) {
    val maxValue = data.maxOfOrNull { it.value } ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEachIndexed { idx, item ->
            val color = chartPalette[idx % chartPalette.size]
            val fraction = item.value.toFloat() / maxValue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.label,
                    fontSize = 11.sp, color = AppColors.TextSecondary,
                    modifier = Modifier.width(90.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Box(
                    Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.Background)
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                            .clip(RoundedCornerShape(4.dp)).background(color)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${item.value}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            }
        }
    }
}
