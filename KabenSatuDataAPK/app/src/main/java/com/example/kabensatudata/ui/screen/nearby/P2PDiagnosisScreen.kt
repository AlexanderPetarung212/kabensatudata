package com.example.kabensatudata.ui.screen.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.nearby.P2PMedicalPayload
import com.example.kabensatudata.nearby.PeerInfo
import com.example.kabensatudata.nearby.PrescriptionItem
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.appTextFieldColors

private val DocBlue = Color(0xFF3B82F6)

/**
 * Stage 2: Doctor — receives registration data, adds diagnosis + prescriptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2PDiagnosisScreen(
    recordUuid: String,
    onBack: () -> Unit,
    onSent: () -> Unit,
    viewModel: P2PDiagnosisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(recordUuid) { viewModel.loadRecord(recordUuid) }
    LaunchedEffect(state.sent) { if (state.sent) onSent() }

    var showPeerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Diagnosis & Resep", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Tahap 2 — ${state.record?.patientName ?: ""}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DocBlue, titleContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        val record = state.record
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DocBlue)
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
            StepIndicator(currentStep = 2)

            // ─── Patient info (read-only) ──────────────────
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(DocBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = DocBlue)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(record.patientName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AppColors.TextPrimary)
                            Text("${record.patientGender} · ${record.visitDate}", fontSize = 12.sp, color = AppColors.TextSecondary)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 0.5.dp, color = AppColors.DividerColor)

                    // Vitals summary
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        VitalChip("TD", "${record.systolic ?: "-"}/${record.diastolic ?: "-"}")
                        VitalChip("BB", record.weight?.let { "${it}kg" } ?: "-")
                        VitalChip("Nadi", record.heartRate?.toString() ?: "-")
                        VitalChip("Suhu", record.bodyTemperature?.let { "${it}°C" } ?: "-")
                    }

                    if (record.chiefComplaint.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Keluhan: ${record.chiefComplaint}", fontSize = 13.sp, color = AppColors.TextSecondary)
                    }
                    if (record.anamnesis.isNotBlank()) {
                        Text("Anamnesis: ${record.anamnesis}", fontSize = 13.sp, color = AppColors.TextSecondary)
                    }
                }
            }

            // ─── Diagnosis ─────────────────────────────────
            SectionCard(title = "Diagnosis & Tindakan", icon = Icons.Default.MedicalInformation, color = Color(0xFF10B981)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.diagnosisCode, onValueChange = { viewModel.update("diagnosisCode", it) },
                        label = { Text("Kode ICD") }, modifier = Modifier.weight(0.4f), singleLine = true, colors = appTextFieldColors()
                    )
                    OutlinedTextField(
                        value = state.diagnosisName, onValueChange = { viewModel.update("diagnosisName", it) },
                        label = { Text("Diagnosis") }, modifier = Modifier.weight(0.6f), singleLine = true, colors = appTextFieldColors()
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.therapy, onValueChange = { viewModel.update("therapy", it) },
                    label = { Text("Terapi") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = appTextFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.procedure, onValueChange = { viewModel.update("procedure", it) },
                    label = { Text("Tindakan") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = appTextFieldColors()
                )
            }

            // ─── Prescriptions ─────────────────────────────
            SectionCard(title = "Resep Obat", icon = Icons.Default.Medication, color = Color(0xFFF59E0B)) {
                state.prescriptions.forEachIndexed { i, rx ->
                    PrescriptionCard(i + 1, rx, state.medicines,
                        onSelect = { viewModel.selectMedicine(i, it) },
                        onUpdate = { f, v -> viewModel.updatePrescription(i, f, v) },
                        onRemove = { viewModel.removePrescription(i) }
                    )
                    if (i < state.prescriptions.lastIndex) Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.addPrescription() }, Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Icon(Icons.Default.Add, null, tint = Color(0xFFF59E0B))
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah Obat", color = Color(0xFFF59E0B))
                }
            }

            state.error?.let { Text(it, color = AppColors.Error, fontSize = 13.sp) }

            Button(
                onClick = { showPeerDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DocBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text("Kirim ke Farmasi", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showPeerDialog) {
        PeerSelectionDialog(
            peers = state.peers,
            onSelect = { showPeerDialog = false; viewModel.sendToPharmacy(it) },
            onDismiss = { showPeerDialog = false }
        )
    }
}

@Composable
private fun VitalChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
        Text(label, fontSize = 10.sp, color = AppColors.TextTertiary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrescriptionCard(
    index: Int,
    rx: PrescriptionItem,
    medicines: List<com.example.kabensatudata.data.local.entity.MedicineEntity>,
    onSelect: (com.example.kabensatudata.data.local.entity.MedicineEntity) -> Unit,
    onUpdate: (String, String) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Background)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Obat #$index", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFF59E0B))
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, tint = AppColors.Error, modifier = Modifier.size(16.dp))
                }
            }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = rx.medicineName.ifBlank { "Pilih obat..." }, onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = appTextFieldColors(), label = { Text("Obat") }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    medicines.forEach { m ->
                        DropdownMenuItem(
                            text = { Text("${m.fullName} (stok ${m.stockQuantity})") },
                            onClick = { onSelect(m); expanded = false }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = "${if (rx.quantity > 0) rx.quantity else ""}", onValueChange = { onUpdate("quantity", it) },
                    label = { Text("Jumlah") }, modifier = Modifier.weight(0.3f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = appTextFieldColors()
                )
                OutlinedTextField(
                    value = rx.instruction, onValueChange = { onUpdate("instruction", it) },
                    label = { Text("Instruksi") }, modifier = Modifier.weight(0.4f), singleLine = true, colors = appTextFieldColors()
                )
                OutlinedTextField(
                    value = rx.notes, onValueChange = { onUpdate("notes", it) },
                    label = { Text("Catatan") }, modifier = Modifier.weight(0.3f), singleLine = true, colors = appTextFieldColors()
                )
            }
        }
    }
}
