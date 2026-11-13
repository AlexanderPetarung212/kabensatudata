package com.example.kabensatudata.ui.screen.medical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── State ──────────────────────────────────────────────

data class MedicalDashboardState(
    val isLoading: Boolean = true,
    val topDiseases: List<DiseaseCountDto> = emptyList(),
    val topMedicines: List<MedicineUsageCountDto> = emptyList(),
    val dailyVisits: List<DailyVisitDto> = emptyList(),
    val totalVisits: Int = 0,
    val totalPatients: Int = 0,
    val dateFrom: String? = null,   // yyyy-MM-dd
    val dateTo: String? = null,     // yyyy-MM-dd
    val periodLabel: String = "30 hari terakhir",
    val error: String? = null
)

data class MedicineListState(
    val isLoading: Boolean = true,
    val medicines: List<MedicineFullDto> = emptyList(),
    val searchQuery: String = "",
    val filterUnit: String? = null,
    val filterStock: String? = null, // "low", "out", "available"
    val error: String? = null,
    val successMessage: String? = null
) {
    val filteredMedicines: List<MedicineFullDto> get() {
        var list = medicines
        if (searchQuery.length >= 2) {
            val q = searchQuery.lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                (it.genericName?.lowercase()?.contains(q) == true)
            }
        }
        filterUnit?.let { unit -> list = list.filter { it.unit == unit } }
        filterStock?.let { status ->
            list = when (status) {
                "out" -> list.filter { it.stockQuantity <= 0 }
                "low" -> list.filter { it.stockQuantity in 1..it.minimumStock }
                "available" -> list.filter { it.stockQuantity > it.minimumStock }
                else -> list
            }
        }
        return list
    }
}

data class MedicineFormState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val genericName: String = "",
    val strength: String = "",
    val unit: String = "tablet",
    val stockQuantity: String = "0",
    val minimumStock: String = "10",
    val description: String = "",
    val isActive: Boolean = true,
    val editingId: Long? = null,
    val error: String? = null,
    val saved: Boolean = false
)

// ─── ViewModel ──────────────────────────────────────────

