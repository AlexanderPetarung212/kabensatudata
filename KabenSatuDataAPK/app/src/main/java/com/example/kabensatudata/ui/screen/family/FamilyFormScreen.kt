package com.example.kabensatudata.ui.screen.family

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.entity.FamilyEntity
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.AppTopBar
import com.example.kabensatudata.ui.theme.appTextFieldColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════════
// ─── State ────────────────────────────────────────────
// ═══════════════════════════════════════════════════════

data class FamilyFormState(
    val familyNumber: String = "",
    val headName: String = "",
    // Health indicators
    val hasCleanWater: Boolean = false,
    val isWaterProtected: Boolean = false,
    val hasToilet: Boolean = false,
    val isToiletSanitary: Boolean = false,
    val hasMentalIllness: Boolean = false,
    val takesMedicationRegularly: Boolean = false,
    val hasRestrainedMember: Boolean = false,
    // UI
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

// ═══════════════════════════════════════════════════════
// ─── ViewModel ────────────────────────────────────────
// ═══════════════════════════════════════════════════════

@HiltViewModel
class FamilyFormViewModel @Inject constructor(
    private val familyDao: FamilyDao
) : ViewModel() {

    private val _state = MutableStateFlow(FamilyFormState())
    val state: StateFlow<FamilyFormState> = _state

    private var buildingId: Long = 0
    private var editingFamily: FamilyEntity? = null

    fun init(buildingId: Long, familyId: Long? = null) {
        this.buildingId = buildingId
        if (familyId != null && familyId > 0) {
            viewModelScope.launch {
                familyDao.getById(familyId)?.let { f ->
                    editingFamily = f
                    _state.update {
                        it.copy(
                            familyNumber = f.familyNumber, headName = f.headName,
                            hasCleanWater = f.hasCleanWater ?: false,
                            isWaterProtected = f.isWaterProtected ?: false,
                            hasToilet = f.hasToilet ?: false,
                            isToiletSanitary = f.isToiletSanitary ?: false,
                            hasMentalIllness = f.hasMentalIllness ?: false,
                            takesMedicationRegularly = f.takesMedicationRegularly ?: false,
                            hasRestrainedMember = f.hasRestrainedMember ?: false
                        )
                    }
                }
            }
        } else {
            viewModelScope.launch {
                val count = familyDao.countByBuilding(buildingId)
                _state.update { it.copy(familyNumber = "${count + 1}") }
            }
        }
    }

    fun onFieldChange(field: String, value: String) {
        _state.update {
            when (field) {
                "familyNumber" -> it.copy(familyNumber = value)
                "headName" -> it.copy(headName = value)
                else -> it
            }
        }
    }

    fun onToggle(field: String, value: Boolean) {
        _state.update {
            when (field) {
                "hasCleanWater" -> it.copy(
                    hasCleanWater = value,
                    isWaterProtected = if (!value) false else it.isWaterProtected
                )
                "isWaterProtected" -> it.copy(isWaterProtected = value)
                "hasToilet" -> it.copy(
                    hasToilet = value,
                    isToiletSanitary = if (!value) false else it.isToiletSanitary
                )
                "isToiletSanitary" -> it.copy(isToiletSanitary = value)
                "hasMentalIllness" -> it.copy(
                    hasMentalIllness = value,
                    takesMedicationRegularly = if (!value) false else it.takesMedicationRegularly,
                    hasRestrainedMember = if (!value) false else it.hasRestrainedMember
                )
                "takesMedicationRegularly" -> it.copy(takesMedicationRegularly = value)
                "hasRestrainedMember" -> it.copy(hasRestrainedMember = value)
                else -> it
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.familyNumber.isBlank() || s.headName.isBlank()) {
            _state.update { it.copy(error = "No KK dan Nama Kepala Keluarga harus diisi") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                if (editingFamily != null) {
                    familyDao.update(editingFamily!!.copy(
                        familyNumber = s.familyNumber, headName = s.headName,
                        hasCleanWater = s.hasCleanWater, isWaterProtected = s.isWaterProtected,
                        hasToilet = s.hasToilet, isToiletSanitary = s.isToiletSanitary,
                        hasMentalIllness = s.hasMentalIllness,
                        takesMedicationRegularly = s.takesMedicationRegularly,
                        hasRestrainedMember = s.hasRestrainedMember,
                        pendingSync = true, pendingAction = "update"
                    ))
                } else {
                    val seq = familyDao.countByBuilding(buildingId) + 1
                    familyDao.insert(FamilyEntity(
                        id = 0, uuid = java.util.UUID.randomUUID().toString(),
                        buildingId = buildingId, familyNumber = s.familyNumber,
                        headName = s.headName, sequenceNumberInBuilding = seq,
                        hasCleanWater = s.hasCleanWater, isWaterProtected = s.isWaterProtected,
                        hasToilet = s.hasToilet, isToiletSanitary = s.isToiletSanitary,
                        hasMentalIllness = s.hasMentalIllness,
                        takesMedicationRegularly = s.takesMedicationRegularly,
                        hasRestrainedMember = s.hasRestrainedMember,
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
fun FamilyFormScreen(
    buildingId: Long,
    familyId: Long? = null,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: FamilyFormViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.init(buildingId, familyId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = { AppTopBar(if (familyId != null) "Edit Keluarga" else "Tambah Keluarga", onBack) },
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
            // ─── Section: Informasi Keluarga ──────────────
            FormSectionHeader("Informasi Keluarga", Icons.Default.FamilyRestroom)

            OutlinedTextField(
                value = state.familyNumber,
                onValueChange = { viewModel.onFieldChange("familyNumber", it) },
                label = { Text("No Urut Keluarga *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = appTextFieldColors()
            )
            OutlinedTextField(
                value = state.headName,
                onValueChange = { viewModel.onFieldChange("headName", it) },
                label = { Text("Nama Kepala Keluarga *") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = appTextFieldColors()
            )

            Spacer(Modifier.height(4.dp))

            // ─── Section: Kesehatan Keluarga ──────────────
            FormSectionHeader("Informasi Kesehatan Keluarga", Icons.Default.HealthAndSafety)

            // 3. Air bersih
            HealthToggleCard(
                label = "3. Tersedia sarana air bersih di lingkungan rumah?",
                checked = state.hasCleanWater,
                onCheckedChange = { viewModel.onToggle("hasCleanWater", it) }
            )
            AnimatedVisibility(state.hasCleanWater, enter = expandVertically(), exit = shrinkVertically()) {
                HealthToggleCard(
                    label = "4. Jenis sumber air terlindungi?",
                    checked = state.isWaterProtected,
                    onCheckedChange = { viewModel.onToggle("isWaterProtected", it) },
                    indent = true
                )
            }

            // 5. Jamban
            HealthToggleCard(
                label = "5. Tersedia jamban keluarga?",
                checked = state.hasToilet,
                onCheckedChange = { viewModel.onToggle("hasToilet", it) }
            )
            AnimatedVisibility(state.hasToilet, enter = expandVertically(), exit = shrinkVertically()) {
                HealthToggleCard(
                    label = "6. Jenis jamban saniter?",
                    checked = state.isToiletSanitary,
                    onCheckedChange = { viewModel.onToggle("isToiletSanitary", it) },
                    indent = true
                )
            }

            // 7. Gangguan jiwa
            HealthToggleCard(
                label = "7. Ada anggota keluarga gangguan jiwa berat?",
                checked = state.hasMentalIllness,
                onCheckedChange = { viewModel.onToggle("hasMentalIllness", it) }
            )
            AnimatedVisibility(state.hasMentalIllness, enter = expandVertically(), exit = shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthToggleCard(
                        label = "8. Penderita minum obat jiwa secara teratur?",
                        checked = state.takesMedicationRegularly,
                        onCheckedChange = { viewModel.onToggle("takesMedicationRegularly", it) },
                        indent = true
                    )
                    HealthToggleCard(
                        label = "9. Ada anggota keluarga yang dipasung?",
                        checked = state.hasRestrainedMember,
                        onCheckedChange = { viewModel.onToggle("hasRestrainedMember", it) },
                        indent = true
                    )
                }
            }

            // Error
            if (state.error != null) {
                Text(state.error!!, color = AppColors.Error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))

            // Save
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
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Shared Components (used by MemberFormScreen too) ─
// ═══════════════════════════════════════════════════════

@Composable
fun FormSectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
    }
}

@Composable
fun HealthToggleCard(
    label: String,
    checked: Boolean,
    indent: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) AppColors.Success.copy(alpha = 0.3f) else AppColors.CardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (indent) 28.dp else 14.dp,
                    end = 14.dp, top = 10.dp, bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = AppColors.TextPrimary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.Success,
                    uncheckedThumbColor = AppColors.TextTertiary,
                    uncheckedTrackColor = AppColors.Surface
                )
            )
        }
    }
}
