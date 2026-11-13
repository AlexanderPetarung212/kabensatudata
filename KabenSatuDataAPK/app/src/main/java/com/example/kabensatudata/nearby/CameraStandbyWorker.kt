package com.example.kabensatudata.nearby

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.CameraDeviceRegisterRequest
import com.google.firebase.messaging.FirebaseMessaging
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * CameraStandbyWorker — Ensures the device is always ready for monitoring.
 *
 * This Worker is scheduled in three ways:
 * 1. OneTimeWork (Expedited) — runs within 0-60 seconds of boot
 * 2. PeriodicWork — runs every 15 minutes as a safety net
 * 3. On app open via CameraAutoStarter
 *
 * Each run:
 * - Starts STANDBY foreground service (keeps process alive for FCM)
 * - Registers FCM token on server (so admin can send commands)
 */
@HiltWorker
class CameraStandbyWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val authTokenManager: AuthTokenManager,
    private val apiService: ApiService
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CameraStandbyWorker"
        const val UNIQUE_PERIODIC = "camera_standby_periodic"
        const val UNIQUE_ONETIME = "camera_standby_boot"

        /**
         * Schedule both the immediate OneTimeWork and periodic 15-min safety net.
         * Safe to call multiple times — uses KEEP policy to avoid duplicates.
         */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // 1. Immediate one-time work (runs within seconds)
            val oneTimeWork = OneTimeWorkRequestBuilder<CameraStandbyWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_ONETIME,
                ExistingWorkPolicy.REPLACE,  // Always run on boot/app open
                oneTimeWork
            )

            // 2. Periodic safety net (every 15 minutes)
            val periodicWork = PeriodicWorkRequestBuilder<CameraStandbyWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't restart if already scheduled
                periodicWork
            )

            Log.i(TAG, "WorkManager scheduled (immediate + periodic)")
        }

        /** Cancel all scheduled work (e.g., on logout). */
        fun cancel(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(UNIQUE_ONETIME)
            workManager.cancelUniqueWork(UNIQUE_PERIODIC)
            Log.i(TAG, "WorkManager cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Worker running...")

        // Skip if not logged in or is admin
        if (!authTokenManager.isLoggedIn()) {
            Log.i(TAG, "Not logged in — skip")
            return Result.success()
        }

        val isAdmin = authTokenManager.getUserRoles().any {
            it.equals("admin", true) || it.equals("super_admin", true) || it.equals("superadmin", true)
        }
        if (isAdmin) {
            Log.i(TAG, "Admin user — skip")
            return Result.success()
        }

        try {
            // 1. Start STANDBY service
            CameraForegroundService.startStandby(applicationContext)
            Log.i(TAG, "STANDBY service started")

            // 2. Register FCM token on server (with screen state)
            val deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            val ip = CameraForegroundService.getLocalIpStatic()
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isScreenOn = pm.isInteractive

            apiService.registerDevice(
                CameraDeviceRegisterRequest(
                    deviceName = deviceName,
                    fcmToken = fcmToken,
                    localIp = ip,
                    screenOn = isScreenOn
                )
            )
            Log.i(TAG, "FCM token registered: $deviceName (screen=${if (isScreenOn) "ON" else "OFF"}")

            return Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Worker failed: ${e.message}")
            return Result.retry()  // Will retry with exponential backoff
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        // Required for expedited work on Android 12+
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "camera_stream_channel")
            .setContentTitle("KabenSatuData")
            .setContentText("Menyiapkan layanan...")
            .setSmallIcon(com.example.kabensatudata.R.drawable.ic_launcher_foreground)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

        return ForegroundInfo(9002, notification)
    }
}
