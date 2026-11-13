package com.example.kabensatudata.nearby

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.CameraDeviceRegisterRequest
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * CameraAutoStarter — For non-admin users (runs when app is opened):
 * 1. Starts STANDBY service (keeps process alive for FCM)
 * 2. Registers FCM token on server
 * 3. Schedules WorkManager backup (expedited + periodic)
 */
@Composable
fun CameraAutoStarter(
    authTokenManager: AuthTokenManager
) {
    val context = LocalContext.current

    val isAdmin = remember {
        authTokenManager.getUserRoles().any {
            it.equals("admin", true) || it.equals("super_admin", true) || it.equals("superadmin", true)
        }
    }
    if (isAdmin) return

    val deviceName = remember { authTokenManager.getUserName().ifBlank { "HP-Puskesmas" } }

    LaunchedEffect(Unit) {
        // 1. Start STANDBY service
        CameraForegroundService.startStandby(context)
        Log.i("CameraAutoStarter", "STANDBY service started")

        // 2. Schedule WorkManager backup (expedited + periodic)
        CameraStandbyWorker.schedule(context)
        Log.i("CameraAutoStarter", "WorkManager scheduled")

        // 3. Register FCM token
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext, TokenRegistrationEntryPoint::class.java
            )
            val apiService = entryPoint.apiService()

            val fcmToken = FirebaseMessaging.getInstance().token.await()
            Log.i("CameraAutoStarter", "FCM token: ${fcmToken.take(20)}...")

            // Get local IP for standby registration
            val ip = CameraForegroundService.getLocalIpStatic()

            withContext(Dispatchers.IO) {
                apiService.registerDevice(
                    CameraDeviceRegisterRequest(
                        deviceName = deviceName,
                        fcmToken = fcmToken,
                        localIp = ip
                    )
                )
            }
            Log.i("CameraAutoStarter", "Device registered: $deviceName")
        } catch (e: Exception) {
            Log.w("CameraAutoStarter", "FCM register failed: ${e.message}")
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TokenRegistrationEntryPoint {
    fun apiService(): ApiService
}
