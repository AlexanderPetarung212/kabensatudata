package com.example.kabensatudata.ui.screen.medical

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.BuildingDao
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.dao.MedicalRecordDao
import com.example.kabensatudata.data.local.dao.VillageDao
import com.example.kabensatudata.data.local.entity.FamilyMemberEntity
import com.example.kabensatudata.data.local.entity.MedicalRecordEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

// ─── ViewModel ──────────────────────────────────────────

@HiltViewModel
class MedicalRecordListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val medicalRecordDao: MedicalRecordDao,
    private val familyMemberDao: FamilyMemberDao
) : ViewModel() {
    private val memberId: Long = savedStateHandle["memberId"] ?: 0L

    val records = medicalRecordDao.getByMember(memberId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _member = MutableStateFlow<FamilyMemberEntity?>(null)
    val member: StateFlow<FamilyMemberEntity?> = _member

    private val _selectedRecord = MutableStateFlow<MedicalRecordEntity?>(null)
    val selectedRecord: StateFlow<MedicalRecordEntity?> = _selectedRecord

    init {
        viewModelScope.launch {
            _member.value = familyMemberDao.getById(memberId)
        }
        // Auto-select the first record when loaded
        viewModelScope.launch {
            records.collect { list ->
                if (_selectedRecord.value == null && list.isNotEmpty()) {
                    _selectedRecord.value = list.first()
                }
            }
        }
    }

    fun selectRecord(record: MedicalRecordEntity) {
        _selectedRecord.value = record
    }

    fun deleteRecord(record: MedicalRecordEntity) {
        viewModelScope.launch {
            medicalRecordDao.delete(record)
            _selectedRecord.value = null
        }
    }

    fun refreshMember() {
        viewModelScope.launch {
            _member.value = familyMemberDao.getById(memberId)
        }
    }
}

// ─── Screen ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalRecordListScreen(
    memberId: Long,
    onAddRecord: (Long) -> Unit,
    onEditRecord: (Long, Long) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    canEdit: Boolean = true,
    apiService: ApiService? = null,
    villageDao: VillageDao? = null,
    buildingDao: BuildingDao? = null,
    familyDao: FamilyDao? = null,
    viewModel: MedicalRecordListViewModel = hiltViewModel()
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    val records by viewModel.records.collectAsState()
    val member by viewModel.member.collectAsState()
    val selectedRecord by viewModel.selectedRecord.collectAsState()

    // Delete confirmation dialog
    if (showDeleteDialog && selectedRecord != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp)) },
            title = { Text("Hapus Rekam Medis", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary) },
            text = {
                Text(
                    "Apakah Anda yakin ingin menghapus rekam medis tanggal ${formatDateIndonesian(selectedRecord!!.visitDate)}? Tindakan ini tidak dapat dibatalkan.",
                    color = AppColors.TextSecondary, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(selectedRecord!!)
                    showDeleteDialog = false
                }) {
                    Text("Ya, Hapus", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Move Patient Dialog
    if (showMoveDialog && member != null && apiService != null && villageDao != null && buildingDao != null && familyDao != null) {
        MovePatientDialog(
            member = member!!,
            apiService = apiService,
            villageDao = villageDao,
            buildingDao = buildingDao,
            familyDao = familyDao,
            onSuccess = { newRmNumber ->
                showMoveDialog = false
                // Refresh member data to show updated RM
                viewModel.refreshMember()
            },
            onDismiss = { showMoveDialog = false }
        )
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Rekam Medis",
                subtitle = member?.name ?: "Data Pasien",
                onBack = onBack
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { onAddRecord(memberId) },
                    containerColor = AppColors.Accent,
                    modifier = Modifier.padding(bottom = 70.dp)
                ) { Icon(Icons.Default.Add, "Tambah Rekam Medis", tint = Color.White) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(AppColors.Background).padding(padding)) {
            if (records.isEmpty()) {
                // Empty state
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MedicalServices, null, Modifier.size(64.dp), tint = AppColors.TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada rekam medis", fontSize = 16.sp, color = AppColors.TextSecondary)
                        Text("Tekan + untuk menambah catatan kunjungan",
                            fontSize = 13.sp, color = AppColors.TextTertiary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Patient Header ──────────────────
                    item {
                        PatientHeaderCard(
                            member = member,
                            canEdit = canEdit,
                            onMovePatient = {
                                if (apiService != null && villageDao != null && buildingDao != null && familyDao != null) {
                                    showMoveDialog = true
                                }
                            }
                        )
                    }

                    // ── Visit History Pills ─────────────
                    if (records.size > 1) {
                        item {
                            SectionLabel("Riwayat Kunjungan")
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(records, key = { it.id }) { record ->
                                    val isSelected = selectedRecord?.id == record.id
                                    VisitPill(
                                        record = record,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectRecord(record) }
                                    )
                                }
                            }
                        }
                    }

                    // ── Selected Record Detail ──────────
                    if (selectedRecord != null) {
                        val rec = selectedRecord!!

                        // Clinical Summary
                        item {
                            DetailSection(title = "Ringkasan Klinis") {
                                // Keluhan & Anamnesis
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    InfoBox(
                                        label = "Keluhan Utama",
                                        value = rec.chiefComplaint ?: "Tidak ada catatan.",
                                        modifier = Modifier.weight(1f)
                                    )
                                    InfoBox(
                                        label = "Anamnesis",
                                        value = rec.anamnesis ?: "Tidak ada catatan.",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))
                                SectionLabel("Pemeriksaan Fisik")
                                Spacer(Modifier.height(8.dp))

                                // Vital Signs Row
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Blood Pressure
                                    VitalCard(
                                        title = "Tekanan Darah",
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (rec.systolic != null && rec.diastolic != null) {
                                            Text(
                                                "${rec.systolic}/${rec.diastolic}",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.TextPrimary
                                            )
                                            Text("mmHg", fontSize = 11.sp, color = AppColors.TextTertiary)
                                            val bpCategory = getBloodPressureCategory(rec.systolic, rec.diastolic)
                                            Text(bpCategory, fontSize = 10.sp, color = getBpColor(bpCategory))
                                        } else {
                                            Text("—", fontSize = 16.sp, color = AppColors.TextTertiary)
                                        }
                                    }

                                    // Weight/Height
                                    VitalCard(
                                        title = "BB / TB",
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (rec.weight != null && rec.height != null) {
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text("${rec.weight}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                                                Text(" kg", fontSize = 11.sp, color = AppColors.TextTertiary)
                                                Spacer(Modifier.width(4.dp))
                                                Text("/ ${rec.height}", fontSize = 14.sp, color = AppColors.TextPrimary)
                                                Text(" cm", fontSize = 11.sp, color = AppColors.TextTertiary)
                                            }
                                            val bmi = rec.weight / ((rec.height / 100.0) * (rec.height / 100.0))
                                            val bmiStr = String.format("%.1f", bmi)
                                            Text("BMI: $bmiStr", fontSize = 10.sp, color = AppColors.TextSecondary)
                                        } else if (rec.weight != null) {
                                            Text("${rec.weight} kg", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                                        } else {
                                            Text("—", fontSize = 16.sp, color = AppColors.TextTertiary)
                                        }
                                    }

                                    // Temp/HR/RR
                                    VitalCard(
                                        title = "Suhu/Nadi/RR",
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            VitalLine("Suhu", rec.bodyTemperature?.let { "$it°C" })
                                            VitalLine("Nadi", rec.heartRate?.let { "$it bpm" })
                                            VitalLine("RR", rec.respiratoryRate?.let { "$it x/m" })
                                        }
                                    }
                                }
                            }
                        }

                        // Diagnosis & Plan
                        item {
                            DetailSection(title = "Catatan Diagnostik & Rencana") {
                                // Diagnosis
                                Column {
                                    SectionLabel("Diagnosa Utama")
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = AppColors.Background,
                                        border = BorderStroke(1.dp, AppColors.CardBorder)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(
                                                rec.diagnosisName ?: "Belum dicatat",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = AppColors.TextPrimary
                                            )
                                            if (!rec.diagnosisCode.isNullOrEmpty()) {
                                                Text(
                                                    "ICD: ${rec.diagnosisCode}",
                                                    fontSize = 11.sp,
                                                    color = AppColors.TextTertiary
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // Therapy & Procedure
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    InfoBox(
                                        label = "Therapy / Edukasi",
                                        value = rec.therapy ?: "Tidak ada catatan.",
                                        modifier = Modifier.weight(1f)
                                    )
                                    InfoBox(
                                        label = "Tindakan Medis",
                                        value = rec.procedure ?: "Tidak ada tindakan.",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                // Medication
                                Column {
                                    SectionLabel("Obat yang Diresepkan")
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, AppColors.CardBorder)
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Default.Medication, null,
                                                tint = Color(0xFF6366F1),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                rec.medication ?: "Tidak ada obat diresepkan.",
                                                fontSize = 13.sp,
                                                color = AppColors.TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Administrative Details
                        item {
                            DetailSection(title = "Detail Administratif") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AdminRow("Tanggal Kunjungan", formatDateIndonesian(rec.visitDate))
                                    AdminRow("Dicatat oleh", rec.createdBy?.let { "User #$it" } ?: "System")
                                    AdminRow("Status", (rec.workflowStatus ?: "draft").uppercase().replace("_", " "))
                                    if (rec.pendingSync) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CloudUpload, null, tint = AppColors.Warning, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Menunggu sinkronisasi", fontSize = 12.sp, color = AppColors.Warning)
                                        }
                                    }
                                }
                            }
                        }

                        // Edit & Delete Actions
                        if (canEdit) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Edit Button
                                    OutlinedButton(
                                        onClick = { onEditRecord(memberId, rec.id) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, AppColors.Accent),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = AppColors.Accent
                                        )
                                    ) {
                                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Edit", fontWeight = FontWeight.SemiBold)
                                    }
                                    // Delete Button
                                    OutlinedButton(
                                        onClick = { showDeleteDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, AppColors.Error),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = AppColors.Error
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Hapus", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Disclaimer
                        item {
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = AppColors.Background,
                                border = BorderStroke(1.dp, AppColors.CardBorder.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "Dokumen ini merupakan bagian dari rekam medis elektronik. " +
                                            "Informasi bersifat rahasia dan hanya dapat digunakan oleh tenaga kesehatan berwenang.",
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 11.sp,
                                    color = AppColors.TextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Components ───────────────────────────────────────
// ═══════════════════════════════════════════════════════

@Composable
private fun PatientHeaderCard(
    member: FamilyMemberEntity?,
    canEdit: Boolean = false,
    onMovePatient: () -> Unit = {}
) {
    if (member == null) return

    val age = member.birthDate?.let {
        try { Period.between(LocalDate.parse(it.take(10)), LocalDate.now()).years } catch (_: Exception) { null }
    }

    val genderColor = if (member.gender == "Perempuan") Color(0xFFEC4899) else Color(0xFF3B82F6)
    val isGeneralPatient = member.rmNumber?.startsWith("UM-") == true

    Surface(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor   = if (isGeneralPatient) Color(0xFFF59E0B).copy(0.25f) else AppColors.Accent.copy(0.20f),
                ambientColor = if (isGeneralPatient) Color(0xFFF59E0B).copy(0.10f) else AppColors.Accent.copy(0.08f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, if (isGeneralPatient) Color(0xFFF59E0B).copy(alpha = 0.35f) else AppColors.GlossBorder)
    ) {
        Column {
            // Accent bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            if (isGeneralPatient)
                                listOf(Color(0xFFF59E0B), Color(0xFFEF6C00))
                            else
                                listOf(Color(0xFF38BDF8), Color(0xFF3B82F6), Color(0xFF6366F1))
                        ),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            )

            Column(Modifier.padding(16.dp)) {
                // Name + avatar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(genderColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (member.gender == "Perempuan") Icons.Default.Female else Icons.Default.Male,
                            null, tint = genderColor, modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "REKAM MEDIS PASIEN",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = AppColors.TextTertiary
                        )
                        Text(
                            member.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Info tiles — 2x2 grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTile("NO RM", member.rmNumber ?: "—", Modifier.weight(1f))
                    InfoTile("TGL LAHIR", formatDateIndonesian(member.birthDate), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTile("JENIS KELAMIN / USIA", "${member.gender ?: "—"} • ${age ?: "—"} thn", Modifier.weight(1f))
                    InfoTile("NIK", member.nik ?: "—", Modifier.weight(1f))
                }

                // ── General Patient Banner ──
                if (isGeneralPatient) {
                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info, null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Pasien Umum — belum terdaftar di keluarga",
                                fontSize = 12.sp,
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (canEdit) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onMovePatient,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz, null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Pindahkan ke Keluarga",
                                fontSize = 13.sp,
                                color = Color(0xFF3B82F6),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = AppColors.Background,
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = AppColors.TextTertiary)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VisitPill(
    record: MedicalRecordEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF6366F1) else AppColors.CardBorder
    val bgColor = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.08f) else AppColors.Surface
    val textColor = if (isSelected) Color(0xFF6366F1) else AppColors.TextSecondary

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 100.dp)) {
            Text(
                formatDateIndonesian(record.visitDate),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
            if (!record.diagnosisName.isNullOrEmpty()) {
                Text(
                    record.diagnosisName,
                    fontSize = 10.sp,
                    color = AppColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.12f)
                ) {
                    Text(
                        "• Saat ini",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6366F1)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column {
            // Header
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Background)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            }
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.CardBorder)
            // Content
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = AppColors.TextTertiary
    )
}

@Composable
private fun InfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(4.dp))
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AppColors.Background,
            border = BorderStroke(1.dp, AppColors.CardBorder.copy(alpha = 0.6f))
        ) {
            Text(
                value,
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp,
                color = AppColors.TextPrimary
            )
        }
    }
}

