package com.example.kabensatudata.nearby

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

/**
 * RemoteControlHandler — handles remote commands from admin:
 * - LOCK: lock screen immediately
 * - RING / STOP_RING: play alarm at max volume
 * - DEVICE_INFO: collect & upload device info
 * - APP_LIST: collect & upload installed apps list
 */
object RemoteControlHandler {
    private const val TAG = "RemoteControl"
    private var mediaPlayer: MediaPlayer? = null
    private var ringJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RemoteEntryPoint {
        fun authTokenManager(): AuthTokenManager
        fun apiService(): ApiService
        fun bokApiService(): com.example.kabensatudata.data.remote.BokApiService
    }

    // ─── LOCK ──────────────────────────────────────────

    fun lockDevice(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, KabenDeviceAdminReceiver::class.java)

        if (dpm.isAdminActive(adminComponent)) {
            dpm.lockNow()
            Log.i(TAG, "Device locked successfully")
        } else {
            Log.w(TAG, "Device admin not active — cannot lock")
            // Try to prompt activation (won't work from background, but log it)
        }
    }

    // ─── RING ──────────────────────────────────────────

    fun ringDevice(context: Context) {
        stopRinging()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Save current volume, set to max
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "Device ringing at max volume")

            // Auto-stop after 30 seconds
            ringJob = scope.launch {
                delay(30_000)
                stopRinging()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ring: ${e.message}")
        }
    }

    fun stopRinging() {
        ringJob?.cancel()
        ringJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        Log.i(TAG, "Ringing stopped")
    }

    // ─── DEVICE INFO ───────────────────────────────────

    fun collectAndUploadDeviceInfo(context: Context) {
        scope.launch {
            try {
                val info = collectDeviceInfo(context)
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, RemoteEntryPoint::class.java)
                val deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
                val api = ep.apiService()

                val body = com.example.kabensatudata.data.remote.dto.DeviceInfoUpload(
                    deviceName = deviceName,
                    batteryLevel = info["battery_level"] ?: "0",
                    storageTotal = info["storage_total"] ?: "0",
                    storageFree = info["storage_free"] ?: "0",
                    ramTotal = info["ram_total"] ?: "0",
                    ramFree = info["ram_free"] ?: "0",
                    osVersion = info["os_version"] ?: "Unknown",
                    model = info["model"] ?: "Unknown",
                    wifiName = info["wifi_name"] ?: "Disconnected",
                    uptime = info["uptime"] ?: "0",
                    timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                )

                val response = api.uploadDeviceInfo(body)
                Log.i(TAG, "Device info uploaded: ${response.code()}")
            } catch (e: Exception) {
                Log.e(TAG, "Device info upload error: ${e.message}")
            }
        }
    }

    private fun collectDeviceInfo(context: Context): Map<String, String> {
        val info = mutableMapOf<String, String>()

        // Battery
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        info["battery_level"] = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString()

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.freeBytes
        info["storage_total"] = "%.1f".format(totalBytes / 1_073_741_824.0)
        info["storage_free"] = "%.1f".format(freeBytes / 1_073_741_824.0)

        // RAM
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        info["ram_total"] = "%.1f".format(memInfo.totalMem / 1_073_741_824.0)
        info["ram_free"] = "%.1f".format(memInfo.availMem / 1_073_741_824.0)

        // OS & Model
        info["os_version"] = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        info["model"] = "${Build.MANUFACTURER} ${Build.MODEL}"

        // WiFi
        @Suppress("DEPRECATION")
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wm.connectionInfo
        info["wifi_name"] = if (wm.isWifiEnabled && wifiInfo != null) {
            wifiInfo.ssid?.replace("\"", "") ?: "Connected"
        } else "Disconnected"

        // Uptime
        val uptimeMs = SystemClock.elapsedRealtime()
        val hours = uptimeMs / 3_600_000
        val mins = (uptimeMs % 3_600_000) / 60_000
        info["uptime"] = "${hours}h ${mins}m"

        return info
    }

    // ─── APP LIST ──────────────────────────────────────

    fun collectAndUploadAppList(context: Context) {
        scope.launch {
            try {
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, RemoteEntryPoint::class.java)
                val deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
                val api = ep.apiService()
                val pm = context.packageManager

                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // non-system only
                    .map { appInfo ->
                        com.example.kabensatudata.data.remote.dto.InstalledAppItem(
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            packageName = appInfo.packageName
                        )
                    }
                    .sortedBy { it.appName.lowercase() }

                val body = com.example.kabensatudata.data.remote.dto.AppListUpload(
                    deviceName = deviceName,
                    apps = apps,
                    timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                )

                val response = api.uploadAppList(body)
                Log.i(TAG, "App list uploaded (${apps.size} apps): ${response.code()}")
            } catch (e: Exception) {
                Log.e(TAG, "App list upload error: ${e.message}")
            }
        }
    }

    // ─── CAPTURE NOW (Screenshot) ─────────────────────

    fun captureScreenshotNow(context: Context) {
        scope.launch {
            try {
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, RemoteEntryPoint::class.java)
                val deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
                val api = ep.apiService()
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"

                // 1. Coba dari Accessibility (API 30+) — paling akurat
                var screenshotBytes: ByteArray? = null
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    screenshotBytes = ScreenTextService.requestScreenshot()
                    if (screenshotBytes != null) {
                        Log.d(TAG, "Screenshot dari Accessibility (${screenshotBytes.size} bytes)")
                    }
                }

                // 2. Fallback: ambil frame terakhir dari ScreenCaptureService
                if (screenshotBytes == null) {
                    screenshotBytes = ScreenCaptureService.getStaticLatestFrame()
                    if (screenshotBytes != null) {
                        Log.d(TAG, "Screenshot dari ScreenCaptureService fallback")
                    }
                }

                if (screenshotBytes == null) {
                    Log.w(TAG, "Tidak ada screenshot tersedia — Accessibility belum aktif?")
                    return@launch
                }

                // Upload langsung
                val body = screenshotBytes.toRequestBody("image/jpeg".toMediaType())
                val part = okhttp3.MultipartBody.Part.createFormData(
                    "screenshot", "screenshot_${System.currentTimeMillis()}.jpg", body
                )
                val deviceNameBody = deviceName.toRequestBody("text/plain".toMediaType())
                val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaType())
                api.uploadScreenshot(part, deviceNameBody, deviceIdBody)
                Log.i(TAG, "Screenshot on-demand berhasil diupload (${screenshotBytes.size} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot capture error: ${e.message}")
            }
        }
    }

    // ─── SCAN FOLDER ──────────────────────────────────

    fun scanFolder(context: Context, folderPath: String) {
        scope.launch {
            try {
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, RemoteEntryPoint::class.java)
                val deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
                val api = ep.apiService()

                val folder = java.io.File(folderPath)
                if (!folder.exists() || !folder.isDirectory) {
                    Log.w(TAG, "Folder not found: $folderPath")
                    return@launch
                }

                val files = folder.listFiles()?.map { file ->
                    mapOf(
                        "name" to file.name,
                        "path" to file.absolutePath,
                        "is_directory" to file.isDirectory,
                        "size" to if (file.isFile) file.length() else 0L,
                        "mime_type" to if (file.isFile) getMimeType(file.name) else null,
                        "last_modified" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
                    )
                } ?: emptyList()

                // Upload to server
                api.uploadFileScan(
                    deviceName = deviceName,
                    body = mapOf("path" to folderPath, "files" to files)
                )

                Log.i(TAG, "Folder scanned: $folderPath (${files.size} items)")
            } catch (e: Exception) {
                Log.e(TAG, "Folder scan error: ${e.message}")
            }
        }
    }

    // ─── UPLOAD FILE ──────────────────────────────────

    fun uploadFile(context: Context, filePath: String) {
        scope.launch {
            try {
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, RemoteEntryPoint::class.java)
                val deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
                val api = ep.apiService()

                val file = java.io.File(filePath)
                if (!file.exists() || !file.isFile) {
                    Log.w(TAG, "File not found: $filePath")
                    return@launch
                }

                // Max 50MB file size
                if (file.length() > 50 * 1024 * 1024) {
                    Log.w(TAG, "File too large: ${file.length() / 1024 / 1024}MB")
                    return@launch
                }

                val mimeType = getMimeType(file.name) ?: "application/octet-stream"
                val requestBody = file.asRequestBody(mimeType.toMediaType())
                val filePart = okhttp3.MultipartBody.Part.createFormData(
                    "file", file.name, requestBody
                )
                val filePathBody = filePath.toRequestBody("text/plain".toMediaType())

                api.uploadFileContent(deviceName, filePathBody, filePart)

                Log.i(TAG, "File uploaded: $filePath (${file.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "File upload error: ${e.message}")
            }
        }
    }

    // ─── FORCE STOP APP ────────────────────────────────

    fun forceStopApp(context: Context, packageName: String) {
        scope.launch {
            try {
                var stopped = false

                // 1. Coba via root (paling efektif)
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
                    proc.waitFor()
                    stopped = proc.exitValue() == 0
                    if (stopped) Log.i(TAG, "Force-stopped via root: $packageName")
                } catch (_: Exception) { }

                // 2. Fallback: killBackgroundProcesses (terbatas, hanya background)
                if (!stopped) {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    @Suppress("DEPRECATION")
                    am.killBackgroundProcesses(packageName)
                    Log.i(TAG, "killBackgroundProcesses: $packageName")
                }

                // Upload log
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, RemoteEntryPoint::class.java)
                val deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                ep.apiService().uploadDeviceLogs(
                    com.example.kabensatudata.data.remote.dto.DeviceLogBulkRequest(
                        deviceName = deviceName,
                        deviceId = deviceId,
                        logs = listOf(
                            com.example.kabensatudata.data.remote.dto.DeviceLogItem(
                                eventType = "app_force_stop",
                                eventData = packageName,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Force stop error: ${e.message}")
            }
        }
    }

    // ─── UNINSTALL APP ─────────────────────────────────

    fun uninstallApp(context: Context, packageName: String) {
        scope.launch {
            try {
                var uninstalled = false

                // 1. Coba via root (silent, tidak perlu konfirmasi)
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm uninstall -k --user 0 $packageName"))
                    proc.waitFor()
                    uninstalled = proc.exitValue() == 0
                    if (uninstalled) Log.i(TAG, "Uninstalled via root: $packageName")
                } catch (_: Exception) { }

                // 2. Fallback: launch system uninstall UI (user perlu konfirmasi)
                if (!uninstalled) {
                    val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                        data = android.net.Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(uninstallIntent)
                    Log.i(TAG, "Launched system uninstall UI for: $packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Uninstall error: ${e.message}")
            }
        }
    }

    private fun getMimeType(filename: String): String? {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}

