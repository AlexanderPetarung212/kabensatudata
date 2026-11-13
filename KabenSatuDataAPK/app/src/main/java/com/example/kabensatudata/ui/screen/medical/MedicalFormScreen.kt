package com.example.kabensatudata.ui.screen.medical

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.appTextFieldColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.dao.MedicalRecordDao
import com.example.kabensatudata.data.local.dao.MedicineDao
import com.example.kabensatudata.data.local.dao.MedicineUsageDao
import com.example.kabensatudata.data.local.entity.MedicalRecordEntity
import com.example.kabensatudata.data.local.entity.MedicineEntity
import com.example.kabensatudata.data.local.entity.MedicineUsageEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.SpmRecordLinkRequest
import com.example.kabensatudata.data.remote.dto.SpmRelevantSubIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// ─── Data Classes ────────────────────────────────────────

data class MedicineUsageEntry(
    val tempId: Long = System.nanoTime(),
    val medicineId: Long = 0,
    val medicineName: String = "",
    val medicineUnit: String = "",
    val stockAvailable: Int = 0,
    val quantityUsed: String = "",
    val instructionText: String = "",
    val frequency: String = "",
    val dosage: String = "",
    val notes: String = ""
)

data class MedicalFormState(
    val visitDate: String = "",
    val chiefComplaint: String = "",
    val anamnesis: String = "",
    val systolic: String = "",
    val diastolic: String = "",
    val weight: String = "",
    val height: String = "",
    val heartRate: String = "",
    val bodyTemperature: String = "",
    val respiratoryRate: String = "",
    val diagnosisCode: String = "",
    val diagnosisName: String = "",
    val therapy: String = "",
    val medication: String = "",
    val procedure: String = "",
    val medicineUsages: List<MedicineUsageEntry> = emptyList(),
    val availableMedicines: List<MedicineEntity> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    // SPM
    val spmLoading: Boolean = false,
    val spmRelevant: Boolean = false,
    val spmSubIndicators: List<SpmRelevantSubIndicator> = emptyList(),
    val spmCheckedIds: Set<Long> = emptySet(),
    val spmLinkError: String? = null
)

// ═══════════════════════════════════════════════════════
// ─── ViewModel ────────────────────────────────────────
// ═══════════════════════════════════════════════════════

