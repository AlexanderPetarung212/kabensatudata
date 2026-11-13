package com.example.kabensatudata.ui.screen.member

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.screen.family.FormSectionHeader
import com.example.kabensatudata.ui.screen.family.HealthToggleCard
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.AppTopBar
import com.example.kabensatudata.ui.theme.appTextFieldColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.entity.FamilyMemberEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// ═══════════════════════════════════════════════════════
// ─── Options ──────────────────────────────────────────
// ═══════════════════════════════════════════════════════

private val RELATIONSHIPS = listOf(
    "Kepala Keluarga", "Istri", "Suami", "Anak", "Menantu",
    "Cucu", "Orang Tua", "Mertua", "Pembantu", "Lainnya"
)
private val GENDERS = listOf("Laki-laki", "Perempuan")
private val RELIGIONS = listOf("Islam", "Kristen", "Katolik", "Hindu", "Buddha", "Konghucu", "Lainnya")
private val EDUCATIONS = listOf(
    "Tidak Pernah Sekolah", "Tidak Tamat SD/MI", "Tamat SD/MI",
    "Tamat SMP/MTs", "Tamat SMA/MA/SMK", "Tamat D1/D2/D3",
    "Tamat D4/S1", "Tamat S2/S3"
)
private val MARITAL_STATUSES = listOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati")
private val OCCUPATIONS = listOf(
    "Tidak Kerja", "Sekolah", "ASN", "TNI/Polri", "Honorer",
    "Pegawai Swasta", "Nelayan", "Petani", "IRT", "Lainnya"
)

// ═══════════════════════════════════════════════════════
// ─── State ────────────────────────────────────────────
// ═══════════════════════════════════════════════════════

data class MemberFormState(
    val name: String = "",
    val nik: String = "",
    val relationship: String = "",
    val gender: String = "",
    val birthPlace: String = "",
    val birthDate: String = "",   // YYYY-MM-DD
    val religion: String = "",
    val education: String = "",
    val maritalStatus: String = "",
    val occupation: String = "",
    // Health
    val isPregnant: Boolean = false,
    val hasJkn: Boolean = false,
    val isSmoker: Boolean = false,
    val useWater: Boolean = false,
    val useToilet: Boolean = false,
    val hasTuberculosis: Boolean = false,
    val takesTbMedicationRegularly: Boolean = false,
    val hasChronicCough: Boolean = false,
    val hasHypertension: Boolean = false,
    val takesHypertensionMedicationRegularly: Boolean = false,
    val usesContraception: Boolean = false,
    val gaveBirthInHealthFacility: Boolean = false,
    val exclusiveBreastfeeding: Boolean = false,
    val completeImmunization: Boolean = false,
    val growthMonitoring: Boolean = false,
    // UI
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

// ═══════════════════════════════════════════════════════
// ─── Age helpers ──────────────────────────────────────
// ═══════════════════════════════════════════════════════

private fun parseAge(birthDate: String): Int? {
    if (birthDate.isBlank()) return null
    return try {
        val bd = LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE)
        Period.between(bd, LocalDate.now()).years
    } catch (_: Exception) { null }
}

private fun parseAgeMonths(birthDate: String): Long? {
    if (birthDate.isBlank()) return null
    return try {
        val bd = LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE)
        ChronoUnit.MONTHS.between(bd, LocalDate.now())
    } catch (_: Exception) { null }
}

// ═══════════════════════════════════════════════════════
// ─── ViewModel ────────────────────────────────────────
// ═══════════════════════════════════════════════════════

