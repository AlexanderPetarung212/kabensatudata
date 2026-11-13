package com.example.kabensatudata.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.kabensatudata.R
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.dao.*
import com.example.kabensatudata.data.local.entity.SpmCacheEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.SyncChangeDto
import com.example.kabensatudata.data.remote.dto.SyncPushRequest
import com.example.kabensatudata.data.local.entity.*
import com.example.kabensatudata.nearby.P2PMedicalPayload
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Background worker that synchronizes offline data with the server.
 * Uses WorkManager for reliable execution even if the app is killed.
 *
 * Sync flow:
 * 1. Push: Upload local changes (pendingSync=true) to server
 * 2. Pull: Download new/updated data from server since last sync
 * 3. Update local database with pulled data
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val tokenManager: AuthTokenManager,
    private val buildingDao: BuildingDao,
    private val familyDao: FamilyDao,
    private val familyMemberDao: FamilyMemberDao,
    private val medicalRecordDao: MedicalRecordDao,
    private val villageDao: VillageDao,
    private val syncQueueDao: SyncQueueDao,
    private val spmCacheDao: SpmCacheDao,
    private val p2pMedicalRecordDao: P2PMedicalRecordDao,
    private val medicineUsageDao: MedicineUsageDao
) : CoroutineWorker(appContext, workerParams) {

    private val gson = Gson()

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME_PERIODIC = "periodic_sync"
        const val WORK_NAME_ONE_SHOT = "one_shot_sync"
        const val WORK_NAME_FULL_RESYNC = "full_resync"
        const val KEY_FULL_RESYNC = "full_resync"

        /**
         * Enqueue a one-time sync (e.g., when user taps "Sync Now").
         */
        fun enqueueOneShot(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag(WORK_NAME_ONE_SHOT)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Enqueue a full resync: clears lastSyncTimestamp so ALL data is pulled from server.
         */
        fun enqueueFullResync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(KEY_FULL_RESYNC to true)

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(WORK_NAME_FULL_RESYNC)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_FULL_RESYNC, ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Schedule periodic sync (every 15 minutes when connected).
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WORK_NAME_PERIODIC)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        if (!tokenManager.isLoggedIn()) {
            Log.w(TAG, "Not logged in, skipping sync")
            return Result.success()
        }

        return try {
            val isFullResync = inputData.getBoolean(KEY_FULL_RESYNC, false)

            if (isFullResync) {
                Log.i(TAG, "Full resync requested — clearing lastSyncTimestamp")
                tokenManager.saveLastSyncTimestamp("")
            }

            // Step 1: Convert completed P2P records → MedicalRecord + MedicineUsage
            val p2pSyncCount = syncP2PRecords()

            // Step 2: Push local changes (includes P2P-converted records)
            pushLocalChanges()

            // Step 3: Pull server updates
            pullServerUpdates()

            // Step 4: Pre-seed SPM cache for offline access
            preSeedSpmCache()

            // Step 5: Notify user if P2P records were synced
            if (p2pSyncCount > 0) {
                showP2PSyncNotification(p2pSyncCount)
            }

            Log.i(TAG, "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    // ─── Push ───────────────────────────────────────────────────────────────

    private suspend fun pushLocalChanges() {
        val changes = mutableListOf<SyncChangeDto>()

        // Collect pending buildings
        buildingDao.getPendingSync().forEach { b ->
            changes.add(SyncChangeDto(
                table = "buildings",
                action = b.pendingAction ?: "update",
                uuid = b.uuid,
                data = mapOf(
                    "building_number" to b.buildingNumber,
                    "village_uuid" to getVillageUuid(b.villageId),
                    "latitude" to b.latitude,
                    "longitude" to b.longitude,
                    "description" to b.description
                ),
                clientUpdatedAt = b.updatedAt ?: Instant.now().toString()
            ))
        }

        // Collect pending families
        familyDao.getPendingSync().forEach { f ->
            changes.add(SyncChangeDto(
                table = "families",
                action = f.pendingAction ?: "update",
                uuid = f.uuid,
                data = mapOf(
                    "building_uuid" to getBuildingUuid(f.buildingId),
                    "family_number" to f.familyNumber,
                    "head_name" to f.headName,
                    "sequence_number_in_building" to f.sequenceNumberInBuilding,
                    "has_clean_water" to f.hasCleanWater,
                    "is_water_protected" to f.isWaterProtected,
                    "has_toilet" to f.hasToilet,
                    "is_toilet_sanitary" to f.isToiletSanitary,
                    "has_mental_illness" to f.hasMentalIllness,
                    "takes_medication_regularly" to f.takesMedicationRegularly,
                    "has_restrained_member" to f.hasRestrainedMember
                ),
                clientUpdatedAt = f.updatedAt ?: Instant.now().toString()
            ))
        }

        // Collect pending family members
        familyMemberDao.getPendingSync().forEach { m ->
            changes.add(SyncChangeDto(
                table = "family_members",
                action = m.pendingAction ?: "update",
                uuid = m.uuid,
                data = mapOf(
                    "family_uuid" to getFamilyUuid(m.familyId),
                    "name" to m.name, "nik" to m.nik, "relationship" to m.relationship,
                    "birth_place" to m.birthPlace, "birth_date" to m.birthDate,
                    "gender" to m.gender, "religion" to m.religion,
                    "education" to m.education, "marital_status" to m.maritalStatus,
                    "occupation" to m.occupation, "address" to m.address,
                    "is_pregnant" to m.isPregnant, "has_jkn" to m.hasJkn,
                    "is_smoker" to m.isSmoker, "use_water" to m.useWater,
                    "use_toilet" to m.useToilet,
                    "has_tuberculosis" to m.hasTuberculosis,
                    "takes_tb_medication_regularly" to m.takesTbMedicationRegularly,
                    "has_chronic_cough" to m.hasChronicCough,
                    "has_hypertension" to m.hasHypertension,
                    "takes_hypertension_medication_regularly" to m.takesHypertensionMedicationRegularly,
                    "uses_contraception" to m.usesContraception,
                    "gave_birth_in_health_facility" to m.gaveBirthInHealthFacility,
                    "exclusive_breastfeeding" to m.exclusiveBreastfeeding,
                    "complete_immunization" to m.completeImmunization,
                    "growth_monitoring" to m.growthMonitoring
                ),
                clientUpdatedAt = m.updatedAt ?: Instant.now().toString()
            ))
        }

        // Collect pending medical records (include medicine_usages for P2P-converted records)
        medicalRecordDao.getPendingSync().forEach { r ->
            val memberUuid = getMemberUuid(r.familyMemberId)

            // Collect medicine usages for this record
            val usages = medicineUsageDao.getByRecordSuspend(r.id)
            val dataMap = mutableMapOf<String, Any?>(
                "family_member_uuid" to memberUuid,
                "visit_date" to r.visitDate,
                "chief_complaint" to r.chiefComplaint, "anamnesis" to r.anamnesis,
                "systolic" to r.systolic, "diastolic" to r.diastolic,
                "weight" to r.weight, "height" to r.height,
                "heart_rate" to r.heartRate, "body_temperature" to r.bodyTemperature,
                "respiratory_rate" to r.respiratoryRate,
                "diagnosis_code" to r.diagnosisCode, "diagnosis_name" to r.diagnosisName,
                "therapy" to r.therapy, "medication" to r.medication,
                "procedure" to r.procedure
            )
            if (usages.isNotEmpty()) {
                dataMap["medicine_usages"] = usages.map { u ->
                    mapOf(
                        "medicine_id" to u.medicineId,
                        "quantity_used" to u.quantityUsed,
                        "instruction_text" to u.instructionText,
                        "frequency" to u.frequency,
                        "dosage" to u.dosage,
                        "notes" to u.notes
                    )
                }
            }
            changes.add(SyncChangeDto(
                table = "medical_records",
                action = r.pendingAction ?: "update",
                uuid = r.uuid,
                data = dataMap,
                clientUpdatedAt = r.updatedAt ?: Instant.now().toString()
            ))
        }

        if (changes.isEmpty()) {
            Log.i(TAG, "No pending changes to push")
            return
        }

        Log.i(TAG, "Pushing ${changes.size} changes to server")

        val response = apiService.syncPush(SyncPushRequest(
            changes = changes,
            deviceId = tokenManager.getDeviceId()
        ))

        if (response.isSuccessful) {
            val body = response.body()!!
            Log.i(TAG, "Push result: ${body.summary.accepted} accepted, " +
                    "${body.summary.conflicts} conflicts, ${body.summary.errors} errors")

            // Mark pushed items as synced
            clearPendingFlags()
        } else {
            Log.e(TAG, "Push failed: ${response.code()}")
            throw Exception("Push failed: ${response.code()}")
        }
    }

    // ─── Pull ───────────────────────────────────────────────────────────────

    private suspend fun pullServerUpdates() {
        val since = tokenManager.getLastSyncTimestamp()?.takeIf { it.isNotEmpty() }

        Log.i(TAG, "Pulling updates since: ${since ?: "initial sync (full)"}")

        val response = apiService.syncPull(since = since)

        if (response.isSuccessful) {
            val body = response.body()!!
            val data = body.data

            Log.i(TAG, "Pulled: ${body.counts}")

            // Upsert villages
            if (data.villages.isNotEmpty()) {
                villageDao.insertAll(data.villages.map { v ->
                    VillageEntity(
                        id = v.id, uuid = v.uuid, name = v.name, code = v.code,
                        sequenceNumber = v.sequenceNumber, district = v.district,
                        regency = v.regency, province = v.province,
                        updatedAt = v.updatedAt
                    )
                })
            }

            // Upsert buildings
            if (data.buildings.isNotEmpty()) {
                buildingDao.insertAll(data.buildings.map { b ->
                    BuildingEntity(
                        id = b.id, uuid = b.uuid, villageId = b.villageId,
                        buildingNumber = b.buildingNumber,
                        latitude = b.latitude, longitude = b.longitude,
                        description = b.description, updatedAt = b.updatedAt
                    )
                })
            }

            // Upsert families
            if (data.families.isNotEmpty()) {
                familyDao.insertAll(data.families.map { f ->
                    FamilyEntity(
                        id = f.id, uuid = f.uuid, buildingId = f.buildingId,
                        familyNumber = f.familyNumber, headName = f.headName,
                        sequenceNumberInBuilding = f.sequenceNumberInBuilding,
                        hasCleanWater = f.hasCleanWater, isWaterProtected = f.isWaterProtected,
                        hasToilet = f.hasToilet, isToiletSanitary = f.isToiletSanitary,
                        hasMentalIllness = f.hasMentalIllness,
                        takesMedicationRegularly = f.takesMedicationRegularly,
                        hasRestrainedMember = f.hasRestrainedMember,
                        updatedAt = f.updatedAt
                    )
                })
            }

            // Upsert members
            if (data.familyMembers.isNotEmpty()) {
                familyMemberDao.insertAll(data.familyMembers.map { m ->
                    FamilyMemberEntity(
                        id = m.id, uuid = m.uuid, familyId = m.familyId,
                        name = m.name, nik = m.nik, rmNumber = m.rmNumber,
                        sequenceNumberInFamily = m.sequenceNumberInFamily,
                        relationship = m.relationship, birthPlace = m.birthPlace,
                        birthDate = m.birthDate, gender = m.gender,
                        isPregnant = m.isPregnant, religion = m.religion,
                        education = m.education, maritalStatus = m.maritalStatus,
                        occupation = m.occupation, address = m.address,
                        hasJkn = m.hasJkn, isSmoker = m.isSmoker,
                        useWater = m.useWater, useToilet = m.useToilet,
                        hasTuberculosis = m.hasTuberculosis,
                        takesTbMedicationRegularly = m.takesTbMedicationRegularly,
                        hasChronicCough = m.hasChronicCough,
                        hasHypertension = m.hasHypertension,
                        takesHypertensionMedicationRegularly = m.takesHypertensionMedicationRegularly,
                        usesContraception = m.usesContraception,
                        gaveBirthInHealthFacility = m.gaveBirthInHealthFacility,
                        exclusiveBreastfeeding = m.exclusiveBreastfeeding,
                        completeImmunization = m.completeImmunization,
                        growthMonitoring = m.growthMonitoring,
                        // NIK Verification (SATUSEHAT)
                        ihsNumber = m.ihsNumber,
                        nikVerifiedStatus = m.nikVerifiedStatus,
                        nikVerifiedAt = m.nikVerifiedAt,
                        nikVerifiedSource = m.nikVerifiedSource,
                        updatedAt = m.updatedAt
                    )
                })
            }

            // Upsert medical records
            if (data.medicalRecords.isNotEmpty()) {
                medicalRecordDao.insertAll(data.medicalRecords.map { r ->
                    MedicalRecordEntity(
                        id = r.id, uuid = r.uuid, familyMemberId = r.familyMemberId,
                        visitDate = r.visitDate,
                        chiefComplaint = r.chiefComplaint, anamnesis = r.anamnesis,
                        systolic = r.systolic, diastolic = r.diastolic,
                        weight = r.weight, height = r.height,
                        heartRate = r.heartRate, bodyTemperature = r.bodyTemperature,
                        respiratoryRate = r.respiratoryRate,
                        diagnosisCode = r.diagnosisCode, diagnosisName = r.diagnosisName,
                        therapy = r.therapy, medication = r.medication,
                        procedure = r.procedure,
                        workflowStatus = r.workflowStatus,
                        createdBy = r.createdBy,
                        updatedAt = r.updatedAt
                    )
                })
            }

            // Save the server time for incremental sync
            tokenManager.saveLastSyncTimestamp(body.serverTime)
            Log.i(TAG, "Pull completed, next sync from: ${body.serverTime}")
        } else {
            Log.e(TAG, "Pull failed: ${response.code()}")
            throw Exception("Pull failed: ${response.code()}")
        }
    }

    // ─── SPM Pre-seed Cache ───────────────────────────────────────────────────

    /**
     * Pre-fetch SPM dashboard for the current year and cache it.
     * This ensures SPM data is available offline even on a fresh install.
     * Runs silently — does NOT fail the overall sync if SPM fetch fails.
     */
    private suspend fun preSeedSpmCache() {
        try {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val gson = Gson()

            // Fetch overall dashboard (no month/village filter)
            val resp = apiService.getSpmDashboard(currentYear, null, null)
            if (resp.isSuccessful) {
                val data = resp.body()?.data
                if (data != null) {
                    val key = "spm_dashboard_${currentYear}_all_all"
                    spmCacheDao.insertOrUpdate(SpmCacheEntity(key, gson.toJson(data)))
                    Log.i(TAG, "SPM cache seeded for year $currentYear")
                }
            } else {
                Log.w(TAG, "SPM pre-seed failed: HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "SPM pre-seed error (non-fatal)", e)
            // Don't throw — SPM cache failure should not break PIS-PK sync
        }
    }

    // ─── P2P Records Sync ────────────────────────────────────────────────────

    /**
     * Convert completed P2P medical records into MedicalRecordEntity + MedicineUsageEntity
     * so they get pushed to the server via the existing pushLocalChanges() mechanism.
     * Returns the number of records converted.
     */
    private suspend fun syncP2PRecords(): Int {
        val unsyncedRecords = p2pMedicalRecordDao.getUnsyncedCompleted()
        if (unsyncedRecords.isEmpty()) return 0

        var syncCount = 0
        Log.i(TAG, "Found ${unsyncedRecords.size} unsynced P2P records")

        for (entity in unsyncedRecords) {
            val payload = P2PMedicalPayload.fromJson(entity.payloadJson) ?: continue

            // Lookup patient — must exist in Room and have a valid UUID
            val member = familyMemberDao.getById(payload.patientId)
            if (member == null || member.uuid.isBlank()) {
                Log.w(TAG, "P2P sync skip: patient ${payload.patientId} not found or no UUID")
                continue
            }

            // Build medication text from prescriptions
            val medicationText = payload.prescriptions.joinToString("\n") { rx ->
                buildString {
                    append(rx.medicineName)
                    if (rx.quantity > 0) append(" - ${rx.quantity} ${rx.medicineUnit}")
                    if (rx.frequency.isNotBlank()) append(" (${rx.frequency})")
                    else if (rx.instruction.isNotBlank()) append(" (${rx.instruction})")
                    if (rx.notes.isNotBlank()) append(" - ${rx.notes}")
                }
            }

            // Create MedicalRecordEntity with negative temp ID
            val recordUuid = UUID.randomUUID().toString()
            val tempRecordId = -(System.currentTimeMillis() / 1000)
            val newRecord = MedicalRecordEntity(
                id = tempRecordId,
                uuid = recordUuid,
                familyMemberId = payload.patientId,
                visitDate = payload.visitDate,
                chiefComplaint = payload.chiefComplaint,
                anamnesis = payload.anamnesis,
                systolic = payload.systolic,
                diastolic = payload.diastolic,
                weight = payload.weight,
                height = payload.height,
                heartRate = payload.heartRate,
                bodyTemperature = payload.bodyTemperature,
                respiratoryRate = payload.respiratoryRate,
                diagnosisCode = payload.diagnosisCode,
                diagnosisName = payload.diagnosisName,
                therapy = payload.therapy,
                medication = medicationText.ifBlank { null },
                procedure = payload.procedure,
                workflowStatus = "completed",
                pendingSync = true,
                pendingAction = "create",
                updatedAt = Instant.now().toString()
            )
            val recordId = medicalRecordDao.insert(newRecord)

            // Create MedicineUsageEntity for each prescription
            for (rx in payload.prescriptions) {
                if (rx.medicineId > 0) {
                    medicineUsageDao.insert(
                        MedicineUsageEntity(
                            medicalRecordId = recordId,
                            medicineId = rx.medicineId,
                            quantityUsed = rx.quantity,
                            instructionText = rx.instruction.ifBlank { null },
                            frequency = rx.frequency.ifBlank { null },
                            dosage = rx.dosage.ifBlank { null },
                            notes = rx.notes.ifBlank { null },
                            pendingSync = true,
                            pendingAction = "create"
                        )
                    )
                }
            }

            // Mark P2P record as synced
            p2pMedicalRecordDao.markSynced(entity.recordUuid)
            syncCount++
            Log.i(TAG, "P2P record converted: ${payload.patientName} → MR uuid=$recordUuid")
        }

        Log.i(TAG, "P2P sync: $syncCount/${unsyncedRecords.size} records converted")
        return syncCount
    }

    // ─── Sync Notification ───────────────────────────────────────────────────

    private fun showP2PSyncNotification(count: Int) {
        val channelId = "p2p_sync"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Sinkronisasi P2P",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifikasi sinkronisasi data rekam medis offline" }
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sinkronisasi P2P Berhasil")
            .setContentText("$count rekam medis offline berhasil disinkronkan ke server")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(9001, notification)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun clearPendingFlags() {
        buildingDao.getPendingSync().forEach { b ->
            buildingDao.update(b.copy(pendingSync = false, pendingAction = null))
        }
        familyDao.getPendingSync().forEach { f ->
            familyDao.update(f.copy(pendingSync = false, pendingAction = null))
        }
        familyMemberDao.getPendingSync().forEach { m ->
            familyMemberDao.update(m.copy(pendingSync = false, pendingAction = null))
        }
        medicalRecordDao.getPendingSync().forEach { r ->
            medicalRecordDao.update(r.copy(pendingSync = false, pendingAction = null))
        }
    }

    private suspend fun getVillageUuid(id: Long): String? =
        villageDao.getById(id)?.uuid

    private suspend fun getBuildingUuid(id: Long): String? =
        buildingDao.getById(id)?.uuid

    private suspend fun getFamilyUuid(id: Long): String? =
        familyDao.getById(id)?.uuid

    private suspend fun getMemberUuid(id: Long): String? =
        familyMemberDao.getById(id)?.uuid
}
