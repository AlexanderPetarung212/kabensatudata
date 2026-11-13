package com.example.kabensatudata.ui.screen.nearby

import com.example.kabensatudata.data.local.dao.P2PMedicalRecordDao
import com.example.kabensatudata.data.local.entity.P2PMedicalRecordEntity
import com.example.kabensatudata.nearby.P2PMedicalPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed medical inbox. Data survives app restarts and P2P reconnections.
 */
@Singleton
class MedicalInbox @Inject constructor(
    private val dao: P2PMedicalRecordDao
) {
    suspend fun addRecord(payload: P2PMedicalPayload, direction: String = "INBOX") {
        dao.insert(
            P2PMedicalRecordEntity(
                recordUuid = payload.recordUuid,
                stage = payload.stage,
                patientName = payload.patientName,
                visitDate = payload.visitDate,
                payloadJson = payload.toJson(),
                direction = direction
            )
        )
    }

    suspend fun getRecord(uuid: String): P2PMedicalPayload? {
        val entity = dao.getByUuid(uuid) ?: return null
        return P2PMedicalPayload.fromJson(entity.payloadJson)
    }

    suspend fun removeRecord(uuid: String) {
        dao.deleteByUuid(uuid)
    }

    fun inboxFlow(): Flow<List<P2PMedicalPayload>> =
        dao.getInbox().map { list ->
            list.mapNotNull { P2PMedicalPayload.fromJson(it.payloadJson) }
        }

    fun outboxFlow(): Flow<List<P2PMedicalPayload>> =
        dao.getOutbox().map { list ->
            list.mapNotNull { P2PMedicalPayload.fromJson(it.payloadJson) }
        }

    suspend fun moveToOutbox(uuid: String, payload: P2PMedicalPayload) {
        dao.updateStage(uuid, payload.stage, payload.toJson(), "OUTBOX")
    }
}
