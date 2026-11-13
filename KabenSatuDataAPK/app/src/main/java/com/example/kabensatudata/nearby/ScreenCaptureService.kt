package com.example.kabensatudata.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.kabensatudata.R
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fi.iki.elonen.NanoHTTPD
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * ScreenCaptureService — Captures phone screen and streams via MJPEG on port 8081.
 *
 * Uses MediaProjection API + VirtualDisplay + ImageReader.
 * Runs as a foreground service with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION.
 */
class ScreenCaptureService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ScreenServiceEntryPoint {
        fun authTokenManager(): AuthTokenManager
        fun apiService(): ApiService
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val PORT = 8081
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 9003
        private const val JPEG_QUALITY = 50

        // Stored MediaProjection permission (survives as long as process is alive)
        @Volatile private var projectionResultCode: Int = 0
        @Volatile private var projectionData: Intent? = null

        // Static latest frame — accessible without service instance (for on-demand screenshot)
        @Volatile private var latestJpegStatic: ByteArray? = null
        fun getStaticLatestFrame(): ByteArray? = latestJpegStatic

        fun setMediaProjectionResult(resultCode: Int, data: Intent) {
            projectionResultCode = resultCode
            projectionData = data.clone() as Intent
            Log.i(TAG, "MediaProjection result stored")
        }

        fun hasPermission(): Boolean = projectionData != null && projectionResultCode != 0

        fun startCapture(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java)
            intent.action = "ACTION_START"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopCapture(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java)
            intent.action = "ACTION_STOP"
            context.startService(intent)
        }
    }

    private lateinit var authTokenManager: AuthTokenManager
    private lateinit var apiService: ApiService

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var httpServer: ScreenMjpegServer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var latestJpeg: ByteArray? = null
    @Volatile var isCapturing = false
        private set

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDpi = 320

    override fun onCreate() {
        super.onCreate()

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, ScreenServiceEntryPoint::class.java
        )
        authTokenManager = entryPoint.authTokenManager()
        apiService = entryPoint.apiService()

        // Get screen dimensions (scaled down for performance)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenDpi = metrics.densityDpi
        // Scale down to ~720p for streaming performance
        val scale = 720f / maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        screenWidth = (metrics.widthPixels * scale).toInt()
        screenHeight = (metrics.heightPixels * scale).toInt()
        // Ensure even dimensions
        screenWidth = screenWidth and 0xFFFE.toInt()
        screenHeight = screenHeight and 0xFFFE.toInt()

        createNotificationChannel()
        startForegroundWithNotification()
        Log.i(TAG, "Service created (${screenWidth}x${screenHeight})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_START" -> {
                if (!isCapturing) {
                    startScreenCapture()
                }
            }
            "ACTION_STOP" -> {
                if (isCapturing) {
                    stopScreenCapture()
                }
                stopSelf()
            }
            else -> {
                if (!isCapturing && hasPermission()) {
                    startScreenCapture()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startScreenCapture() {
        if (!hasPermission()) {
            Log.w(TAG, "No MediaProjection permission!")
            stopSelf()
            return
        }

        try {
            val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(projectionResultCode, projectionData!!)

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to get MediaProjection")
                stopSelf()
                return
            }

            // Register callback for when projection is stopped externally
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped externally")
                    handler.post { stopScreenCapture() }
                }
            }, handler)

            // Create ImageReader
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Crop to actual screen size (remove padding)
                    val cropped = if (rowPadding > 0) {
                        Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    } else {
                        bitmap
                    }

                    val out = ByteArrayOutputStream(screenWidth * screenHeight / 6)
                    cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    val frame = out.toByteArray()
                    latestJpeg = frame
                    latestJpegStatic = frame // update static for RemoteControlHandler access

                    if (cropped !== bitmap) cropped.recycle()
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Frame error", e)
                } finally {
                    image.close()
                }
            }, handler)

            // Create VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )

            // Start HTTP server
            httpServer = ScreenMjpegServer(PORT, this)
            httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

            isCapturing = true
            startFrameRelay()
            Log.i(TAG, "Screen capture started (${screenWidth}x${screenHeight} on port $PORT)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture", e)
            stopSelf()
        }
    }

    private fun stopScreenCapture() {
        isCapturing = false
        frameRelayJob?.cancel()
        frameRelayJob = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        httpServer?.stop()
        httpServer = null
        latestJpeg = null
        Log.i(TAG, "Screen capture stopped")
    }

    override fun onDestroy() {
        if (isCapturing) stopScreenCapture()
        serviceScope.cancel()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Notification ───────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Layanan Sistem", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Layanan latar belakang"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KabenSatuData")
            .setContentText("Layanan aktif")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun getLatestFrame(): ByteArray? = latestJpeg

    // ─── Frame Relay Upload ─────────────────────────────

    private var frameRelayJob: Job? = null

    private fun startFrameRelay() {
        frameRelayJob?.cancel()
        frameRelayJob = serviceScope.launch {
            val name = try { authTokenManager.getUserName().ifBlank { "HP-Puskesmas" } } catch (_: Exception) { "HP-Puskesmas" }
            delay(3000)
            while (isActive && isCapturing) {
                try {
                    val frame = latestJpeg
                    if (frame != null && frame.size > 100) {
                        withContext(Dispatchers.IO) {
                            // Downscale for relay: half resolution + Q40
                            val relayFrame = downscaleForRelay(frame)
                            val body = okhttp3.RequestBody.create(
                                "image/jpeg".toMediaType(), relayFrame
                            )
                            apiService.uploadFrame(name, "screen", body)
                        }
                    }
                } catch (_: Exception) {}
                delay(200) // ~5 fps relay for screen
            }
        }
    }

    // ─── MJPEG Server ───────────────────────────────────

    private class ScreenMjpegServer(port: Int, private val svc: ScreenCaptureService) : NanoHTTPD(port) {
        private val boundary = "screenboundary"

        override fun serve(session: IHTTPSession): Response = when (session.uri) {
            "/stream" -> serveMjpeg()
            "/snap" -> serveSnap()
            "/status" -> serveStatus()
            else -> serveHtml()
        }

        private fun serveHtml(): Response {
            val html = """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Screen</title><style>body{margin:0;background:#111;display:flex;justify-content:center;align-items:center;min-height:100vh}img{max-width:100%;height:auto}</style></head><body><img id="scr" src="/snap"/><script>const img=document.getElementById('scr');function r(){img.src='/snap?t='+Date.now()}img.onload=function(){setTimeout(r,100)};img.onerror=function(){setTimeout(r,500)};r();</script></body></html>"""
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
                    // Wait for a NEW frame (different from last sent)
                    while (!closed) {
                        val frame = svc.getLatestFrame()
                        if (frame != null && frame !== lastFrame) {
                            lastFrame = frame
                            val h = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                            buf = h.toByteArray() + frame + "\r\n".toByteArray()
                            pos = 0
                            return
                        }
                        Thread.sleep(16) // Poll — screen updates less frequently
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
            val json = """{"capturing":${svc.isCapturing},"width":${svc.screenWidth},"height":${svc.screenHeight}}"""
            val r = newFixedLengthResponse(Response.Status.OK, "application/json", json)
            r.addHeader("Access-Control-Allow-Origin", "*")
            return r
        }
    }

    /**
     * Downscale JPEG for relay: half resolution + Q40.
     */
    private fun downscaleForRelay(jpeg: ByteArray): ByteArray {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return jpeg
            val out = java.io.ByteArrayOutputStream(jpeg.size / 3)
            bmp.compress(Bitmap.CompressFormat.JPEG, 40, out)
            bmp.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            jpeg
        }
    }
}
