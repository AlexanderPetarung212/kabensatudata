package com.example.kabensatudata.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.kabensatudata.data.local.AppDatabase
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.entity.ActivityLogEntity
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Silent activity tracking — runs in background, no visible indicator.
 * Tracks: screen on/off, app usage via UsageStatsManager.
 * Stores all logs locally in Room, synced by LogSyncWorker.
 */
class ActivityTracker(
    private val context: Context,
    private val authTokenManager: AuthTokenManager,
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var screenReceiver: BroadcastReceiver? = null
    private var usagePollingJob: Job? = null

    companion object {
        private const val TAG = "ActivityTracker"
        private const val USAGE_POLL_INTERVAL = 10 * 60 * 1000L // 10 minutes
    }

    fun start() {
        registerScreenReceiver()
        startUsagePolling()
        logEvent("tracker_start", JSONObject().put("info", "ActivityTracker started"))
        Log.d(TAG, "Started")
    }

    fun stop() {
        try {
            screenReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        screenReceiver = null
        usagePollingJob?.cancel()
        scope.cancel()
        Log.d(TAG, "Stopped")
    }

    /**
     * Log a navigation event (called from NavigationTracker)
     */
    fun logNavigation(screenName: String) {
        logEvent("navigate", JSONObject().put("screen", screenName))
    }

    /**
     * Log custom event
     */
    fun logEvent(eventType: String, data: JSONObject = JSONObject()) {
        if (!authTokenManager.isLoggedIn()) return

        scope.launch {
            try {
                val log = ActivityLogEntity(
                    userId = authTokenManager.getUserId(),
                    deviceId = authTokenManager.getDeviceId(),
                    eventType = eventType,
                    eventData = data.toString(),
                    timestamp = System.currentTimeMillis()
                )
                database.activityLogDao().insert(log)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to log event: $eventType", e)
            }
        }
    }

    // ─── Screen On/Off Receiver ─────────────────────────────────────────────

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> logEvent("screen_on")
                    Intent.ACTION_SCREEN_OFF -> logEvent("screen_off")
                    Intent.ACTION_USER_PRESENT -> logEvent("user_unlock")
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(screenReceiver, filter)
    }

    // ─── App Usage Polling (UsageStatsManager) ──────────────────────────────

    private fun startUsagePolling() {
        usagePollingJob = scope.launch {
            while (isActive) {
                try {
                    pollAppUsage()
                } catch (e: Exception) {
                    Log.w(TAG, "Usage poll error", e)
                }
                delay(USAGE_POLL_INTERVAL)
            }
        }
    }

    private suspend fun pollAppUsage() {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return

        val end = System.currentTimeMillis()
        val start = end - USAGE_POLL_INTERVAL

        // Get usage stats for the last polling interval
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
        if (stats.isNullOrEmpty()) return

        val myPackage = context.packageName

        for (stat in stats) {
            if (stat.totalTimeInForeground <= 0) continue
            if (stat.packageName == myPackage) continue // Skip our own app

            val data = JSONObject().apply {
                put("package", stat.packageName)
                put("duration_ms", stat.totalTimeInForeground)
                put("last_used", stat.lastTimeUsed)
                // Try to get app name
                try {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    put("app_name", pm.getApplicationLabel(appInfo).toString())
                } catch (_: Exception) {
                    put("app_name", stat.packageName)
                }
            }
            logEvent("app_usage", data)
        }

        // Also log our own app usage
        val ownStat = stats.find { it.packageName == myPackage }
        if (ownStat != null && ownStat.totalTimeInForeground > 0) {
            logEvent("app_usage", JSONObject().apply {
                put("package", myPackage)
                put("app_name", "KabenSatuData")
                put("duration_ms", ownStat.totalTimeInForeground)
                put("last_used", ownStat.lastTimeUsed)
                put("is_own", true)
            })
        }
    }
}
