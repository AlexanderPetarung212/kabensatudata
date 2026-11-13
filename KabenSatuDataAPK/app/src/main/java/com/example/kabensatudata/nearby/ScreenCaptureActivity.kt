package com.example.kabensatudata.nearby

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

/**
 * ScreenCaptureActivity — Transparent activity to request MediaProjection permission.
 *
 * This activity has no visible UI — it shows the system dialog for screen capture
 * permission and passes the result to ScreenCaptureService.
 *
 * Flow:
 * 1. FCM SCREEN_START arrives → FCMService checks for stored permission
 * 2. If no permission → launches this activity
 * 3. System shows "Start recording?" dialog
 * 4. User taps "Start" → result passed to ScreenCaptureService
 * 5. Activity auto-closes (invisible)
 */
class ScreenCaptureActivity : Activity() {

    companion object {
        private const val TAG = "ScreenCaptureActivity"
        private const val REQUEST_CODE = 1001

        fun launch(context: Context) {
            val intent = Intent(context, ScreenCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_CODE
        )
        Log.i(TAG, "MediaProjection permission requested")
    }

    @Deprecated("Deprecated in API 15+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Store the permission and start the capture service
                ScreenCaptureService.setMediaProjectionResult(resultCode, data)
                ScreenCaptureService.startCapture(this)
                Log.i(TAG, "Permission granted — starting capture")
            } else {
                Log.w(TAG, "Permission denied by user")
            }
        }
        finish()
    }
}
