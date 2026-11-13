package com.example.kabensatudata.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kabensatudata.data.remote.dto.CalendarDetailResponse
import com.example.kabensatudata.data.remote.dto.ClaimPrepResponse
import com.example.kabensatudata.data.remote.dto.ClaimResponse
import com.example.kabensatudata.ui.theme.AppColors
import java.text.NumberFormat
import java.util.Locale

private val TahapGreen = Color(0xFF22C55E)
private val TahapYellow = Color(0xFFEAB308)
private val TahapRed = Color(0xFFEF4444)

private fun tahapGradientStart(tahap: Int): Color = when (tahap) {
    1 -> Color(0xFF16A34A); 2 -> Color(0xFFCA8A04); 3 -> Color(0xFFDC2626); else -> Color.Gray
}

private fun formatRupiah(amount: Double): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    return fmt.format(amount).replace("Rp", "Rp ").replace(",00", "")
}

@Composable
fun CalendarDetailSheet(
    isLoading: Boolean,
    error: String?,
    data: CalendarDetailResponse?,
    onDismiss: () -> Unit,
    // Claim
    claimPrep: ClaimPrepResponse? = null,
    isClaimPrepLoading: Boolean = false,
    isClaimExecuting: Boolean = false,
    claimError: String? = null,
    claimSuccess: String? = null,
    claimResult: ClaimResponse? = null,
    onLoadClaimPrep: () -> Unit = {},
    onExecuteClaim: () -> Unit = {},
    // Download
    downloadingFile: String? = null,
    downloadError: String? = null,
    downloadSuccess: String? = null,
    onDownloadLpj: (Long, String) -> Unit = { _, _ -> },
    onDownloadTibaBerangkat: (Long) -> Unit = {},
    onDownloadItemOpsional: (Long, String) -> Unit = { _, _ -> },
    onClearDownloadMessages: () -> Unit = {},
    // Download indicator
    isFileDownloaded: (String) -> Boolean = { false },
    onOpenFile: (String) -> Unit = {},
    // Admin edit
    isAdmin: Boolean = false,
    onArturPeserta: (poaId: Long, month: Int) -> Unit = { _, _ -> }
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.Surface,
            shadowElevation = 8.dp
        ) {
            Column {
                // Header
                if (data != null) {
                    val headerColor = tahapGradientStart(data.tahap)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerColor, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(data.kegiatan, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${data.bulan} ${data.tahun}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                                    Spacer(Modifier.height(4.dp))
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.2f)) {
                                        Text(
                                            "Tahap ${"I".repeat(data.tahap.coerceIn(1, 3))}",
                                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, "Tutup", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    when {
                        isLoading -> {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Memuat data...", fontSize = 13.sp, color = AppColors.TextTertiary)
                                }
                            }
                        }
                        error != null -> {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Error, null, tint = AppColors.Error, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(error, fontSize = 13.sp, color = AppColors.TextSecondary)
                                }
                            }
                        }
                        data != null -> {
                            // Status badge
                            StatusBadge(claimed = data.claimed, marked = data.marked)

                            Spacer(Modifier.height(12.dp))

                            // ── Jadwal SPPT/SPPD ──
                            SectionHeader("📋", "Jadwal Kegiatan")
                            if (data.schedules.isNotEmpty()) {
                                data.schedules.forEach { schedule ->
                                    val isSPPT = schedule.type == "SPPT"
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSPPT) Color(0xFF3B82F6).copy(alpha = 0.08f) else Color(0xFF8B5CF6).copy(alpha = 0.08f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSPPT) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isSPPT) Color(0xFF3B82F6).copy(alpha = 0.15f) else Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        schedule.type, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                        color = if (isSPPT) Color(0xFF3B82F6) else Color(0xFF8B5CF6),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (schedule.nomor_surat != null) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("No. ${schedule.nomor_surat}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                                                }
                                            }
                                            if (schedule.tanggal_surat != null) {
                                                Spacer(Modifier.height(4.dp))
                                                DetailRow("Tgl Surat", schedule.tanggal_surat)
                                            }
                                            if (schedule.tanggal_kegiatan != null) {
                                                DetailRow("Tgl Kegiatan", schedule.tanggal_kegiatan)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("ℹ️ Belum ada jadwal", fontSize = 12.sp, color = AppColors.TextTertiary, modifier = Modifier.padding(vertical = 4.dp))
                            }

                            Spacer(Modifier.height(12.dp))

                            // ── Pelaksana ──
                            SectionHeader("👥", "Pelaksana")
                            if (data.no_participants) {
                                Text("⚠️ Data pegawai belum diinput", fontSize = 12.sp, color = Color(0xFFD97706), modifier = Modifier.padding(vertical = 4.dp))
                            } else if (data.participants.isNotEmpty()) {
                                data.participants.forEachIndexed { i, p ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp), color = AppColors.Secondary.copy(alpha = 0.08f)
                                    ) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(26.dp).clip(CircleShape).background(AppColors.Accent), contentAlignment = Alignment.Center) {
                                                Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(p.nama, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                                                if (p.role != null) Text(p.role, fontSize = 11.sp, color = AppColors.TextTertiary)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // ── Alokasi Dana ──
                            SectionHeader("💰", "Alokasi Dana")
                            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = AppColors.Secondary.copy(alpha = 0.08f)) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    BudgetRow("Total Anggaran POA", formatRupiah(data.planned_total))
                                    BudgetRow("Frekuensi Bulan Ini", "${data.monthly_count}x kegiatan")
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = AppColors.CardBorder)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Alokasi Bulan Ini", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                                        Text(formatRupiah(data.estimated_budget), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                            color = if (data.estimated_budget > 0) TahapGreen else AppColors.TextTertiary)
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // ══════════════════════════════════════════════════════
                            // ── Download Section (when already claimed) ──
                            // ══════════════════════════════════════════════════════
                            if (data.claimed) {
                                SectionHeader("📥", "Unduh Dokumen")

                                // SPPT
                                if (data.sppt_lpj_id != null) {
                                    val kegiatan = data.kegiatan.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
                                    val spptFile = "SPPT $kegiatan.docx"
                                    DownloadButton("SPPT", Icons.Default.Description, Color(0xFF3B82F6),
                                        isDownloading = downloadingFile == spptFile,
                                        isDownloaded = isFileDownloaded(spptFile),
                                        onClick = { onDownloadLpj(data.sppt_lpj_id, "SPPT") },
                                        onOpen = { onOpenFile(spptFile) }
                                    )
                                }
                                // SPPD
                                if (data.sppd_lpj_id != null) {
                                    val kegiatan = data.kegiatan.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
                                    val sppdFile = "SPPD $kegiatan.docx"
                                    DownloadButton("SPPD", Icons.Default.Description, Color(0xFF8B5CF6),
                                        isDownloading = downloadingFile == sppdFile,
                                        isDownloaded = isFileDownloaded(sppdFile),
                                        onClick = { onDownloadLpj(data.sppd_lpj_id, "SPPD") },
                                        onOpen = { onOpenFile(sppdFile) }
                                    )
                                }
                                // Tiba Berangkat
                                if (data.tiba_berangkat_id != null) {
                                    val kegiatan = data.kegiatan.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
                                    val tbFile = "Tiba_Berangkat $kegiatan.docx"
                                    DownloadButton("Tiba Berangkat", Icons.Default.FlightTakeoff, Color(0xFF0EA5E9),
                                        isDownloading = downloadingFile == tbFile,
                                        isDownloaded = isFileDownloaded(tbFile),
                                        onClick = { onDownloadTibaBerangkat(data.tiba_berangkat_id) },
                                        onOpen = { onOpenFile(tbFile) }
                                    )
                                }
                                // Item Opsional
                                data.item_opsional.forEach { item ->
                                    val kegiatan = data.kegiatan.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
                                    val safeLabel = item.label.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
                                    val itemFile = "Kwitansi $safeLabel $kegiatan.docx"
                                    DownloadButton("${item.label}", Icons.Default.Receipt, Color(0xFFF59E0B),
                                        isDownloading = downloadingFile == itemFile,
                                        isDownloaded = isFileDownloaded(itemFile),
                                        onClick = { onDownloadItemOpsional(item.id, item.label) },
                                        onOpen = { onOpenFile(itemFile) }
                                    )
                                }

                                // Download feedback
                                if (downloadSuccess != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(downloadSuccess, fontSize = 12.sp, color = TahapGreen)
                                }
                                if (downloadError != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("❌ $downloadError", fontSize = 12.sp, color = TahapRed)
                                }

                                Spacer(Modifier.height(12.dp))

                                // Klaim Ulang LPJ button (orange)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = AppColors.CardBorder)

                                if (claimPrep == null && !isClaimPrepLoading) {
                                    Button(
                                        onClick = onLoadClaimPrep,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Klaim Ulang LPJ", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (isClaimPrepLoading) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.Accent, strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Memeriksa klaim...", fontSize = 12.sp, color = AppColors.TextTertiary)
                                        }
                                    }
                                }

                                if (claimPrep != null) {
                                    ClaimConfirmSection(
                                        prep = claimPrep,
                                        isExecuting = isClaimExecuting,
                                        onConfirm = onExecuteClaim
                                    )
                                }
                            }

                            // ══════════════════════════════════════════════════════
                            // ── Atur Peserta (Admin only) ──
                            // ══════════════════════════════════════════════════════
                            if (isAdmin && !data.claimed) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = AppColors.CardBorder)
                                Button(
                                    onClick = {
                                        // poaId is embedded in detail URL — we need to pass it from viewModel
                                        // We use a workaround: onArturPeserta(-1, detailMonth) and let screen handle it
                                        onArturPeserta(-1L, 0) // will be overridden in CalendarScreen callback
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("⚙️ Atur Peserta", fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(4.dp))
                            }

                            // ══════════════════════════════════════════════════════
                            // ── Claim Section ──
                            // ══════════════════════════════════════════════════════
                            if (!data.claimed) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = AppColors.CardBorder)

                                if (claimPrep == null && !isClaimPrepLoading) {
                                    // Show "Klaim" button to load prep
                                    Button(
                                        onClick = onLoadClaimPrep,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Buat LPJ", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (isClaimPrepLoading) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.Accent, strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Memeriksa klaim...", fontSize = 12.sp, color = AppColors.TextTertiary)
                                        }
                                    }
                                }

                                if (claimPrep != null) {
                                    ClaimConfirmSection(
                                        prep = claimPrep,
                                        isExecuting = isClaimExecuting,
                                        onConfirm = onExecuteClaim
                                    )
                                }
                            }

                            // Success/error messages
                            if (claimSuccess != null) {
                                Spacer(Modifier.height(8.dp))
                                Surface(shape = RoundedCornerShape(8.dp), color = TahapGreen.copy(alpha = 0.1f)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = TahapGreen, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(claimSuccess, fontSize = 12.sp, color = Color(0xFF166534))
                                    }
                                }
                            }
                            if (claimError != null) {
                                Spacer(Modifier.height(8.dp))
                                Surface(shape = RoundedCornerShape(8.dp), color = TahapRed.copy(alpha = 0.1f)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Error, null, tint = TahapRed, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(claimError, fontSize = 12.sp, color = Color(0xFF991B1B))
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── Claim Confirmation ─────────────────────────────────────────────────────

@Composable
private fun ClaimConfirmSection(
    prep: ClaimPrepResponse,
    isExecuting: Boolean,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF22C55E).copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!prep.can_claim) {
                Text("❌ ${prep.reason ?: "Tidak dapat klaim"}", fontSize = 13.sp, color = TahapRed, fontWeight = FontWeight.Medium)
            } else {
                if (prep.is_reclaim) {
                    Text("⚠️ Klaim ulang — dokumen lama akan dihapus", fontSize = 12.sp, color = Color(0xFFD97706))
                    Spacer(Modifier.height(6.dp))
                }

                Text("Detail Klaim", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(4.dp))

                if (prep.target_darat > 0) {
                    DetailRow("Desa Darat", "${prep.target_darat} desa")
                    if (prep.auto_desa_darat != null) DetailRow("Tujuan", prep.auto_desa_darat)
                }
                if (prep.target_seberang > 0) {
                    DetailRow("Desa Seberang", "${prep.target_seberang} desa")
                    if (prep.auto_desa_seberang != null) DetailRow("Tujuan", prep.auto_desa_seberang)
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onConfirm,
                    enabled = !isExecuting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Memproses klaim...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (prep.is_reclaim) "Klaim Ulang" else "Konfirmasi Klaim", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Download Button ────────────────────────────────────────────────────────

@Composable
private fun DownloadButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isDownloading: Boolean,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    onOpen: () -> Unit = {}
) {
    val displayColor = if (isDownloaded) Color(0xFF22C55E) else color
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = displayColor.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, displayColor.copy(alpha = 0.15f)),
        onClick = {
            if (!isDownloading) {
                if (isDownloaded) onOpen() else onClick()
            }
        }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isDownloaded) Icons.Default.InsertDriveFile else icon, null, tint = displayColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                if (isDownloaded) {
                    Text("Sudah diunduh • Ketuk untuk buka", fontSize = 10.sp, color = Color(0xFF22C55E))
                }
            }
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = displayColor, strokeWidth = 2.dp)
            } else if (isDownloaded) {
                Icon(Icons.Default.OpenInNew, null, tint = displayColor, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Default.Download, null, tint = displayColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Shared Components ──────────────────────────────────────────────────────

@Composable
private fun StatusBadge(claimed: Boolean, marked: Boolean) {
    val (text, color, icon) = when {
        claimed -> Triple("Sudah Diklaim", Color(0xFF166534), Icons.Default.CheckCircle)
        marked -> Triple("Ditandai Aktif", Color(0xFF1D4ED8), Icons.Default.Pending)
        else -> Triple("Belum Diklaim", Color(0xFF6B7280), Icons.Default.Cancel)
    }
    val bgColor = when {
        claimed -> Color(0xFF22C55E).copy(alpha = 0.12f)
        marked -> Color(0xFF3B82F6).copy(alpha = 0.12f)
        else -> Color(0xFF9CA3AF).copy(alpha = 0.12f)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bgColor) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun SectionHeader(emoji: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text("$label: ", fontSize = 11.sp, color = AppColors.TextTertiary, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 11.sp, color = AppColors.TextSecondary)
    }
}

@Composable
private fun BudgetRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
    }
}
