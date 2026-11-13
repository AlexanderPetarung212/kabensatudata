package com.example.kabensatudata.ui.screen.poa

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.remote.dto.*
import com.example.kabensatudata.ui.theme.AppColors
import java.text.NumberFormat
import java.util.Locale

private fun fmtRp(v: Double): String = NumberFormat.getCurrencyInstance(Locale("id","ID")).format(v).replace("Rp","Rp ").replace(",00","")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoaFormScreen(
    viewModel: PoaViewModel,
    editPoaId: Long? = null,
    onBack: () -> Unit,
    onSuccess: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEdit = editPoaId != null

    // Form state
    var year by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var selectedRabId by remember { mutableStateOf<Long?>(null) }
    var kegiatan by remember { mutableStateOf("") }
    var nomorSurat by remember { mutableStateOf("") }
    var outputTarget by remember { mutableStateOf("") }
    var plannedTotal by remember { mutableStateOf("") }
    var selectedParticipants by remember { mutableStateOf<List<Long>>(emptyList()) }
    var showRabDropdown by remember { mutableStateOf(false) }
    var showParticipantPicker by remember { mutableStateOf(false) }

    // Load form data
    LaunchedEffect(year) { viewModel.loadFormData(year, editPoaId) }

    // Pre-fill from detail if editing
    LaunchedEffect(editPoaId) {
        if (editPoaId != null) {
            viewModel.loadPoaDetail(editPoaId)
        }
    }
    LaunchedEffect(uiState.detailData) {
        val detail = uiState.detailData
        if (isEdit && detail != null) {
            year = detail.year
            selectedRabId = detail.rab_id
            kegiatan = detail.kegiatan
            nomorSurat = detail.nomor_surat ?: ""
            outputTarget = detail.output_target ?: ""
            plannedTotal = detail.planned_total.toLong().toString()
            selectedParticipants = detail.participants.map { it.employee_id }
        }
    }

    // Auto-fill kegiatan from RAB
    val selectedRab = uiState.availableRabs.find { it.id == selectedRabId }
    LaunchedEffect(selectedRabId) {
        if (!isEdit && selectedRab != null) {
            kegiatan = selectedRab.kegiatan ?: ""
            plannedTotal = selectedRab.total.toLong().toString()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.formError, uiState.formSuccess) {
        val msg = uiState.formError ?: uiState.formSuccess
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit POA" else "Buat POA", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Year
            OutlinedTextField(
                value = year.toString(), onValueChange = { it.toIntOrNull()?.let { y -> year = y } },
                label = { Text("Tahun") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            // RAB picker
            ExposedDropdownMenuBox(expanded = showRabDropdown, onExpandedChange = { showRabDropdown = it }) {
                OutlinedTextField(
                    value = selectedRab?.let { "${it.kegiatan} (${it.komponen})" } ?: "Pilih RAB...",
                    onValueChange = {}, readOnly = true,
                    label = { Text("RAB") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRabDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = showRabDropdown, onDismissRequest = { showRabDropdown = false }) {
                    uiState.availableRabs.forEach { rab ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(rab.kegiatan ?: "-", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("${rab.komponen} | ${fmtRp(rab.total)}", fontSize = 11.sp, color = AppColors.TextTertiary)
                                }
                            },
                            onClick = { selectedRabId = rab.id; showRabDropdown = false }
                        )
                    }
                    if (uiState.availableRabs.isEmpty()) {
                        DropdownMenuItem(text = { Text("Tidak ada RAB tersedia", color = AppColors.TextTertiary) }, onClick = {})
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Kegiatan
            OutlinedTextField(
                value = kegiatan, onValueChange = { kegiatan = it },
                label = { Text("Kegiatan") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            // Nomor Surat
            OutlinedTextField(
                value = nomorSurat, onValueChange = { nomorSurat = it },
                label = { Text("Nomor Surat (opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            // Output Target
            OutlinedTextField(
                value = outputTarget, onValueChange = { outputTarget = it },
                label = { Text("Output Target (opsional)") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            // Planned Total
            OutlinedTextField(
                value = plannedTotal, onValueChange = { plannedTotal = it },
                label = { Text("Anggaran (Rp)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))

            // Participants
            Text("Peserta", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            selectedParticipants.forEach { empId ->
                val emp = uiState.employees.find { it.id == empId }
                if (emp != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("• ${emp.nama}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedParticipants = selectedParticipants - empId }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Hapus", modifier = Modifier.size(16.dp), tint = AppColors.Error)
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showParticipantPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tambah Peserta")
            }

            Spacer(Modifier.height(24.dp))

            // Submit
            Button(
                onClick = {
                    val request = PoaCreateRequest(
                        year = year,
                        rab_id = selectedRabId ?: 0,
                        nomor_surat = nomorSurat.ifBlank { null },
                        kegiatan = kegiatan,
                        output_target = outputTarget.ifBlank { null },
                        planned_total = plannedTotal.toDoubleOrNull() ?: 0.0,
                        participants = selectedParticipants.map { ParticipantInput(it) }
                    )
                    if (isEdit) viewModel.updatePoa(editPoaId!!, request) { onBack() }
                    else viewModel.createPoa(request) { id -> onSuccess(id) }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = kegiatan.isNotBlank() && selectedRabId != null && !uiState.isFormLoading,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isFormLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text(if (isEdit) "Simpan Perubahan" else "Buat POA", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Participant picker dialog
    if (showParticipantPicker) {
        AlertDialog(
            onDismissRequest = { showParticipantPicker = false },
            title = { Text("Pilih Peserta", fontSize = 16.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                    uiState.employees.forEach { emp ->
                        val isSelected = selectedParticipants.contains(emp.id)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedParticipants = if (isSelected) selectedParticipants - emp.id
                                else selectedParticipants + emp.id
                            }.padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = {
                                selectedParticipants = if (isSelected) selectedParticipants - emp.id
                                else selectedParticipants + emp.id
                            })
                            Column {
                                Text(emp.nama, fontSize = 13.sp)
                                emp.jabatan?.let { Text(it, fontSize = 10.sp, color = AppColors.TextTertiary) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showParticipantPicker = false }) { Text("Selesai") } }
        )
    }
}