@Composable
private fun VitalCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor   = AppColors.Accent.copy(0.22f),
                ambientColor = AppColors.Accent.copy(0.08f)
            ),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.GlossBorder),
        shadowElevation = 0.dp
    ) {
        Column {
            // Glossy stripe atas VitalCard
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(0.70f),
                                AppColors.Accent.copy(0.45f),
                                Color.White.copy(0.40f)
                            )
                        )
                    )
            )
            Column(Modifier.padding(10.dp)) {
                Text(
                    title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = AppColors.TextTertiary
                )
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable
private fun VitalLine(label: String, value: String?) {
    Row {
        Text("$label: ", fontSize = 12.sp, color = AppColors.TextSecondary)
        Text(value ?: "—", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
    }
}

@Composable
private fun AdminRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
    }
}

// ─── Helpers ────────────────────────────────────────────

private val BULAN_INDONESIA = arrayOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

private fun formatDateIndonesian(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return "—"
    return try {
        val date = LocalDate.parse(dateStr.take(10))
        "${date.dayOfMonth} ${BULAN_INDONESIA[date.monthValue - 1]} ${date.year}"
    } catch (_: Exception) {
        dateStr.take(10)
    }
}

private fun getBloodPressureCategory(systolic: Int, diastolic: Int): String = when {
    systolic < 120 && diastolic < 80 -> "Normal"
    systolic in 120..129 && diastolic < 80 -> "Elevated"
    systolic in 130..139 || diastolic in 80..89 -> "Hipertensi Stage 1"
    systolic >= 140 || diastolic >= 90 -> "Hipertensi Stage 2"
    systolic > 180 || diastolic > 120 -> "Krisis Hipertensi"
    else -> "—"
}

private fun getBpColor(category: String): Color = when (category) {
    "Normal" -> Color(0xFF10B981)
    "Elevated" -> Color(0xFFF59E0B)
    "Hipertensi Stage 1" -> Color(0xFFEF4444)
    "Hipertensi Stage 2" -> Color(0xFFDC2626)
    "Krisis Hipertensi" -> Color(0xFF991B1B)
    else -> Color(0xFF6B7280)
}