@HiltViewModel
class MedicineViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _dashboardState = MutableStateFlow(MedicalDashboardState())
    val dashboardState: StateFlow<MedicalDashboardState> = _dashboardState

    private val _listState = MutableStateFlow(MedicineListState())
    val listState: StateFlow<MedicineListState> = _listState

    private val _formState = MutableStateFlow(MedicineFormState())
    val formState: StateFlow<MedicineFormState> = _formState

    // ─── Dashboard ──────────────────────────────────────

    fun loadDashboard(dateFrom: String? = null, dateTo: String? = null) {
        viewModelScope.launch {
            _dashboardState.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = apiService.getMedicalDashboard(dateFrom, dateTo)
                if (resp.isSuccessful && resp.body() != null) {
                    val d = resp.body()!!
                    _dashboardState.update {
                        it.copy(
                            isLoading = false,
                            topDiseases = d.topDiseases,
                            topMedicines = d.topMedicines,
                            dailyVisits = d.dailyVisits,
                            totalVisits = d.summary.totalVisits,
                            totalPatients = d.summary.totalPatients,
                            dateFrom = dateFrom,
                            dateTo = dateTo
                        )
                    }
                } else {
                    _dashboardState.update { it.copy(isLoading = false, error = "Gagal memuat dashboard") }
                }
            } catch (e: Exception) {
                _dashboardState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setDateRange(from: String?, to: String?, label: String) {
        _dashboardState.update { it.copy(dateFrom = from, dateTo = to, periodLabel = label) }
        loadDashboard(from, to)
    }

    // ─── Medicine List ──────────────────────────────────

    fun loadMedicines() {
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = apiService.getMedicines()
                if (resp.isSuccessful && resp.body() != null) {
                    val meds = resp.body()!!.data.map { m ->
                        MedicineFullDto(
                            id = m.id, name = m.name, genericName = m.genericName,
                            strength = m.strength, stockQuantity = m.stockQuantity,
                            stockInitial = 0, unit = m.unit, minimumStock = m.minimumStock,
                            description = null, isActive = m.isActive
                        )
                    }
                    _listState.update { it.copy(isLoading = false, medicines = meds) }
                } else {
                    _listState.update { it.copy(isLoading = false, error = "Gagal memuat obat") }
                }
            } catch (e: Exception) {
                _listState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onSearchChanged(q: String) = _listState.update { it.copy(searchQuery = q) }
    fun onFilterUnit(u: String?) = _listState.update { it.copy(filterUnit = u) }
    fun onFilterStock(s: String?) = _listState.update { it.copy(filterStock = s) }
    fun clearSuccessMessage() = _listState.update { it.copy(successMessage = null) }

    fun deleteMedicine(id: Long) {
        viewModelScope.launch {
            try {
                val resp = apiService.deleteMedicine(id)
                if (resp.isSuccessful) {
                    _listState.update {
                        it.copy(
                            medicines = it.medicines.filter { m -> m.id != id },
                            successMessage = "Obat berhasil dihapus"
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun adjustStock(id: Long, adjustment: Int, reason: String) {
        viewModelScope.launch {
            try {
                val resp = apiService.adjustStock(id, StockAdjustRequest(adjustment, reason))
                if (resp.isSuccessful && resp.body() != null) {
                    val updated = resp.body()!!.data
                    _listState.update {
                        it.copy(
                            medicines = it.medicines.map { m -> if (m.id == id) updated else m },
                            successMessage = resp.body()!!.message
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ─── Medicine Form ──────────────────────────────────

    fun initForm(medicineId: Long? = null) {
        if (medicineId != null) {
            val med = _listState.value.medicines.find { it.id == medicineId }
            if (med != null) {
                _formState.value = MedicineFormState(
                    editingId = med.id, name = med.name,
                    genericName = med.genericName ?: "", strength = med.strength ?: "",
                    unit = med.unit, stockQuantity = med.stockQuantity.toString(),
                    minimumStock = med.minimumStock.toString(),
                    description = med.description ?: "", isActive = med.isActive
                )
            }
        } else {
            _formState.value = MedicineFormState()
        }
    }

    fun onFormName(v: String) = _formState.update { it.copy(name = v) }
    fun onFormGenericName(v: String) = _formState.update { it.copy(genericName = v) }
    fun onFormStrength(v: String) = _formState.update { it.copy(strength = v) }
    fun onFormUnit(v: String) = _formState.update { it.copy(unit = v) }
    fun onFormStock(v: String) = _formState.update { it.copy(stockQuantity = v) }
    fun onFormMinStock(v: String) = _formState.update { it.copy(minimumStock = v) }
    fun onFormDescription(v: String) = _formState.update { it.copy(description = v) }
    fun onFormActive(v: Boolean) = _formState.update { it.copy(isActive = v) }

    fun saveMedicine() {
        viewModelScope.launch {
            val f = _formState.value
            if (f.name.isBlank()) {
                _formState.update { it.copy(error = "Nama obat wajib diisi") }
                return@launch
            }
            _formState.update { it.copy(isSaving = true, error = null) }

            val body = MedicineCreateRequest(
                name = f.name, genericName = f.genericName.ifBlank { null },
                strength = f.strength.ifBlank { null }, unit = f.unit,
                stockQuantity = f.stockQuantity.toIntOrNull() ?: 0,
                minimumStock = f.minimumStock.toIntOrNull() ?: 10,
                description = f.description.ifBlank { null }, isActive = f.isActive
            )

            try {
                val resp = if (f.editingId != null) {
                    apiService.updateMedicine(f.editingId, body)
                } else {
                    apiService.createMedicine(body)
                }

                if (resp.isSuccessful) {
                    _formState.update { it.copy(isSaving = false, saved = true) }
                } else {
                    _formState.update { it.copy(isSaving = false, error = "Gagal menyimpan") }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
