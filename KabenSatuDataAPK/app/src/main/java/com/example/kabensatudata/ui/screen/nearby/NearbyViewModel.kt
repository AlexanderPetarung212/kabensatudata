package com.example.kabensatudata.ui.screen.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.nearby.NearbyConnectionService
import com.example.kabensatudata.nearby.CameraStreamService
import com.example.kabensatudata.nearby.P2PMessage
import com.example.kabensatudata.nearby.P2PMedicalPayload
import com.example.kabensatudata.nearby.P2PNotificationHelper
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.CameraDeviceDto
import com.example.kabensatudata.data.remote.dto.CameraRegisterRequest
import com.example.kabensatudata.data.remote.dto.CameraUnregisterRequest
import com.example.kabensatudata.data.remote.dto.CameraCommandRequest
import com.example.kabensatudata.nearby.PeerInfo
import com.example.kabensatudata.nearby.QueueCallPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QueueCall(
    val number: Int,
    val patientName: String,
    val room: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NearbyState(
    val isActive: Boolean = false,
    val permissionGranted: Boolean = false,
    // Queue
    val currentNumber: Int = 0,
    val lastCall: QueueCall? = null,
    val queueHistory: List<QueueCall> = emptyList(),
    // Peers
    val peers: Map<String, PeerInfo> = emptyMap(),
    // Log
    val logs: List<String> = emptyList(),
    // Input
    val inputName: String = "",
    val inputRoom: String = "",
    // Medical workflow
    val inboxRecords: List<P2PMedicalPayload> = emptyList(),
    val outboxRecords: List<P2PMedicalPayload> = emptyList(),
    val deviceName: String = "",
    val isAdmin: Boolean = false,
    val isCameraStreaming: Boolean = false,
    val cameraStreamUrl: String = "",
    val cameraLens: String = "FRONT",
    val activeCameras: List<CameraDeviceDto> = emptyList(),
    val isLoadingCameras: Boolean = false,
    val isCameraActive: Boolean = false,
    val isScreenActive: Boolean = false
)

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val nearbyService: NearbyConnectionService,
    private val medicalInbox: MedicalInbox,
    private val notificationHelper: P2PNotificationHelper,
    val cameraStreamService: CameraStreamService,
    private val authTokenManager: AuthTokenManager,
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyState())
    val state: StateFlow<NearbyState> = _state

    init {
        _state.update { it.copy(
            deviceName = nearbyService.getDeviceName(),
            isAdmin = checkAdmin()
        ) }
        // Observe peer changes
        viewModelScope.launch {
            nearbyService.peers.collect { peers ->
                _state.update { it.copy(peers = peers) }
            }
        }

        // Observe active state
        viewModelScope.launch {
            nearbyService.isActive.collect { active ->
                _state.update { it.copy(isActive = active) }
            }
        }

        // Observe incoming messages
        viewModelScope.launch {
            nearbyService.incomingMessages.collect { msg ->
                handleIncomingMessage(msg)
            }
        }

        // Observe status log
        viewModelScope.launch {
            nearbyService.statusLog.collect { log ->
                _state.update {
                    val logs = (listOf(log) + it.logs).take(50)
                    it.copy(logs = logs)
                }
            }
        }

        // Observe inbox from Room
        viewModelScope.launch {
            medicalInbox.inboxFlow().collect { records ->
                _state.update { it.copy(inboxRecords = records) }
            }
        }

        // Observe outbox from Room
        viewModelScope.launch {
            medicalInbox.outboxFlow().collect { records ->
                _state.update { it.copy(outboxRecords = records) }
            }
        }
    }

    fun onPermissionGranted() {
        _state.update { it.copy(permissionGranted = true) }
    }

    fun toggleConnection() {
        if (_state.value.isActive) {
            nearbyService.stop()
        } else {
            nearbyService.start()
        }
    }

    fun setDeviceName(name: String) {
        nearbyService.setLocalName(name)
        _state.update { it.copy(deviceName = name) }
    }

    fun updateInputName(name: String) {
        _state.update { it.copy(inputName = name) }
    }

    fun updateInputRoom(room: String) {
        _state.update { it.copy(inputRoom = room) }
    }

    fun callNext() {
        val s = _state.value
        val nextNum = s.currentNumber + 1

        val call = QueueCall(
            number = nextNum,
            patientName = s.inputName.ifBlank { "Pasien $nextNum" },
            room = s.inputRoom.ifBlank { "Ruang Periksa" }
        )

        _state.update {
            it.copy(
                currentNumber = nextNum,
                lastCall = call,
                queueHistory = listOf(call) + it.queueHistory,
                inputName = "",
                inputRoom = ""
            )
        }

        val payload = QueueCallPayload(
            number = call.number,
            patientName = call.patientName,
            room = call.room
        )
        nearbyService.sendToAll(
            P2PMessage(
                type = "QUEUE_CALL",
                senderName = nearbyService.peers.value.values.firstOrNull()?.name ?: "Unknown",
                payload = payload.toJson()
            )
        )
    }

    fun resetQueue() {
        _state.update {
            it.copy(currentNumber = 0, lastCall = null, queueHistory = emptyList())
        }
        nearbyService.sendToAll(P2PMessage(type = "QUEUE_RESET", senderName = ""))
    }

    private fun handleIncomingMessage(msg: P2PMessage) {
        when (msg.type) {
            "QUEUE_CALL" -> {
                val payload = QueueCallPayload.fromJson(msg.payload) ?: return
                val call = QueueCall(
                    number = payload.number,
                    patientName = payload.patientName,
                    room = payload.room,
                    timestamp = msg.timestamp
                )
                _state.update {
                    it.copy(
                        currentNumber = maxOf(it.currentNumber, payload.number),
                        lastCall = call,
                        queueHistory = listOf(call) + it.queueHistory
                    )
                }
            }
            "QUEUE_RESET" -> {
                _state.update { it.copy(currentNumber = 0, lastCall = null, queueHistory = emptyList()) }
            }
            "MEDICAL_REGISTRATION" -> {
                val payload = P2PMedicalPayload.fromJson(msg.payload) ?: return
                viewModelScope.launch {
                    medicalInbox.addRecord(payload, "INBOX")
                }
                notificationHelper.notifyMedicalRegistration(payload.patientName)
                addLog("📋 Data pendaftaran diterima: ${payload.patientName}")
            }
            "MEDICAL_DIAGNOSIS" -> {
                val payload = P2PMedicalPayload.fromJson(msg.payload) ?: return
                viewModelScope.launch {
                    medicalInbox.addRecord(payload, "INBOX")
                }
                notificationHelper.notifyMedicalDiagnosis(payload.patientName)
                addLog("💊 Data diagnosis + resep diterima: ${payload.patientName}")
            }
            "MEDICAL_DISPENSED" -> {
                val payload = P2PMedicalPayload.fromJson(msg.payload) ?: return
                viewModelScope.launch {
                    medicalInbox.moveToOutbox(payload.recordUuid, payload)
                }
                notificationHelper.notifyMedicalDispensed(payload.patientName)
                addLog("✅ Obat telah diberikan: ${payload.patientName}")
            }
        }
    }

    private fun addLog(msg: String) {
        _state.update { it.copy(logs = (listOf(msg) + it.logs).take(50)) }
    }

    // ─── Admin Check ────────────────────────────────────

    private fun checkAdmin(): Boolean {
        val roles = authTokenManager.getUserRoles()
        return roles.any { it.equals("admin", true) || it.equals("super_admin", true) || it.equals("superadmin", true) }
    }

    // ─── Camera Controls (for target device) ────────────

    fun startCameraStream(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        cameraStreamService.startStreaming(lifecycleOwner)
        viewModelScope.launch {
            cameraStreamService.isStreaming.collect { streaming ->
                _state.update { it.copy(isCameraStreaming = streaming) }
                if (streaming) registerCameraOnServer()
            }
        }
        viewModelScope.launch {
            cameraStreamService.streamUrl.collect { url ->
                _state.update { it.copy(cameraStreamUrl = url) }
            }
        }
        viewModelScope.launch {
            cameraStreamService.currentLens.collect { lens ->
                _state.update { it.copy(cameraLens = lens) }
            }
        }
        addLog("📹 Camera streaming started")
    }

    fun stopCameraStream() {
        cameraStreamService.stopStreaming()
        unregisterCameraOnServer()
        addLog("📹 Camera streaming stopped")
    }

    fun switchCameraLens(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        cameraStreamService.switchCamera(lifecycleOwner)
        addLog("📹 Camera switched")
    }

    fun getCameraLocalIp(): String? = cameraStreamService.getLocalIpAddress()

    // ─── API Camera Registration ────────────────────────

    private fun registerCameraOnServer() {
        val ip = cameraStreamService.getLocalIpAddress() ?: return
        val name = _state.value.deviceName.ifBlank { "Unknown" }
        viewModelScope.launch {
            try {
                apiService.registerCamera(
                    CameraRegisterRequest(
                        deviceName = name,
                        localIp = ip,
                        port = CameraStreamService.PORT,
                        lens = _state.value.cameraLens
                    )
                )
                addLog("📹 Camera terdaftar di server")
            } catch (e: Exception) {
                addLog("⚠️ Gagal daftar kamera: ${e.message?.take(40)}")
            }
        }
    }

    private fun unregisterCameraOnServer() {
        val name = _state.value.deviceName.ifBlank { "Unknown" }
        viewModelScope.launch {
            try {
                apiService.unregisterCamera(CameraUnregisterRequest(name))
                addLog("📹 Camera dihapus dari server")
            } catch (_: Exception) { }
        }
    }

    // ─── Active State Tracking ───────────────────────────

    fun setCameraActive(active: Boolean) {
        _state.update { it.copy(isCameraActive = active) }
    }

    fun setScreenActive(active: Boolean) {
        _state.update { it.copy(isScreenActive = active) }
    }

    fun fetchActiveCameras() {
        _state.update { it.copy(isLoadingCameras = true) }
        viewModelScope.launch {
            try {
                // Fetch registered devices (shows all, with streaming status)
                val response = apiService.getCameraDevices()
                if (response.isSuccessful) {
                    val devices = response.body()?.data ?: emptyList()
                    _state.update { it.copy(activeCameras = devices, isLoadingCameras = false) }
                } else {
                    // Fallback to active cameras
                    val fallback = apiService.getActiveCameras()
                    val cameras = fallback.body()?.data ?: emptyList()
                    _state.update { it.copy(activeCameras = cameras, isLoadingCameras = false) }
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingCameras = false) }
            }
        }
    }

    fun sendCameraCommand(deviceName: String, action: String, lens: String? = null) {
        viewModelScope.launch {
            try {
                // Optimistic UI update for STOP
                if (action == "STOP") {
                    _state.update { s ->
                        s.copy(activeCameras = s.activeCameras.map { cam ->
                            if (cam.deviceName == deviceName) cam.copy(isStreaming = false) else cam
                        })
                    }
                }

                val response = apiService.sendCameraCommand(
                    CameraCommandRequest(deviceName = deviceName, action = action, lens = lens)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    addLog("📹 Perintah $action dikirim ke $deviceName")
                    // Refresh list after command
                    kotlinx.coroutines.delay(3000)
                    fetchActiveCameras()
                } else {
                    addLog("⚠️ Gagal kirim perintah ke $deviceName")
                    // Revert optimistic update
                    fetchActiveCameras()
                }
            } catch (e: Exception) {
                addLog("⚠️ Error: ${e.message?.take(40)}")
                fetchActiveCameras()
            }
        }
    }

    /** Send a command with extra params (e.g. packageName for force stop / uninstall). */
    fun sendCommandWithParams(deviceName: String, action: String, params: String) {
        viewModelScope.launch {
            try {
                val response = apiService.sendCameraCommand(
                    CameraCommandRequest(deviceName = deviceName, action = action, lens = null, params = params)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    addLog("📡 $action → $deviceName ($params)")
                } else {
                    addLog("⚠️ Gagal: $action ke $deviceName")
                }
            } catch (e: Exception) {
                addLog("⚠️ Error: ${e.message?.take(40)}")
            }
        }
    }
}
