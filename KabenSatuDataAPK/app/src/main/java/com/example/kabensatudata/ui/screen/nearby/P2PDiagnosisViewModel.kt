package com.example.kabensatudata.ui.screen.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.MedicineDao
import com.example.kabensatudata.data.local.entity.MedicineEntity
import com.example.kabensatudata.nearby.NearbyConnectionService
import com.example.kabensatudata.nearby.P2PMessage
import com.example.kabensatudata.nearby.P2PMedicalPayload
import com.example.kabensatudata.nearby.PeerInfo
import com.example.kabensatudata.nearby.PrescriptionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosisState(
    val record: P2PMedicalPayload? = null,
    val diagnosisCode: String = "",
    val diagnosisName: String = "",
    val therapy: String = "",
    val procedure: String = "",
    val prescriptions: List<PrescriptionItem> = emptyList(),
    val medicines: List<MedicineEntity> = emptyList(),
    val peers: Map<String, PeerInfo> = emptyMap(),
    val error: String? = null,
    val sent: Boolean = false
)

@HiltViewModel
class P2PDiagnosisViewModel @Inject constructor(
    private val nearbyService: NearbyConnectionService,
    private val medicineDao: MedicineDao,
    private val medicalInbox: MedicalInbox
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosisState())
    val state: StateFlow<DiagnosisState> = _state

    init {
        viewModelScope.launch {
            medicineDao.getAll().collect { meds -> _state.update { it.copy(medicines = meds) } }
        }
        viewModelScope.launch {
            nearbyService.peers.collect { p -> _state.update { it.copy(peers = p) } }
        }
    }

    fun loadRecord(uuid: String) {
        viewModelScope.launch {
            val payload = medicalInbox.getRecord(uuid)
            if (payload != null) {
                _state.update { it.copy(record = payload) }
            }
        }
    }

    fun update(field: String, value: String) {
        _state.update {
            when (field) {
                "diagnosisCode" -> it.copy(diagnosisCode = value)
                "diagnosisName" -> it.copy(diagnosisName = value)
                "therapy" -> it.copy(therapy = value)
                "procedure" -> it.copy(procedure = value)
                else -> it
            }
        }
    }

    fun addPrescription() {
        _state.update { it.copy(prescriptions = it.prescriptions + PrescriptionItem()) }
    }

    fun removePrescription(index: Int) {
        _state.update { it.copy(prescriptions = it.prescriptions.toMutableList().apply { removeAt(index) }) }
    }

    fun selectMedicine(index: Int, medicine: MedicineEntity) {
        _state.update {
            val list = it.prescriptions.toMutableList()
            list[index] = list[index].copy(
                medicineId = medicine.id,
                medicineName = medicine.fullName,
                medicineUnit = medicine.unit
            )
            it.copy(prescriptions = list)
        }
    }

    fun updatePrescription(index: Int, field: String, value: String) {
        _state.update {
            val list = it.prescriptions.toMutableList()
            list[index] = when (field) {
                "quantity" -> list[index].copy(quantity = value.toIntOrNull() ?: 0)
                "instruction" -> list[index].copy(instruction = value)
                "notes" -> list[index].copy(notes = value)
                else -> list[index]
            }
            it.copy(prescriptions = list)
        }
    }

    fun sendToPharmacy(endpointId: String) {
        val s = _state.value
        val record = s.record ?: return

        val payload = record.copy(
            stage = P2PMedicalPayload.STAGE_DIAGNOSIS,
            diagnosisCode = s.diagnosisCode,
            diagnosisName = s.diagnosisName,
            therapy = s.therapy,
            procedure = s.procedure,
            prescriptions = s.prescriptions
        )
        val msg = P2PMessage(
            type = "MEDICAL_DIAGNOSIS",
            senderName = "Dokter",
            payload = payload.toJson()
        )
        nearbyService.sendTo(endpointId, msg)
        nearbyService.sendToAll(msg)
        viewModelScope.launch {
            medicalInbox.removeRecord(record.recordUuid)
            medicalInbox.addRecord(payload, "OUTBOX")
        }
        _state.update { it.copy(sent = true) }
    }
}
