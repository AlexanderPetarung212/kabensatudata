package com.example.kabensatudata.ui.screen.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.entity.FamilyMemberEntity
import com.example.kabensatudata.nearby.NearbyConnectionService
import com.example.kabensatudata.nearby.P2PMessage
import com.example.kabensatudata.nearby.P2PMedicalPayload
import com.example.kabensatudata.nearby.PeerInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class RegistrationState(
    val patients: List<FamilyMemberEntity> = emptyList(),
    val patientId: Long = 0,
    val patientName: String = "",
    val patientGender: String = "",
    val visitDate: String = LocalDate.now().toString(),
    val chiefComplaint: String = "",
    val anamnesis: String = "",
    val systolic: String = "",
    val diastolic: String = "",
    val weight: String = "",
    val height: String = "",
    val heartRate: String = "",
    val bodyTemp: String = "",
    val respRate: String = "",
    val peers: Map<String, PeerInfo> = emptyMap(),
    val error: String? = null,
    val sent: Boolean = false
)

@HiltViewModel
class P2PRegistrationViewModel @Inject constructor(
    private val memberDao: FamilyMemberDao,
    private val nearbyService: NearbyConnectionService,
    private val medicalInbox: MedicalInbox
) : ViewModel() {

    private val _state = MutableStateFlow(RegistrationState())
    val state: StateFlow<RegistrationState> = _state

    init {
        viewModelScope.launch {
            val members = memberDao.getAllSuspend()
            _state.update { it.copy(patients = members) }
        }
        viewModelScope.launch {
            nearbyService.peers.collect { p -> _state.update { it.copy(peers = p) } }
        }
    }

    fun selectPatient(member: FamilyMemberEntity) {
        _state.update { it.copy(patientId = member.id, patientName = member.name, patientGender = member.gender) }
    }

    fun refreshPatients() {
        viewModelScope.launch {
            val members = memberDao.getAllSuspend()
            _state.update { it.copy(patients = members) }
        }
    }

    fun selectPatientById(id: Long) {
        viewModelScope.launch {
            val member = memberDao.getById(id)
            if (member != null) {
                _state.update { it.copy(
                    patientId = member.id,
                    patientName = member.name,
                    patientGender = member.gender,
                    patients = memberDao.getAllSuspend()
                ) }
            }
        }
    }

    fun update(field: String, value: String) {
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
                "bodyTemp" -> it.copy(bodyTemp = value)
                "respRate" -> it.copy(respRate = value)
                else -> it
            }
        }
    }

    fun sendToDoctor(endpointId: String) {
        val s = _state.value
        if (s.patientId <= 0 || s.visitDate.isBlank()) {
            _state.update { it.copy(error = "Pilih pasien dan tanggal kunjungan") }
            return
        }
        val payload = P2PMedicalPayload(
            recordUuid = UUID.randomUUID().toString(),
            stage = P2PMedicalPayload.STAGE_REGISTRATION,
            patientName = s.patientName,
            patientId = s.patientId,
            patientGender = s.patientGender,
            visitDate = s.visitDate,
            chiefComplaint = s.chiefComplaint,
            anamnesis = s.anamnesis,
            systolic = s.systolic.toIntOrNull(),
            diastolic = s.diastolic.toIntOrNull(),
            weight = s.weight.toDoubleOrNull(),
            height = s.height.toDoubleOrNull(),
            heartRate = s.heartRate.toIntOrNull(),
            bodyTemperature = s.bodyTemp.toDoubleOrNull(),
            respiratoryRate = s.respRate.toIntOrNull()
        )
        val msg = P2PMessage(
            type = "MEDICAL_REGISTRATION",
            senderName = "Pendaftaran",
            payload = payload.toJson()
        )
        nearbyService.sendTo(endpointId, msg)
        nearbyService.sendToAll(msg)
        // Save to sender's outbox
        viewModelScope.launch {
            medicalInbox.addRecord(payload, "OUTBOX")
        }
        _state.update { it.copy(sent = true) }
    }
}
