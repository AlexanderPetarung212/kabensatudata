package com.example.kabensatudata.ui.screen.poa

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.remote.dto.*
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import java.text.NumberFormat
import java.util.Locale

private val monthNames = listOf("Januari","Februari","Maret","April","Mei","Juni","Juli","Agustus","September","Oktober","November","Desember")
private fun fmtRp(v: Double): String = NumberFormat.getCurrencyInstance(Locale("id","ID")).format(v).replace("Rp","Rp ").replace(",00","")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoaDetailScreen(
    poaId: Long,
    viewModel: PoaViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToList: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val detail = uiState.detailData
    var expandedMonth by remember { mutableStateOf<Int?>(null) }
    var showMonthMeta by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(poaId) {
        viewModel.loadPoaDetail(poaId)
        viewModel.loadFormData(detail?.year ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
    }

    // Messages Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.actionSuccess, uiState.actionError, uiState.downloadSuccess, uiState.downloadError) {
        val msg = uiState.actionSuccess ?: uiState.actionError ?: uiState.downloadSuccess ?: uiState.downloadError
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = detail?.kegiatan?.let {
                    if (it.length > 30) it.take(30) + "…" else it
                } ?: "Detail POA",
                subtitle = detail?.let { "Tahun ${it.year} • ${it.komponen}" } ?: "Memuat data…",
                onBack = onBack,
                actions = {
                    if (detail?.is_admin == true) {
                        IconButton(onClick = { onEdit(poaId) }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Hapus", tint = Color.White.copy(0.85f))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isDetailLoading && detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
        } else if (uiState.detailError != null && detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(uiState.detailError!!, color = AppColors.Error)
            }
        } else if (detail != null) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                // Header
                PoaDetailHeader(detail)
                Spacer(Modifier.height(12.dp))

                // 12-month grid
                Text("  Jadwal Per Bulan", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(8.dp))

                // Admin bulk lock
                if (detail.is_admin) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { viewModel.bulkLock(detail.year, expandedMonth ?: 1, true) }, modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("🔒 Bulk Lock", fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(onClick = { viewModel.bulkLock(detail.year, expandedMonth ?: 1, false) }, modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("🔓 Bulk Unlock", fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                for (m in 1..12) {
                    val cs = detail.claim_status[m.toString()]
                    if (cs != null) {
                        MonthSection(
                            month = m,
                            cs = cs,
                            detail = detail,
                            isExpanded = expandedMonth == m,
                            onToggle = { expandedMonth = if (expandedMonth == m) null else m },
                            viewModel = viewModel,
                            poaId = poaId,
                            onShowMonthMeta = { showMonthMeta = m }
                        )
                    }
                }

                // Item Progress section
                if (detail.rab_items.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ItemProgressSection(detail = detail, viewModel = viewModel, poaId = poaId)
                }

                // Participants
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ParticipantSection(detail.participants)

                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus POA?") },
            text = { Text("Yakin ingin menghapus POA \"${detail?.kegiatan}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deletePoa(poaId) { onNavigateToList() }
                }) { Text("Hapus", color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") } }
        )
    }

    // Month Meta bottom sheet
    if (showMonthMeta != null) {
        PoaMonthMetaSheet(
            month = showMonthMeta!!,
            detail = detail!!,
            employees = uiState.employees,
            onDismiss = { showMonthMeta = null },
            onSave = { request -> viewModel.upsertMonth(poaId, request); showMonthMeta = null }
        )
    }
}

@Composable
private fun PoaDetailHeader(detail: PoaDetailResponse) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor   = AppColors.Accent.copy(0.25f),
                ambientColor = AppColors.Accent.copy(0.10f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.GlossBorder),
        shadowElevation = 0.dp
    ) {
        Column {
            // Glossy stripe atas
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(0.70f),
                                AppColors.Accent.copy(0.55f),
                                Color.White.copy(0.45f),
                                AppColors.Accent.copy(0.20f)
                            )
                        )
                    )
            )
            Column(Modifier.padding(14.dp)) {
                Text(detail.kegiatan, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = AppColors.Accent.copy(alpha = 0.1f)) {
                        Text(detail.komponen, fontSize = 10.sp, color = AppColors.Accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    detail.rincian_menu?.let {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF3E8FF)) {
                            Text(it, fontSize = 10.sp, color = Color(0xFF7C3AED), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Anggaran", fontSize = 10.sp, color = AppColors.TextTertiary)
                        Text(fmtRp(detail.planned_total), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Tahun", fontSize = 10.sp, color = AppColors.TextTertiary)
                        Text("${detail.year}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (detail.nomor_surat != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("No. Surat: ${detail.nomor_surat}", fontSize = 11.sp, color = AppColors.TextSecondary)
                }
                if (detail.output_target != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("Target: ${detail.output_target}", fontSize = 11.sp, color = AppColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MonthSection(
    month: Int, cs: PoaClaimStatus, detail: PoaDetailResponse,
    isExpanded: Boolean, onToggle: () -> Unit,
    viewModel: PoaViewModel, poaId: Long, onShowMonthMeta: () -> Unit
) {
    val hasActivity = cs.count > 0 || cs.marked || cs.claimed
    val monthColor = when {
        cs.claimed -> Color(0xFF22C55E)
        cs.locked  -> Color(0xFFEF4444)
        cs.marked  -> Color(0xFFF59E0B)
        hasActivity-> AppColors.Accent
        else       -> AppColors.CardBorder
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .shadow(
                elevation = if (isExpanded) 5.dp else 2.dp,
                shape = RoundedCornerShape(10.dp),
                spotColor   = monthColor.copy(if (isExpanded) 0.25f else 0.10f),
                ambientColor = monthColor.copy(0.06f)
            ),
        shape = RoundedCornerShape(10.dp),
        color = AppColors.Surface,
        border = BorderStroke(
            1.dp,
            if (isExpanded) monthColor.copy(0.50f)
            else if (cs.claimed) Color(0xFF22C55E).copy(0.30f)
            else AppColors.GlossBorder
        ),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.clickable { onToggle() }.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status dot
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(
                    when { cs.claimed -> Color(0xFF22C55E).copy(alpha = 0.15f); cs.locked -> Color(0xFFEF4444).copy(alpha = 0.1f); hasActivity -> Color(0xFFEAB308).copy(alpha = 0.12f); else -> AppColors.Secondary.copy(alpha = 0.08f) }
                ), contentAlignment = Alignment.Center) {
                    Text(when { cs.claimed -> "✅"; cs.locked -> "🔒"; cs.marked -> "⏳"; hasActivity -> "${cs.count}"; else -> "—" }, fontSize = 10.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(monthNames[month - 1], fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (cs.claimed) Text("Diklaim ${cs.claimed_at?.take(10) ?: ""}", fontSize = 10.sp, color = Color(0xFF22C55E))
                    if (cs.amount > 0) Text(fmtRp(cs.amount), fontSize = 10.sp, color = AppColors.TextTertiary)
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(20.dp))
            }

            // Expanded content
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Meta info
                cs.no_surat_sppt?.let { LabelValue("No. Surat SPPT", it) }
                cs.no_surat_sppd?.let { LabelValue("No. Surat SPPD", it) }
                cs.tanggal_kegiatan_sppt?.let { LabelValue("Tgl Kegiatan SPPT", it) }
                cs.tanggal_kegiatan_sppd?.let { LabelValue("Tgl Kegiatan SPPD", it) }
                cs.tanggal_surat_sppt?.let { LabelValue("Tgl Surat SPPT", it) }
                cs.tanggal_surat_sppd?.let { LabelValue("Tgl Surat SPPD", it) }
                cs.desa_tujuan_darat?.let { LabelValue("Desa Darat", it) }
                cs.desa_tujuan_seberang?.let { LabelValue("Desa Seberang", it) }
                cs.holidays?.let { LabelValue("Tanggal Merah", it) }
                cs.claimed_by_name?.let { LabelValue("Diklaim oleh", it) }

                // Participants for this month
                if (cs.participant_ids.isNotEmpty()) {
                    val names = detail.participants.filter { cs.participant_ids.contains(it.employee_id) }.map { it.nama }
                    LabelValue("Peserta Bulan", names.joinToString(", "))
                }

                // Item opsional
                if (cs.item_opsional.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Item Opsional:", fontWeight = FontWeight.Medium, fontSize = 11.sp)
                    cs.item_opsional.forEach { io ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("  • ${io.label}", fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text(fmtRp(io.amount?.toDouble() ?: 0.0), fontSize = 11.sp, color = AppColors.Accent)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Admin actions
                if (detail.is_admin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        SmallButton("⚙️ Meta", onClick = onShowMonthMeta)
                        SmallButton(if (cs.marked) "✖ Hapus Tanda" else "📌 Tandai", onClick = { viewModel.toggleMark(poaId, month, !cs.marked) })
                        SmallButton(if (cs.locked) "🔓 Unlock" else "🔒 Lock", onClick = { viewModel.toggleClaimLock(poaId, month, !cs.locked) })
                        if (cs.claimed) {
                            SmallButton(if (cs.claimed_hidden) "👁 Tampilkan" else "🙈 Sembunyikan", onClick = { viewModel.toggleClaimLabel(poaId, month, !cs.claimed_hidden) })
                            SmallButton("↗ Carry Over", onClick = { viewModel.carryOver(poaId, month, cs.amount) })
                        }
                    }
                }

                // Download buttons
                if (cs.claimed) {
                    Spacer(Modifier.height(8.dp))
                    Text("Download Dokumen:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        cs.sppt_lpj_id?.let { id ->
                            DownloadChip("📄 SPPT") { viewModel.downloadLpj(id, "SPPT", detail.kegiatan) }
                        }
                        cs.sppd_lpj_id?.let { id ->
                            DownloadChip("📄 SPPD") { viewModel.downloadLpj(id, "SPPD", detail.kegiatan) }
                        }
                        cs.tiba_berangkat_id?.let { id ->
                            DownloadChip("✈️ TB") { viewModel.downloadTibaBerangkat(id, detail.kegiatan) }
                        }
                        cs.item_opsional.forEach { io ->
                            DownloadChip("🧾 ${io.label}") { viewModel.downloadItemOpsional(io.id.toLong(), io.label, detail.kegiatan) }
                        }
                    }
                }

                // Claim button (if allowed)
                if (cs.allowed) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { /* claim handled via calendar */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📋 Klaim Bulan ${monthNames[month - 1]}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemProgressSection(detail: PoaDetailResponse, viewModel: PoaViewModel, poaId: Long) {
    Text("  Item Progress", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp))
    Spacer(Modifier.height(4.dp))

    val optionalTypes = listOf("snack", "konsumsi", "penggandaan", "bahan_makanan", "lainnya", "transportasi_lokal")
    val optionalItems = detail.rab_items.filter { it.type != null && optionalTypes.contains(it.type) }
    val progressMonths = (detail.item_progress as? Map<*, *>)?.get("months") as? Map<*, *>

    if (optionalItems.isEmpty()) {
        Text("    Tidak ada item opsional", fontSize = 12.sp, color = AppColors.TextTertiary, modifier = Modifier.padding(horizontal = 12.dp))
        return
    }

    for (m in 1..12) {
        val cs = detail.claim_status[m.toString()] ?: continue
        if (!cs.claimed && !cs.marked && cs.count == 0) continue

        val monthProgress = (progressMonths?.get(m.toString()) as? Map<*, *>)?.get("items") as? Map<*, *>

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            shape = RoundedCornerShape(8.dp), color = AppColors.Surface,
            border = BorderStroke(1.dp, AppColors.CardBorder.copy(alpha = 0.1f))
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(monthNames[m - 1], fontWeight = FontWeight.Medium, fontSize = 12.sp)
                optionalItems.forEach { item ->
                    val itemProg = monthProgress?.get(item.id.toString()) as? Map<*, *>
                    val completed = itemProg?.get("completed") as? Boolean ?: false
                    val absorbed = (itemProg?.get("absorbed_amount") as? Number)?.toDouble() ?: 0.0

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        if (detail.is_admin) {
                            Checkbox(
                                checked = completed,
                                onCheckedChange = { checked ->
                                    viewModel.updateItemProgress(poaId, ItemProgressRequest(
                                        month = m, rab_item_id = item.id, completed = checked,
                                        absorbed_amount = if (checked) item.subtotal else 0.0
                                    ))
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(if (completed) "✅" else "⬜", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                        }
                        Text(item.label ?: "-", fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 2)
                        Text(if (completed) fmtRp(absorbed) else fmtRp(item.subtotal), fontSize = 10.sp, color = if (completed) Color(0xFF22C55E) else AppColors.TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantSection(participants: List<PoaParticipantDto>) {
    Text("  Peserta", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp))
    Spacer(Modifier.height(4.dp))
    participants.forEachIndexed { i, p ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            Text("${i + 1}. ", fontSize = 12.sp, color = AppColors.TextTertiary)
            Text(p.nama, fontSize = 12.sp)
            p.role?.let { Text(" ($it)", fontSize = 11.sp, color = AppColors.TextTertiary) }
        }
    }
}

// ─── Helper Composables ─────────────────────────────────────────────────────

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text("$label: ", fontSize = 11.sp, color = AppColors.TextTertiary, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 11.sp, color = AppColors.TextPrimary)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp), shape = RoundedCornerShape(8.dp)) {
        Text(text, fontSize = 10.sp)
    }
}

@Composable
private fun DownloadChip(text: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), color = AppColors.Accent.copy(alpha = 0.1f),
        modifier = Modifier.clickable { onClick() }) {
        Text(text, fontSize = 11.sp, color = AppColors.Accent, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
