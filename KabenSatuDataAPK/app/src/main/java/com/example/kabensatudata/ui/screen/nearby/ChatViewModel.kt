package com.example.kabensatudata.ui.screen.nearby

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.ChatMessageDao
import com.example.kabensatudata.data.local.entity.ChatMessageEntity
import com.example.kabensatudata.nearby.NearbyConnectionService
import com.example.kabensatudata.nearby.P2PMessage
import com.example.kabensatudata.nearby.P2PNotificationHelper
import com.example.kabensatudata.nearby.PeerInfo
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Payload for individual chat messages over P2P */
data class ChatPayload(
    val messageId: String = UUID.randomUUID().toString(),
    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val mediaType: String = "text",    // "text", "image", "voice"
    val mediaBase64: String = "",      // Base64 encoded media
    val mediaDuration: Int = 0         // Duration in seconds (voice)
) {
    fun toJson(): String = Gson().toJson(this)
    companion object {
        fun fromJson(json: String): ChatPayload? = try { Gson().fromJson(json, ChatPayload::class.java) } catch (_: Exception) { null }
    }
}

// ─── Conversation List State ─────────────────────────────
data class ConversationItem(
    val peerId: String,
    val peerName: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

data class ChatListState(
    val conversations: List<ConversationItem> = emptyList(),
    val peers: Map<String, PeerInfo> = emptyMap(),
    val totalUnread: Int = 0
)

// ─── Single Chat State ───────────────────────────────────
data class ChatState(
    val peerId: String = "",        // peer's deviceId (persistent)
    val endpointId: String = "",    // peer's endpointId (for sending)
    val peerName: String = "",
    val messages: List<ChatMessageEntity> = emptyList(),
    val inputText: String = "",
    val isOnline: Boolean = false,
    val peerIsTyping: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatDao: ChatMessageDao,
    private val nearbyService: NearbyConnectionService,
    private val notificationHelper: P2PNotificationHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _listState = MutableStateFlow(ChatListState())
    val listState: StateFlow<ChatListState> = _listState

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState

    private var currentPeerId: String? = null
    private var typingJob: Job? = null
    private var typingClearJob: Job? = null

    init {
        // Observe conversations
        viewModelScope.launch {
            chatDao.getLatestPerConversation().collect { latest ->
                val peers = nearbyService.peers.value
                val convos = latest.map { msg ->
                    val peerId = msg.conversationId
                    val peerName = if (msg.isMine) {
                        // For sent messages: find peer name from active connections
                        peers.values.firstOrNull { it.deviceId == peerId }?.name
                            ?: msg.senderName.ifBlank { null }
                            ?: peerId.take(8)
                    } else {
                        // For received messages: use sender name
                        msg.senderName.ifBlank { peers.values.firstOrNull { it.deviceId == peerId }?.name ?: peerId.take(8) }
                    }
                    ConversationItem(
                        peerId = peerId,
                        peerName = peerName,
                        lastMessage = when (msg.mediaType) {
                            "image" -> "📷 Foto"
                            "voice" -> "🎤 Voice Note"
                            else -> msg.content
                        },
                        lastTimestamp = msg.timestamp,
                        isOnline = peers.values.any { it.deviceId == peerId }
                    )
                }
                _listState.update { it.copy(conversations = convos) }
            }
        }

        // Observe peers
        viewModelScope.launch {
            nearbyService.peers.collect { p -> _listState.update { it.copy(peers = p) } }
        }

        // Observe total unread
        viewModelScope.launch {
            chatDao.getTotalUnreadCount().collect { count ->
                _listState.update { it.copy(totalUnread = count) }
            }
        }

        // Observe incoming chat messages
        viewModelScope.launch {
            nearbyService.incomingMessages.collect { msg ->
                when (msg.type) {
                    "CHAT_MESSAGE" -> handleChatMessage(msg)
                    "CHAT_DELIVERED" -> handleDeliveryReceipt(msg)
                    "CHAT_READ" -> handleReadReceipt(msg)
                    "CHAT_TYPING" -> handleTypingIndicator(msg)
                }
            }
        }
    }

    // ─── Conversation List ───────────────────────────────

    fun startChatWithPeer(peerId: String) {
        val peer = nearbyService.peers.value[peerId]
        val peerDeviceId = peer?.deviceId?.ifBlank { peerId } ?: peerId
        val peerName = peer?.name ?: peerId.take(8)

        _chatState.update { ChatState(
            peerId = peerDeviceId,
            endpointId = peerId,
            peerName = peerName,
            isOnline = true
        ) }
        currentPeerId = peerDeviceId

        // Observe conversation messages
        viewModelScope.launch {
            chatDao.getConversation(peerDeviceId).collect { msgs ->
                _chatState.update { it.copy(messages = msgs) }
            }
        }
        // Observe online status reactively
        viewModelScope.launch {
            nearbyService.peers.collect { peers ->
                val online = peers.containsKey(peerId) ||
                    peers.values.any { it.deviceId == peerDeviceId }
                _chatState.update { it.copy(isOnline = online) }
            }
        }
        // Mark as read + send read receipt
        viewModelScope.launch {
            chatDao.markRead(peerDeviceId)
            sendReadReceipt(peerDeviceId)
        }
    }

    fun openExistingChat(peerId: String, peerName: String) {
        // peerId here could be endpointId or deviceId
        val peer = nearbyService.peers.value[peerId]
        val peerDeviceId = peer?.deviceId?.ifBlank { peerId } ?: peerId
        val isOnline = nearbyService.peers.value.containsKey(peerId) ||
            nearbyService.peers.value.values.any { it.deviceId == peerId }

        _chatState.update { ChatState(
            peerId = peerDeviceId,
            endpointId = peerId,
            peerName = peerName,
            isOnline = isOnline
        ) }
        currentPeerId = peerDeviceId

        viewModelScope.launch {
            chatDao.getConversation(peerDeviceId).collect { msgs ->
                _chatState.update { it.copy(messages = msgs) }
            }
        }
        viewModelScope.launch {
            nearbyService.peers.collect { peers ->
                val online = peers.containsKey(peerId) ||
                    peers.values.any { it.deviceId == peerDeviceId }
                _chatState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            chatDao.markRead(peerDeviceId)
            sendReadReceipt(peerDeviceId)
        }
    }

    fun closeChatView() {
        currentPeerId = null
        _chatState.update { ChatState() }
    }

    // ─── Chat Input ──────────────────────────────────────

    fun updateInput(text: String) {
        _chatState.update { it.copy(inputText = text) }

        // Debounced typing indicator
        if (text.isNotBlank()) {
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                sendTypingIndicator()
            }
        }
    }

    fun sendMessage() {
        val state = _chatState.value
        val text = state.inputText.trim()
        if (text.isBlank() || state.peerId.isBlank()) return

        sendChatPayload(
            content = text,
            mediaType = "text"
        )
        _chatState.update { it.copy(inputText = "") }
    }

    /** Send an image from a Bitmap */
    fun sendImage(bitmap: Bitmap) {
        val state = _chatState.value
        if (state.peerId.isBlank()) return

        viewModelScope.launch {
            val baos = ByteArrayOutputStream()
            // Compress to keep under Bluetooth payload limit
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            sendChatPayload(
                content = "📷 Foto",
                mediaType = "image",
                mediaBase64 = base64
            )
        }
    }

    /** Send an image from a content URI */
    fun sendImageFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = appContext.contentResolver.openInputStream(uri) ?: return@launch
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                // Resize if too large (max 800px width)
                val maxWidth = 800
                val scaledBitmap = if (originalBitmap.width > maxWidth) {
                    val ratio = maxWidth.toFloat() / originalBitmap.width
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        maxWidth,
                        (originalBitmap.height * ratio).toInt(),
                        true
                    )
                } else originalBitmap

                sendImage(scaledBitmap)
            } catch (e: Exception) {
                Log.e("ChatVM", "Failed to send image", e)
            }
        }
    }

    /** Send a voice note from a recorded file */
    fun sendVoiceNote(file: File, durationSeconds: Int) {
        val state = _chatState.value
        if (state.peerId.isBlank()) return

        viewModelScope.launch {
            try {
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                sendChatPayload(
                    content = "🎤 Voice Note",
                    mediaType = "voice",
                    mediaBase64 = base64,
                    mediaDuration = durationSeconds
                )
                // Clean up temp file
                file.delete()
            } catch (e: Exception) {
                Log.e("ChatVM", "Failed to send voice note", e)
            }
        }
    }

    /** Retry sending a failed message */
    fun retryMessage(msg: ChatMessageEntity) {
        viewModelScope.launch {
            chatDao.clearSendFailed(msg.messageId)

            val chatPayload = ChatPayload(
                messageId = msg.messageId,
                senderId = nearbyService.getDeviceId(),
                senderName = nearbyService.getDeviceName(),
                recipientId = msg.conversationId,
                content = msg.content,
                mediaType = msg.mediaType,
                mediaBase64 = msg.mediaBase64,
                mediaDuration = msg.mediaDuration
            )

            val endpoint = resolveEndpoint(msg.conversationId)
            if (endpoint != null) {
                val p2pMsg = P2PMessage(
                    type = "CHAT_MESSAGE",
                    senderId = nearbyService.getDeviceId(),
                    senderName = nearbyService.getDeviceName(),
                    payload = chatPayload.toJson()
                )
                val sent = nearbyService.sendTo(endpoint, p2pMsg)
                if (!sent) {
                    chatDao.markSendFailed(msg.messageId)
                }
            } else {
                chatDao.markSendFailed(msg.messageId)
            }
        }
    }

    fun deleteConversation(peerId: String) {
        viewModelScope.launch { chatDao.deleteConversation(peerId) }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { chatDao.deleteMessage(messageId) }
    }

    // ─── Core Send Logic ────────────────────────────────

    private fun sendChatPayload(
        content: String,
        mediaType: String = "text",
        mediaBase64: String = "",
        mediaDuration: Int = 0
    ) {
        val state = _chatState.value
        val myId = nearbyService.getDeviceId()
        val myName = nearbyService.getDeviceName()

        val chatPayload = ChatPayload(
            senderId = myId,
            senderName = myName,
            recipientId = state.peerId,  // peer's deviceId
            content = content,
            mediaType = mediaType,
            mediaBase64 = mediaBase64,
            mediaDuration = mediaDuration
        )

        // Save locally
        viewModelScope.launch {
            chatDao.insert(
                ChatMessageEntity(
                    messageId = chatPayload.messageId,
                    conversationId = state.peerId,  // peer's deviceId (stable)
                    senderId = myId,
                    senderName = myName,
                    recipientId = state.peerId,
                    content = content,
                    timestamp = chatPayload.timestamp,
                    isMine = true,
                    mediaType = mediaType,
                    mediaBase64 = mediaBase64,
                    mediaDuration = mediaDuration
                )
            )
        }

        // Send via P2P — to specific peer only
        val endpoint = resolveEndpoint(state.peerId)
        if (endpoint != null) {
            val msg = P2PMessage(
                type = "CHAT_MESSAGE",
                senderId = myId,
                senderName = myName,
                payload = chatPayload.toJson()
            )
            val sent = nearbyService.sendTo(endpoint, msg)
            if (!sent) {
                viewModelScope.launch { chatDao.markSendFailed(chatPayload.messageId) }
            }
        } else {
            // No endpoint found — mark as failed
            viewModelScope.launch { chatDao.markSendFailed(chatPayload.messageId) }
        }
    }

    // ─── Handlers ────────────────────────────────────────

    private suspend fun handleChatMessage(msg: P2PMessage) {
        val payload = ChatPayload.fromJson(msg.payload) ?: return
        val myId = nearbyService.getDeviceId()

        // Don't store own messages (already saved on send)
        if (payload.senderId == myId) return

        // Ignore messages not intended for us
        if (payload.recipientId.isNotBlank() && payload.recipientId != myId) return

        chatDao.insert(
            ChatMessageEntity(
                messageId = payload.messageId,
                conversationId = payload.senderId, // group by sender's deviceId (stable)
                senderId = payload.senderId,
                senderName = payload.senderName,
                recipientId = myId,
                content = payload.content,
                timestamp = payload.timestamp,
                isMine = false,
                mediaType = payload.mediaType,
                mediaBase64 = payload.mediaBase64,
                mediaDuration = payload.mediaDuration
            )
        )

        // Mark read if currently viewing this conversation
        if (currentPeerId == payload.senderId) {
            chatDao.markRead(payload.senderId)
            // Send read receipt immediately
            sendReadReceipt(payload.senderId)
        } else {
            // Show notification if not viewing this conversation
            val notifText = when (payload.mediaType) {
                "image" -> "📷 Foto"
                "voice" -> "🎤 Voice Note"
                else -> payload.content
            }
            notificationHelper.notifyChat(payload.senderName, notifText)
        }

        // Send delivery receipt — to specific sender only
        val senderEndpoint = nearbyService.findEndpointByDeviceId(payload.senderId)
        if (senderEndpoint != null) {
            nearbyService.sendTo(senderEndpoint, P2PMessage(
                type = "CHAT_DELIVERED",
                senderId = myId,
                senderName = nearbyService.getDeviceName(),
                payload = payload.messageId
            ))
        }
    }

    private suspend fun handleDeliveryReceipt(msg: P2PMessage) {
        chatDao.markDelivered(msg.payload)
    }

    private suspend fun handleReadReceipt(msg: P2PMessage) {
        // msg.senderId = the peer who read our messages
        // msg.payload = conversationId (our deviceId from their perspective)
        chatDao.markAllRead(msg.senderId)
    }

    private fun handleTypingIndicator(msg: P2PMessage) {
        val senderId = msg.senderId
        if (currentPeerId == senderId || currentPeerId != null) {
            // Check if this typing is from the peer we're chatting with
            val chatPeerId = _chatState.value.peerId
            if (senderId == chatPeerId || nearbyService.peers.value.values.any {
                    it.deviceId == chatPeerId && it.endpointId == senderId
                }) {
                _chatState.update { it.copy(peerIsTyping = true) }
                typingClearJob?.cancel()
                typingClearJob = viewModelScope.launch {
                    delay(3000)
                    _chatState.update { it.copy(peerIsTyping = false) }
                }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────

    private fun resolveEndpoint(peerDeviceId: String): String? {
        // Try finding by deviceId first
        val byDeviceId = nearbyService.findEndpointByDeviceId(peerDeviceId)
        if (byDeviceId != null) return byDeviceId
        // Fallback: maybe peerDeviceId IS the endpointId
        return if (nearbyService.peers.value.containsKey(peerDeviceId)) peerDeviceId else null
    }

    private fun sendReadReceipt(peerDeviceId: String) {
        val endpoint = resolveEndpoint(peerDeviceId)
        if (endpoint != null) {
            nearbyService.sendTo(endpoint, P2PMessage(
                type = "CHAT_READ",
                senderId = nearbyService.getDeviceId(),
                senderName = nearbyService.getDeviceName(),
                payload = peerDeviceId
            ))
        }
    }

    private fun sendTypingIndicator() {
        val state = _chatState.value
        val endpoint = resolveEndpoint(state.peerId)
        if (endpoint != null) {
            nearbyService.sendTo(endpoint, P2PMessage(
                type = "CHAT_TYPING",
                senderId = nearbyService.getDeviceId(),
                senderName = nearbyService.getDeviceName()
            ))
        }
    }
}
