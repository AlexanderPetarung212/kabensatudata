package com.example.kabensatudata.ui.screen.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.DeviceReportDetail
import com.example.kabensatudata.data.remote.dto.DeviceReportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MonitorUiState(
    val isLoading: Boolean = false,
    val devices: List<DeviceReportSummary> = emptyList(),
    val selectedDetail: DeviceReportDetail? = null,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
    val selectedDate: String = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
)

@HiltViewModel
class DeviceMonitorViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    var state by mutableStateOf(MonitorUiState())
        private set

    init {
        loadDevices()
    }

    fun loadDevices(date: String? = null) {
        val queryDate = date ?: state.selectedDate
        state = state.copy(isLoading = true, error = null, selectedDate = queryDate)

        viewModelScope.launch {
            try {
                val response = apiService.getDeviceReports(queryDate)
                if (response.isSuccessful) {
                    state = state.copy(
                        isLoading = false,
                        devices = response.body()?.devices ?: emptyList()
                    )
                } else {
                    state = state.copy(
                        isLoading = false,
                        error = "Gagal memuat data: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    error = "Koneksi error: ${e.message}"
                )
            }
        }
    }

    fun loadDeviceDetail(deviceId: String) {
        state = state.copy(isDetailLoading = true, selectedDetail = null)

        viewModelScope.launch {
            try {
                val response = apiService.getDeviceReportDetail(deviceId, state.selectedDate)
                if (response.isSuccessful) {
                    state = state.copy(
                        isDetailLoading = false,
                        selectedDetail = response.body()?.report
                    )
                } else {
                    state = state.copy(
                        isDetailLoading = false,
                        error = "Gagal memuat detail: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                state = state.copy(
                    isDetailLoading = false,
                    error = "Koneksi error: ${e.message}"
                )
            }
        }
    }

    fun clearDetail() {
        state = state.copy(selectedDetail = null)
    }

    fun clearError() {
        state = state.copy(error = null)
    }
}
