package com.example.kabensatudata.ui.screen.spm

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.remote.dto.SpmPatientRow
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpmSubDetailScreen(
    subId: Long,
    year: Int,
    month: Int? = null,
    villageId: Long? = null,
    onBack: () -> Unit,
    viewModel: SpmViewModel = hiltViewModel()
) {
    val state by viewModel.detailState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(subId) { viewModel.loadSubDetail(subId, year, month, villageId) }

    val canExport = !state.isLoading && state.error == null &&
        (state.rows.isNotEmpty() || state.potential.isNotEmpty())

    Scaffold(
        topBar = {
            WaveTopBar(
                title = state.subIndicator?.name ?: "Detail Sub-Indikator",
                subtitle = state.subIndicator?.code ?: "SPM Puskesmas",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.exportSubDetail(context) },
                        enabled = canExport && !state.isExporting
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "Export Excel",
                                tint = if (canExport) Color.White
                                       else Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background)) {
            // Offline indicator
            if (state.isOffline) {
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

            // Stats Header glossy
            if (!state.isLoading && state.error == null) {
                val pct = if (state.potentialCount > 0)
                    (state.achievedCount.toFloat() / state.potentialCount * 100) else 0f
                val pctColor = when {
                    pct >= 80f -> Color(0xFF10B981)
                    pct >= 50f -> Color(0xFFF59E0B)
                    else       -> Color(0xFFEF4444)
                }
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor   = AppColors.Accent.copy(0.22f),
                            ambientColor = AppColors.Accent.copy(0.08f)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppColors.GlossBorder),
                    color = AppColors.Surface,
                    shadowElevation = 0.dp
                ) {
                    Column {
                        Box(
                            Modifier.fillMaxWidth().height(3.dp).background(
                                Brush.horizontalGradient(listOf(
                                    Color.White.copy(0.65f),
                                    AppColors.Accent.copy(0.50f),
                                    pctColor.copy(0.55f),
                                    Color.White.copy(0.40f)
                                ))
                            )
                        )
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatBox("Tercatat", state.achievedCount, Color(0xFF10B981), Modifier.weight(1f))
                            StatBox("Sasaran", state.potentialCount, Color(0xFF6366F1), Modifier.weight(1f))
                            StatBox("Capaian", null, pctColor, Modifier.weight(1f), "${String.format("%.1f", pct)}%")
                        }
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFF0F766E)
            ) {
                Tab(state.selectedTab == 0, onClick = { viewModel.setDetailTab(0) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF10B981))
                        Spacer(Modifier.width(4.dp))
                        Text("Tercatat (${state.achievedCount})", fontSize = 13.sp)
                    }
                }
                Tab(state.selectedTab == 1, onClick = { viewModel.setDetailTab(1) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, null, Modifier.size(16.dp), tint = Color(0xFF6366F1))
                        Spacer(Modifier.width(4.dp))
                        Text("Sasaran (${state.potentialCount})", fontSize = 13.sp)
                    }
                }
            }

            // Loading
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF0F766E))
                }
                return@Column
            }

            // Error
            if (state.error != null) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(48.dp), tint = Color(0xFFEF4444))
                        Spacer(Modifier.height(8.dp))
                        Text(state.error ?: "", fontSize = 13.sp, color = Color.Gray)
                    }
                }
                return@Column
            }

            // Patient List
            val items = if (state.selectedTab == 0) state.rows else state.potential
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, null, Modifier.size(48.dp), tint = Color(0xFFCBD5E1))
                        Spacer(Modifier.height(8.dp))
                        Text("Tidak ada data", fontSize = 14.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(items, key = { it.id }) { patient ->
                        PatientCard(patient, showStatus = state.selectedTab == 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int?, color: Color, modifier: Modifier = Modifier, valueStr: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valueStr ?: "$value", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun PatientCard(patient: SpmPatientRow, showStatus: Boolean) {
    val genderColor = if (patient.gender == "Laki-laki") Color(0xFF3B82F6) else Color(0xFFEC4899)
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor   = genderColor.copy(0.15f),
                ambientColor = genderColor.copy(0.05f)
            ),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.GlossBorder),
        shadowElevation = 0.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(
                    if (patient.gender == "Laki-laki") Color(0xFF3B82F6).copy(alpha = 0.15f) else Color(0xFFEC4899).copy(alpha = 0.15f)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (patient.gender == "Laki-laki") Icons.Default.Male else Icons.Default.Female,
                    null, Modifier.size(18.dp),
                    tint = if (patient.gender == "Laki-laki") Color(0xFF3B82F6) else Color(0xFFEC4899)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(patient.name ?: "-", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (patient.age != null) Text("${patient.age} thn", fontSize = 10.sp, color = Color.Gray)
                    if (patient.village != null) Text(patient.village, fontSize = 10.sp, color = Color(0xFF6366F1))
                    if (patient.lastVisit != null) Text(patient.lastVisit, fontSize = 10.sp, color = Color(0xFF10B981))
                }
            }
            if (showStatus && patient.status != null) {
                val isTercatat = patient.status == "Tercatat"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isTercatat) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                ) {
                    Text(
                        patient.status, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = if (isTercatat) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}
