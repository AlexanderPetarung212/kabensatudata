package com.example.kabensatudata.nearby

import android.content.Context
import android.util.Log
import com.example.kabensatudata.data.local.AuthTokenManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class PeerInfo(
    val endpointId: String,
    val name: String,
    val deviceId: String = "",
    val connectedAt: Long = System.currentTimeMillis()
)

/**
 * Singleton service managing Nearby Connections for P2P communication.
 * Uses P2P_CLUSTER strategy for true peer-to-peer (no master).
 * All devices advertise AND discover simultaneously.
 */
@Singleton
class NearbyConnectionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authTokenManager: AuthTokenManager
) {
    companion object {
        private const val TAG = "NearbyP2P"
        private const val SERVICE_ID = "com.example.kabensatudata.p2p"
    }

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val messageRelay = MessageRelay()
    private var localName = ""
    private var retryCount = 0
    private val maxRetries = 3
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // Cache endpoint names from onConnectionInitiated for use in onConnectionResult
    private val pendingNames = mutableMapOf<String, String>()

    // Persistent device ID + custom name
    private val prefs = context.getSharedPreferences("nearby_p2p", Context.MODE_PRIVATE)
    private val deviceId: String = prefs.getString("device_id", null) ?: run {
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).apply()
        id
    }

    init {
        // Priority: 1) Manual override  2) Login user (first name)  3) Device model
        localName = prefs.getString("device_name", null)
            ?: extractFirstName(authTokenManager.getUserName())
            ?: "KabenSatu-${android.os.Build.MODEL}"
    }

    /** Extract first meaningful name from full name, e.g. "Ns. Makdalena Ilely, S.Kep" → "Makdalena" */
    private fun extractFirstName(fullName: String): String? {
        if (fullName.isBlank()) return null
        val cleaned = fullName
            .replace(Regex("(Ns\\.|dr\\.|S\\.Kep|S\\.Ked|SKM|S\\.KM|,)", RegexOption.IGNORE_CASE), "")
            .trim()
        val parts = cleaned.split(" ").filter { it.isNotBlank() && it.length > 1 }
        return parts.firstOrNull()?.replaceFirstChar { it.uppercase() }
    }

    fun getDeviceId(): String = deviceId
    fun getDeviceName(): String = localName

    // ─── State ─────────────────────────────────────────

    private val _peers = MutableStateFlow<Map<String, PeerInfo>>(emptyMap())
    val peers: StateFlow<Map<String, PeerInfo>> = _peers

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _incomingMessages = MutableSharedFlow<P2PMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<P2PMessage> = _incomingMessages

    private val _statusLog = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val statusLog: SharedFlow<String> = _statusLog

    // ─── Lifecycle ─────────────────────────────────────

    fun setLocalName(name: String) {
        localName = name
        prefs.edit().putString("device_name", name).apply()
    }

    fun start() {
        if (_isActive.value) return
        _isActive.value = true
        retryCount = 0
        messageRelay.clearCache()
        startAdvertising()
        startDiscovery()
        scheduleRediscovery()
        emitLog("P2P aktif — mencari perangkat...")
    }

    fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        handler.removeCallbacksAndMessages(null)
        _peers.value = emptyMap()
        _isActive.value = false
        emitLog("P2P dinonaktifkan")
    }

    /** Periodically restart discovery to find new devices */
    private fun scheduleRediscovery() {
        handler.postDelayed({
            if (_isActive.value) {
                try {
                    connectionsClient.stopDiscovery()
                } catch (_: Exception) {}
                startDiscovery()
                scheduleRediscovery()
            }
        }, 15_000) // every 15 seconds
    }

    // ─── Advertising ───────────────────────────────────

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener { Log.d(TAG, "Advertising started") }
            .addOnFailureListener { e ->
                Log.e(TAG, "Advertising failed", e)
                emitLog("Gagal advertise: ${e.message}")
            }
    }

    // ─── Discovery ─────────────────────────────────────

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener { Log.d(TAG, "Discovery started"); retryCount = 0 }
            .addOnFailureListener { e ->
                Log.e(TAG, "Discovery failed", e)
                if (retryCount < maxRetries && _isActive.value) {
                    retryCount++
                    emitLog("Gagal discover, mencoba lagi ($retryCount/$maxRetries)...")
                    handler.postDelayed({ if (_isActive.value) startDiscovery() }, 2000L)
                } else {
                    emitLog("Gagal discover: ${e.message}")
                }
            }
    }

    // ─── Connection Lifecycle ──────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated: ${info.endpointName} ($endpointId)")
            // Cache the human-readable name for use in onConnectionResult
            pendingNames[endpointId] = info.endpointName
            // Auto-accept — same SERVICE_ID implies same app
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            emitLog("Menghubungkan ke ${info.endpointName}...")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val peerName = pendingNames.remove(endpointId) ?: endpointId
                Log.d(TAG, "Connected: $peerName ($endpointId)")
                _peers.update { it + (endpointId to PeerInfo(endpointId, peerName)) }
                emitLog("Terhubung ke $peerName")
                // Send PEER_HELLO so peer knows our persistent deviceId
                sendTo(endpointId, P2PMessage(
                    type = "PEER_HELLO",
                    senderId = deviceId,
                    senderName = localName
                ))
            } else {
                pendingNames.remove(endpointId)
                Log.d(TAG, "Connection failed: $endpointId status=${result.status}")
                emitLog("Gagal terhubung")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected: $endpointId")
            val peer = _peers.value[endpointId]
            _peers.update { it - endpointId }
            emitLog("${peer?.name ?: endpointId} terputus")
        }
    }

    // ─── Endpoint Discovery ────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: ${info.endpointName} ($endpointId)")
            emitLog("Ditemukan: ${info.endpointName}")
            // Auto-connect to all discovered endpoints
            connectionsClient.requestConnection(localName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e -> Log.d(TAG, "requestConnection failed: ${e.message}") }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    // ─── Payload (Message Receiving) ───────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val json = String(bytes, Charsets.UTF_8)
            val message = P2PMessage.fromJson(json) ?: return

            Log.d(TAG, "Received: type=${message.type} from=$endpointId ttl=${message.ttl}")

            // Dedup check
            if (!messageRelay.shouldProcess(message)) return

            // Handle PEER_HELLO: update peer's persistent deviceId + name
            if (message.type == "PEER_HELLO") {
                _peers.update { map ->
                    val existing = map[endpointId]
                    if (existing != null) {
                        map + (endpointId to existing.copy(
                            name = message.senderName.ifBlank { existing.name },
                            deviceId = message.senderId
                        ))
                    } else map
                }
                Log.d(TAG, "PEER_HELLO from $endpointId: deviceId=${message.senderId}, name=${message.senderName}")
                return // Don't relay or emit PEER_HELLO
            }

            // Update peer name if we learn it from message
            if (message.senderName.isNotBlank()) {
                _peers.update { map ->
                    val existing = map[endpointId]
                    if (existing != null && existing.name == endpointId) {
                        map + (endpointId to existing.copy(name = message.senderName))
                    } else map
                }
            }

            // Emit to local listeners
            _incomingMessages.tryEmit(message)

            // Relay to other peers (mesh) — but NOT chat-specific messages (they are point-to-point)
            val noRelay = setOf("CHAT_MESSAGE", "CHAT_DELIVERED", "CHAT_READ", "CHAT_TYPING")
            if (message.type !in noRelay) {
                val relayMsg = messageRelay.prepareRelay(message)
                if (relayMsg != null) {
                    val otherPeers = _peers.value.keys.filter { it != endpointId }
                    otherPeers.forEach { peerId ->
                        sendPayload(peerId, relayMsg.toJson())
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Not needed for byte payloads (instant)
        }
    }

    // ─── Sending ───────────────────────────────────────

    fun sendToAll(message: P2PMessage) {
        messageRelay.markSeen(message.id)
        val json = message.toJson()
        val peerIds = _peers.value.keys.toList()
        if (peerIds.isEmpty()) {
            Log.d(TAG, "No peers to send to")
            return
        }
        peerIds.forEach { peerId ->
            sendPayload(peerId, json)
        }
        Log.d(TAG, "Sent to ${peerIds.size} peers: type=${message.type}")
    }

    fun sendTo(endpointId: String, message: P2PMessage): Boolean {
        messageRelay.markSeen(message.id)
        return sendPayload(endpointId, message.toJson())
    }

    /** Find endpointId by persistent deviceId */
    fun findEndpointByDeviceId(deviceId: String): String? {
        return _peers.value.entries.firstOrNull { it.value.deviceId == deviceId }?.key
    }

    private fun sendPayload(endpointId: String, json: String): Boolean {
        return try {
            val payload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(endpointId, payload)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send failed to $endpointId", e)
            false
        }
    }

    private fun emitLog(msg: String) {
        _statusLog.tryEmit(msg)
    }
}
