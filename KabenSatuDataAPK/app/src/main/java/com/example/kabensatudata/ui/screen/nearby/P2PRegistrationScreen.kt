package com.example.kabensatudata.ui.screen.nearby

import androidx.compose.animation.*
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
import com.example.kabensatudata.data.local.entity.FamilyMemberEntity
import com.example.kabensatudata.nearby.P2PMedicalPayload
import com.example.kabensatudata.nearby.PeerInfo
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.appTextFieldColors
import com.example.kabensatudata.ui.screen.medical.QuickRegisterDialog
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.dao.FamilyDao

private val Teal = Color(0xFF0D9488)

/**
 * Stage 1: Registration — fill visit date, complaint, anamnesis, vital signs.
 * Then select a connected peer to send to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2PRegistrationScreen(
    onBack: () -> Unit,
    onSent: () -> Unit,
    apiService: ApiService,
    familyMemberDao: FamilyMemberDao,
    familyDao: FamilyDao,
    viewModel: P2PRegistrationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.sent) { if (state.sent) onSent() }

    // Date picker
    var showDatePicker by remember { mutableStateOf(false) }

    // Peer selection dialog
    var showPeerDialog by remember { mutableStateOf(false) }

    // Quick register dialog
    var showQuickRegister by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pendaftaran P2P", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Tahap 1 — Pendaftaran", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Step indicator ──────────────────────────────
            StepIndicator(currentStep = 1)

            // ─── Patient selection ───────────────────────────
            PatientSelector(
                selectedName = state.patientName,
                patients = state.patients,
                onSelect = { viewModel.selectPatient(it) }
            )

            // ─── Quick Register: Pasien Baru ─────────────────
            Surface(
                Modifier.fillMaxWidth().clickable { showQuickRegister = true },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0EA5E9).copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.3f))
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.PersonAdd, null,
                        tint = Color(0xFF0EA5E9), modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Daftarkan Pasien Baru",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0EA5E9)
                    )
                }
            }

            // ─── Date picker ─────────────────────────────────
            Surface(
                Modifier.fillMaxWidth().clickable { showDatePicker = true },
                shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Teal.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CalendarMonth, null, tint = Teal, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tanggal Kunjungan *", fontSize = 11.sp, color = AppColors.TextTertiary, fontWeight = FontWeight.SemiBold)
                        Text(state.visitDate.ifBlank { "Pilih tanggal" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                    }
                    Icon(Icons.Default.Edit, null, tint = Teal, modifier = Modifier.size(18.dp))
                }
            }

            // ─── Keluhan & Anamnesis ─────────────────────────
            SectionCard(title = "Keluhan & Anamnesis", icon = Icons.Default.Description, color = Color(0xFF6366F1)) {
                OutlinedTextField(
                    value = state.chiefComplaint, onValueChange = { viewModel.update("chiefComplaint", it) },
                    label = { Text("Keluhan Utama") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    colors = appTextFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.anamnesis, onValueChange = { viewModel.update("anamnesis", it) },
                    label = { Text("Anamnesis") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    colors = appTextFieldColors()
                )
            }

            // ─── Tanda Vital ─────────────────────────────────
            SectionCard(title = "Tanda Vital", icon = Icons.Default.FavoriteBorder, color = Color(0xFFEF4444)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Sistolik", state.systolic, Modifier.weight(1f)) { viewModel.update("systolic", it) }
                    NumField("Diastolik", state.diastolic, Modifier.weight(1f)) { viewModel.update("diastolic", it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("BB (kg)", state.weight, Modifier.weight(1f)) { viewModel.update("weight", it) }
                    NumField("TB (cm)", state.height, Modifier.weight(1f)) { viewModel.update("height", it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Nadi", state.heartRate, Modifier.weight(1f)) { viewModel.update("heartRate", it) }
                    NumField("Suhu (°C)", state.bodyTemp, Modifier.weight(1f)) { viewModel.update("bodyTemp", it) }
                }
                Spacer(Modifier.height(8.dp))
                NumField("Resp. Rate", state.respRate, Modifier.fillMaxWidth()) { viewModel.update("respRate", it) }
            }

            // ─── Error ───────────────────────────────────────
            state.error?.let {
                Text(it, color = AppColors.Error, fontSize = 13.sp)
            }

            // ─── Send button ─────────────────────────────────
            Button(
                onClick = { showPeerDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(14.dp),
                enabled = state.patientId > 0 && state.visitDate.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text("Kirim ke Dokter", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val dps = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dps.selectedDateMillis?.let { ms ->
                        val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        viewModel.update("visitDate", d.toString())
                    }
                    showDatePicker = false
                }) { Text("OK", color = Teal) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Batal") } }
        ) { DatePicker(state = dps) }
    }

    // Peer selection dialog
    if (showPeerDialog) {
        PeerSelectionDialog(
            peers = state.peers,
            onSelect = { endpointId ->
                showPeerDialog = false
                viewModel.sendToDoctor(endpointId)
            },
            onDismiss = { showPeerDialog = false }
        )
    }

    // Quick Register dialog
    if (showQuickRegister) {
        QuickRegisterDialog(
            apiService = apiService,
            familyMemberDao = familyMemberDao,
            familyDao = familyDao,
            onDismiss = { showQuickRegister = false },
            onPatientRegistered = { memberId ->
                showQuickRegister = false
                viewModel.selectPatientById(memberId)
            }
        )
    }
}

// ─── Shared Components ───────────────────────────────────

@Composable
fun StepIndicator(currentStep: Int) {
    val steps = listOf("Pendaftaran", "Dokter", "Farmasi")
    val colors = listOf(Color(0xFF0D9488), Color(0xFF3B82F6), Color(0xFF8B5CF6))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { i, label ->
            val isActive = i + 1 <= currentStep
            Surface(
                Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                color = if (isActive) colors[i].copy(alpha = 0.12f) else AppColors.Surface,
                border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, colors[i].copy(alpha = 0.4f)) else null
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(20.dp).clip(CircleShape)
                        .background(if (isActive) colors[i] else AppColors.TextTertiary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.White else AppColors.TextTertiary)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 11.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) colors[i] else AppColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 0.5.dp, color = AppColors.DividerColor)
            content()
        }
    }
}

@Composable
fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = appTextFieldColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientSelector(selectedName: String, patients: List<FamilyMemberEntity>, onSelect: (FamilyMemberEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Text("Pasien *", fontSize = 11.sp, color = AppColors.TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = if (selectedName.isNotBlank()) selectedName else searchQuery,
                    onValueChange = { searchQuery = it; if (!expanded) expanded = true },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    placeholder = { Text("Cari nama pasien...") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    singleLine = true, colors = appTextFieldColors()
                )
                val filtered = patients.filter {
                    searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                }.take(20)
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    filtered.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(p.name, fontWeight = FontWeight.Medium)
                                    Text("${p.gender} · ${p.birthDate ?: "-"}", fontSize = 12.sp, color = AppColors.TextSecondary)
                                }
                            },
                            onClick = { onSelect(p); expanded = false; searchQuery = "" }
                        )
                    }
                    if (filtered.isEmpty()) {
                        DropdownMenuItem(text = { Text("Tidak ditemukan", color = AppColors.TextTertiary) }, onClick = {})
                    }
                }
            }
        }
    }
}

@Composable
fun PeerSelectionDialog(peers: Map<String, PeerInfo>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Perangkat Tujuan", fontWeight = FontWeight.SemiBold) },
        text = {
            if (peers.isEmpty()) {
                Text("Tidak ada perangkat terhubung. Aktifkan P2P terlebih dahulu.", color = AppColors.TextSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    peers.forEach { (id, peer) ->
                        Surface(
                            Modifier.fillMaxWidth().clickable { onSelect(id) },
                            shape = RoundedCornerShape(10.dp), color = AppColors.Background
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF3B82F6))
                                Spacer(Modifier.width(10.dp))
                                Text(peer.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
