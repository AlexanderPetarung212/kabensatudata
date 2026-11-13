package com.example.kabensatudata.ui.screen.nearby

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

private val DarkBg = Color(0xFF0F0F1A)
private val DarkSurface = Color(0xFF1A1A2E)

/**
 * MonitorScreen — Admin live camera preview.
 *
 * Direct mode: Native MJPEG stream parser (single persistent connection, ~25-30fps)
 * Relay mode:  Snapshot polling from server (~6fps)
 * Auto-fallback: Direct fails → automatically switches to Relay
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    streamUrl: String,
    relayUrl: String = "",
    peerName: String,
    authToken: String? = null,
    onBack: () -> Unit
) {
    val Teal = AppColors.Primary
    val hasLocalUrl = !streamUrl.contains("/camera/frame/") && relayUrl.isNotBlank()

    // ─── Mode: "checking" → "local" → (if error) → "relay"
    var mode by remember { mutableStateOf(if (hasLocalUrl) "checking" else "relay") }
    var retryKey by remember { mutableIntStateOf(0) } // Increment to re-trigger check
    var isScanning by remember { mutableStateOf(false) } // Scanning indicator

    // ─── Shared frame state ────────────
    var currentFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var frameCount by remember { mutableIntStateOf(0) }
    var resolvedStreamUrl by remember { mutableStateOf(streamUrl) }

    val localStreamUrl = remember(resolvedStreamUrl) {
        resolvedStreamUrl.trimEnd('/') + "/stream"
    }
    val activeRelayUrl = remember(streamUrl, relayUrl) {
        if (relayUrl.isNotBlank()) relayUrl else streamUrl
    }

    // ═══════════════════════════════════════════
    // Phase 1: Cross-Subnet P2P Discovery
    // Puskesmas has 3 routers on same Starlink.
    // If direct IP fails, try same host on other subnets
    // before falling back to server relay.
    // ═══════════════════════════════════════════

    if (hasLocalUrl && mode == "checking") {
        LaunchedEffect(streamUrl, retryKey) {
            isScanning = true

            // ─── Step 0 + Step 1 with AUTO-RETRY ───
            // If direct connection fails, wait and re-fetch fresh IP from server.
            // Camera device re-registers IP every 10s, so retry catches router switches.
            var directConnected = false
            for (ipRetry in 1..3) {
                // ─── Fetch FRESH device IP from server ───
                val freshStreamUrl = withContext(Dispatchers.IO) {
                    try {
                        val apiUrl = "https://kabensatudata.my.id/api/mobile/camera/devices"
                        val conn = URL(apiUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.requestMethod = "GET"
                        if (!authToken.isNullOrBlank()) {
                            conn.setRequestProperty("Authorization", "Bearer $authToken")
                        }
                        conn.setRequestProperty("Accept", "application/json")
                        val code = conn.responseCode
                        if (code == 200) {
                            val body = conn.inputStream.bufferedReader().readText()
                            conn.disconnect()
                            val jsonObj = org.json.JSONObject(body)
                            val dataArr = jsonObj.optJSONArray("data")
                            if (dataArr != null) {
                                for (i in 0 until dataArr.length()) {
                                    val dev = dataArr.getJSONObject(i)
                                    if (dev.optString("device_name") == peerName) {
                                        val freshIp = dev.optString("local_ip", "")
                                        val freshPort = dev.optInt("port", 8080)
                                        if (freshIp.isNotBlank()) {
                                            "http://$freshIp:$freshPort"
                                        } else null
                                    }
                                }
                            }
                            null
                        } else {
                            conn.disconnect()
                            null
                        }
                    } catch (_: Exception) { null }
                }

                // Use fresh IP if available
                val effectiveStreamUrl = freshStreamUrl ?: streamUrl
                if (freshStreamUrl != null && freshStreamUrl != streamUrl) {
                    resolvedStreamUrl = freshStreamUrl
                }

                // ─── Try direct connection (2 sub-attempts) ───
                var reachable = false
                for (attempt in 1..2) {
                    reachable = withContext(Dispatchers.IO) {
                        try {
                            val statusUrl = effectiveStreamUrl.trimEnd('/') + "/status"
                            val conn = URL(statusUrl).openConnection() as HttpURLConnection
                            conn.connectTimeout = 3000
                            conn.readTimeout = 3000
                            conn.requestMethod = "GET"
                            val code = conn.responseCode
                            conn.disconnect()
                            code == 200
                        } catch (_: Exception) { false }
                    }
                    if (reachable) break
                    if (attempt < 2) delay(500L)
                }

                if (reachable) {
                    resolvedStreamUrl = effectiveStreamUrl
                    directConnected = true
                    break // Success! 
                }

                // Not reachable — wait 5s for camera to re-register its new IP
                if (ipRetry < 3) {
                    delay(5000L)
                }
            }

            if (directConnected) {
                isScanning = false
                mode = "local"
                return@LaunchedEffect
            }

            // ─── Step 2: Cross-Subnet Scan ───
            // Direct connection failed even with retries.
            // Compute variables needed for subnet scanning.
            val lastEffectiveUrl = resolvedStreamUrl.ifBlank { streamUrl }
            val adminIp = withContext(Dispatchers.IO) {
                try {
                    java.net.NetworkInterface.getNetworkInterfaces()?.toList()
                        ?.flatMap { it.inetAddresses.toList() }
                        ?.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                        ?.hostAddress
                } catch (_: Exception) { null }
            }
            val deviceIp = lastEffectiveUrl.substringAfter("//").substringBefore(":")
            val devicePort = lastEffectiveUrl.substringAfterLast(":").substringBefore("/").toIntOrNull() ?: 8080
            val hostPart = deviceIp.substringAfterLast(".")
            val trustedPrefixes = listOf("192.168.3.", "192.168.0.", "192.168.2.")
            val adminTrusted = adminIp != null && trustedPrefixes.any { adminIp.startsWith(it) }
            val deviceTrusted = trustedPrefixes.any { deviceIp.startsWith(it) }

            if (!(adminTrusted && deviceTrusted)) {
                mode = "relay"
                isScanning = false
                return@LaunchedEffect
            }

            // Original IP failed (different subnet NAT).
            // Try same host part on ALL trusted subnets in parallel.
            val candidateIps = trustedPrefixes
                .map { prefix -> prefix + hostPart }
                .filter { it != deviceIp } // skip the one we already tried

            var foundUrl: String? = null

            // Probe each candidate IP sequentially (only 2 IPs to check)
            for (candidateIp in candidateIps) {
                val candidateReachable = withContext(Dispatchers.IO) {
                    try {
                        val statusUrl = "http://$candidateIp:$devicePort/status"
                        val conn = URL(statusUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        conn.requestMethod = "GET"
                        val code = conn.responseCode
                        conn.disconnect()
                        code == 200
                    } catch (_: Exception) { false }
                }
                if (candidateReachable) {
                    foundUrl = "http://$candidateIp:$devicePort"
                    break
                }
            }

            if (foundUrl != null) {
                resolvedStreamUrl = foundUrl!!
                isScanning = false
                mode = "local"
                return@LaunchedEffect
            }

            // ─── Step 3: Full subnet scan (gateway IPs may differ) ───
            // Try common DHCP IPs on admin's own subnet (in case device got
            // a completely different host part from the other router's DHCP)
            val adminSubnet = adminIp?.substringBeforeLast(".") ?: ""
            if (adminSubnet.isNotEmpty()) {
                // Quick scan: try the device's registered IP directly on admin's subnet
                // plus a few common alternatives
                val quickScanIps = listOf(
                    "$adminSubnet.$hostPart" // same host part on admin's subnet
                )
                    .filter { it != deviceIp && !candidateIps.contains(it) }
                    .distinct()

                for (scanIp in quickScanIps) {
                    val scanReachable = withContext(Dispatchers.IO) {
                        try {
                            val statusUrl = "http://$scanIp:$devicePort/status"
                            val conn = URL(statusUrl).openConnection() as HttpURLConnection
                            conn.connectTimeout = 2000
                            conn.readTimeout = 2000
                            conn.requestMethod = "GET"
                            val code = conn.responseCode
                            conn.disconnect()
                            code == 200
                        } catch (_: Exception) { false }
                    }
                    if (scanReachable) {
                        resolvedStreamUrl = "http://$scanIp:$devicePort"
                        isScanning = false
                        mode = "local"
                        return@LaunchedEffect
                    }
                }
            }

            // ─── Step 4: All P2P attempts failed → relay ───
            isScanning = false
            mode = "relay"
        }
    }

    // ═══════════════════════════════════════════
    // Phase 2: MJPEG stream parser (Direct mode)
    // ═══════════════════════════════════════════
    if (mode == "local") {
        LaunchedEffect(localStreamUrl) {
            isLoading = true
            errorMsg = null

            withContext(Dispatchers.IO) {
                try {
                    val conn = URL(localStreamUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 0 // Infinite — MJPEG is a persistent stream
                    conn.setRequestProperty("Cache-Control", "no-cache")
                    conn.useCaches = false

                    if (conn.responseCode != 200) {
                        mode = "relay"
                        return@withContext
                    }

                    // Extract boundary from Content-Type header
                    val contentType = conn.getHeaderField("Content-Type") ?: ""
                    val streamBoundary = contentType
                        .split(";")
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("boundary=", ignoreCase = true) }
                        ?.substringAfter("=")
                        ?.trim()
                        ?: "frameboundary"

                    val input = BufferedInputStream(conn.inputStream, 128 * 1024)
                    isLoading = false
                    isConnected = true

                    // MJPEG format per frame:
                    // --boundary\r\n
                    // Content-Type: image/jpeg\r\n
                    // Content-Length: N\r\n
                    // \r\n
                    // <N bytes JPEG>
                    // \r\n

                    val lineBuffer = StringBuilder(256)

                    while (isActive && mode == "local") {
                        // ── Read headers until empty line ──
                        var contentLength = -1
                        lineBuffer.clear()

                        headerLoop@ while (isActive) {
                            val b = input.read()
                            if (b == -1) { mode = "relay"; break@headerLoop }

                            lineBuffer.append(b.toChar())

                            if (lineBuffer.endsWith("\r\n")) {
                                val line = lineBuffer.toString().trimEnd()
                                lineBuffer.clear()

                                if (line.isEmpty()) {
                                    // Empty line only counts as "end of headers" if we
                                    // already saw at least one header (boundary line)
                                    if (contentLength > 0) break@headerLoop
                                    // Otherwise it's just trailing \r\n from previous frame — skip
                                    continue@headerLoop
                                }

                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
                                }
                            }
                        }

                        if (mode != "local") break

                        // ── Read JPEG frame by Content-Length ──
                        if (contentLength > 0) {
                            val frameBytes = ByteArray(contentLength)
                            var read = 0
                            while (read < contentLength && isActive) {
                                val n = input.read(frameBytes, read, contentLength - read)
                                if (n == -1) break
                                read += n
                            }

                            if (read == contentLength) {
                                val bmp = BitmapFactory.decodeByteArray(frameBytes, 0, contentLength)
                                if (bmp != null) {
                                    currentFrame = bmp
                                    frameCount++
                                }
                            }
                            // Trailing \r\n will be consumed as empty line in next header loop
                        }
                    }

                    try { input.close() } catch (_: Exception) {}
                    try { conn.disconnect() } catch (_: Exception) {}

                } catch (e: Exception) {
                    if (mode == "local") mode = "relay"
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    // Phase 3: Relay — MJPEG stream first, snapshot polling fallback
    // Optimized: 1 persistent connection eliminates per-request overhead
    // ═══════════════════════════════════════════
    if (mode == "relay") {
        // Derive MJPEG stream URL from snapshot URL: /frame/ → /stream/
        val relayStreamUrl = remember(activeRelayUrl) {
            activeRelayUrl.replace("/camera/frame/", "/camera/stream/")
        }

        LaunchedEffect(relayStreamUrl) {
            isLoading = true
            errorMsg = null

            // Phase 3a: Try MJPEG stream relay (1 persistent connection)
            val streamWorked = withContext(Dispatchers.IO) {
                try {
                    val conn = URL(relayStreamUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 0 // Infinite for persistent stream
                    conn.setRequestProperty("Cache-Control", "no-cache")
                    if (!authToken.isNullOrBlank()) {
                        conn.setRequestProperty("Authorization", "Bearer $authToken")
                    }
                    conn.useCaches = false

                    if (conn.responseCode != 200) {
                        conn.disconnect()
                        return@withContext false
                    }

                    val contentType = conn.getHeaderField("Content-Type") ?: ""
                    val streamBoundary = contentType
                        .split(";").map { it.trim() }
                        .firstOrNull { it.startsWith("boundary=", ignoreCase = true) }
                        ?.substringAfter("=")?.trim()
                        ?: "relayboundary"

                    val input = BufferedInputStream(conn.inputStream, 64 * 1024)
                    isLoading = false
                    isConnected = true

                    val lineBuffer = StringBuilder(256)

                    while (isActive && mode == "relay") {
                        var contentLength = -1
                        lineBuffer.clear()

                        headerLoop@ while (isActive) {
                            val b = input.read()
                            if (b == -1) break@headerLoop
                            lineBuffer.append(b.toChar())
                            if (lineBuffer.endsWith("\r\n")) {
                                val line = lineBuffer.toString().trimEnd()
                                lineBuffer.clear()
                                if (line.isEmpty()) {
                                    if (contentLength > 0) break@headerLoop
                                    continue@headerLoop
                                }
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
                                }
                            }
                        }

                        if (contentLength > 0) {
                            val frameBytes = ByteArray(contentLength)
                            var read = 0
                            while (read < contentLength && isActive) {
                                val n = input.read(frameBytes, read, contentLength - read)
                                if (n == -1) break
                                read += n
                            }
                            if (read == contentLength) {
                                val bmp = BitmapFactory.decodeByteArray(frameBytes, 0, contentLength)
                                if (bmp != null) {
                                    currentFrame = bmp
                                    frameCount++
                                }
                            }
                        } else {
                            break // Stream ended or parse error → fallback
                        }
                    }

                    try { input.close() } catch (_: Exception) {}
                    try { conn.disconnect() } catch (_: Exception) {}
                    frameCount > 0 // Considered "worked" if at least 1 frame parsed
                } catch (_: Exception) {
                    false
                }
            }

            // Phase 3b: Fallback to snapshot polling if stream didn't work
            if (!streamWorked && isActive && mode == "relay") {
                var retryCount = 0
                withContext(Dispatchers.IO) {
                    while (isActive) {
                        try {
                            val conn = URL(activeRelayUrl).openConnection() as HttpURLConnection
                            conn.connectTimeout = 4000
                            conn.readTimeout = 4000
                            conn.requestMethod = "GET"
                            conn.setRequestProperty("Cache-Control", "no-cache")
                            if (!authToken.isNullOrBlank()) {
                                conn.setRequestProperty("Authorization", "Bearer $authToken")
                            }
                            conn.useCaches = false

                            val code = conn.responseCode
                            if (code == 200) {
                                val bytes = conn.inputStream.use { it.readBytes() }
                                if (bytes.size > 100) {
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bmp != null) {
                                        currentFrame = bmp
                                        isConnected = true
                                        isLoading = false
                                        errorMsg = null
                                        frameCount++
                                        retryCount = 0
                                    }
                                }
                            } else if (code == 404) {
                                errorMsg = "Frame belum tersedia — pastikan streaming sudah aktif"
                                isLoading = false
                            }
                            conn.disconnect()
                            delay(80L)
                        } catch (e: Exception) {
                            retryCount++
                            if (retryCount > 5 && !isConnected) {
                                errorMsg = "Gagal terhubung ke server relay"
                                isLoading = false
                            }
                            delay(if (retryCount > 3) 2000L else 500L)
                        }
                    }
                }
            }
        }
    }

    // ─── Status ──────────────────────────
    val statusText = when {
        isScanning && currentFrame != null -> "Scanning MJPEG..."
        mode == "checking" -> "Memeriksa koneksi..."
        mode == "local" && isConnected -> "MJPEG Direct • $frameCount frames"
        mode == "relay" && isConnected -> "Relay • $frameCount frames"
        mode == "relay" && hasLocalUrl -> "Fallback relay..."
        else -> "Menghubungkan..."
    }
    val statusColor = when {
        isScanning -> Color(0xFFFFA726) // Orange = scanning
        mode == "local" && isConnected -> AppColors.Success
        mode == "relay" && isConnected -> AppColors.Success
        else -> Color.Gray
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(peerName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFFA726)
                                )
                            } else {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(statusText, fontSize = 11.sp, color = statusColor)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    // Show "Try MJPEG" button when in relay mode
                    if (mode == "relay" && hasLocalUrl && !isScanning) {
                        TextButton(
                            onClick = {
                                retryKey++
                                mode = "checking"
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF4FC3F7)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "MJPEG",
                                color = Color(0xFF4FC3F7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Show scanning indicator in actions area
                    if (isScanning && currentFrame != null) {
                        TextButton(onClick = {}, enabled = false) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFFA726)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Scanning...", color = Color(0xFFFFA726), fontSize = 11.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when {
                    mode == "checking" || (isLoading && currentFrame == null) -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Teal, strokeWidth = 3.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                when (mode) {
                                    "checking" -> "Memeriksa koneksi langsung..."
                                    "relay" -> if (hasLocalUrl) "Koneksi langsung gagal, beralih ke relay..." else "Menghubungkan..."
                                    else -> "Menghubungkan MJPEG..."
                                },
                                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp
                            )
                        }
                    }
                    errorMsg != null && currentFrame == null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.VideocamOff, null,
                                modifier = Modifier.size(48.dp), tint = AppColors.Error)
                            Spacer(Modifier.height(8.dp))
                            Text("Gagal Terhubung", color = AppColors.Error,
                                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(errorMsg ?: "", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                    currentFrame != null -> {
                        // key(frameCount) forces Image re-render on every new frame
                        key(frameCount) {
                            Image(
                                bitmap = currentFrame!!.asImageBitmap(),
                                contentDescription = "Live Camera",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // LIVE badge
                        Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                            val pulse = rememberInfiniteTransition(label = "live")
                            val alpha by pulse.animateFloat(
                                0.4f, 1f,
                                infiniteRepeatable(tween(800), RepeatMode.Reverse),
                                label = "liveDot"
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Red.copy(alpha = 0.85f)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape)
                                        .background(Color.White.copy(alpha = alpha)))
                                    Spacer(Modifier.width(6.dp))
                                    Text("LIVE", color = Color.White, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (mode == "local") "MJPEG" else "RELAY",
                                        color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Info Bar ──────────────────
            Surface(Modifier.fillMaxWidth(), color = DarkSurface) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Perangkat", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        Text(peerName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Mode", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        Text(
                            when (mode) {
                                "local" -> "MJPEG Direct (WiFi)"
                                "relay" -> "Relay Server"
                                else -> "Memeriksa..."
                            },
                            fontSize = 12.sp,
                            color = when (mode) {
                                "local" -> AppColors.Success
                                "relay" -> Teal
                                else -> Color.Gray
                            }
                        )
                    }
                }
            }
        }
    }
}
