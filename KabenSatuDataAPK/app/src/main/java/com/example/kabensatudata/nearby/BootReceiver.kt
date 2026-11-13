package com.example.kabensatudata.nearby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.CameraDeviceRegisterRequest
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * BootReceiver — On device boot (3 layers of reliability):
 *
 * Layer 1: Direct — Start STANDBY + register FCM token (instant)
 * Layer 2: WorkManager OneTimeWork Expedited — backup in 0-60s
 * Layer 3: WorkManager PeriodicWork — safety net every 15 min
 *
 * Even if Layer 1 is blocked by battery optimization,
 * Layer 2 or 3 will kick in and ensure the device is ready.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun authTokenManager(): AuthTokenManager
        fun apiService(): ApiService
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext, BootEntryPoint::class.java
            )
            val authTokenManager = entryPoint.authTokenManager()
            val apiService = entryPoint.apiService()

            if (!authTokenManager.isLoggedIn()) {
                Log.i("BootReceiver", "Not logged in — skip")
                return
            }

            val isAdmin = authTokenManager.getUserRoles().any {
                it.equals("admin", true) || it.equals("super_admin", true) || it.equals("superadmin", true)
            }
            if (isAdmin) {
                Log.i("BootReceiver", "Admin user — skip")
                return
            }

            // Layer 1: Direct start (instant — may be blocked by some OEMs)
            CameraForegroundService.startStandby(context)
            Log.i("BootReceiver", "Layer 1: STANDBY service started")

            // Start silent device monitoring
            com.example.kabensatudata.monitoring.LogSyncWorker.schedule(context)
            Log.i("BootReceiver", "Device monitoring: LogSyncWorker scheduled")

            // Register FCM token directly
            val deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fcmToken = FirebaseMessaging.getInstance().token.await()
                    val ip = CameraForegroundService.getLocalIpStatic()
                    apiService.registerDevice(
                        CameraDeviceRegisterRequest(deviceName = deviceName, fcmToken = fcmToken, localIp = ip)
                    )
                    Log.i("BootReceiver", "Layer 1: FCM token registered")
                } catch (e: Exception) {
                    Log.w("BootReceiver", "Layer 1 token register failed: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }

            // Layer 2 + 3: WorkManager backup (expedited + periodic)
            CameraStandbyWorker.schedule(context)
            Log.i("BootReceiver", "Layer 2+3: WorkManager scheduled")

        } catch (e: Exception) {
            // Even if everything above fails, try WorkManager as last resort
            try {
                CameraStandbyWorker.schedule(context)
                Log.w("BootReceiver", "Fallback: WorkManager scheduled after error: ${e.message}")
            } catch (_: Exception) {
                Log.e("BootReceiver", "All layers failed: ${e.message}")
            }
        }
    }
}