@HiltViewModel
class MedicalFormViewModel @Inject constructor(
    private val recordDao: MedicalRecordDao,
    private val medicineDao: MedicineDao,
    private val medicineUsageDao: MedicineUsageDao,
    private val tokenManager: AuthTokenManager,
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(MedicalFormState())
    val state: StateFlow<MedicalFormState> = _state

    private var familyMemberId: Long = 0
    private var editingRecord: MedicalRecordEntity? = null

    fun init(memberId: Long, recordId: Long? = null) {
        this.familyMemberId = memberId

        val today = LocalDate.now().toString()
        _state.update { it.copy(visitDate = today) }

        viewModelScope.launch {
            val medicines = medicineDao.getActiveSuspend()
            _state.update { it.copy(availableMedicines = medicines) }
        }

        // Check SPM relevance
        checkSpmRelevance(memberId)

        if (recordId != null && recordId > 0) {
            viewModelScope.launch {
                recordDao.getById(recordId)?.let { r ->
                    editingRecord = r
                    _state.update {
                        it.copy(
                            visitDate = r.visitDate,
                            chiefComplaint = r.chiefComplaint ?: "",
                            anamnesis = r.anamnesis ?: "",
                            systolic = r.systolic?.toString() ?: "",
                            diastolic = r.diastolic?.toString() ?: "",
                            weight = r.weight?.toString() ?: "",
                            height = r.height?.toString() ?: "",
                            heartRate = r.heartRate?.toString() ?: "",
                            bodyTemperature = r.bodyTemperature?.toString() ?: "",
                            respiratoryRate = r.respiratoryRate?.toString() ?: "",
                            diagnosisCode = r.diagnosisCode ?: "",
                            diagnosisName = r.diagnosisName ?: "",
                            therapy = r.therapy ?: "",
                            medication = r.medication ?: "",
                            procedure = r.procedure ?: ""
                        )
                    }

                    val usages = medicineUsageDao.getByRecordSuspend(r.id)
                    val entries = usages.mapNotNull { u ->
                        val med = medicineDao.getById(u.medicineId) ?: return@mapNotNull null
                        MedicineUsageEntry(
                            medicineId = u.medicineId,
                            medicineName = med.fullName,
                            medicineUnit = med.unit,
                            stockAvailable = med.stockQuantity,
                            quantityUsed = u.quantityUsed.toString(),
                            instructionText = u.instructionText ?: "",
                            frequency = u.frequency ?: "",
                            dosage = u.dosage ?: "",
                            notes = u.notes ?: ""
                        )
                    }
                    _state.update { it.copy(medicineUsages = entries) }
                }
            }
        }
    }

    private fun checkSpmRelevance(memberId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(spmLoading = true) }
            try {
                val resp = apiService.checkSpmRelevance(memberId)
                if (resp.isSuccessful) {
                    val d = resp.body()?.data
                    if (d != null) {
                        _state.update {
                            it.copy(
                                spmLoading = false,
                                spmRelevant = d.relevant,
                                spmSubIndicators = d.subIndicators
                            )
                        }
                        return@launch
                    }
                }
                _state.update { it.copy(spmLoading = false) }
            } catch (_: Exception) {
                _state.update { it.copy(spmLoading = false) }
            }
        }
    }

    fun updateField(field: String, value: String) {
        _state.update {
            when (field) {
                "visitDate" -> it.copy(visitDate = value)
                "chiefComplaint" -> it.copy(chiefComplaint = value)
                "anamnesis" -> it.copy(anamnesis = value)
                "systolic" -> it.copy(systolic = value)
                "diastolic" -> it.copy(diastolic = value)
                "weight" -> it.copy(weight = value)
                "height" -> it.copy(height = value)
                "heartRate" -> it.copy(heartRate = value)
                "bodyTemperature" -> it.copy(bodyTemperature = value)
                "respiratoryRate" -> it.copy(respiratoryRate = value)
                "diagnosisCode" -> it.copy(diagnosisCode = value)
                "diagnosisName" -> it.copy(diagnosisName = value)
                "therapy" -> it.copy(therapy = value)
                "procedure" -> it.copy(procedure = value)
                else -> it
            }
        }
    }

    // ─── Medicine Usage Management ──────────────────────
    fun addMedicineUsage() {
        _state.update { it.copy(medicineUsages = it.medicineUsages + MedicineUsageEntry()) }
    }

    fun removeMedicineUsage(tempId: Long) {
        _state.update { it.copy(medicineUsages = it.medicineUsages.filter { u -> u.tempId != tempId }) }
        regenerateMedicationText()
    }

    fun updateMedicineUsage(tempId: Long, field: String, value: String) {
        _state.update { s ->
            s.copy(medicineUsages = s.medicineUsages.map { u ->
                if (u.tempId != tempId) u
                else when (field) {
                    "quantityUsed" -> u.copy(quantityUsed = value)
                    "instructionText" -> {
                        val freq = parseInstruction(value)
                        u.copy(instructionText = value, frequency = freq)
                    }
                    "dosage" -> u.copy(dosage = value)
                    "notes" -> u.copy(notes = value)
                    else -> u
                }
            })
        }
        regenerateMedicationText()
    }

    fun selectMedicine(tempId: Long, medicine: MedicineEntity) {
        _state.update { s ->
            s.copy(medicineUsages = s.medicineUsages.map { u ->
                if (u.tempId != tempId) u
                else u.copy(
                    medicineId = medicine.id,
                    medicineName = medicine.fullName,
                    medicineUnit = medicine.unit,
                    stockAvailable = medicine.stockQuantity
                )
            })
        }
        regenerateMedicationText()
    }

    private fun parseInstruction(text: String): String {
        val regex = Regex("(\\d+)dd(\\d+)", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        return if (match != null) "${match.groupValues[1]}x${match.groupValues[2]}" else text
    }

    private fun regenerateMedicationText() {
        _state.update { s ->
            val lines = s.medicineUsages.filter { it.medicineId > 0 }.map { u ->
                buildString {
                    append(u.medicineName)
                    if (u.quantityUsed.isNotBlank()) append(" - ${u.quantityUsed} ${u.medicineUnit}")
                    if (u.frequency.isNotBlank()) append(" (${u.frequency})")
                    else if (u.instructionText.isNotBlank()) append(" (${u.instructionText})")
                    if (u.notes.isNotBlank()) append(" - ${u.notes}")
                }
            }
            s.copy(medication = lines.joinToString("\n"))
        }
    }

    // ─── SPM Checkbox ──────────────────────────────────
    fun toggleSpmCheck(subId: Long) {
        _state.update {
            val set = it.spmCheckedIds.toMutableSet()
            if (set.contains(subId)) set.remove(subId) else set.add(subId)
            it.copy(spmCheckedIds = set)
        }
    }

    // ─── Save ───────────────────────────────────────────
    fun save() {
        val s = _state.value
        if (s.visitDate.isBlank()) {
            _state.update { it.copy(error = "Tanggal kunjungan harus diisi") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val recordId: Long
                if (editingRecord != null) {
                    recordDao.update(editingRecord!!.copy(
                        visitDate = s.visitDate,
                        chiefComplaint = s.chiefComplaint.ifBlank { null },
                        anamnesis = s.anamnesis.ifBlank { null },
                        systolic = s.systolic.toIntOrNull(),
                        diastolic = s.diastolic.toIntOrNull(),
                        weight = s.weight.toDoubleOrNull(),
                        height = s.height.toDoubleOrNull(),
                        heartRate = s.heartRate.toIntOrNull(),
                        bodyTemperature = s.bodyTemperature.toDoubleOrNull(),
                        respiratoryRate = s.respiratoryRate.toIntOrNull(),
                        diagnosisCode = s.diagnosisCode.ifBlank { null },
                        diagnosisName = s.diagnosisName.ifBlank { null },
                        therapy = s.therapy.ifBlank { null },
                        medication = s.medication.ifBlank { null },
                        procedure = s.procedure.ifBlank { null },
                        pendingSync = true, pendingAction = "update"
                    ))
                    recordId = editingRecord!!.id
                } else {
                    recordId = recordDao.insert(MedicalRecordEntity(
                        id = 0, uuid = java.util.UUID.randomUUID().toString(),
                        familyMemberId = familyMemberId,
                        visitDate = s.visitDate,
                        chiefComplaint = s.chiefComplaint.ifBlank { null },
                        anamnesis = s.anamnesis.ifBlank { null },
                        systolic = s.systolic.toIntOrNull(),
                        diastolic = s.diastolic.toIntOrNull(),
                        weight = s.weight.toDoubleOrNull(),
                        height = s.height.toDoubleOrNull(),
                        heartRate = s.heartRate.toIntOrNull(),
                        bodyTemperature = s.bodyTemperature.toDoubleOrNull(),
                        respiratoryRate = s.respiratoryRate.toIntOrNull(),
                        diagnosisCode = s.diagnosisCode.ifBlank { null },
                        diagnosisName = s.diagnosisName.ifBlank { null },
                        therapy = s.therapy.ifBlank { null },
                        medication = s.medication.ifBlank { null },
                        procedure = s.procedure.ifBlank { null },
                        createdBy = tokenManager.getUserId(),
                        pendingSync = true, pendingAction = "create"
                    ))
                }

                // Save medicine usages
                medicineUsageDao.deleteByRecord(recordId)
                val usageEntities = s.medicineUsages.filter { it.medicineId > 0 }.map { u ->
                    MedicineUsageEntity(
                        medicalRecordId = recordId,
                        medicineId = u.medicineId,
                        quantityUsed = u.quantityUsed.toIntOrNull() ?: 0,
                        instructionText = u.instructionText.ifBlank { null },
                        frequency = u.frequency.ifBlank { null },
                        dosage = u.dosage.ifBlank { null },
                        notes = u.notes.ifBlank { null },
                        pendingSync = true, pendingAction = "create"
                    )
                }
                if (usageEntities.isNotEmpty()) medicineUsageDao.insertAll(usageEntities)

                // Link SPM sub-indicators if any checked
                if (s.spmCheckedIds.isNotEmpty()) {
                    try {
                        apiService.linkSpmRecord(SpmRecordLinkRequest(
                            medicalRecordId = recordId,
                            spmSubIndicatorIds = s.spmCheckedIds.toList()
                        ))
                    } catch (e: Exception) {
                        _state.update { it.copy(spmLinkError = e.message) }
                    }
                }

                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Screen ───────────────────────────────────────────
// ═══════════════════════════════════════════════════════

private val BULAN = arrayOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

private fun formatTanggal(iso: String): String {
    return try {
        val d = LocalDate.parse(iso)
        "${d.dayOfMonth} ${BULAN[d.monthValue - 1]} ${d.year}"
    } catch (_: Exception) { iso }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalFormScreen(
    familyMemberId: Long,
    recordId: Long? = null,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: MedicalFormViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.init(familyMemberId, recordId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    // Section expand states
    var sec1Expanded by remember { mutableStateOf(true) }  // Keluhan
    var sec2Expanded by remember { mutableStateOf(false) } // Vital
    var sec3Expanded by remember { mutableStateOf(false) } // Diagnosis
    var sec4Expanded by remember { mutableStateOf(false) } // Obat
    var sec5Expanded by remember { mutableStateOf(false) } // SPM

    // Date picker
    var showDatePicker by remember { mutableStateOf(false) }

    // SPM banner dismissible
    val spmNotRecorded = state.spmSubIndicators.filter { !it.alreadyRecorded }
    var spmBannerDismissed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (recordId != null) "Edit Rekam Medis" else "Rekam Medis Baru",
                            fontWeight = FontWeight.SemiBold, fontSize = 18.sp
                        )
                        Text(
                            formatTanggal(state.visitDate),
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.TopBarBg,
                    titleContentColor = AppColors.TextOnDark,
                    navigationIconContentColor = AppColors.TextOnDark
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
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ─── SPM Banner ──────────────────────────────────
            if (state.spmRelevant && spmNotRecorded.isNotEmpty() && !spmBannerDismissed) {
                SpmBanner(
                    count = spmNotRecorded.size,
                    onClick = {
                        sec5Expanded = true
                        spmBannerDismissed = true
                    },
                    onDismiss = { spmBannerDismissed = true }
                )
            }

            // ─── Date Picker ─────────────────────────────────
            Surface(
                Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                shape = RoundedCornerShape(14.dp),
                color = AppColors.Surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tanggal Kunjungan *", fontSize = 11.sp, color = AppColors.TextTertiary, fontWeight = FontWeight.SemiBold)
                        Text(
                            formatTanggal(state.visitDate),
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary
                        )
                    }
                    Icon(Icons.Default.Edit, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                }
            }

            // ─── Section 1: Keluhan & Anamnesis ──────────────
            FormSection(
                title = "Keluhan & Anamnesis",
                icon = Icons.Default.Description,
                color = Color(0xFF6366F1),
                expanded = sec1Expanded,
                onToggle = { sec1Expanded = !sec1Expanded },
                hasFilled = state.chiefComplaint.isNotBlank() || state.anamnesis.isNotBlank()
            ) {
                OutlinedTextField(
                    value = state.chiefComplaint,
                    onValueChange = { viewModel.updateField("chiefComplaint", it) },
                    label = { Text("Keluhan Utama") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    colors = appTextFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.anamnesis,
                    onValueChange = { viewModel.updateField("anamnesis", it) },
                    label = { Text("Anamnesis") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    colors = appTextFieldColors()
                )
            }

            // ─── Section 2: Tanda Vital ──────────────────────
            FormSection(
                title = "Tanda Vital",
                icon = Icons.Default.FavoriteBorder,
                color = Color(0xFFEF4444),
                expanded = sec2Expanded,
                onToggle = { sec2Expanded = !sec2Expanded },
                hasFilled = state.systolic.isNotBlank() || state.weight.isNotBlank()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Sistolik (mmHg)", state.systolic, Modifier.weight(1f)) {
                        viewModel.updateField("systolic", it)
                    }
                    NumField("Diastolik (mmHg)", state.diastolic, Modifier.weight(1f)) {
                        viewModel.updateField("diastolic", it)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("BB (kg)", state.weight, Modifier.weight(1f)) { viewModel.updateField("weight", it) }
                    NumField("TB (cm)", state.height, Modifier.weight(1f)) { viewModel.updateField("height", it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Nadi (bpm)", state.heartRate, Modifier.weight(1f)) { viewModel.updateField("heartRate", it) }
                    NumField("Suhu (°C)", state.bodyTemperature, Modifier.weight(1f)) { viewModel.updateField("bodyTemperature", it) }
                }
                Spacer(Modifier.height(8.dp))
                NumField("Resp. Rate (/menit)", state.respiratoryRate, Modifier.fillMaxWidth()) {
                    viewModel.updateField("respiratoryRate", it)
                }
            }

            // ─── Section 3: Diagnosis & Tindakan ─────────────
            FormSection(
                title = "Diagnosis & Tindakan",
                icon = Icons.Default.MedicalInformation,
                color = Color(0xFF10B981),
                expanded = sec3Expanded,
                onToggle = { sec3Expanded = !sec3Expanded },
                hasFilled = state.diagnosisName.isNotBlank()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.diagnosisCode,
                        onValueChange = { viewModel.updateField("diagnosisCode", it) },
                        label = { Text("Kode ICD") }, modifier = Modifier.weight(0.4f),
                        singleLine = true, colors = appTextFieldColors()
                    )
                    OutlinedTextField(
                        value = state.diagnosisName,
                        onValueChange = { viewModel.updateField("diagnosisName", it) },
                        label = { Text("Diagnosis") }, modifier = Modifier.weight(0.6f),
                        singleLine = true, colors = appTextFieldColors()
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.therapy,
                    onValueChange = { viewModel.updateField("therapy", it) },
                    label = { Text("Terapi") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    colors = appTextFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.procedure,
                    onValueChange = { viewModel.updateField("procedure", it) },
                    label = { Text("Tindakan") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    colors = appTextFieldColors()
                )
            }

            // ─── Section 4: Resep Obat ───────────────────────
            FormSection(
                title = "Resep Obat",
                icon = Icons.Default.Medication,
                color = Color(0xFFF59E0B),
                expanded = sec4Expanded,
                onToggle = { sec4Expanded = !sec4Expanded },
                hasFilled = state.medicineUsages.isNotEmpty(),
                badge = if (state.medicineUsages.isNotEmpty()) "${state.medicineUsages.size}" else null
            ) {
                state.medicineUsages.forEachIndexed { index, usage ->
                    MedicineUsageCard(
                        usage = usage,
                        index = index + 1,
                        availableMedicines = state.availableMedicines,
                        onSelectMedicine = { med -> viewModel.selectMedicine(usage.tempId, med) },
                        onUpdateField = { field, value -> viewModel.updateMedicineUsage(usage.tempId, field, value) },
                        onRemove = { viewModel.removeMedicineUsage(usage.tempId) }
                    )
                    if (index < state.medicineUsages.lastIndex) Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.addMedicineUsage() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Accent)
                ) {
                    Icon(Icons.Default.Add, null, tint = AppColors.Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah Obat", color = AppColors.Accent)
                }

                if (state.medication.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.medication, onValueChange = {},
                        label = { Text("Obat (otomatis)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                        readOnly = true, colors = appTextFieldColors()
                    )
                }
            }

            // ─── Section 5: Pelayanan SPM ────────────────────
            if (state.spmRelevant && state.spmSubIndicators.isNotEmpty()) {
                FormSection(
                    title = "Pelayanan SPM",
                    icon = Icons.Default.VerifiedUser,
                    color = Color(0xFF8B5CF6),
                    expanded = sec5Expanded,
                    onToggle = { sec5Expanded = !sec5Expanded },
                    hasFilled = state.spmCheckedIds.isNotEmpty(),
                    badge = if (spmNotRecorded.isNotEmpty()) "${spmNotRecorded.size} baru" else null
                ) {
                    Text(
                        "Pasien ini relevan dengan indikator SPM berikut. Centang untuk mencatat pelayanan.",
                        fontSize = 12.sp, color = AppColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Group by indicator name
                    val grouped = state.spmSubIndicators.groupBy { it.indicatorName }
                    grouped.forEach { (indicatorName, subs) ->
                        Text(
                            indicatorName,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8B5CF6),
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        subs.forEach { sub ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (sub.alreadyRecorded) Color(0xFF10B981).copy(alpha = 0.06f)
                                        else if (state.spmCheckedIds.contains(sub.id)) Color(0xFF8B5CF6).copy(alpha = 0.06f)
                                        else Color.Transparent
                                    )
                                    .clickable(enabled = !sub.alreadyRecorded) {
                                        viewModel.toggleSpmCheck(sub.id)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sub.alreadyRecorded || state.spmCheckedIds.contains(sub.id),
                                    onCheckedChange = { if (!sub.alreadyRecorded) viewModel.toggleSpmCheck(sub.id) },
                                    enabled = !sub.alreadyRecorded,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = if (sub.alreadyRecorded) Color(0xFF10B981) else Color(0xFF8B5CF6),
                                        disabledCheckedColor = Color(0xFF10B981)
                                    )
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        sub.name,
                                        fontSize = 13.sp,
                                        color = AppColors.TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        sub.code,
                                        fontSize = 10.sp,
                                        color = AppColors.TextTertiary
                                    )
                                }
                                if (sub.alreadyRecorded) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            "✓ Tercatat",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Error & Save ────────────────────────────────
            if (state.error != null) {
                Text(state.error!!, color = AppColors.Error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    disabledContainerColor = AppColors.Surface
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Simpan Rekam Medis", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ─── Date Picker Dialog ──────────────────────────────
    if (showDatePicker) {
        val initialMillis = try {
            LocalDate.parse(state.visitDate).toEpochDay() * 86400000L
        } catch (_: Exception) { System.currentTimeMillis() }

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate().toString()
                        viewModel.updateField("visitDate", date)
                    }
                    showDatePicker = false
                }) { Text("OK", color = AppColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Reusable Components ─────────────────────────────
// ═══════════════════════════════════════════════════════

@Composable
private fun SpmBanner(count: Int, onClick: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF8B5CF6).copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Pelayanan SPM Tersedia",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8B5CF6)
                )
                Text(
                    "$count indikator SPM belum tercatat untuk pasien ini. Tap untuk catat.",
                    fontSize = 11.sp, color = AppColors.TextSecondary
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = AppColors.TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FormSection(
    title: String,
    icon: ImageVector,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    hasFilled: Boolean = false,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250), label = "chevron"
    )

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        shadowElevation = if (expanded) 2.dp else 1.dp
    ) {
        Column {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = color.copy(alpha = 0.12f)
                    ) {
                        Text(
                            badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            color = color
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                } else if (hasFilled) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    Icons.Default.ExpandMore, null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(22.dp).rotate(chevronRotation)
                )
            }

            // Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(250)) + fadeOut(tween(150))
            ) {
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = AppColors.DividerColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = appTextFieldColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineUsageCard(
    usage: MedicineUsageEntry,
    index: Int,
    availableMedicines: List<MedicineEntity>,
    onSelectMedicine: (MedicineEntity) -> Unit,
    onUpdateField: (String, String) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Obat #$index", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.Accent)
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Hapus", tint = AppColors.Error, modifier = Modifier.size(18.dp))
                }
            }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = usage.medicineName.ifBlank { "Pilih obat..." },
                    onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = appTextFieldColors(), label = { Text("Obat") }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    availableMedicines.forEach { med ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(med.fullName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(
                                        "Stok: ${med.stockQuantity} ${med.unit}",
                                        fontSize = 12.sp,
                                        color = if (med.stockQuantity <= med.minimumStock) AppColors.Error else AppColors.TextSecondary
                                    )
                                }
                            },
                            onClick = { onSelectMedicine(med); expanded = false }
                        )
                    }
                }
            }

            if (usage.medicineId > 0) {
                Text(
                    "Stok tersedia: ${usage.stockAvailable} ${usage.medicineUnit}",
                    fontSize = 12.sp,
                    color = if (usage.stockAvailable <= 5) AppColors.Error else AppColors.TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = usage.quantityUsed, onValueChange = { onUpdateField("quantityUsed", it) },
                    label = { Text("Jumlah") }, modifier = Modifier.weight(0.35f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appTextFieldColors()
                )
                OutlinedTextField(
                    value = usage.instructionText, onValueChange = { onUpdateField("instructionText", it) },
                    label = { Text("Instruksi (3dd1)") }, modifier = Modifier.weight(0.35f), singleLine = true,
                    colors = appTextFieldColors()
                )
                OutlinedTextField(
                    value = usage.frequency, onValueChange = {},
                    label = { Text("Frekuensi") }, modifier = Modifier.weight(0.3f), singleLine = true,
                    readOnly = true, colors = appTextFieldColors()
                )
            }

            OutlinedTextField(
                value = usage.notes, onValueChange = { onUpdateField("notes", it) },
                label = { Text("Catatan (mis. setelah makan)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = appTextFieldColors()
            )
        }
    }
}
