package com.example.kabensatudata.nearby

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Standard message format for all P2P communication.
 * Serialized as JSON for transport via Nearby Connections.
 */
data class P2PMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: String,                   // QUEUE_CALL, QUEUE_RESET, RECORD_TRANSFER, PEER_INFO, etc.
    @SerializedName("sender_id")
    val senderId: String = "",          // endpoint id of original sender
    @SerializedName("sender_name")
    val senderName: String = "",        // human-readable sender name
    val payload: String = "",           // JSON string for type-specific data
    val ttl: Int = 3,                   // max relay hops remaining
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): P2PMessage? = try {
            gson.fromJson(json, P2PMessage::class.java)
        } catch (_: Exception) { null }
    }
}

// ─── Queue-specific payloads ──────────────────────────────

data class QueueCallPayload(
    val number: Int,
    @SerializedName("patient_name")
    val patientName: String = "",
    val room: String = ""
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): QueueCallPayload? = try {
            Gson().fromJson(json, QueueCallPayload::class.java)
        } catch (_: Exception) { null }
    }
}
