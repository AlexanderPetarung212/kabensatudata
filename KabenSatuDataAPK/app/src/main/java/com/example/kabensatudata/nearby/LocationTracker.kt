package com.example.kabensatudata.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.DeviceLogBulkRequest
import com.example.kabensatudata.data.remote.dto.DeviceLogItem
import com.example.kabensatudata.data.remote.dto.LocationUploadRequest
import com.google.android.gms.location.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * LocationTracker — On-demand GPS location tracking (like camera — STANDBY/ACTIVE model).
 *
 * Modes:
 * - One-shot: getOnce(context) — get location once and upload (admin taps "Cek Lokasi")
 * - Continuous: startTracking(context) — upload every 60s (admin taps "Mulai Tracking")
 * - Stop: stopTracking() — stop continuous tracking (admin taps "Stop Tracking")
 *
 * Always STANDBY — activated only when admin triggers via FCM.
 * No battery consumption when idle.
 *
 * Feedback: sends status back to server so admin knows if the command succeeded or failed.
 */
object LocationTracker {

    private const val TAG = "LocationTracker"
    private const val TRACKING_INTERVAL_MS = 60_000L // 1 minute when active

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocationEntryPoint {
        fun authTokenManager(): AuthTokenManager
        fun apiService(): ApiService
    }

    @Volatile
    var isTracking = false
        private set

    private var locationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    /**
     * Get location once and upload to server.
     * Sends feedback to admin (success/failed).
     */
    fun getOnce(context: Context) {
        Log.i(TAG, "Getting location once...")

        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            sendFeedback(context, "failed", "permission_denied_foreground")
            return
        }

        if (!hasBackgroundLocationPermission(context)) {
            Log.w(TAG, "Background location permission not granted — trying anyway")
            // Don't return, still try — might work if called from foreground service
        }

        val client = getClient(context)

        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.i(TAG, "Got location: ${location.latitude}, ${location.longitude} (±${location.accuracy}m)")
                        uploadLocation(context, location.latitude, location.longitude, location.accuracy)
                        sendFeedback(context, "success",
                            "lat:${location.latitude},lng:${location.longitude},acc:${location.accuracy}m")
                    } else {
                        Log.w(TAG, "Location is null, trying last known...")
                        getLastKnownWithFeedback(context)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "getCurrentLocation failed: ${e.message}")
                    // Retry with last known
                    getLastKnownWithFeedback(context)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            sendFeedback(context, "failed", "security_exception:${e.message}")
        }
    }

    /**
     * Start continuous tracking — uploads location every 60 seconds.
     * Admin triggers this via FCM location_command START.
     */
    fun startTracking(context: Context) {
        if (isTracking) {
            Log.i(TAG, "Already tracking, skipping start")
            sendFeedback(context, "already_tracking", "interval:${TRACKING_INTERVAL_MS / 1000}s")
            return
        }
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            sendFeedback(context, "failed", "permission_denied")
            return
        }

        isTracking = true
        Log.i(TAG, "Starting continuous tracking (every ${TRACKING_INTERVAL_MS / 1000}s)")
        sendFeedback(context, "tracking_started", "interval:${TRACKING_INTERVAL_MS / 1000}s")

        val client = getClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, TRACKING_INTERVAL_MS)
            .setMinUpdateIntervalMillis(TRACKING_INTERVAL_MS / 2)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                Log.d(TAG, "Tracking update: ${location.latitude}, ${location.longitude}")
                uploadLocation(context, location.latitude, location.longitude, location.accuracy)
            }
        }

        try {
            client.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            isTracking = false
            sendFeedback(context, "failed", "security_exception:${e.message}")
        }
    }

    /**
     * Stop continuous tracking.
     * Admin triggers this via FCM location_command STOP.
     */
    fun stopTracking(context: Context? = null) {
        if (!isTracking) return
        isTracking = false
        locationCallback?.let { callback ->
            locationClient?.removeLocationUpdates(callback)
        }
        locationCallback = null
        Log.i(TAG, "Tracking stopped")
        if (context != null) {
            sendFeedback(context, "tracking_stopped", "by_admin")
        }
    }

    // ─── Private ─────────────────────────────────────────

    private fun getClient(context: Context): FusedLocationProviderClient {
        if (locationClient == null) {
            locationClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        }
        return locationClient!!
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Try to get last known location as a fallback. Sends feedback to admin.
     */
    private fun getLastKnownWithFeedback(context: Context) {
        try {
            getClient(context).lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Log.i(TAG, "Last known: ${location.latitude}, ${location.longitude}")
                    uploadLocation(context, location.latitude, location.longitude, location.accuracy)
                    sendFeedback(context, "success_lastknown",
                        "lat:${location.latitude},lng:${location.longitude},acc:${location.accuracy}m")
                } else {
                    Log.w(TAG, "No last known location available")
                    sendFeedback(context, "failed", "no_location_available")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on lastLocation: ${e.message}")
            sendFeedback(context, "failed", "security_exception:${e.message}")
        }
    }

    /**
     * Upload location data to server.
     * Includes retry logic (max 3 attempts).
     */
    private fun uploadLocation(context: Context, lat: Double, lng: Double, accuracy: Float) {
        uploadScope.launch {
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, LocationEntryPoint::class.java)
            val deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
            val api = ep.apiService()

            var retries = 0
            val maxRetries = 3
            while (retries < maxRetries) {
                try {
                    api.uploadLocation(
                        LocationUploadRequest(
                            deviceName = deviceName,
                            latitude = lat,
                            longitude = lng,
                            accuracy = accuracy,
                            timestamp = dateFormat.format(Date())
                        )
                    )
                    Log.d(TAG, "Location uploaded: $lat, $lng")
                    return@launch // Success
                } catch (e: Exception) {
                    retries++
                    Log.w(TAG, "Upload location failed (attempt $retries/$maxRetries): ${e.message}")
                    if (retries < maxRetries) {
                        delay(3000L * retries) // 3s, 6s backoff
                    }
                }
            }
            Log.e(TAG, "Location upload failed after $maxRetries attempts")
        }
    }

    /**
     * Send feedback status to server so admin knows if the command succeeded or failed.
     * Uses the existing device logs endpoint.
     */
    private fun sendFeedback(context: Context, status: String, detail: String) {
        uploadScope.launch {
            try {
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, LocationEntryPoint::class.java)
                val deviceName = ep.authTokenManager().getUserName().ifBlank { "HP-Puskesmas" }
                val deviceId = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                val api = ep.apiService()

                api.uploadDeviceLogs(
                    DeviceLogBulkRequest(
                        deviceName = deviceName,
                        deviceId = deviceId,
                        logs = listOf(
                            DeviceLogItem(
                                eventType = "location_status",
                                eventData = """{"status":"$status","detail":"$detail"}""",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    )
                )
                Log.d(TAG, "Feedback sent: $status — $detail")
            } catch (e: Exception) {
                Log.w(TAG, "Feedback send failed: ${e.message}")
            }
        }
    }
}
