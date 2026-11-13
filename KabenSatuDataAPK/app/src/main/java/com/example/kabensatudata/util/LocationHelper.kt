package com.example.kabensatudata.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * GPS location helper using Android's native LocationManager.
 * We avoid Google Play Services dependency to keep APK size small
 * and support devices without Play Services.
 */
object LocationHelper {

    private const val MIN_ACCURACY_METERS = 20f
    private const val LOCATION_TIMEOUT_MS = 30_000L

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get current location as a one-shot request.
     * Returns null if location is unavailable or permission denied.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
            as android.location.LocationManager

        // Try GPS first, fallback to network
        val providers = listOf(
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.NETWORK_PROVIDER
        )

        // Check for last known location first (fast)
        for (provider in providers) {
            try {
                val lastKnown = locationManager.getLastKnownLocation(provider)
                if (lastKnown != null && lastKnown.accuracy <= MIN_ACCURACY_METERS) {
                    val age = System.currentTimeMillis() - lastKnown.time
                    if (age < 60_000) { // Less than 1 minute old
                        return lastKnown
                    }
                }
            } catch (_: Exception) { }
        }

        // Request fresh location
        return suspendCancellableCoroutine { continuation ->
            var resolved = false

            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!resolved) {
                        resolved = true
                        locationManager.removeUpdates(this)
                        continuation.resume(location)
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in API")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            try {
                for (provider in providers) {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(
                            provider, 1000L, 0f, listener, Looper.getMainLooper()
                        )
                    }
                }
            } catch (e: Exception) {
                if (!resolved) {
                    resolved = true
                    continuation.resume(null)
                }
            }

            // Timeout handler
            val handler = android.os.Handler(Looper.getMainLooper())
            handler.postDelayed({
                if (!resolved) {
                    resolved = true
                    locationManager.removeUpdates(listener)
                    // Return last known as fallback
                    val fallback = providers.firstNotNullOfOrNull { provider ->
                        try { locationManager.getLastKnownLocation(provider) } catch (_: Exception) { null }
                    }
                    continuation.resume(fallback)
                }
            }, LOCATION_TIMEOUT_MS)

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }
    }

    /**
     * Stream location updates as a Flow.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(context: Context, intervalMs: Long = 3000L): Flow<Location> = callbackFlow {
        if (!hasLocationPermission(context)) {
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
            as android.location.LocationManager

        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in API")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        try {
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER,
                    intervalMs, 1f, listener, Looper.getMainLooper()
                )
            }
        } catch (_: Exception) { }

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }
}
