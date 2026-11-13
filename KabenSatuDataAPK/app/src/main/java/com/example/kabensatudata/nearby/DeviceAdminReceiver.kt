package com.example.kabensatudata.nearby

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * DeviceAdminReceiver — enables device admin features like lockNow().
 * Must be activated by user once via Settings > Security > Device Administrators.
 */
class KabenDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("DeviceAdmin", "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i("DeviceAdmin", "Device admin disabled")
    }
}
