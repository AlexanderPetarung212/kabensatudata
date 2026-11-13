package com.example.kabensatudata.ui.screen.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.nearby.P2PMedicalPayload
import com.example.kabensatudata.ui.theme.AppColors

private val Teal = Color(0xFF0D9488)
private val Blue = Color(0xFF3B82F6)
private val Green = Color(0xFF10B981)
private val Red = Color(0xFFEF4444)
private val Purple = Color(0xFF8B5CF6)
private val Orange = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2PRecordDetailScreen(
    recordUuid: String,
    onBack: () -> Unit,
    onEdit: (Long, String) -> Unit,   // (patientId, uuid)
    onDeleted: () -> Unit,
    viewModel: P2PRecordDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(recordUuid) { viewModel.loadRecord(recordUuid) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    var showDeleteDialog by remember { mutableStateOf(false) }

    val record = state.record

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Detail Rekam Medis", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(record?.patientName ?: "", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = {
                    IconButton(onClick = {
                        record?.let { onEdit(it.patientId, it.recordUuid) }
                    }) { Icon(Icons.Default.Edit, "Edit", tint = Color.White) }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Hapus", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal, titleContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Status Badge ────────────────────────────
            val stageColor = when (record.stage) {
                P2PMedicalPayload.STAGE_REGISTRATION -> Teal
                P2PMedicalPayload.STAGE_DIAGNOSIS -> Blue
                P2PMedicalPayload.STAGE_DISPENSED -> Green
                else -> AppColors.TextTertiary
            }
            val stageLabel = when (record.stage) {
                P2PMedicalPayload.STAGE_REGISTRATION -> "Tahap Pendaftaran"
                P2PMedicalPayload.STAGE_DIAGNOSIS -> "Tahap Diagnosis"
                P2PMedicalPayload.STAGE_DISPENSED -> "Selesai — Obat Diberikan"
                else -> record.stage
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = stageColor.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, stageColor.copy(alpha = 0.3f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (record.stage == P2PMedicalPayload.STAGE_DISPENSED) Icons.Default.CheckCircle else Icons.Default.Info,
                        null, tint = stageColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stageLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = stageColor)
                }
            }

            // Sync status
            if (state.synced) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Green.copy(alpha = 0.08f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, null, tint = Green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("✅ Sudah disinkronkan ke server", fontSize = 12.sp, color = Green)
                    }
                }
            }

            // ─── Patient Info ────────────────────────────
            DetailSection("Informasi Pasien", Icons.Default.Person, Blue) {
                DetailRow("Nama", record.patientName)
                DetailRow("Jenis Kelamin", record.patientGender)
                DetailRow("Tanggal Kunjungan", record.visitDate)
                DetailRow("ID Record", record.recordUuid.take(8) + "...")
            }

            // ─── Keluhan & Anamnesis ─────────────────────
            if (record.chiefComplaint.isNotBlank() || record.anamnesis.isNotBlank()) {
                DetailSection("Keluhan & Anamnesis", Icons.Default.Description, Color(0xFF6366F1)) {
                    if (record.chiefComplaint.isNotBlank()) DetailRow("Keluhan", record.chiefComplaint)
                    if (record.anamnesis.isNotBlank()) DetailRow("Anamnesis", record.anamnesis)
                }
            }

            // ─── Vital Signs ─────────────────────────────
            DetailSection("Tanda Vital", Icons.Default.FavoriteBorder, Red) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    VitalItem("TD", "${record.systolic ?: "-"}/${record.diastolic ?: "-"}")
                    VitalItem("BB", record.weight?.let { "${it} kg" } ?: "-")
                    VitalItem("TB", record.height?.let { "${it} cm" } ?: "-")
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    VitalItem("Nadi", record.heartRate?.let { "$it x/m" } ?: "-")
                    VitalItem("Suhu", record.bodyTemperature?.let { "$it °C" } ?: "-")
                    VitalItem("RR", record.respiratoryRate?.let { "$it x/m" } ?: "-")
                }
            }

            // ─── Diagnosis ───────────────────────────────
            if (!record.diagnosisCode.isNullOrBlank() || !record.diagnosisName.isNullOrBlank()) {
                DetailSection("Diagnosis & Tindakan", Icons.Default.MedicalInformation, Green) {
                    if (!record.diagnosisCode.isNullOrBlank()) DetailRow("Kode ICD", record.diagnosisCode!!)
                    if (!record.diagnosisName.isNullOrBlank()) DetailRow("Diagnosis", record.diagnosisName!!)
                    if (!record.therapy.isNullOrBlank()) DetailRow("Terapi", record.therapy!!)
                    if (!record.procedure.isNullOrBlank()) DetailRow("Tindakan", record.procedure!!)
                }
            }

            // ─── Prescriptions ───────────────────────────
            if (record.prescriptions.isNotEmpty()) {
                DetailSection("Resep Obat", Icons.Default.Medication, Orange) {
                    record.prescriptions.forEachIndexed { i, rx ->
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = AppColors.Background) {
                            Column(Modifier.padding(10.dp)) {
                                Text("${i + 1}. ${rx.medicineName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.TextPrimary)
                                Text("Jumlah: ${rx.quantity} ${rx.medicineUnit}", fontSize = 12.sp, color = AppColors.TextSecondary)
                                if (rx.instruction.isNotBlank()) Text("Instruksi: ${rx.instruction}", fontSize = 12.sp, color = AppColors.TextSecondary)
                                if (rx.notes.isNotBlank()) Text("Catatan: ${rx.notes}", fontSize = 11.sp, color = AppColors.TextTertiary)
                            }
                        }
                        if (i < record.prescriptions.lastIndex) Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ─── Action Buttons ──────────────────────────
            if (!state.synced) {
                Button(
                    onClick = { viewModel.syncToServer() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    shape = RoundedCornerShape(12.dp), enabled = !state.syncing
                ) {
                    if (state.syncing) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.syncing) "Menyimpan..." else "Sinkronkan ke Server", fontWeight = FontWeight.SemiBold)
                }
            }

            state.error?.let { Text(it, color = Red, fontSize = 13.sp) }
            state.successMsg?.let { Text(it, color = Green, fontSize = 13.sp) }

            Spacer(Modifier.height(80.dp))
        }
    }

    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Rekam Medis?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Data rekam medis P2P untuk ${record?.patientName ?: ""} akan dihapus secara permanen dari perangkat ini.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteRecord() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Red)) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun DetailSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 0.5.dp, color = AppColors.DividerColor)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextSecondary, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 13.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VitalItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
        Text(label, fontSize = 10.sp, color = AppColors.TextTertiary)
    }
}
