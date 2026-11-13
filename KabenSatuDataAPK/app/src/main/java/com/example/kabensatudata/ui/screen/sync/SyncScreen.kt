package com.example.kabensatudata.ui.screen.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.dao.*
import com.example.kabensatudata.sync.SyncWorker
import com.example.kabensatudata.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val isOnline: Boolean = false,
    val lastSync: String? = null,
    val pendingBuildings: Int = 0,
    val pendingFamilies: Int = 0,
    val pendingMembers: Int = 0,
    val pendingRecords: Int = 0,
    val totalVillages: Int = 0,
    val totalBuildings: Int = 0,
    val totalFamilies: Int = 0,
    val totalMembers: Int = 0,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
) {
    val totalPending get() = pendingBuildings + pendingFamilies + pendingMembers + pendingRecords
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val tokenManager: AuthTokenManager,
    private val apiService: com.example.kabensatudata.data.remote.ApiService,
    private val buildingDao: BuildingDao,
    private val familyDao: FamilyDao,
    private val familyMemberDao: FamilyMemberDao,
    private val medicalRecordDao: MedicalRecordDao,
    private val medicineDao: MedicineDao,
    private val medicineUsageDao: MedicineUsageDao,
    private val villageDao: VillageDao,
    private val syncQueueDao: SyncQueueDao
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state

    init {
        _state.update { it.copy(lastSync = tokenManager.getLastSyncTimestamp()) }

        viewModelScope.launch {
            NetworkMonitor.connectivityStatus(context).collect { online ->
                _state.update { it.copy(isOnline = online) }
            }
        }

        refreshCounts()
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            _state.update { it.copy(
                pendingBuildings = buildingDao.getPendingSync().size,
                pendingFamilies = familyDao.getPendingSync().size,
                pendingMembers = familyMemberDao.getPendingSync().size,
                pendingRecords = medicalRecordDao.getPendingSync().size
            ) }
        }
    }

    fun syncNow() {
        _state.update { it.copy(isSyncing = true, syncMessage = null) }
        doSync(fullResync = false)
    }

    fun fullResync() {
        _state.update { it.copy(isSyncing = true, syncMessage = null) }
        // Clear the lastSyncTimestamp so pull fetches ALL data
        tokenManager.saveLastSyncTimestamp("")
        doSync(fullResync = true)
    }

    private fun doSync(fullResync: Boolean) {
        viewModelScope.launch {
            try {
                // ── Step 1: PUSH local changes ───────────────
                var pushSummary = ""
                val changes = mutableListOf<com.example.kabensatudata.data.remote.dto.SyncChangeDto>()

                // Collect pending buildings
                val pendingBuildings = buildingDao.getPendingSync()
                for (b in pendingBuildings) {
                    val villageUuid = villageDao.getById(b.villageId)?.uuid ?: continue
                    changes.add(com.example.kabensatudata.data.remote.dto.SyncChangeDto(
                        table = "buildings",
                        action = b.pendingAction ?: "update",
                        uuid = b.uuid,
                        data = mapOf(
                            "building_number" to b.buildingNumber,
                            "village_uuid" to villageUuid,
                            "latitude" to b.latitude,
                            "longitude" to b.longitude,
                            "description" to b.description
                        ),
                        clientUpdatedAt = b.updatedAt ?: java.time.Instant.now().toString()
                    ))
                }

                // Collect pending families
                val pendingFams = familyDao.getPendingSync()
                for (f in pendingFams) {
                    val buildingUuid = buildingDao.getById(f.buildingId)?.uuid ?: continue
                    changes.add(com.example.kabensatudata.data.remote.dto.SyncChangeDto(
                        table = "families",
                        action = f.pendingAction ?: "update",
                        uuid = f.uuid,
                        data = mapOf(
                            "building_uuid" to buildingUuid,
                            "family_number" to f.familyNumber,
                            "head_name" to f.headName,
                            "sequence_number_in_building" to f.sequenceNumberInBuilding,
                            "has_clean_water" to f.hasCleanWater,
                            "is_water_protected" to f.isWaterProtected,
                            "has_toilet" to f.hasToilet,
                            "is_toilet_sanitary" to f.isToiletSanitary,
                            "has_mental_illness" to f.hasMentalIllness,
                            "takes_medication_regularly" to f.takesMedicationRegularly,
                            "has_restrained_member" to f.hasRestrainedMember
                        ),
                        clientUpdatedAt = f.updatedAt ?: java.time.Instant.now().toString()
                    ))
                }

                // Collect pending members
                val pendingMems = familyMemberDao.getPendingSync()
                for (m in pendingMems) {
                    val familyUuid = familyDao.getById(m.familyId)?.uuid ?: continue
                    changes.add(com.example.kabensatudata.data.remote.dto.SyncChangeDto(
                        table = "family_members",
                        action = m.pendingAction ?: "update",
                        uuid = m.uuid,
                        data = mapOf(
                            "family_uuid" to familyUuid,
                            "name" to m.name, "nik" to m.nik, "relationship" to m.relationship,
                            "birth_place" to m.birthPlace, "birth_date" to m.birthDate,
                            "gender" to m.gender, "religion" to m.religion,
                            "education" to m.education, "marital_status" to m.maritalStatus,
                            "occupation" to m.occupation, "address" to m.address,
                            "has_jkn" to m.hasJkn, "is_smoker" to m.isSmoker,
                            "is_pregnant" to m.isPregnant,
                            "has_tuberculosis" to m.hasTuberculosis,
                            "takes_tb_medication_regularly" to m.takesTbMedicationRegularly,
                            "has_chronic_cough" to m.hasChronicCough,
                            "has_hypertension" to m.hasHypertension,
                            "takes_hypertension_medication_regularly" to m.takesHypertensionMedicationRegularly,
                            "uses_contraception" to m.usesContraception,
                            "use_water" to m.useWater, "use_toilet" to m.useToilet,
                            "gave_birth_in_health_facility" to m.gaveBirthInHealthFacility,
                            "exclusive_breastfeeding" to m.exclusiveBreastfeeding,
                            "complete_immunization" to m.completeImmunization,
                            "growth_monitoring" to m.growthMonitoring
                        ),
                        clientUpdatedAt = m.updatedAt ?: java.time.Instant.now().toString()
                    ))
                }

                // Collect pending medical records (include medicine_usages)
                val pendingRecs = medicalRecordDao.getPendingSync()
                for (r in pendingRecs) {
                    val memberUuid = familyMemberDao.getById(r.familyMemberId)?.uuid ?: continue

                    // Collect medicine usages for this record
                    val usages = medicineUsageDao.getByRecordSuspend(r.id)
                    val usagesList = usages.map { u ->
                        mapOf(
                            "medicine_id" to u.medicineId,
                            "quantity_used" to u.quantityUsed,
                            "instruction_text" to u.instructionText,
                            "frequency" to u.frequency,
                            "dosage" to u.dosage,
                            "notes" to u.notes
                        )
                    }

                    val dataMap = mutableMapOf<String, Any?>(
                        "family_member_uuid" to memberUuid,
                        "visit_date" to r.visitDate,
                        "chief_complaint" to r.chiefComplaint, "anamnesis" to r.anamnesis,
                        "systolic" to r.systolic, "diastolic" to r.diastolic,
                        "weight" to r.weight, "height" to r.height,
                        "heart_rate" to r.heartRate, "body_temperature" to r.bodyTemperature,
                        "respiratory_rate" to r.respiratoryRate,
                        "diagnosis_code" to r.diagnosisCode, "diagnosis_name" to r.diagnosisName,
                        "therapy" to r.therapy, "medication" to r.medication,
                        "procedure" to r.procedure
                    )
                    if (usagesList.isNotEmpty()) {
                        dataMap["medicine_usages"] = usagesList
                    }

                    changes.add(com.example.kabensatudata.data.remote.dto.SyncChangeDto(
                        table = "medical_records",
                        action = r.pendingAction ?: "update",
                        uuid = r.uuid,
                        data = dataMap,
                        clientUpdatedAt = r.updatedAt ?: java.time.Instant.now().toString()
                    ))
                }

                if (changes.isNotEmpty()) {
                    android.util.Log.i("SyncVM", "Pushing ${changes.size} changes")
                    val pushResponse = apiService.syncPush(
                        com.example.kabensatudata.data.remote.dto.SyncPushRequest(
                            changes = changes,
                            deviceId = tokenManager.getDeviceId()
                        )
                    )

                    if (pushResponse.isSuccessful) {
                        val pushBody = pushResponse.body()!!
                        android.util.Log.i("SyncVM", "Push: ${pushBody.summary.accepted} accepted, ${pushBody.summary.errors} errors")
                        pushSummary = "📤 Upload: ${pushBody.summary.accepted} diterima"
                        if (pushBody.summary.errors > 0) {
                            pushSummary += ", ${pushBody.summary.errors} error"
                        }
                        // Clear pending flags
                        for (b in pendingBuildings) buildingDao.update(b.copy(pendingSync = false, pendingAction = null))
                        for (f in pendingFams) familyDao.update(f.copy(pendingSync = false, pendingAction = null))
                        for (m in pendingMems) familyMemberDao.update(m.copy(pendingSync = false, pendingAction = null))
                        for (r in pendingRecs) medicalRecordDao.update(r.copy(pendingSync = false, pendingAction = null))
                    } else {
                        val errorBody = pushResponse.errorBody()?.string() ?: ""
                        android.util.Log.e("SyncVM", "Push failed: ${pushResponse.code()} $errorBody")
                        pushSummary = "📤 Upload gagal: HTTP ${pushResponse.code()}"
                    }
                }

                // ── Step 2: PULL server updates ──────────────
                val since = tokenManager.getLastSyncTimestamp()
                android.util.Log.i("SyncVM", "Pulling since=$since")

                val response = apiService.syncPull(since = since)

                if (response.isSuccessful) {
                    val body = response.body()!!
                    val data = body.data

                    // Upsert villages
                    if (data.villages.isNotEmpty()) {
                        villageDao.insertAll(data.villages.map { v ->
                            com.example.kabensatudata.data.local.entity.VillageEntity(
                                id = v.id, uuid = v.uuid, name = v.name, code = v.code,
                                sequenceNumber = v.sequenceNumber, district = v.district,
                                regency = v.regency, province = v.province,
                                updatedAt = v.updatedAt
                            )
                        })
                    }
                    if (data.buildings.isNotEmpty()) {
                        buildingDao.insertAll(data.buildings.map { b ->
                            com.example.kabensatudata.data.local.entity.BuildingEntity(
                                id = b.id, uuid = b.uuid, villageId = b.villageId,
                                buildingNumber = b.buildingNumber,
                                latitude = b.latitude, longitude = b.longitude,
                                description = b.description, updatedAt = b.updatedAt
                            )
                        })
                    }
                    if (data.families.isNotEmpty()) {
                        familyDao.insertAll(data.families.map { f ->
                            com.example.kabensatudata.data.local.entity.FamilyEntity(
                                id = f.id, uuid = f.uuid, buildingId = f.buildingId,
                                familyNumber = f.familyNumber, headName = f.headName,
                                sequenceNumberInBuilding = f.sequenceNumberInBuilding,
                                hasCleanWater = f.hasCleanWater, isWaterProtected = f.isWaterProtected,
                                hasToilet = f.hasToilet, isToiletSanitary = f.isToiletSanitary,
                                hasMentalIllness = f.hasMentalIllness,
                                takesMedicationRegularly = f.takesMedicationRegularly,
                                hasRestrainedMember = f.hasRestrainedMember,
                                updatedAt = f.updatedAt
                            )
                        })
                    }
                    if (data.familyMembers.isNotEmpty()) {
                        familyMemberDao.insertAll(data.familyMembers.map { m ->
                            com.example.kabensatudata.data.local.entity.FamilyMemberEntity(
                                id = m.id, uuid = m.uuid, familyId = m.familyId,
                                name = m.name, nik = m.nik, rmNumber = m.rmNumber,
                                sequenceNumberInFamily = m.sequenceNumberInFamily,
                                relationship = m.relationship, birthPlace = m.birthPlace,
                                birthDate = m.birthDate, gender = m.gender,
                                isPregnant = m.isPregnant, religion = m.religion,
                                education = m.education, maritalStatus = m.maritalStatus,
                                occupation = m.occupation, address = m.address,
                                hasJkn = m.hasJkn, isSmoker = m.isSmoker,
                                useWater = m.useWater, useToilet = m.useToilet,
                                hasTuberculosis = m.hasTuberculosis,
                                takesTbMedicationRegularly = m.takesTbMedicationRegularly,
                                hasChronicCough = m.hasChronicCough,
                                hasHypertension = m.hasHypertension,
                                takesHypertensionMedicationRegularly = m.takesHypertensionMedicationRegularly,
                                usesContraception = m.usesContraception,
                                gaveBirthInHealthFacility = m.gaveBirthInHealthFacility,
                                exclusiveBreastfeeding = m.exclusiveBreastfeeding,
                                completeImmunization = m.completeImmunization,
                                growthMonitoring = m.growthMonitoring,
                                // NIK Verification (SATUSEHAT)
                                ihsNumber = m.ihsNumber,
                                nikVerifiedStatus = m.nikVerifiedStatus,
                                nikVerifiedAt = m.nikVerifiedAt,
                                nikVerifiedSource = m.nikVerifiedSource,
                                updatedAt = m.updatedAt
                            )
                        })
                    }
                    if (data.medicalRecords.isNotEmpty()) {
                        medicalRecordDao.insertAll(data.medicalRecords.map { r ->
                            com.example.kabensatudata.data.local.entity.MedicalRecordEntity(
                                id = r.id, uuid = r.uuid, familyMemberId = r.familyMemberId,
                                visitDate = r.visitDate,
                                chiefComplaint = r.chiefComplaint, anamnesis = r.anamnesis,
                                systolic = r.systolic, diastolic = r.diastolic,
                                weight = r.weight, height = r.height,
                                heartRate = r.heartRate, bodyTemperature = r.bodyTemperature,
                                respiratoryRate = r.respiratoryRate,
                                diagnosisCode = r.diagnosisCode, diagnosisName = r.diagnosisName,
                                therapy = r.therapy, medication = r.medication,
                                procedure = r.procedure,
                                workflowStatus = r.workflowStatus,
                                createdBy = r.createdBy,
                                updatedAt = r.updatedAt
                            )
                        })
                    }

                    tokenManager.saveLastSyncTimestamp(body.serverTime)

                    // ── Step 3: Pull medicines (separate endpoint) ───
                    var medicineCount = 0
                    try {
                        val medResp = apiService.getMedicines()
                        if (medResp.isSuccessful) {
                            val medicines = medResp.body()?.data ?: emptyList()
                            if (medicines.isNotEmpty()) {
                                medicineDao.deleteAll()
                                medicineDao.insertAll(medicines.map { m ->
                                    com.example.kabensatudata.data.local.entity.MedicineEntity(
                                        id = m.id,
                                        name = m.name,
                                        genericName = m.genericName,
                                        strength = m.strength,
                                        stockQuantity = m.stockQuantity,
                                        unit = m.unit,
                                        minimumStock = m.minimumStock,
                                        isActive = m.isActive
                                    )
                                })
                                medicineCount = medicines.size
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SyncVM", "Medicines pull failed: ${e.message}")
                    }

                    val pullSummary = "📥 Download: ${body.counts["villages"] ?: 0} desa, " +
                            "${body.counts["buildings"] ?: 0} bangunan, " +
                            "${body.counts["families"] ?: 0} keluarga, " +
                            "${body.counts["family_members"] ?: 0} anggota" +
                            if (medicineCount > 0) ", $medicineCount obat" else ""

                    val message = if (pushSummary.isNotEmpty()) {
                        "✅ $pushSummary\n$pullSummary"
                    } else {
                        "✅ $pullSummary"
                    }

                    _state.update { it.copy(
                        isSyncing = false,
                        lastSync = body.serverTime,
                        syncMessage = message
                    ) }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("SyncVM", "Pull failed: ${response.code()} - $errorBody")
                    _state.update { it.copy(
                        isSyncing = false,
                        syncMessage = "❌ Pull gagal: HTTP ${response.code()}"
                    ) }
                }
            } catch (e: Exception) {
                android.util.Log.e("SyncVM", "Sync error", e)
                _state.update { it.copy(
                    isSyncing = false,
                    syncMessage = "❌ Error: ${e.message}"
                ) }
            }
            refreshCounts()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Sinkronisasi",
                subtitle = if (state.isOnline) "Terhubung ke server" else "Mode Offline",
                onBack = onBack
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status
            val statusColor = if (state.isOnline) AppColors.Success else AppColors.Error
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = statusColor.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (state.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                        null, Modifier.size(24.dp), tint = statusColor
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (state.isOnline) "Online" else "Offline",
                            fontWeight = FontWeight.Bold, color = statusColor
                        )
                        if (state.lastSync != null) {
                            Text("Sinkronisasi terakhir: ${state.lastSync}",
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // Pending changes
            if (state.totalPending > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📤 Data Menunggu Upload", fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B), fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        if (state.pendingBuildings > 0)
                            Text("• ${state.pendingBuildings} bangunan", color = Color.White.copy(alpha = 0.7f))
                        if (state.pendingFamilies > 0)
                            Text("• ${state.pendingFamilies} keluarga", color = Color.White.copy(alpha = 0.7f))
                        if (state.pendingMembers > 0)
                            Text("• ${state.pendingMembers} anggota keluarga", color = Color.White.copy(alpha = 0.7f))
                        if (state.pendingRecords > 0)
                            Text("• ${state.pendingRecords} rekam medis", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Semua data sudah tersinkronisasi",
                            color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            // Sync button
            Button(
                onClick = { viewModel.syncNow() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = state.isOnline && !state.isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    disabledContainerColor = AppColors.Surface
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Menyinkronkan...")
                } else {
                    Icon(Icons.Default.Sync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sinkronisasi Sekarang")
                }
            }

            // Full Resync button
            var showFullResyncDialog by remember { mutableStateOf(false) }

            if (showFullResyncDialog) {
                AlertDialog(
                    onDismissRequest = { showFullResyncDialog = false },
                    title = { Text("Full Resync", fontWeight = FontWeight.SemiBold) },
                    text = { Text("Ini akan mengunduh SEMUA data dari server. Proses ini membutuhkan waktu dan koneksi internet yang stabil.\n\nLanjutkan?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showFullResyncDialog = false
                            viewModel.fullResync()
                        }) {
                            Text("Ya, Full Resync", color = Color(0xFFFF9B51))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFullResyncDialog = false }) {
                            Text("Batal")
                        }
                    }
                )
            }

            OutlinedButton(
                onClick = { showFullResyncDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = state.isOnline && !state.isSyncing,
                border = BorderStroke(1.dp, Color(0xFFFF9B51).copy(alpha = if (state.isOnline && !state.isSyncing) 0.6f else 0.2f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CloudDownload, null, tint = Color(0xFFFF9B51))
                Spacer(Modifier.width(8.dp))
                Text("Full Resync (Unduh Semua Data)", color = Color(0xFFFF9B51))
            }

            // Sync result message
            if (state.syncMessage != null) {
                val isSuccess = state.syncMessage!!.startsWith("✅")
                val msgColor = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = msgColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, msgColor.copy(alpha = 0.2f))
                ) {
                    Text(state.syncMessage!!,
                        modifier = Modifier.padding(16.dp), color = msgColor)
                }
            }

            if (!state.isOnline) {
                Text(
                    "⚠️ Sinkronisasi membutuhkan koneksi internet. " +
                            "Data yang Anda input akan tetap tersimpan secara offline dan " +
                            "akan otomatis diupload saat koneksi tersedia.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}