@HiltViewModel
class MemberFormViewModel @Inject constructor(
    private val memberDao: FamilyMemberDao
) : ViewModel() {

    private val _state = MutableStateFlow(MemberFormState())
    val state: StateFlow<MemberFormState> = _state

    private var familyId: Long = 0
    private var editingMember: FamilyMemberEntity? = null

    fun init(familyId: Long, memberId: Long? = null) {
        this.familyId = familyId
        if (memberId != null && memberId > 0) {
            viewModelScope.launch {
                memberDao.getById(memberId)?.let { m ->
                    editingMember = m
                    _state.update {
                        it.copy(
                            name = m.name, nik = m.nik ?: "", relationship = m.relationship,
                            gender = m.gender, birthPlace = m.birthPlace ?: "",
                            birthDate = m.birthDate ?: "", religion = m.religion ?: "",
                            education = m.education ?: "", maritalStatus = m.maritalStatus ?: "",
                            occupation = m.occupation ?: "",
                            isPregnant = m.isPregnant ?: false, hasJkn = m.hasJkn ?: false,
                            isSmoker = m.isSmoker ?: false, useWater = m.useWater ?: false,
                            useToilet = m.useToilet ?: false,
                            hasTuberculosis = m.hasTuberculosis ?: false,
                            takesTbMedicationRegularly = m.takesTbMedicationRegularly ?: false,
                            hasChronicCough = m.hasChronicCough ?: false,
                            hasHypertension = m.hasHypertension ?: false,
                            takesHypertensionMedicationRegularly = m.takesHypertensionMedicationRegularly ?: false,
                            usesContraception = m.usesContraception ?: false,
                            gaveBirthInHealthFacility = m.gaveBirthInHealthFacility ?: false,
                            exclusiveBreastfeeding = m.exclusiveBreastfeeding ?: false,
                            completeImmunization = m.completeImmunization ?: false,
                            growthMonitoring = m.growthMonitoring ?: false
                        )
                    }
                }
            }
        }
    }

    fun updateField(field: String, value: Any) {
        _state.update {
            when (field) {
                "name" -> it.copy(name = value as String)
                "nik" -> it.copy(nik = value as String)
                "relationship" -> it.copy(relationship = value as String)
                "gender" -> it.copy(gender = value as String)
                "birthPlace" -> it.copy(birthPlace = value as String)
                "birthDate" -> it.copy(birthDate = value as String)
                "religion" -> it.copy(religion = value as String)
                "education" -> it.copy(education = value as String)
                "maritalStatus" -> it.copy(maritalStatus = value as String)
                "occupation" -> it.copy(occupation = value as String)
                // Toggles
                "isPregnant" -> it.copy(isPregnant = value as Boolean)
                "hasJkn" -> it.copy(hasJkn = value as Boolean)
                "isSmoker" -> it.copy(isSmoker = value as Boolean)
                "useWater" -> it.copy(useWater = value as Boolean)
                "useToilet" -> it.copy(useToilet = value as Boolean)
                "hasTuberculosis" -> it.copy(
                    hasTuberculosis = value as Boolean,
                    takesTbMedicationRegularly = if (!(value as Boolean)) false else it.takesTbMedicationRegularly
                )
                "takesTbMedicationRegularly" -> it.copy(takesTbMedicationRegularly = value as Boolean)
                "hasChronicCough" -> it.copy(hasChronicCough = value as Boolean)
                "hasHypertension" -> it.copy(
                    hasHypertension = value as Boolean,
                    takesHypertensionMedicationRegularly = if (!(value as Boolean)) false else it.takesHypertensionMedicationRegularly
                )
                "takesHypertensionMedicationRegularly" -> it.copy(takesHypertensionMedicationRegularly = value as Boolean)
                "usesContraception" -> it.copy(usesContraception = value as Boolean)
                "gaveBirthInHealthFacility" -> it.copy(gaveBirthInHealthFacility = value as Boolean)
                "exclusiveBreastfeeding" -> it.copy(exclusiveBreastfeeding = value as Boolean)
                "completeImmunization" -> it.copy(completeImmunization = value as Boolean)
                "growthMonitoring" -> it.copy(growthMonitoring = value as Boolean)
                else -> it
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(error = "Nama harus diisi") }; return
        }
        if (s.relationship.isBlank()) {
            _state.update { it.copy(error = "Hubungan keluarga harus dipilih") }; return
        }
        if (s.gender.isBlank()) {
            _state.update { it.copy(error = "Jenis kelamin harus dipilih") }; return
        }
        if (s.birthDate.isBlank()) {
            _state.update { it.copy(error = "Tanggal lahir harus diisi") }; return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                if (editingMember != null) {
                    memberDao.update(editingMember!!.copy(
                        name = s.name, nik = s.nik.ifBlank { null }, relationship = s.relationship,
                        gender = s.gender, birthPlace = s.birthPlace.ifBlank { null },
                        birthDate = s.birthDate.ifBlank { null }, religion = s.religion.ifBlank { null },
                        education = s.education.ifBlank { null }, maritalStatus = s.maritalStatus.ifBlank { null },
                        occupation = s.occupation.ifBlank { null },
                        isPregnant = s.isPregnant, hasJkn = s.hasJkn, isSmoker = s.isSmoker,
                        useWater = s.useWater, useToilet = s.useToilet,
                        hasTuberculosis = s.hasTuberculosis,
                        takesTbMedicationRegularly = s.takesTbMedicationRegularly,
                        hasChronicCough = s.hasChronicCough, hasHypertension = s.hasHypertension,
                        takesHypertensionMedicationRegularly = s.takesHypertensionMedicationRegularly,
                        usesContraception = s.usesContraception,
                        gaveBirthInHealthFacility = s.gaveBirthInHealthFacility,
                        exclusiveBreastfeeding = s.exclusiveBreastfeeding,
                        completeImmunization = s.completeImmunization,
                        growthMonitoring = s.growthMonitoring,
                        pendingSync = true, pendingAction = "update"
                    ))
                } else {
                    memberDao.insert(FamilyMemberEntity(
                        id = 0, uuid = java.util.UUID.randomUUID().toString(),
                        familyId = familyId, name = s.name, nik = s.nik.ifBlank { null },
                        relationship = s.relationship, gender = s.gender,
                        birthPlace = s.birthPlace.ifBlank { null },
                        birthDate = s.birthDate.ifBlank { null },
                        religion = s.religion.ifBlank { null },
                        education = s.education.ifBlank { null },
                        maritalStatus = s.maritalStatus.ifBlank { null },
                        occupation = s.occupation.ifBlank { null },
                        isPregnant = s.isPregnant, hasJkn = s.hasJkn, isSmoker = s.isSmoker,
                        useWater = s.useWater, useToilet = s.useToilet,
                        hasTuberculosis = s.hasTuberculosis,
                        takesTbMedicationRegularly = s.takesTbMedicationRegularly,
                        hasChronicCough = s.hasChronicCough, hasHypertension = s.hasHypertension,
                        takesHypertensionMedicationRegularly = s.takesHypertensionMedicationRegularly,
                        usesContraception = s.usesContraception,
                        gaveBirthInHealthFacility = s.gaveBirthInHealthFacility,
                        exclusiveBreastfeeding = s.exclusiveBreastfeeding,
                        completeImmunization = s.completeImmunization,
                        growthMonitoring = s.growthMonitoring,
                        pendingSync = true, pendingAction = "create"
                    ))
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Composable ───────────────────────────────────────
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberFormScreen(
    familyId: Long,
    memberId: Long? = null,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: MemberFormViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.init(familyId, memberId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    // Derived age
    val age = remember(state.birthDate) { parseAge(state.birthDate) }
    val ageMonths = remember(state.birthDate) { parseAgeMonths(state.birthDate) }
    val isFemale = state.gender == "Perempuan"
    val isMale = state.gender == "Laki-laki"

    // DatePicker state
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            if (state.birthDate.isNotBlank()) {
                LocalDate.parse(state.birthDate).toEpochDay() * 86_400_000L
            } else null
        } catch (_: Exception) { null }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / 86_400_000L)
                        viewModel.updateField("birthDate", date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Batal") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = { AppTopBar(if (memberId != null) "Edit Anggota" else "Tambah Anggota", onBack) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ══════════════════════════════════════════════
            // SECTION 1: Informasi Dasar
            // ══════════════════════════════════════════════
            FormSectionHeader("Informasi Dasar", Icons.Default.Person)

            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateField("name", it) },
                label = { Text("Nama Lengkap *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = appTextFieldColors()
            )

            OutlinedTextField(
                value = state.nik,
                onValueChange = { if (it.length <= 16 && it.all { c -> c.isDigit() }) viewModel.updateField("nik", it) },
                label = { Text("NIK") },
                supportingText = { Text("16 digit NIK tanpa spasi") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = appTextFieldColors()
            )

            // Hubungan Keluarga
            FormDropdown("Hubungan dengan Kepala Keluarga *", state.relationship, RELATIONSHIPS) {
                viewModel.updateField("relationship", it)
            }

            // Tempat & Tanggal Lahir
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.birthPlace,
                    onValueChange = { viewModel.updateField("birthPlace", it) },
                    label = { Text("Tempat Lahir") },
                    modifier = Modifier.weight(1f), singleLine = true,
                    colors = appTextFieldColors()
                )
            }

            // DatePicker trigger
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CardBorder)
            ) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.birthDate.isNotBlank()) "Tanggal Lahir: ${state.birthDate}"
                    else "Pilih Tanggal Lahir *",
                    fontSize = 14.sp
                )
                if (age != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppColors.Accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "$age thn",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent
                        )
                    }
                }
            }

            // Gender
            Text("Jenis Kelamin *", fontSize = 13.sp, color = AppColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GENDERS.forEach { g ->
                    FilterChip(
                        selected = state.gender == g,
                        onClick = { viewModel.updateField("gender", g) },
                        label = { Text(g) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Accent.copy(alpha = 0.15f),
                            selectedLabelColor = AppColors.Accent
                        )
                    )
                }
            }

            // Marital Status — umur > 15
            AnimatedVisibility(age != null && age > 15, enter = expandVertically(), exit = shrinkVertically()) {
                FormDropdown("Status Perkawinan", state.maritalStatus, MARITAL_STATUSES) {
                    viewModel.updateField("maritalStatus", it)
                }
            }

            // Religion
            FormDropdown("Agama", state.religion, RELIGIONS) {
                viewModel.updateField("religion", it)
            }

            // Education — umur >= 6
            AnimatedVisibility(age != null && age >= 6, enter = expandVertically(), exit = shrinkVertically()) {
                FormDropdown("Pendidikan Terakhir", state.education, EDUCATIONS) {
                    viewModel.updateField("education", it)
                }
            }

            // ══════════════════════════════════════════════
            // SECTION: Khusus Perempuan
            // ══════════════════════════════════════════════
            AnimatedVisibility(isFemale && age != null && age in 10..54, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSectionHeader("Pertanyaan Khusus Perempuan", Icons.Default.Female)
                    HealthToggleCard(
                        label = "Sedang hamil?",
                        checked = state.isPregnant,
                        onCheckedChange = { viewModel.updateField("isPregnant", it) }
                    )
                }
            }

            // ══════════════════════════════════════════════
            // SECTION: Informasi Umum — Pekerjaan (umur > 10)
            // ══════════════════════════════════════════════
            AnimatedVisibility(age != null && age > 10, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSectionHeader("Informasi Umum", Icons.Default.Work)
                    FormDropdown("Pekerjaan", state.occupation, OCCUPATIONS) {
                        viewModel.updateField("occupation", it)
                    }
                }
            }

            // ══════════════════════════════════════════════
            // SECTION: Informasi Kesehatan
            // ══════════════════════════════════════════════
            Spacer(Modifier.height(4.dp))
            FormSectionHeader("Informasi Kesehatan", Icons.Default.HealthAndSafety)

            // 1. JKN
            HealthToggleCard("1. Memiliki kartu JKN?", state.hasJkn) {
                viewModel.updateField("hasJkn", it)
            }

            // 2. Merokok — umur > 15
            AnimatedVisibility(age != null && age > 15, enter = expandVertically(), exit = shrinkVertically()) {
                HealthToggleCard("2. Apakah merokok?", state.isSmoker) {
                    viewModel.updateField("isSmoker", it)
                }
            }

            // 3. BAB di jamban
            HealthToggleCard("3. Apakah buang air besar di jamban?", state.useToilet) {
                viewModel.updateField("useToilet", it)
            }

            // 4. Air bersih
            HealthToggleCard("4. Menggunakan air bersih?", state.useWater) {
                viewModel.updateField("useWater", it)
            }

            // 5. TBC
            HealthToggleCard("5. Pernah didiagnosis TBC Paru?", state.hasTuberculosis) {
                viewModel.updateField("hasTuberculosis", it)
            }
            AnimatedVisibility(state.hasTuberculosis, enter = expandVertically(), exit = shrinkVertically()) {
                HealthToggleCard("6. Minum obat TBC secara teratur?", state.takesTbMedicationRegularly, indent = true) {
                    viewModel.updateField("takesTbMedicationRegularly", it)
                }
            }

            // 7. Batuk kronis
            HealthToggleCard("7. Batuk berdahak lebih dari 2 minggu?", state.hasChronicCough) {
                viewModel.updateField("hasChronicCough", it)
            }

            // 8. Hipertensi — umur > 15
            AnimatedVisibility(age != null && age > 15, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthToggleCard("8. Didiagnosis tekanan darah tinggi?", state.hasHypertension) {
                        viewModel.updateField("hasHypertension", it)
                    }
                    AnimatedVisibility(state.hasHypertension, enter = expandVertically(), exit = shrinkVertically()) {
                        HealthToggleCard("9. Minum obat darah tinggi teratur?", state.takesHypertensionMedicationRegularly, indent = true) {
                            viewModel.updateField("takesHypertensionMedicationRegularly", it)
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════
            // SECTION: Kesehatan Reproduksi & Anak
            // ══════════════════════════════════════════════
            val showReproSection = remember(age, isFemale, isMale, state.relationship) {
                if (age == null) false
                else {
                    val rel = state.relationship
                    val femaleRepro = isFemale && age in 10..54 && rel in listOf("Kepala Keluarga", "Istri")
                    val maleRepro = isMale && age > 10 && rel in listOf("Kepala Keluarga", "Suami")
                    val childHealth = rel in listOf("Anak", "Cucu") && age < 5
                    femaleRepro || maleRepro || childHealth
                }
            }

            AnimatedVisibility(showReproSection, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(4.dp))
                    FormSectionHeader("Kesehatan Reproduksi & Anak", Icons.Default.ChildCare)

                    // 11. KB — Kawin + (♀ 10-54 tidak hamil, atau ♂ > 10)
                    val showKb = remember(age, isFemale, isMale, state.maritalStatus, state.isPregnant) {
                        if (state.maritalStatus != "Kawin" || age == null) false
                        else if (isFemale) age in 10..54 && !state.isPregnant
                        else if (isMale) age > 10
                        else false
                    }
                    AnimatedVisibility(showKb, enter = expandVertically(), exit = shrinkVertically()) {
                        HealthToggleCard("11. Menggunakan kontrasepsi / ikut KB?", state.usesContraception) {
                            viewModel.updateField("usesContraception", it)
                        }
                    }

                    // 12. Melahirkan di faskes — ♀ >= 18, KK/Istri
                    val showBirth = isFemale && age != null && age >= 18 &&
                            state.relationship in listOf("Kepala Keluarga", "Istri")
                    AnimatedVisibility(showBirth, enter = expandVertically(), exit = shrinkVertically()) {
                        HealthToggleCard("12. Melahirkan di fasilitas kesehatan?", state.gaveBirthInHealthFacility) {
                            viewModel.updateField("gaveBirthInHealthFacility", it)
                        }
                    }

                    // 13. ASI eksklusif — Anak/Cucu, 7-23 bulan
                    val showAsi = ageMonths != null && ageMonths in 7..23 &&
                            state.relationship in listOf("Anak", "Cucu")
                    AnimatedVisibility(showAsi, enter = expandVertically(), exit = shrinkVertically()) {
                        HealthToggleCard("13. Bayi diberi ASI eksklusif 0-6 bulan?", state.exclusiveBreastfeeding) {
                            viewModel.updateField("exclusiveBreastfeeding", it)
                        }
                    }

                    // 14. Imunisasi — Anak/Cucu, 12-23 bulan
                    val showImun = ageMonths != null && ageMonths in 12..23 &&
                            state.relationship in listOf("Anak", "Cucu")
                    AnimatedVisibility(showImun, enter = expandVertically(), exit = shrinkVertically()) {
                        HealthToggleCard("14. Imunisasi lengkap 0-11 bulan?", state.completeImmunization) {
                            viewModel.updateField("completeImmunization", it)
                        }
                    }

                    // 15. Pemantauan tumbuh kembang — Anak/Cucu, 2-59 bulan
                    val showGrowth = ageMonths != null && ageMonths in 2..59 &&
                            state.relationship in listOf("Anak", "Cucu")
                    AnimatedVisibility(showGrowth, enter = expandVertically(), exit = shrinkVertically()) {
                        HealthToggleCard("15. Pemantauan pertumbuhan dalam 1 bulan terakhir?", state.growthMonitoring) {
                            viewModel.updateField("growthMonitoring", it)
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════
            // Error & Save
            // ══════════════════════════════════════════════
            if (state.error != null) {
                Text(state.error!!, color = AppColors.Error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
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
                    Text("Simpan")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Dropdown Component ───────────────────────────────
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
