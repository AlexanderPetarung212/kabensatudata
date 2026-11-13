package com.example.kabensatudata.ui.screen.inventaris

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.kabensatudata.data.remote.dto.RuanganDto
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarisListScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToForm: () -> Unit,
    onNavigateToQrScan: () -> Unit,
    onBack: () -> Unit,
    viewModel: InventarisViewModel = hiltViewModel()
) {
    val state by viewModel.listState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadRuanganList() }

    val filteredList = remember(state.list, state.searchQuery) {
        if (state.searchQuery.isBlank()) state.list
        else state.list.filter {
            it.nama.contains(state.searchQuery, true) ||
            (it.kode?.contains(state.searchQuery, true) == true) ||
            (it.lantai?.contains(state.searchQuery, true) == true)
        }
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Inventaris",
                subtitle = "Kelola barang & ruangan",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onNavigateToQrScan) {
                        Icon(Icons.Filled.QrCodeScanner, "Scan QR", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.resetRuanganForm(); onNavigateToForm() },
                containerColor = Color(0xFF06B6D4),
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 60.dp)
            ) { Icon(Icons.Filled.Add, "Tambah Ruangan") }
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
        ) {
            // Offline banner
            if (state.isOffline) {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = Color(0xFFFFA726).copy(alpha = 0.12f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.WifiOff, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mode offline — data dari cache", fontSize = 12.sp, color = Color(0xFFB45309))
                    }
                }
            }

            if (state.isLoading && state.list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            } else if (state.error != null && state.list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AppColors.TextTertiary, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(state.error ?: "", color = AppColors.TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadRuanganList() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) { Text("Coba Lagi") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Stats row (span 2)
                    item(span = { GridItemSpan(2) }) {
                        StatsRow(state.stats)
                    }

                    // Search
                    item(span = { GridItemSpan(2) }) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Cari ruangan...", fontSize = 13.sp, color = AppColors.TextTertiary) },
                            leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = AppColors.TextTertiary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = AppColors.CardBorder,
                                focusedBorderColor = AppColors.Primary,
                                unfocusedContainerColor = AppColors.Surface,
                                focusedContainerColor = AppColors.Surface,
                                unfocusedTextColor = AppColors.TextPrimary,
                                focusedTextColor = AppColors.TextPrimary
                            )
                        )
                    }

                    if (filteredList.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.MeetingRoom, null, tint = AppColors.TextTertiary, modifier = Modifier.size(56.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Belum ada ruangan", color = AppColors.TextSecondary, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        items(filteredList) { ruangan ->
                            RuanganCard(ruangan) { onNavigateToDetail(ruangan.id) }
                        }
                    }

                    // Bottom spacing for FAB
                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(stats: com.example.kabensatudata.data.remote.dto.InventarisStatsDto?) {
    if (stats == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatMini("Ruangan", "${stats.totalRuangan}", Color(0xFF3B82F6), Modifier.weight(1f))
        StatMini("Unit", "${stats.totalUnit}", Color(0xFF8B5CF6), Modifier.weight(1f))
        StatMini("Baik", "${stats.kondisi?.baik ?: 0}", Color(0xFF10B981), Modifier.weight(1f))
        StatMini("Rusak", "${(stats.kondisi?.rusakRingan ?: 0) + (stats.kondisi?.rusakBerat ?: 0)}", Color(0xFFEF4444), Modifier.weight(1f))
    }
}

@Composable
private fun StatMini(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier.clip(RoundedCornerShape(10.dp)),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 9.sp, color = color.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RuanganCard(ruangan: RuanganDto, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column {
            // Photo
            Box(
                Modifier.fillMaxWidth().height(100.dp)
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                if (ruangan.fotoUrl != null) {
                    AsyncImage(
                        model = ruangan.fotoUrl,
                        contentDescription = ruangan.nama,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(Color(0xFF06B6D4).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MeetingRoom, null, tint = Color(0xFF06B6D4), modifier = Modifier.size(22.dp))
                    }
                }
            }

            Column(Modifier.padding(10.dp)) {
                Text(
                    ruangan.nama,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ruangan.kode != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF06B6D4).copy(alpha = 0.10f)
                        ) {
                            Text(ruangan.kode, fontSize = 9.sp, color = Color(0xFF0891B2),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (ruangan.lantai != null) {
                        Text("Lt ${ruangan.lantai}", fontSize = 9.sp, color = AppColors.TextTertiary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("${ruangan.totalUnit ?: 0}", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, fontSize = 12.sp)
                    Text(" unit", color = AppColors.TextTertiary, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("${ruangan.totalBarang ?: 0}", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, fontSize = 12.sp)
                    Text(" jenis", color = AppColors.TextTertiary, fontSize = 11.sp)
                }
            }
        }
    }
}
