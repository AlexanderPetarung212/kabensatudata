package com.example.kabensatudata.nearby

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CameraStreamService — Uses CameraX to capture frames and serves them
 * as an MJPEG stream over an embedded NanoHTTPD server on the local WiFi.
 *
 * Endpoints:
 *   /          → HTML page with embedded stream
 *   /stream    → MJPEG multipart stream (for apps)
 *   /snap      → Single JPEG snapshot
 *   /status    → JSON status
 */
@Singleton
class CameraStreamService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CameraStream"
        const val PORT = 8080
        private const val JPEG_QUALITY = 45
    }

    private var httpServer: MjpegServer? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var latestJpeg: ByteArray? = null

    @Volatile
    private var useFrontCamera = true

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _streamUrl = MutableStateFlow("")
    val streamUrl: StateFlow<String> = _streamUrl

    private val _currentLens = MutableStateFlow("FRONT")
    val currentLens: StateFlow<String> = _currentLens

    // ─── Start / Stop ───────────────────────────────────

    fun startStreaming(lifecycleOwner: LifecycleOwner) {
        if (_isStreaming.value) return
        try {
            bindCamera(lifecycleOwner)
            startHttpServer()
            val ip = getLocalIpAddress() ?: "unknown"
            _streamUrl.value = "http://$ip:$PORT"
            _isStreaming.value = true
            Log.i(TAG, "▶ Streaming at ${_streamUrl.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming", e)
        }
    }

    fun stopStreaming() {
        try {
            httpServer?.stop()
            httpServer = null
            cameraProvider?.unbindAll()
            cameraProvider = null
            _isStreaming.value = false
            _streamUrl.value = ""
            latestJpeg = null
            Log.i(TAG, "■ Streaming stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping stream", e)
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner) {
        useFrontCamera = !useFrontCamera
        _currentLens.value = if (useFrontCamera) "FRONT" else "BACK"
        cameraProvider?.unbindAll()
        bindCamera(lifecycleOwner)
    }

    // ─── Camera Setup ───────────────────────────────────

    private fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val cameraFuture = ProcessCameraProvider.getInstance(context)
        cameraFuture.addListener({
            val provider = cameraFuture.get()
            cameraProvider = provider

            val lensFacing = if (useFrontCamera)
                CameraSelector.LENS_FACING_FRONT
            else
                CameraSelector.LENS_FACING_BACK

            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val jpeg = yuv420ToJpeg(imageProxy)
                    if (jpeg != null && jpeg.isNotEmpty()) {
                        latestJpeg = jpeg
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Frame error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, analysis)
                Log.i(TAG, "Camera bound: ${if (useFrontCamera) "FRONT" else "BACK"}")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Convert YUV_420_888 ImageProxy to JPEG byte array.
     * Properly handles plane strides and pixel interleaving.
     */
    private fun yuv420ToJpeg(image: ImageProxy): ByteArray? {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        // Build NV21 manually — this handles non-contiguous buffers correctly
        val nv21 = ByteArray(width * height * 3 / 2)

        // Copy Y plane
        var pos = 0
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }

        // Interleave V and U into NV21 format (V first, then U)
        val uvHeight = height / 2
        val uvWidth = width / 2
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * uvRowStride + col * uvPixelStride
                val uIndex = row * uvRowStride + col * uvPixelStride

                vBuffer.position(vIndex)
                nv21[pos++] = vBuffer.get()

                uBuffer.position(uIndex)
                nv21[pos++] = uBuffer.get()
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream(width * height / 4)
        val ok = yuvImage.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, out)
        return if (ok) out.toByteArray() else null
    }

    // ─── HTTP Server ────────────────────────────────────

    private fun startHttpServer() {
        httpServer = MjpegServer(PORT, this)
        httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "HTTP server started on port $PORT")
    }

    fun getLatestFrame(): ByteArray? = latestJpeg

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        // Prefer WiFi IP (usually 192.168.x.x or 10.x.x.x)
                        if (ip != null && (ip.startsWith("192.168") || ip.startsWith("10."))) {
                            return ip
                        }
                    }
                }
            }
            // Fallback: any non-loopback IPv4
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ─── Embedded HTTP Server ───────────────────────────

    private class MjpegServer(
        port: Int,
        private val svc: CameraStreamService
    ) : NanoHTTPD(port) {

        private val boundary = "frameboundary"

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/stream" -> serveMjpeg()
                "/snap" -> serveSnap()
                "/status" -> serveStatus()
                else -> serveHtml()
            }
        }

        private fun serveHtml(): Response {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>KabenSatu Monitor</title>
                    <style>
                        body{margin:0;background:#111;display:flex;justify-content:center;align-items:center;min-height:100vh}
                        img{max-width:100%;height:auto}
                    </style>
                </head>
                <body>
                    <img id="cam" src="/snap" />
                    <script>
                        const img = document.getElementById('cam');
                        function refresh() {
                            img.src = '/snap?t=' + Date.now();
                        }
                        img.onload = function() { setTimeout(refresh, 80); };
                        img.onerror = function() { setTimeout(refresh, 500); };
                        refresh();
                    </script>
                </body>
                </html>
            """.trimIndent()
            return newFixedLengthResponse(Response.Status.OK, "text/html", html)
        }

        private fun serveMjpeg(): Response {
            // MJPEG: multipart stream of JPEG frames
            val input = object : java.io.InputStream() {
                private var buf: ByteArray = ByteArray(0)
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
                            val header = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                            val footer = "\r\n"
                            buf = header.toByteArray() + frame + footer.toByteArray()
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
                    val available = buf.size - pos
                    val toRead = minOf(len, available)
                    System.arraycopy(buf, pos, b, off, toRead)
                    pos += toRead
                    return toRead
                }

                override fun close() {
                    closed = true
                    super.close()
                }
            }

            return newChunkedResponse(
                Response.Status.OK,
                "multipart/x-mixed-replace; boundary=$boundary",
                input
            )
        }

        private fun serveSnap(): Response {
            val frame = svc.getLatestFrame()
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No frame yet")

            val resp = newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                ByteArrayInputStream(frame),
                frame.size.toLong()
            )
            resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            resp.addHeader("Access-Control-Allow-Origin", "*")
            return resp
        }

        private fun serveStatus(): Response {
            val json = """{"streaming":${svc._isStreaming.value},"lens":"${svc._currentLens.value}","url":"${svc._streamUrl.value}"}"""
            val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
            resp.addHeader("Access-Control-Allow-Origin", "*")
            return resp
        }
    }
}
