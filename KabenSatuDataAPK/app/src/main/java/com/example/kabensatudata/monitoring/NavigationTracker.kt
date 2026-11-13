package com.example.kabensatudata.monitoring

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks screen navigation within the app.
 * Injected as singleton and called from NavHost when routes change.
 * Zero UI footprint — fully silent.
 */
@Singleton
class NavigationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var activityTracker: ActivityTracker? = null
    private var lastScreen: String? = null
    private var lastScreenTime: Long = 0

    companion object {
        private const val TAG = "NavigationTracker"
    }

    fun setTracker(tracker: ActivityTracker) {
        this.activityTracker = tracker
    }

    /**
     * Called whenever the user navigates to a new screen.
     * Automatically calculates time spent on previous screen.
     */
    fun onScreenChanged(screenRoute: String) {
        val now = System.currentTimeMillis()
        val friendlyName = routeToFriendlyName(screenRoute)

        // Log time spent on previous screen
        if (lastScreen != null && lastScreenTime > 0) {
            val duration = now - lastScreenTime
            if (duration > 1000) { // Only log if > 1 second
                val data = org.json.JSONObject().apply {
                    put("screen", lastScreen)
                    put("duration_ms", duration)
                }
                activityTracker?.logEvent("screen_time", data)
            }
        }

        // Log navigation to new screen
        activityTracker?.logNavigation(friendlyName)

        lastScreen = friendlyName
        lastScreenTime = now
    }

    /**
     * Convert route like "medical_hub" to "Medical Hub"
     */
    private fun routeToFriendlyName(route: String): String {
        // Strip query params and path params
        val base = route.split("?").first().split("/").last()
        return base
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
