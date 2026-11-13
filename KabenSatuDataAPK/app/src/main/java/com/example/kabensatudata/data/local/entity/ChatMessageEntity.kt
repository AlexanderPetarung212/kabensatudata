package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for P2P chat messages.
 * Supports 1-to-1 conversations between peers via Bluetooth.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,         // UUID for deduplication
    val conversationId: String,    // peer deviceId — groups messages per peer
    val senderId: String,          // device endpoint/unique id
    val senderName: String,
    val recipientId: String,       // target peer id ("ALL" for broadcast)
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = false,   // true if sent by this device
    val delivered: Boolean = false, // delivery receipt received
    val read: Boolean = false,      // read receipt received
    val sendFailed: Boolean = false, // true if send failed (for retry)
    val mediaType: String = "text", // "text", "image", "voice"
    val mediaBase64: String = "",   // Base64 encoded media data
    val mediaDuration: Int = 0      // Duration in seconds (for voice notes)
)
