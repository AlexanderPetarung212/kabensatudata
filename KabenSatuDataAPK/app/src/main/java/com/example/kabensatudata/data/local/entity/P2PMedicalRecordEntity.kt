package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity to persist P2P medical records locally.
 * Stores the full JSON payload so data survives app restarts.
 */
@Entity(tableName = "p2p_medical_records")
data class P2PMedicalRecordEntity(
    @PrimaryKey val recordUuid: String,
    val stage: String,          // REGISTRATION, DIAGNOSIS, DISPENSED
    val patientName: String,
    val visitDate: String,
    val payloadJson: String,    // Full P2PMedicalPayload as JSON
    val direction: String,      // INBOX, OUTBOX
    val syncedToServer: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
