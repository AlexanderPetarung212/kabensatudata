package com.example.kabensatudata.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.kabensatudata.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ─── DTOs ────────────────────────────────────────────────────────────────────

data class AppVersionInfo(
    @SerializedName("version_code") val versionCode: Int = 0,
    @SerializedName("version_name") val versionName: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("changelog") val changelog: String = "",
    @SerializedName("force_update") val forceUpdate: Boolean = false
)

data class UpdateState(
    val isChecking: Boolean = false,
    val updateAvailable: Boolean = false,
    val versionInfo: AppVersionInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,  // 0-100
    val downloadComplete: Boolean = false,
    val downloadedFile: File? = null,
    val error: String? = null,
    val dismissed: Boolean = false
)

// ─── Manager ─────────────────────────────────────────────────────────────────

class InAppUpdateManager(private val context: Context) {

    companion object {
        private const val VERSION_JSON_URL = "https://kabensatudata.my.id/downloads/version.json"
        private const val APK_FILENAME = "KabenSatuData_update.apk"
    }

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var downloadId: Long = -1

    /**
     * Check version.json on the server and compare with current app version.
     */
    suspend fun checkForUpdate() {
        _state.value = _state.value.copy(isChecking = true, error = null)
        try {
            val info = withContext(Dispatchers.IO) { fetchVersionInfo() }
            if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                _state.value = _state.value.copy(
                    isChecking = false,
                    updateAvailable = true,
                    versionInfo = info
                )
            } else {
                _state.value = _state.value.copy(isChecking = false, updateAvailable = false)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(isChecking = false, error = e.message)
        }
    }

    /**
     * Fetch version.json from server.
     */
    private fun fetchVersionInfo(): AppVersionInfo? {
        val url = URL(VERSION_JSON_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Cache-Control", "no-cache")
        return try {
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                Gson().fromJson(json, AppVersionInfo::class.java)
            } else null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Download APK using Android DownloadManager (shows notification progress automatically).
     */
    fun startDownload() {
        val info = _state.value.versionInfo ?: return
        _state.value = _state.value.copy(isDownloading = true, downloadProgress = 0, error = null)

        // Delete old APK if exists
        val oldFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            APK_FILENAME
        )
        if (oldFile.exists()) oldFile.delete()

        val request = DownloadManager.Request(Uri.parse(info.downloadUrl)).apply {
            setTitle("Update KabenSatuData")
            setDescription("Mengunduh versi ${info.versionName}...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILENAME)
            setMimeType("application/vnd.android.package-archive")
            // Allow on all network types
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        // Register receiver for download complete
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(downloadReceiver, filter)
        }

        // Start progress polling in background
        pollProgress(dm)
    }

    private fun pollProgress(dm: DownloadManager) {
        Thread {
            var running = true
            while (running) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    if (bytesIdx >= 0 && totalIdx >= 0 && statusIdx >= 0) {
                        val bytes = cursor.getLong(bytesIdx)
                        val total = cursor.getLong(totalIdx)
                        val status = cursor.getInt(statusIdx)

                        if (total > 0) {
                            val progress = ((bytes * 100) / total).toInt()
                            _state.value = _state.value.copy(downloadProgress = progress)
                        }

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> running = false
                            DownloadManager.STATUS_FAILED -> {
                                _state.value = _state.value.copy(
                                    isDownloading = false,
                                    error = "Download gagal. Coba lagi."
                                )
                                running = false
                            }
                        }
                    }
                    cursor.close()
                }
                if (running) Thread.sleep(500)
            }
        }.start()
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id == downloadId) {
                val file = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    APK_FILENAME
                )
                _state.value = _state.value.copy(
                    isDownloading = false,
                    downloadProgress = 100,
                    downloadComplete = true,
                    downloadedFile = file
                )
                try { context.unregisterReceiver(this) } catch (_: Exception) {}
            }
        }
    }

    /**
     * Open the downloaded APK for installation.
     */
    fun installUpdate() {
        val file = _state.value.downloadedFile ?: return
        if (!file.exists()) {
            _state.value = _state.value.copy(error = "File APK tidak ditemukan")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    }

    /**
     * User dismisses the update dialog.
     */
    fun dismiss() {
        _state.value = _state.value.copy(dismissed = true)
    }

    /**
     * Reset the state (useful for retry).
     */
    fun reset() {
        _state.value = UpdateState()
    }
}
