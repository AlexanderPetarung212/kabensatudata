package com.example.kabensatudata.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.DeviceLogBulkRequest
import com.example.kabensatudata.data.remote.dto.DeviceLogItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AudioRecorderService — Foreground service for recording audio from microphone.
 *
 * Modes:
 * - CLIP (Rekam 2 Menit): record for 120 seconds, upload, stop
 * - LIVE: record 5-second chunks continuously, upload each chunk
 *
 * Lifecycle fixes:
 * - Handles null intent when Android restarts the service
 * - Sends feedback to server (admin knows recording started/completed/failed)
 * - Retry logic for audio uploads (3 attempts with backoff)
 */
class AudioRecorderService : Service() {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val CHANNEL_ID = "audio_recorder_channel"
        private const val NOTIFICATION_ID = 4002
        private const val CLIP_DURATION_MS = 120_000L     // 2 minutes
        private const val LIVE_CHUNK_DURATION_MS = 5_000L  // 5 seconds
        private const val MAX_UPLOAD_RETRIES = 3

        const val EXTRA_MODE = "mode" // "CLIP" or "LIVE"

        fun startService(context: Context, mode: String) {
            val intent = Intent(context, AudioRecorderService::class.java).apply {
                putExtra(EXTRA_MODE, mode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, AudioRecorderService::class.java))
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AudioEntryPoint {
        fun authTokenManager(): AuthTokenManager
        fun apiService(): ApiService
    }

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null
    private var mode = "CLIP"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // Cached for feedback
    private var deviceName: String = "HP-Puskesmas"
    private var deviceId: String = "unknown"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Cache device info for feedback
        try {
            val ep = EntryPointAccessors.fromApplication(applicationContext, AudioEntryPoint::class.java)
            deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIX: Handle null intent when Android restarts the service after being killed
        if (intent == null) {
            Log.i(TAG, "Service restarted by Android — no active recording, stopping")
            stopSelf()
            return START_NOT_STICKY // Don't restart again automatically
        }

        mode = intent.getStringExtra(EXTRA_MODE) ?: "CLIP"

        val notification = buildNotification(
            if (mode == "LIVE") "Audio streaming aktif..." else "Merekam audio (2 menit)..."
        )
        startForeground(NOTIFICATION_ID, notification)

        if (!isRecording) {
            when (mode) {
                "CLIP" -> startClipRecording()
                "LIVE" -> startLiveRecording()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopRecordingSafe()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─── CLIP Mode (2 minutes) ─────────────────────────

    private fun startClipRecording() {
        isRecording = true
        val outputFile = createOutputFile("clip")
        currentFile = outputFile

        try {
            recorder = createRecorder(outputFile)
            recorder?.start()
            Log.i(TAG, "CLIP recording started: ${outputFile.name}")
            sendFeedback("recording_started", "mode:CLIP,duration:${CLIP_DURATION_MS / 1000}s")

            // Auto-stop after 2 minutes
            serviceScope.launch {
                delay(CLIP_DURATION_MS)
                if (isRecording && mode == "CLIP") {
                    stopRecordingSafe()
                    sendFeedback("recording_completed", "file:${outputFile.name},size:${outputFile.length() / 1024}KB")
                    uploadAudioFile(outputFile, "clip")
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CLIP recording: ${e.message}")
            sendFeedback("recording_failed", "mode:CLIP,error:${e.message?.take(100)}")
            isRecording = false
            stopSelf()
        }
    }

    // ─── LIVE Mode (5-second chunks) ───────────────────

    private fun startLiveRecording() {
        isRecording = true
        Log.i(TAG, "LIVE recording started (5s chunks)")
        sendFeedback("recording_started", "mode:LIVE,chunk:${LIVE_CHUNK_DURATION_MS / 1000}s")

        serviceScope.launch {
            var chunkIndex = 0
            while (isRecording) {
                val outputFile = createOutputFile("live_${chunkIndex++}")
                currentFile = outputFile

                try {
                    val rec = createRecorder(outputFile)
                    recorder = rec
                    rec.start()

                    delay(LIVE_CHUNK_DURATION_MS)

                    // Stop this chunk
                    try {
                        rec.stop()
                        rec.release()
                    } catch (_: Exception) { }
                    recorder = null

                    // Upload in background
                    if (outputFile.exists() && outputFile.length() > 0) {
                        launch { uploadAudioFile(outputFile, "live") }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "LIVE chunk error: ${e.message}")
                    if (chunkIndex == 1) {
                        // First chunk failed — microphone likely busy
                        sendFeedback("recording_failed", "mode:LIVE,error:${e.message?.take(100)}")
                    }
                    delay(1000) // Brief pause before retry
                }
            }
            sendFeedback("recording_stopped", "mode:LIVE,chunks:$chunkIndex")
        }
    }

    // ─── Recording Helpers ─────────────────────────────

    @Suppress("DEPRECATION")
    private fun createRecorder(outputFile: File): MediaRecorder {
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000) // 128kbps — clear audio
            setAudioSamplingRate(44100)     // CD quality sampling
            setOutputFile(outputFile.absolutePath)
            prepare()
        }
        return rec
    }

    private fun createOutputFile(prefix: String): File {
        val dir = File(cacheDir, "audio_recordings")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${prefix}_${dateFormat.format(Date())}.aac")
    }

    private fun stopRecordingSafe() {
        isRecording = false
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        try {
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
    }

    // ─── Upload with Retry ─────────────────────────────

    private suspend fun uploadAudioFile(file: File, type: String) {
        var retries = 0
        while (retries < MAX_UPLOAD_RETRIES) {
            try {
                val ep = EntryPointAccessors.fromApplication(applicationContext, AudioEntryPoint::class.java)
                val api = ep.apiService()

                val requestFile = file.asRequestBody("audio/aac".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("audio", file.name, requestFile)
                val deviceNamePart = deviceName.toRequestBody("text/plain".toMediaTypeOrNull())
                val typePart = type.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = api.uploadAudio(body, deviceNamePart, typePart)
                if (response.isSuccessful) {
                    Log.d(TAG, "Uploaded: ${file.name}")
                    sendFeedback("upload_success", "file:${file.name},size:${file.length() / 1024}KB")
                    break // Success!
                } else {
                    Log.w(TAG, "Upload failed: ${response.code()}")
                    retries++
                    if (retries >= MAX_UPLOAD_RETRIES) {
                        sendFeedback("upload_failed", "file:${file.name},code:${response.code()}")
                    }
                }
            } catch (e: Exception) {
                retries++
                Log.w(TAG, "Upload error (attempt $retries/$MAX_UPLOAD_RETRIES): ${e.message}")
                if (retries < MAX_UPLOAD_RETRIES) {
                    delay(5000L * retries) // 5s, 10s backoff
                } else {
                    sendFeedback("upload_failed", "file:${file.name},error:${e.message?.take(80)}")
                }
            }
        }
        // Clean up local file after all attempts
        file.delete()
    }

    // ─── Feedback to Server ───────────────────────────

    private fun sendFeedback(status: String, detail: String) {
        serviceScope.launch {
            try {
                val ep = EntryPointAccessors.fromApplication(applicationContext, AudioEntryPoint::class.java)
                ep.apiService().uploadDeviceLogs(
                    DeviceLogBulkRequest(
                        deviceName = deviceName,
                        deviceId = deviceId,
                        logs = listOf(
                            DeviceLogItem(
                                eventType = "audio_status",
                                eventData = """{"status":"$status","detail":"$detail"}""",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    )
                )
                Log.d(TAG, "Feedback: $status — $detail")
            } catch (e: Exception) {
                Log.w(TAG, "Feedback failed: ${e.message}")
            }
        }
    }

    // ─── Notification ──────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Recorder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio recording service"
                setShowBadge(false)
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("KabenSatuData")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("KabenSatuData")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        }
    }
}
