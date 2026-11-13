package com.example.kabensatudata.ui.screen.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.MedicineDao
import com.example.kabensatudata.nearby.NearbyConnectionService
import com.example.kabensatudata.nearby.P2PMessage
import com.example.kabensatudata.nearby.P2PMedicalPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PharmacyState(
    val record: P2PMedicalPayload? = null,
    val checkedItems: Set<Int> = emptySet(),
    val error: String? = null,
    val completed: Boolean = false
)

@HiltViewModel
class P2PPharmacyViewModel @Inject constructor(
    private val nearbyService: NearbyConnectionService,
    private val medicineDao: MedicineDao,
    private val medicalInbox: MedicalInbox
) : ViewModel() {

    private val _state = MutableStateFlow(PharmacyState())
    val state: StateFlow<PharmacyState> = _state

    fun loadRecord(uuid: String) {
        viewModelScope.launch {
            val payload = medicalInbox.getRecord(uuid)
            if (payload != null) {
                _state.update { it.copy(record = payload) }
            }
        }
    }

    fun toggleItem(index: Int) {
        _state.update {
            val checked = it.checkedItems.toMutableSet()
            if (checked.contains(index)) checked.remove(index) else checked.add(index)
            it.copy(checkedItems = checked)
        }
    }

    fun dispenseAndComplete() {
        val record = _state.value.record ?: return
        viewModelScope.launch {
            try {
                // Deduct stock for dispensed items
                val checkedItems = _state.value.checkedItems
                record.prescriptions.forEachIndexed { i, rx ->
                    if (checkedItems.contains(i) && rx.medicineId > 0 && rx.quantity > 0) {
                        medicineDao.deductStock(rx.medicineId, rx.quantity)
                    }
                }

                // Broadcast completion
                val payload = record.copy(stage = P2PMedicalPayload.STAGE_DISPENSED)
                nearbyService.sendToAll(P2PMessage(
                    type = "MEDICAL_DISPENSED",
                    senderName = "Farmasi",
                    payload = payload.toJson()
                ))

                medicalInbox.moveToOutbox(record.recordUuid, payload)
                _state.update { it.copy(completed = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Gagal: ${e.message}") }
            }
        }
    }
}
