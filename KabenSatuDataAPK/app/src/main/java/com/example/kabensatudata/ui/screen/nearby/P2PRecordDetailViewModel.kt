package com.example.kabensatudata.ui.screen.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.MedicalRecordDao
import com.example.kabensatudata.data.local.entity.MedicalRecordEntity
import com.example.kabensatudata.nearby.P2PMedicalPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RecordDetailState(
    val record: P2PMedicalPayload? = null,
    val synced: Boolean = false,
    val syncing: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
    val successMsg: String? = null
)

@HiltViewModel
class P2PRecordDetailViewModel @Inject constructor(
    private val medicalInbox: MedicalInbox,
    private val medicalRecordDao: MedicalRecordDao
) : ViewModel() {

    private val _state = MutableStateFlow(RecordDetailState())
    val state: StateFlow<RecordDetailState> = _state

    fun loadRecord(uuid: String) {
        viewModelScope.launch {
            val payload = medicalInbox.getRecord(uuid)
            if (payload != null) {
                // Check if already synced to medical_records table
                val existing = medicalRecordDao.getByUuid(uuid)
                _state.update { it.copy(record = payload, synced = existing != null) }
            }
        }
    }

    fun deleteRecord() {
        val record = _state.value.record ?: return
        viewModelScope.launch {
            medicalInbox.removeRecord(record.recordUuid)
            _state.update { it.copy(deleted = true) }
        }
    }

    /**
     * Convert P2P record to MedicalRecordEntity and insert into
     * the main medical_records table with pendingSync = true.
     * When app goes online, SyncWorker will push it to the server.
     */
    fun syncToServer() {
        val record = _state.value.record ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(syncing = true, error = null) }

                // Check if already exists
                val existing = medicalRecordDao.getByUuid(record.recordUuid)
                if (existing != null) {
                    _state.update { it.copy(syncing = false, synced = true, successMsg = "Sudah disinkronkan sebelumnya") }
                    return@launch
                }

                // Convert P2P payload → MedicalRecordEntity
                val entity = MedicalRecordEntity(
                    id = 0, // auto-generate or let server assign
                    uuid = record.recordUuid,
                    familyMemberId = record.patientId,
                    visitDate = record.visitDate,
                    chiefComplaint = record.chiefComplaint.ifBlank { null },
                    anamnesis = record.anamnesis.ifBlank { null },
                    systolic = record.systolic,
                    diastolic = record.diastolic,
                    weight = record.weight,
                    height = record.height,
                    heartRate = record.heartRate,
                    bodyTemperature = record.bodyTemperature,
                    respiratoryRate = record.respiratoryRate,
                    diagnosisCode = record.diagnosisCode,
                    diagnosisName = record.diagnosisName,
                    therapy = record.therapy,
                    medication = record.prescriptions.joinToString("; ") {
                        "${it.medicineName} x${it.quantity} (${it.instruction})"
                    }.ifBlank { null },
                    procedure = record.procedure,
                    workflowStatus = record.stage,
                    pendingSync = true,
                    pendingAction = "create",
                    updatedAt = java.time.LocalDateTime.now().toString()
                )

                medicalRecordDao.insert(entity)
                _state.update {
                    it.copy(syncing = false, synced = true,
                        successMsg = "✅ Berhasil disimpan. Akan disinkronkan saat online.")
                }
            } catch (e: Exception) {
                _state.update { it.copy(syncing = false, error = "Gagal: ${e.message}") }
            }
        }
    }
}
