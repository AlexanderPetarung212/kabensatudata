package com.example.kabensatudata.monitoring

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.kabensatudata.data.local.AppDatabase
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.DeviceLogBulkRequest
import com.example.kabensatudata.data.remote.dto.DeviceLogItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic job — syncs local activity logs to server every 15 minutes.
 * Runs silently in background, battery-friendly.
 */
@HiltWorker
class LogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val apiService: ApiService,
    private val authTokenManager: AuthTokenManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LogSyncWorker"
        private const val WORK_NAME = "device_log_sync"
        private const val BATCH_SIZE = 100

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<LogSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Scheduled periodic sync (every 15m)")
        }
    }

    override suspend fun doWork(): Result {
        if (!authTokenManager.isLoggedIn()) return Result.success()

        try {
            val dao = database.activityLogDao()
            var totalSynced = 0

            // Sync in batches
            while (true) {
                val unsyncedLogs = dao.getUnsynced(BATCH_SIZE)
                if (unsyncedLogs.isEmpty()) break

                val entries = unsyncedLogs.map { log ->
                    DeviceLogItem(
                        eventType = log.eventType,
                        eventData = log.eventData,
                        timestamp = log.timestamp
                    )
                }

                val request = DeviceLogBulkRequest(
                    deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" },
                    deviceId = authTokenManager.getDeviceId(),
                    logs = entries
                )

                val response = apiService.uploadDeviceLogs(request)
                if (response.isSuccessful) {
                    val ids = unsyncedLogs.map { it.id }
                    dao.markSynced(ids)
                    totalSynced += ids.size
                } else {
                    Log.w(TAG, "Sync failed: ${response.code()}")
                    break
                }
            }

            // Clean old synced logs (keep last 7 days)
            val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            dao.deleteSyncedBefore(cutoff)

            if (totalSynced > 0) {
                Log.d(TAG, "Synced $totalSynced logs")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Sync error", e)
            return Result.retry()
        }
    }
}
