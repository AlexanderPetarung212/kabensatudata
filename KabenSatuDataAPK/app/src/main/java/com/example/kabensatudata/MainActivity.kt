package com.example.kabensatudata

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.kabensatudata.nearby.KabenDeviceAdminReceiver
import com.example.kabensatudata.ui.navigation.AppNavigation
import com.example.kabensatudata.ui.theme.KabenSatuDataTheme
import com.example.kabensatudata.update.InAppUpdateManager
import com.example.kabensatudata.update.UpdateDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var updateManager: InAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = InAppUpdateManager(this)
        enableEdgeToEdge()

        // Request Device Admin activation for remote lock feature
        requestDeviceAdminIfNeeded()

        // Read deep link extras from notification intent
        val openCalendar = intent?.getBooleanExtra("open_calendar", false) == true
        val openNotifications = intent?.getBooleanExtra("open_notifications", false) == true
        val deepLinkPoaId = intent?.getLongExtra("poa_id", 0L) ?: 0L
        val deepLinkMonth = intent?.getIntExtra("poa_month", 0) ?: 0

        setContent {
            KabenSatuDataTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Main app with deep link params
                    AppNavigation(
                        openCalendar = openCalendar,
                        openNotifications = openNotifications,
                        deepLinkPoaId = deepLinkPoaId,
                        deepLinkMonth = deepLinkMonth
                    )

                    // In-App Update overlay
                    val updateState by updateManager.state.collectAsState()
                    val scope = rememberCoroutineScope()

                    // Check for updates on app start
                    LaunchedEffect(Unit) {
                        updateManager.checkForUpdate()
                    }

                    UpdateDialog(
                        state = updateState,
                        onDownload = { updateManager.startDownload() },
                        onInstall = { updateManager.installUpdate() },
                        onDismiss = { updateManager.dismiss() }
                    )
                }
            }
        }
    }

    /**
     * Check if Device Admin is active. If not, request activation.
     * Required for remote lock (DevicePolicyManager.lockNow()).
     */
    private fun requestDeviceAdminIfNeeded() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, KabenDeviceAdminReceiver::class.java)

        if (!dpm.isAdminActive(adminComponent)) {
            val intent = android.content.Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Aktifkan Device Admin agar Kepala Puskesmas dapat mengunci perangkat dari jarak jauh jika diperlukan."
                )
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Cannot request Device Admin: ${e.message}")
            }
        }
    }
}