package com.example.kabensatudata.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.kabensatudata.R
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.CameraRegisterRequest
import com.example.kabensatudata.data.remote.dto.CameraDeviceRegisterRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import com.example.kabensatudata.data.remote.dto.CameraUnregisterRequest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * CameraForegroundService — Dual-mode foreground service.
 *
 * MODE 1: STANDBY (default)
 *   - Minimal foreground notification only
 *   - Keeps the app process alive so FCM data messages are received
 *   - No camera, no HTTP server, no battery drain
 *   - Started on boot and when app opens
 *
 * MODE 2: STREAMING
 *   - Full camera + HTTP server for MJPEG streaming
 *   - Activated by admin via FCM START command
 *   - Deactivated by admin via FCM STOP command (returns to STANDBY)
 */
class CameraForegroundService : Service(), LifecycleOwner {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        fun authTokenManager(): AuthTokenManager
        fun apiService(): ApiService
    }

    companion object {
        private const val TAG = "CameraFgService"
        const val PORT = 8080
        private const val CHANNEL_ID = "camera_stream_channel"
        private const val NOTIFICATION_ID = 9001
        private const val JPEG_QUALITY = 45
        private const val RELAY_QUALITY = 25
        private const val RELAY_SAMPLE_SIZE = 4
        private const val HEARTBEAT_INTERVAL_MS = 120_000L // 2 menit

        const val ACTION_STANDBY = "ACTION_STANDBY"
        const val ACTION_START_STREAM = "ACTION_START_STREAM"
        const val ACTION_STOP_STREAM = "ACTION_STOP_STREAM"
        const val ACTION_SWITCH_CAMERA = "ACTION_SWITCH_CAMERA"
        const val ACTION_TAKE_PHOTO = "ACTION_TAKE_PHOTO"
        const val EXTRA_LENS = "extra_lens"

        /** Start in STANDBY mode (lightweight — no camera). */
        fun startStandby(context: Context) {
            val intent = Intent(context, CameraForegroundService::class.java)
            intent.action = ACTION_STANDBY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Switch to STREAMING mode (camera + HTTP server). */
        fun start(context: Context, lens: String = "FRONT") {
            val intent = Intent(context, CameraForegroundService::class.java)
            intent.action = ACTION_START_STREAM
            intent.putExtra(EXTRA_LENS, lens)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stop streaming (return to STANDBY). */
        fun stop(context: Context) {
            val intent = Intent(context, CameraForegroundService::class.java)
            intent.action = ACTION_STOP_STREAM
            context.startService(intent)
        }

        /** Switch camera lens while streaming. */
        fun switchCamera(context: Context, lens: String) {
            val intent = Intent(context, CameraForegroundService::class.java)
            intent.action = ACTION_SWITCH_CAMERA
            intent.putExtra(EXTRA_LENS, lens)
            context.startService(intent)
        }

        /** Take a single high-res photo and upload it. */
        fun takePhoto(context: Context, lens: String = "FRONT") {
            val intent = Intent(context, CameraForegroundService::class.java)
            intent.action = ACTION_TAKE_PHOTO
            intent.putExtra(EXTRA_LENS, lens)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Static helper to get local IP without service instance. */
        fun getLocalIpStatic(): String? {
            try {
                for (intf in java.net.NetworkInterface.getNetworkInterfaces()) {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress
                            if (ip != null && (ip.startsWith("192.168") || ip.startsWith("10."))) return ip
                        }
                    }
                }
                for (intf in java.net.NetworkInterface.getNetworkInterfaces()) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
                    }
                }
            } catch (_: Exception) {}
            return null
        }
    }

    // Manual LifecycleOwner for CameraX binding
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var apiService: ApiService

    private var httpServer: MjpegServer? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var latestJpeg: ByteArray? = null
    @Volatile var isStreaming = false
        private set
    @Volatile var currentLens = "FRONT"
        private set

    // WakeLocks to keep CPU+WiFi alive during streaming
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var heartbeatJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, ServiceEntryPoint::class.java
        )
        authTokenManager = entryPoint.authTokenManager()
        apiService = entryPoint.apiService()

        createNotificationChannel()
        startForegroundWithNotification()
        Log.i(TAG, "Service created (STANDBY)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STANDBY
        val requestedLens = intent?.getStringExtra(EXTRA_LENS) ?: "FRONT"

        when (action) {
            ACTION_STANDBY -> {
                // Keep running + start heartbeat to re-register FCM token periodically
                Log.i(TAG, "STANDBY mode — waiting for FCM commands")
                startHeartbeat()
            }

            ACTION_START_STREAM -> {
                if (!isStreaming) {
                    currentLens = requestedLens
                    lifecycleRegistry.currentState = Lifecycle.State.STARTED
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                    startCamera()
                    startHttpServer()
                    isStreaming = true
                    acquireWakeLocks()
                    upgradeServiceType()
                    startKeepAlive()
                    startFrameRelay("camera")
                    Log.i(TAG, "STREAMING started ($currentLens)")
                }
            }

            ACTION_STOP_STREAM -> {
                if (isStreaming) {
                    stopStreaming()
                    Log.i(TAG, "STREAMING stopped → back to STANDBY")
                }
            }

            ACTION_SWITCH_CAMERA -> {
                if (isStreaming && requestedLens != currentLens) {
                    currentLens = requestedLens
                    restartCamera()
                    Log.i(TAG, "Camera switched to $currentLens")
                }
            }

            ACTION_TAKE_PHOTO -> {
                handleTakePhoto(requestedLens)
            }
        }

        return START_STICKY
    }

    /** Stop streaming but keep service alive in STANDBY mode. */
    private fun stopStreaming() {
        isStreaming = false
        unregisterNetworkCallback()
        releaseWakeLocks()
        frameRelayJob?.cancel()
        frameRelayJob = null
        httpServer?.stop()
        httpServer = null
        cameraProvider?.unbindAll()
        latestJpeg = null

        // Unregister from server
        unregisterFromServer()

        // Downgrade service type back to DATA_SYNC
        startForegroundWithNotification()
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroying")
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (isStreaming) stopStreaming()
        releaseWakeLocks()
        serviceScope.cancel()
        cameraExecutor.shutdown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Heartbeat (re-register FCM token every 2 minutes) ───

    /**
     * Start periodic heartbeat that re-registers the device's FCM token on the server.
     * This ensures the device reappears in dashboard within 2 minutes after server cache clear.
     *
     * - Runs inside the already-running foreground service (no extra battery cost)
     * - Lightweight: ~200 bytes per API call
     * - Skips if not logged in or user is admin
     */
    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return // Already running

        heartbeatJob = serviceScope.launch(Dispatchers.IO) {
            // Initial delay — let WorkManager handle the first registration
            delay(10_000) // 10 seconds after STANDBY starts

            while (isActive) {
                try {
                    // Skip if not logged in
                    if (!authTokenManager.isLoggedIn()) {
                        delay(HEARTBEAT_INTERVAL_MS)
                        continue
                    }

                    // Skip if admin user
                    val isAdmin = authTokenManager.getUserRoles().any {
                        it.equals("admin", true) || it.equals("super_admin", true) || it.equals("superadmin", true)
                    }
                    if (isAdmin) {
                        delay(HEARTBEAT_INTERVAL_MS)
                        continue
                    }

                    val deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }
                    val fcmToken = FirebaseMessaging.getInstance().token.await()
                    val ip = getLocalIpStatic()
                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    val isScreenOn = pm.isInteractive

                    apiService.registerDevice(
                        CameraDeviceRegisterRequest(
                            deviceName = deviceName,
                            fcmToken = fcmToken,
                            localIp = ip,
                            screenOn = isScreenOn
                        )
                    )
                    Log.d(TAG, "Heartbeat OK: $deviceName (screen=${if (isScreenOn) "ON" else "OFF"})")
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat failed: ${e.message}")
                    // Not critical — will retry next interval
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    // ─── Notification ───────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Sinkronisasi", NotificationManager.IMPORTANCE_NONE
            ).apply {
                description = "Sinkronisasi data latar belakang"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (isStreaming) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun upgradeServiceType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val notification = buildNotification()
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            Log.i(TAG, "Service type upgraded to CAMERA")
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sinkronisasi data")
            .setContentText("Berjalan di latar belakang")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLocks() {
        try {
            // CPU WakeLock — keeps coroutines, heartbeat, and camera processing alive
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KabenSatuData:CameraStream"
            ).apply { acquire() }

            // WiFi WakeLock — keeps WiFi at full performance (no low-power mode)
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "KabenSatuData:CameraStream"
            ).apply { acquire() }

            Log.i(TAG, "WakeLocks acquired (CPU + WiFi)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLocks: ${e.message}")
        }
    }

    private fun releaseWakeLocks() {
        try {
            cpuWakeLock?.let { if (it.isHeld) it.release() }
            cpuWakeLock = null
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
            Log.i(TAG, "WakeLocks released")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLocks: ${e.message}")
        }
    }

    // ─── Camera ─────────────────────────────────────────

    private fun startCamera() {
        val cameraFuture = ProcessCameraProvider.getInstance(this)
        cameraFuture.addListener({
            val provider = cameraFuture.get()
            cameraProvider = provider

            val lensFacing = if (currentLens == "BACK") CameraSelector.LENS_FACING_BACK
                             else CameraSelector.LENS_FACING_FRONT

            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(960, 540))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val jpeg = yuv420ToJpeg(imageProxy)
                    if (jpeg != null && jpeg.isNotEmpty()) latestJpeg = jpeg
                } catch (e: Exception) {
                    Log.e(TAG, "Frame error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this@CameraForegroundService, selector, analysis)
                Log.i(TAG, "Camera bound ($currentLens)")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun restartCamera() {
        cameraProvider?.unbindAll()
        latestJpeg = null
        startCamera()
        serviceScope.launch {
            delay(1000)
            registerOnServer()
        }
    }

    // ─── Remote Photo Capture ────────────────────────────

    /**
     * Take a single high-resolution photo and upload it to the server.
     * The photo is NOT saved to device gallery — only uploaded.
     * If camera was not streaming, it will be opened and closed.
     */
    private fun handleTakePhoto(lens: String) {
        Log.i(TAG, "Taking photo ($lens)...")
        val wasStreaming = isStreaming

        serviceScope.launch {
            // Acquire temporary WakeLocks if not already streaming
            val needsTempLocks = !wasStreaming
            if (needsTempLocks) {
                acquireWakeLocks()
                withContext(Dispatchers.Main) { upgradeServiceType() }
            }

            try {
                // If already streaming, grab the latest frame at higher quality
                if (wasStreaming && currentLens == lens && latestJpeg != null) {
                    uploadSnapshot(latestJpeg!!, lens)
                    return@launch
                }

                // Need to open camera fresh for photo
                val previousLens = currentLens
                currentLens = lens

                // Ensure lifecycle is in correct state
                if (!wasStreaming) {
                    withContext(Dispatchers.Main) {
                        lifecycleRegistry.currentState = Lifecycle.State.STARTED
                        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                    }
                }

                // Open camera and wait for a good frame
                val capturedFrame = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<ByteArray?> { cont ->
                        val cameraFuture = ProcessCameraProvider.getInstance(this@CameraForegroundService)
                        cameraFuture.addListener({
                            try {
                                val provider = cameraFuture.get()
                                if (!wasStreaming) cameraProvider = provider

                                val lensFacing = if (lens == "BACK") CameraSelector.LENS_FACING_BACK
                                                 else CameraSelector.LENS_FACING_FRONT

                                val selector = CameraSelector.Builder()
                                    .requireLensFacing(lensFacing)
                                    .build()

                                val analysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1920, 1080))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                    .build()

                                var frameCount = 0
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    try {
                                        frameCount++
                                        // Skip first 2 frames (auto-exposure not ready)
                                        if (frameCount >= 3 && !cont.isCompleted) {
                                            val jpeg = yuv420ToJpeg(imageProxy, 85) // High quality
                                            cont.resume(jpeg, null)
                                        }
                                    } catch (e: Exception) {
                                        if (!cont.isCompleted) cont.resume(null, null)
                                    } finally {
                                        imageProxy.close()
                                    }
                                }

                                if (!wasStreaming) provider.unbindAll()
                                provider.bindToLifecycle(
                                    this@CameraForegroundService, selector, analysis
                                )

                                // Timeout: auto-cancel after 8 seconds
                                serviceScope.launch {
                                    delay(8000)
                                    if (!cont.isCompleted) cont.resume(null, null)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Photo camera failed", e)
                                if (!cont.isCompleted) cont.resume(null, null)
                            }
                        }, ContextCompat.getMainExecutor(this@CameraForegroundService))
                    }
                }

                // Clean up: stop camera if we weren't streaming
                if (!wasStreaming) {
                    withContext(Dispatchers.Main) {
                        cameraProvider?.unbindAll()
                    }
                } else {
                    // Restore original lens if changed
                    if (previousLens != lens) {
                        currentLens = previousLens
                        withContext(Dispatchers.Main) { restartCamera() }
                    }
                }

                // Upload the captured frame
                if (capturedFrame != null) {
                    uploadSnapshot(capturedFrame, lens)
                } else {
                    Log.w(TAG, "Failed to capture photo")
                }
            } finally {
                // Release temporary WakeLocks
                if (needsTempLocks) {
                    releaseWakeLocks()
                    withContext(Dispatchers.Main) { startForegroundWithNotification() }
                }
            }
        }
    }

    private suspend fun uploadSnapshot(jpeg: ByteArray, lens: String) {
        val name = try { authTokenManager.getUserName().ifBlank { "HP-Puskesmas" } }
                   catch (_: Exception) { "HP-Puskesmas" }
        try {
            withContext(Dispatchers.IO) {
                val body = okhttp3.RequestBody.create("image/jpeg".toMediaType(), jpeg)
                apiService.uploadFrame(name, "snapshot_$lens", body)
            }
            Log.i(TAG, "Snapshot uploaded: ${jpeg.size} bytes ($lens)")
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot upload failed: ${e.message}")
        }
    }

    private fun yuv420ToJpeg(image: ImageProxy): ByteArray? {
        val w = image.width; val h = image.height
        val yP = image.planes[0]; val uP = image.planes[1]; val vP = image.planes[2]
        val yBuf = yP.buffer; val uBuf = uP.buffer; val vBuf = vP.buffer
        val yStride = yP.rowStride; val uvStride = uP.rowStride; val uvPx = uP.pixelStride
        val nv21 = ByteArray(w * h * 3 / 2)

        // Optimized: bulk copy Y plane
        if (yStride == w) {
            yBuf.position(0)
            yBuf.get(nv21, 0, w * h)
        } else {
            var pos = 0
            for (r in 0 until h) { yBuf.position(r * yStride); yBuf.get(nv21, pos, w); pos += w }
        }

        // Optimized: bulk copy interleaved UV if pixelStride == 2 (common case)
        val uvSize = w * h / 2
        if (uvPx == 2 && uvStride == w) {
            vBuf.position(0)
            vBuf.get(nv21, w * h, uvSize)
        } else {
            var pos = w * h
            for (r in 0 until h / 2) {
                for (c in 0 until w / 2) {
                    val idx = r * uvStride + c * uvPx
                    vBuf.position(idx); nv21[pos++] = vBuf.get()
                    uBuf.position(idx); nv21[pos++] = uBuf.get()
                }
            }
        }

        val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
        val out = ByteArrayOutputStream(w * h / 6)
        return if (yuv.compressToJpeg(Rect(0, 0, w, h), JPEG_QUALITY, out)) out.toByteArray() else null
    }

    /** Overload with custom JPEG quality for high-res snapshots. */
    private fun yuv420ToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        val w = image.width; val h = image.height
        val yP = image.planes[0]; val uP = image.planes[1]; val vP = image.planes[2]
        val yBuf = yP.buffer; val uBuf = uP.buffer; val vBuf = vP.buffer
        val yStride = yP.rowStride; val uvStride = uP.rowStride; val uvPx = uP.pixelStride
        val nv21 = ByteArray(w * h * 3 / 2)
        if (yStride == w) { yBuf.position(0); yBuf.get(nv21, 0, w * h) }
        else { var pos = 0; for (r in 0 until h) { yBuf.position(r * yStride); yBuf.get(nv21, pos, w); pos += w } }
        val uvSize = w * h / 2
        if (uvPx == 2 && uvStride == w) { vBuf.position(0); vBuf.get(nv21, w * h, uvSize) }
        else {
            var pos = w * h
            for (r in 0 until h / 2) for (c in 0 until w / 2) {
                val idx = r * uvStride + c * uvPx
                vBuf.position(idx); nv21[pos++] = vBuf.get()
                uBuf.position(idx); nv21[pos++] = uBuf.get()
            }
        }
        val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
        val out = ByteArrayOutputStream(w * h / 4)
        return if (yuv.compressToJpeg(Rect(0, 0, w, h), quality, out)) out.toByteArray() else null
    }

    // ─── HTTP Server ────────────────────────────────────

    private fun startHttpServer() {
        httpServer = MjpegServer(PORT, this)
        httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "HTTP server on port $PORT")
    }

    fun getLatestFrame(): ByteArray? = latestJpeg

    fun getLocalIpAddress(): String? {
        try {
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (ip != null && (ip.startsWith("192.168") || ip.startsWith("10."))) return ip
                    }
                }
            }
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ─── API Registration + Network Change Detection ────

    @Volatile private var lastRegisteredIp: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun startKeepAlive() {
        serviceScope.launch {
            delay(3000)
            registerOnServer()
            // Fast heartbeat: every 10s to keep IP fresh on server
            while (isStreaming) {
                delay(10_000L) // 10 seconds — fast IP detection for router switches
                if (isStreaming) {
                    val currentIp = getLocalIpAddress()
                    if (currentIp != null && currentIp != lastRegisteredIp) {
                        // IP changed! Re-register immediately
                        Log.i(TAG, "IP changed: $lastRegisteredIp → $currentIp")
                        registerOnServer()
                    } else if (isStreaming) {
                        registerOnServer() // Normal heartbeat refresh
                    }
                }
            }
        }
        // Register network change callback for instant detection
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // New WiFi connected — IP may have changed
                    serviceScope.launch {
                        delay(2000) // Wait for IP assignment
                        if (isStreaming) {
                            val newIp = getLocalIpAddress()
                            if (newIp != null && newIp != lastRegisteredIp) {
                                Log.i(TAG, "WiFi changed! Re-registering: $newIp")
                                registerOnServer()
                            }
                        }
                    }
                }
                override fun onLost(network: Network) {
                    Log.w(TAG, "WiFi lost, waiting for reconnect...")
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback registration failed: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            val cb = networkCallback ?: return
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
            networkCallback = null
        } catch (_: Exception) {}
    }

    private suspend fun registerOnServer() {
        val ip = getLocalIpAddress() ?: return
        val name = try { authTokenManager.getUserName().ifBlank { "HP-Puskesmas" } } catch (_: Exception) { "HP-Puskesmas" }
        try {
            withContext(Dispatchers.IO) {
                apiService.registerCamera(CameraRegisterRequest(name, ip, PORT, currentLens))
            }
            lastRegisteredIp = ip
            Log.i(TAG, "Registered: http://$ip:$PORT ($currentLens)")
        } catch (e: Exception) {
            Log.w(TAG, "Register failed: ${e.message}")
        }
    }

    private fun unregisterFromServer() {
        try {
            val name = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }
            Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        apiService.unregisterCamera(CameraUnregisterRequest(name))
                    }
                    Log.i(TAG, "Unregistered from server")
                } catch (e: Exception) {
                    Log.w(TAG, "Unregister failed: ${e.message}")
                }
            }.start()
        } catch (_: Exception) {}
    }

    // ─── Frame Relay Upload ─────────────────────────────

    private var frameRelayJob: kotlinx.coroutines.Job? = null

    @Volatile private var lastRelayFrameSize = 0

    private fun startFrameRelay(type: String) {
        frameRelayJob?.cancel()
        frameRelayJob = serviceScope.launch {
            val name = try { authTokenManager.getUserName().ifBlank { "HP-Puskesmas" } } catch (_: Exception) { "HP-Puskesmas" }
            delay(3000) // Wait for first frames
            var relayIntervalMs = 300L // Start at ~3fps (adaptive)
            while (isActive) {
                try {
                    val frame = latestJpeg
                    if (frame != null && frame.size > 100) {
                        // Delta detection: skip if frame hasn't changed significantly
                        val sizeDiff = kotlin.math.abs(frame.size - lastRelayFrameSize)
                        val threshold = (lastRelayFrameSize * 0.08).toInt() // 8% change threshold
                        if (lastRelayFrameSize > 0 && sizeDiff < threshold) {
                            delay(relayIntervalMs)
                            continue
                        }

                        val startMs = System.currentTimeMillis()
                        withContext(Dispatchers.IO) {
                            val relayFrame = downscaleForRelay(frame)
                            val body = okhttp3.RequestBody.create(
                                "image/jpeg".toMediaType(), relayFrame
                            )
                            apiService.uploadFrame(name, type, body)
                        }
                        lastRelayFrameSize = frame.size

                        // Adaptive interval: adjust based on upload speed
                        val uploadMs = System.currentTimeMillis() - startMs
                        relayIntervalMs = when {
                            uploadMs < 150 -> maxOf(200L, relayIntervalMs - 50)  // Fast → speed up
                            uploadMs > 500 -> minOf(600L, relayIntervalMs + 100) // Slow → slow down
                            else -> relayIntervalMs // OK → keep current
                        }
                    }
                } catch (_: Exception) {
                    // Upload failed → slow down
                    relayIntervalMs = minOf(600L, relayIntervalMs + 100)
                }
                delay(relayIntervalMs)
            }
        }
    }

    // ─── Embedded HTTP Server ───────────────────────────

    private class MjpegServer(port: Int, private val svc: CameraForegroundService) : NanoHTTPD(port) {
        private val boundary = "frameboundary"

        override fun serve(session: IHTTPSession): Response = when (session.uri) {
            "/stream" -> serveMjpeg()
            "/snap" -> serveSnap()
            "/status" -> serveStatus()
            else -> serveHtml()
        }

        private fun serveHtml(): Response {
            val html = """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Monitor</title><style>body{margin:0;background:#111;display:flex;justify-content:center;align-items:center;min-height:100vh}img{max-width:100%;height:auto}</style></head><body><img id="cam" src="/snap"/><script>const img=document.getElementById('cam');function r(){img.src='/snap?t='+Date.now()}img.onload=function(){setTimeout(r,33)};img.onerror=function(){setTimeout(r,500)};r();</script></body></html>"""
            return newFixedLengthResponse(Response.Status.OK, "text/html", html)
        }

        private fun serveMjpeg(): Response {
            val input = object : java.io.InputStream() {
                private var buf = ByteArray(0)
                private var pos = 0
                private var closed = false
                private var lastFrame: ByteArray? = null

                private fun refill() {
                    if (closed) return
                    // Wait for a NEW frame (different from last sent) — fixes "stuck on 1 frame"
                    while (!closed) {
                        val frame = svc.getLatestFrame()
                        if (frame != null && frame !== lastFrame) {
                            lastFrame = frame
                            val h = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                            buf = h.toByteArray() + frame + "\r\n".toByteArray()
                            pos = 0
                            return
                        }
                        Thread.sleep(8) // Poll at ~125Hz, actual FPS limited by camera
                    }
                }

                override fun read(): Int {
                    if (pos >= buf.size) refill()
                    if (closed || pos >= buf.size) return -1
                    return buf[pos++].toInt() and 0xFF
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (pos >= buf.size) refill()
                    if (closed || pos >= buf.size) return -1
                    val n = minOf(len, buf.size - pos)
                    System.arraycopy(buf, pos, b, off, n)
                    pos += n
                    return n
                }

                override fun close() { closed = true; super.close() }
            }
            return newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=$boundary", input)
        }

        private fun serveSnap(): Response {
            val frame = svc.getLatestFrame() ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No frame")
            val r = newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(frame), frame.size.toLong())
            r.addHeader("Cache-Control", "no-cache, no-store")
            r.addHeader("Access-Control-Allow-Origin", "*")
            return r
        }

        private fun serveStatus(): Response {
            val json = """{"streaming":${svc.isStreaming},"lens":"${svc.currentLens}"}"""
            val r = newFixedLengthResponse(Response.Status.OK, "application/json", json)
            r.addHeader("Access-Control-Allow-Origin", "*")
            return r
        }
    }

    /**
     * Aggressive downscale for relay: 960×540 → 240×135, Q25.
     * Produces ~3-8KB frames optimal for shared hosting upload.
     */
    private fun downscaleForRelay(jpeg: ByteArray): ByteArray {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = RELAY_SAMPLE_SIZE }
            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return jpeg
            val out = java.io.ByteArrayOutputStream(jpeg.size / 6)
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, RELAY_QUALITY, out)
            bmp.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            jpeg // Fallback to original on error
        }
    }
}
