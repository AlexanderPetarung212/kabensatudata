package com.example.kabensatudata.ui.screen.medical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.MedicalRecordDao
import com.example.kabensatudata.data.local.dao.MemberWithRecordCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MedicalHubUiState(
    val totalRecords: Int = 0,
    val totalPatients: Int = 0,
    val members: List<MemberWithRecordCount> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class MedicalHubViewModel @Inject constructor(
    private val medicalRecordDao: MedicalRecordDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalHubUiState())
    val uiState: StateFlow<MedicalHubUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    init {
        loadStats()
        setupSearch()
    }

    private fun loadStats() {
        viewModelScope.launch {
            val totalRecords = try { medicalRecordDao.countAll() } catch (_: Exception) { 0 }
            val totalPatients = try { medicalRecordDao.countDistinctMembers() } catch (_: Exception) { 0 }
            _uiState.update { it.copy(totalRecords = totalRecords, totalPatients = totalPatients) }
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val flow = if (query.length >= 2) {
                        medicalRecordDao.searchMembersWithRecords(query)
                    } else {
                        medicalRecordDao.getMembersWithRecords()
                    }
                    flow.collect { members ->
                        _uiState.update { it.copy(members = members, isLoading = false) }
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }
}
