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
import com.example.kabensatudata.nearby.PrescriptionItem
import com.example.kabensatudata.ui.theme.AppColors

private val PharmaPurple = Color(0xFF8B5CF6)
private val Green = Color(0xFF10B981)

/**
 * Stage 3: Pharmacy — view prescriptions, dispense medicines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2PPharmacyScreen(
    recordUuid: String,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: P2PPharmacyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(recordUuid) { viewModel.loadRecord(recordUuid) }
    LaunchedEffect(state.completed) { if (state.completed) onComplete() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Farmasi", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Tahap 3 — ${state.record?.patientName ?: ""}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PharmaPurple, titleContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        val record = state.record
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PharmaPurple)
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
            StepIndicator(currentStep = 3)

            // ─── Patient + diagnosis (read-only) ─────────
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(PharmaPurple.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = PharmaPurple)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(record.patientName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("${record.patientGender} · ${record.visitDate}", fontSize = 12.sp, color = AppColors.TextSecondary)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 0.5.dp, color = AppColors.DividerColor)

                    if (!record.diagnosisCode.isNullOrBlank() || !record.diagnosisName.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MedicalInformation, null, tint = Green, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Diagnosis: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${record.diagnosisCode ?: ""} — ${record.diagnosisName ?: ""}", fontSize = 13.sp, color = AppColors.TextSecondary)
                        }
                    }
                    if (!record.therapy.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Terapi: ${record.therapy}", fontSize = 13.sp, color = AppColors.TextSecondary)
                    }
                    if (!record.chiefComplaint.isBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Keluhan: ${record.chiefComplaint}", fontSize = 12.sp, color = AppColors.TextTertiary)
                    }
                }
            }

            // ─── Prescriptions ─────────────────────────────
            Text("Resep Obat", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)

            if (record.prescriptions.isEmpty()) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = AppColors.Surface) {
                    Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Tidak ada resep obat", color = AppColors.TextTertiary)
                    }
                }
            } else {
                record.prescriptions.forEachIndexed { i, rx ->
                    val checked = state.checkedItems.contains(i)
                    Surface(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        color = if (checked) Green.copy(alpha = 0.04f) else AppColors.Surface,
                        shadowElevation = 1.dp,
                        border = if (checked) androidx.compose.foundation.BorderStroke(1.dp, Green.copy(alpha = 0.3f)) else null
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleItem(i) },
                                colors = CheckboxDefaults.colors(checkedColor = Green, checkmarkColor = Color.White)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(rx.medicineName.ifBlank { "Obat #${i + 1}" },
                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                    color = if (checked) Green else AppColors.TextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Qty: ${rx.quantity} ${rx.medicineUnit}", fontSize = 12.sp, color = AppColors.TextSecondary)
                                    if (rx.instruction.isNotBlank()) Text(rx.instruction, fontSize = 12.sp, color = AppColors.TextSecondary)
                                }
                                if (rx.notes.isNotBlank()) Text(rx.notes, fontSize = 11.sp, color = AppColors.TextTertiary)
                            }
                            if (checked) Icon(Icons.Default.CheckCircle, null, tint = Green)
                        }
                    }
                }
            }

            state.error?.let { Text(it, color = AppColors.Error, fontSize = 13.sp) }

            // ─── Complete button ──────────────────────────
            val allChecked = record.prescriptions.indices.all { state.checkedItems.contains(it) }
            Button(
                onClick = { viewModel.dispenseAndComplete() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allChecked) Green else PharmaPurple
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(if (allChecked) Icons.Default.CheckCircle else Icons.Default.LocalPharmacy, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (allChecked) "Selesai — Semua Obat Diberikan" else "Selesai (${state.checkedItems.size}/${record.prescriptions.size} item)",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
