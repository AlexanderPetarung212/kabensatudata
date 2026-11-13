package com.example.kabensatudata.ui.screen.inventaris

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.remote.dto.InventarisBarangDto
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarisDetailScreen(
    ruanganId: Long,
    onBack: () -> Unit,
    onNavigateToBarangForm: (Long, Long?) -> Unit,
    onNavigateToEditRuangan: (Long) -> Unit,

    viewModel: InventarisViewModel = hiltViewModel()
) {
    val state by viewModel.detailState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteBarangTarget by remember { mutableStateOf<InventarisBarangDto?>(null) }

    LaunchedEffect(ruanganId) { viewModel.loadDetail(ruanganId) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
            if (it.contains("dihapus") && it.contains("Ruangan")) onBack()
        }
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = state.ruangan?.nama ?: "Detail Ruangan",
                subtitle = state.ruangan?.kode?.let { "Kode: $it" } ?: "",
                onBack = onBack,
                actions = {
                    if (!state.isOffline) {
                        IconButton(onClick = { onNavigateToEditRuangan(ruanganId) }) {
                            Icon(Icons.Filled.Edit, "Edit", tint = Color.White)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, "Hapus", tint = Color(0xFFFFCDD2))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.isOffline && state.ruangan != null) {
                FloatingActionButton(
                    onClick = { viewModel.resetBarangForm(); onNavigateToBarangForm(ruanganId, null) },
                    containerColor = Color(0xFF06B6D4),
                    contentColor = Color.White,
                    modifier = Modifier.padding(bottom = 60.dp)
                ) { Icon(Icons.Filled.Add, "Tambah Barang") }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AppColors.Background
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AppColors.TextTertiary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(state.error!!, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loadDetail(ruanganId) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) { Text("Coba Lagi") }
                }
            }
            state.ruangan != null -> {
                val r = state.ruangan!!
                val barangList = r.barang ?: emptyList()

                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Offline banner
                    if (state.isOffline) {
                        item {
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFA726).copy(alpha = 0.12f)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.WifiOff, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Mode offline — data dari cache", fontSize = 12.sp, color = Color(0xFFB45309))
                                }
                            }
                        }
                    }

                    // Info card
                    item {
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = AppColors.Surface,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, AppColors.CardBorder)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(r.nama, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    if (r.kode != null) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF06B6D4).copy(alpha = 0.10f)) {
                                            Text(r.kode, fontSize = 11.sp, color = Color(0xFF0891B2), fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    if (r.lantai != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Layers, null, tint = AppColors.TextTertiary, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Lantai ${r.lantai}", fontSize = 12.sp, color = AppColors.TextSecondary)
                                        }
                                    }
                                }
                                if (r.deskripsi != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(r.deskripsi, fontSize = 12.sp, color = AppColors.TextSecondary, lineHeight = 18.sp)
                                }
                            }
                        }
                    }

                    // ─── PJ (Penanggung Jawab) Section ───
                    if (r.penanggungJawab != null) {
                        item {
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = AppColors.Surface,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            r.penanggungJawab.nama.take(1),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "PENANGGUNG JAWAB",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981),
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            r.penanggungJawab.nama,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.TextPrimary
                                        )
                                        if (!r.penanggungJawab.jabatan.isNullOrEmpty()) {
                                            Text(
                                                r.penanggungJawab.jabatan,
                                                fontSize = 12.sp,
                                                color = AppColors.TextSecondary
                                            )
                                        }
                                    }
                                    // Verified badge
                                    Icon(
                                        Icons.Filled.Verified,
                                        contentDescription = "Verified",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ─── Pegawai Bertugas (Anggota) ───
                    if (!r.pegawaiBertugas.isNullOrEmpty()) {
                        item {
                            Column {
                                Text(
                                    "Petugas Bertugas",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.TextSecondary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(r.pegawaiBertugas.size) { idx ->
                                        val staff = r.pegawaiBertugas[idx]
                                        Surface(
                                            shape = RoundedCornerShape(100),
                                            color = AppColors.Surface,
                                            border = BorderStroke(1.dp, AppColors.CardBorder)
                                        ) {
                                            Row(
                                                Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    Modifier
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        staff.nama.take(1),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF3B82F6)
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    staff.nama,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = AppColors.TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Kondisi summary
                    item {
                        val ks = r.kondisiSummary
                        if (ks != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                KondisiChip("Baik", ks.baik, Color(0xFF10B981), Modifier.weight(1f))
                                KondisiChip("R.Ringan", ks.rusakRingan, Color(0xFFF59E0B), Modifier.weight(1f))
                                KondisiChip("R.Berat", ks.rusakBerat, Color(0xFFEF4444), Modifier.weight(1f))
                                KondisiChip("Tdk Aktif", ks.tidakAktif, Color(0xFF6B7280), Modifier.weight(1f))
                            }
                        }
                    }

                    // Barang header
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Daftar Barang",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(100), color = Color(0xFF06B6D4).copy(alpha = 0.12f)) {
                                Text("${barangList.size} jenis", fontSize = 10.sp, color = Color(0xFF0891B2),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }

                    if (barangList.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Inventory2, null, tint = AppColors.TextTertiary, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text("Belum ada barang", color = AppColors.TextSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        itemsIndexed(barangList) { idx, barang ->
                            BarangCard(
                                index = idx + 1,
                                barang = barang,
                                isOffline = state.isOffline,
                                onEdit = { onNavigateToBarangForm(ruanganId, barang.id) },
                                onDelete = { deleteBarangTarget = barang }
                            )
                        }
                    }

                    // Bottom spacing for FAB
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Delete ruangan dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Ruangan?", color = AppColors.TextPrimary) },
            text = { Text("Semua barang di ruangan ini juga akan dihapus.", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteRuangan(ruanganId) }) {
                    Text("Hapus", color = Color(0xFFEF4444))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") } }
        )
    }

    // Delete barang dialog
    deleteBarangTarget?.let { barang ->
        AlertDialog(
            onDismissRequest = { deleteBarangTarget = null },
            title = { Text("Hapus \"${barang.nama}\"?", color = AppColors.TextPrimary) },
            confirmButton = {
                TextButton(onClick = { deleteBarangTarget = null; viewModel.deleteBarang(ruanganId, barang.id) }) {
                    Text("Hapus", color = Color(0xFFEF4444))
                }
            },
            dismissButton = { TextButton(onClick = { deleteBarangTarget = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun KondisiChip(label: String, count: Int, color: Color, modifier: Modifier) {
    Surface(
        modifier.clip(RoundedCornerShape(8.dp)),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 8.sp, color = color.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BarangCard(
    index: Int,
    barang: InventarisBarangDto,
    isOffline: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val kondisiColor = when (barang.kondisi) {
        "baik" -> Color(0xFF10B981)
        "rusak_ringan" -> Color(0xFFF59E0B)
        "rusak_berat" -> Color(0xFFEF4444)
        "tidak_aktif" -> Color(0xFF6B7280)
        else -> AppColors.TextTertiary
    }
    val kondisiLabel = when (barang.kondisi) {
        "baik" -> "Baik"
        "rusak_ringan" -> "Rusak Ringan"
        "rusak_berat" -> "Rusak Berat"
        "tidak_aktif" -> "Tidak Aktif"
        else -> barang.kondisi
    }

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = AppColors.Surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            // Index badge
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(Color(0xFF06B6D4).copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$index", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0891B2))
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(barang.nama, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (barang.merek != null) {
                        Text(barang.merek, fontSize = 10.sp, color = AppColors.TextSecondary)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (barang.kodeInventaris != null) {
                        Text("· ${barang.kodeInventaris}", fontSize = 10.sp, color = AppColors.TextTertiary)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(100), color = kondisiColor.copy(alpha = 0.12f)) {
                        Text(kondisiLabel, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = kondisiColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (barang.tahunPerolehan != null) {
                        Spacer(Modifier.width(6.dp))
                        Text("${barang.tahunPerolehan}", fontSize = 10.sp, color = AppColors.TextTertiary)
                    }
                    if (barang.sumberDana != null) {
                        Spacer(Modifier.width(6.dp))
                        Text("· ${barang.sumberDana}", fontSize = 10.sp, color = AppColors.TextTertiary)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Qty + actions
            Column(horizontalAlignment = Alignment.End) {
                Text("${barang.jumlah}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text("unit", fontSize = 9.sp, color = AppColors.TextTertiary)
                if (!isOffline) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Edit, "Edit", tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Delete, "Hapus", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
