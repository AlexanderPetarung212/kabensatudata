package com.example.kabensatudata.ui.screen.inventaris

import android.Manifest
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.example.kabensatudata.ui.theme.AppColors
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onQrScanned: (String) -> Unit,  // Pass UUID from QR
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var scannedValue by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Ruangan", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.PrimaryDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (!hasCameraPermission) {
                // Permission not granted
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("📷", fontSize = 56.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Izinkan akses kamera untuk\nmemindai QR Code",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) { Text("Izinkan Kamera") }
                }
            } else {
                // Camera preview
                CameraPreviewWithScanner(
                    onBarcodeDetected = { rawValue ->
                        if (!isProcessing && scannedValue == null) {
                            isProcessing = true
                            scannedValue = rawValue

                            // Extract UUID from QR value (URL or raw UUID)
                            val uuid = extractUuidFromQr(rawValue)
                            if (uuid != null) {
                                onQrScanned(uuid)
                            } else {
                                // Reset if not valid
                                scannedValue = null
                                isProcessing = false
                            }
                        }
                    }
                )

                // Scanning overlay
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(Modifier.height(16.dp))

                    // Scan frame indicator
                    Box(
                        Modifier.size(240.dp)
                            .border(3.dp, AppColors.Primary.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    )

                    Surface(
                        Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            if (scannedValue != null) "✅ QR terdeteksi..." else "Arahkan kamera ke QR Code ruangan",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithScanner(
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processBarcode(imageProxy, onBarcodeDetected)
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("QrScanner", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processBarcode(
    imageProxy: ImageProxy,
    onDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    if (barcode.valueType == Barcode.TYPE_URL || barcode.valueType == Barcode.TYPE_TEXT) {
                        barcode.rawValue?.let { onDetected(it) }
                        break
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    } else {
        imageProxy.close()
    }
}

/**
 * Extract UUID from QR value.
 * QR may contain full URL like "https://domain.com/inventaris/abc-def-123"
 * or just the UUID "abc-def-123".
 */
private fun extractUuidFromQr(rawValue: String): String? {
    // Pattern: URL ending with UUID
    val urlPattern = Regex(".*/inventaris/([a-f0-9-]{36}).*", RegexOption.IGNORE_CASE)
    val urlMatch = urlPattern.find(rawValue)
    if (urlMatch != null) return urlMatch.groupValues[1]

    // Pattern: Raw UUID
    val uuidPattern = Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$", RegexOption.IGNORE_CASE)
    if (uuidPattern.matches(rawValue.trim())) return rawValue.trim()

    return null
}
