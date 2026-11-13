package com.example.kabensatudata.ui.screen.setup

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.kabensatudata.nearby.KabenDeviceAdminReceiver
import com.example.kabensatudata.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * PermissionSetupScreen v2 — "Auto-Pilot Wizard"
 *
 * Problem: 20+ HP Puskesmas harus setup izin manual = 5-10 menit per HP.
 * Solution: Wizard otomatis yang auto-chain semua izin.
 *
 * Flow:
 *   Step 0: Tap "Setup Otomatis" (1x tap saja!)
 *   Step 1: Auto-request semua runtime permissions (batch)
 *   Step 2: Auto-buka Accessibility Settings (dengan instruksi jelas)
 *   Step 3: Auto-buka Device Admin dialog
 *   Step 4: Auto-request Battery Optimization whitelist
 *   Step 5: Auto-complete → masuk aplikasi
 *
 * Jika SEMUA izin sudah granted → auto-skip screen ini!
 */
@Composable
fun PermissionSetupScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ─── Permission states ──────────────────
    var locationGranted by remember { mutableStateOf(false) }
    var cameraGranted by remember { mutableStateOf(false) }
    var audioGranted by remember { mutableStateOf(false) }
    var bluetoothGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    var deviceAdminGranted by remember { mutableStateOf(false) }
    var smsGranted by remember { mutableStateOf(false) }
    var callLogGranted by remember { mutableStateOf(false) }
    var contactsGranted by remember { mutableStateOf(false) }
    var storageGranted by remember { mutableStateOf(false) }
    var backgroundLocationGranted by remember { mutableStateOf(false) }
    var batteryOptimized by remember { mutableStateOf(false) }

    // ─── Wizard state ──────────────────────
    var wizardStep by remember { mutableIntStateOf(-1) } // -1 = not started
    var wizardActive by remember { mutableStateOf(false) }
    var showAccessibilityGuide by remember { mutableStateOf(false) }

    fun recheckPermissions() {
        locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else true

        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        callLogGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        accessibilityGranted = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { it.resolveInfo.serviceInfo.packageName == context.packageName } == true

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, KabenDeviceAdminReceiver::class.java)
        deviceAdminGranted = dpm?.isAdminActive(adminComponent) == true

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        batteryOptimized = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    LaunchedEffect(Unit) { recheckPermissions() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                recheckPermissions()
                // Auto-advance wizard when returning from Settings
                if (wizardActive && showAccessibilityGuide) {
                    showAccessibilityGuide = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ─── Auto-skip if everything already granted ────
    val runtimePerms = listOf(locationGranted, cameraGranted, audioGranted, bluetoothGranted, notificationGranted, smsGranted, callLogGranted, contactsGranted, storageGranted)
    val allRuntimeGranted = runtimePerms.all { it }
    val allPerms = runtimePerms + listOf(backgroundLocationGranted, accessibilityGranted, deviceAdminGranted)
    val grantedCount = allPerms.count { it }
    val totalCount = allPerms.size
    val allGranted = allPerms.all { it }

    // Auto-skip screen if ALL permissions already granted
    LaunchedEffect(allGranted) {
        if (allGranted) {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("permission_setup_done", true)
                .apply()
            delay(300) // Brief pause so user sees "100%"
            onComplete()
        }
    }

    // ─── Launchers ──────────────────────────
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        recheckPermissions()
        // Auto-advance to background location step
        if (wizardActive) {
            wizardStep = 2 // Move to background location step
        }
    }

    // Separate launcher for background location (Android REQUIRES this to be separate!)
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        recheckPermissions()
        if (wizardActive) {
            wizardStep = 3 // Move to accessibility step
        }
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        recheckPermissions()
        // Auto-advance to battery optimization step
        if (wizardActive) {
            wizardStep = 5
        }
    }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        recheckPermissions()
        if (wizardActive) {
            wizardStep = 6 // Complete
        }
    }

    // ─── Wizard auto-chain logic ──────────
    LaunchedEffect(wizardStep) {
        if (!wizardActive) return@LaunchedEffect
        when (wizardStep) {
            1 -> {
                // Step 1: Request ALL runtime permissions at once
                delay(500)
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    perms.addAll(listOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                perms.addAll(listOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CONTACTS
                ))
                // Only request permissions that aren't already granted
                val ungranted = perms.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (ungranted.isNotEmpty()) {
                    runtimePermissionLauncher.launch(ungranted.toTypedArray())
                } else {
                    wizardStep = 2
                }
            }
            2 -> {
                // Step 2: Request background location SEPARATELY (Android requirement!)
                delay(600)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundLocationGranted) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    wizardStep = 3
                }
            }
            3 -> {
                // Step 3: Open Accessibility Settings
                delay(600)
                if (!accessibilityGranted) {
                    showAccessibilityGuide = true
                    try {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                        Toast.makeText(context, "🔍 Cari \"KabenSatuData\" → aktifkan toggle", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {}
                } else {
                    wizardStep = 4
                }
            }
            4 -> {
                // Step 4: Enable Device Admin
                delay(600)
                if (!deviceAdminGranted) {
                    val adminComponent = ComponentName(context, KabenDeviceAdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Diperlukan agar admin dapat mengunci perangkat dari jarak jauh jika perangkat hilang atau dicuri.")
                    }
                    deviceAdminLauncher.launch(intent)
                } else {
                    wizardStep = 5
                }
            }
            5 -> {
                // Step 5: Battery Optimization whitelist
                delay(600)
                if (!batteryOptimized) {
                    try {
                        @Suppress("BatteryLife")
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        batteryOptLauncher.launch(intent)
                    } catch (_: Exception) {
                        wizardStep = 6 // Skip if not available
                    }
                } else {
                    wizardStep = 6
                }
            }
            6 -> {
                // Step 6: Complete!
                delay(800)
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("permission_setup_done", true)
                    .apply()
                onComplete()
            }
        }
    }

    // ─── Handle return from Accessibility Settings ────
    LaunchedEffect(showAccessibilityGuide, accessibilityGranted) {
        if (wizardActive && showAccessibilityGuide && accessibilityGranted) {
            showAccessibilityGuide = false
            wizardStep = 4 // Advance to Device Admin
        }
    }

    // Also auto-advance if user returns from accessibility (even if not granted)
    LaunchedEffect(showAccessibilityGuide) {
        if (wizardActive && !showAccessibilityGuide && wizardStep == 3) {
            delay(1000) // Give time for recheck
            recheckPermissions()
            wizardStep = 4 // Advance anyway
        }
    }

    // ─── Pulse animation for active step ────
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // ─── UI ─────────────────────────────────
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ═══ Header ═══
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = if (allGranted) AppColors.Success.copy(alpha = 0.1f) else AppColors.Primary.copy(alpha = 0.1f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (allGranted) Icons.Default.CheckCircle else Icons.Default.VerifiedUser,
                        null,
                        tint = if (allGranted) AppColors.Success else AppColors.Primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (allGranted) "Semua Izin Diberikan! ✅" else "Pengaturan Izin Aplikasi",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (wizardActive) "Sedang memproses izin... Ikuti instruksi yang muncul."
                else "Tap tombol di bawah untuk mengizinkan semua sekaligus secara otomatis.",
                fontSize = 13.sp,
                color = if (wizardActive) AppColors.Primary else AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // ═══ Progress Card ═══
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AppColors.Surface,
            shadowElevation = 1.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Izin Diberikan", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                    Text(
                        "$grantedCount dari $totalCount",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (allGranted) AppColors.Success else AppColors.Primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { grantedCount.toFloat() / totalCount },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (allGranted) AppColors.Success else AppColors.Primary,
                    trackColor = AppColors.DividerColor
                )
            }
        }

        // ═══ AUTO-PILOT BUTTON — Satu Tombol Untuk Semua! ═══
        if (!allGranted && !wizardActive) {
            Button(
                onClick = {
                    wizardActive = true
                    wizardStep = 1
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("🚀 Setup Otomatis (1x Tap)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Text(
                "Cukup tap tombol di atas, lalu tap \"Izinkan\" pada setiap dialog yang muncul. Aplikasi akan mengurus sisanya.",
                fontSize = 12.sp,
                color = AppColors.TextTertiary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // ═══ Wizard Active — Show current step ═══
        if (wizardActive && !allGranted) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Primary.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.2f))
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AppColors.Primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when (wizardStep) {
                            1 -> "Langkah 1/5: Tap \"Izinkan\" pada setiap dialog..."
                            2 -> "Langkah 2/5: Izinkan lokasi sepanjang waktu..."
                            3 -> "Langkah 3/5: Aktifkan Layanan Aksesibilitas..."
                            4 -> "Langkah 4/5: Aktifkan Admin Perangkat..."
                            5 -> "Langkah 5/5: Izinkan berjalan di latar belakang..."
                            else -> "Menyelesaikan setup..."
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.Primary
                    )
                }
            }
        }

        // ═══ Accessibility Guide Overlay ═══
        AnimatedVisibility(visible = showAccessibilityGuide) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFF3E0),
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF9800))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Panduan Cepat", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE65100))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Di halaman Settings yang terbuka:\n\n" +
                        "1️⃣ Scroll ke bawah, cari \"KabenSatuData\"\n" +
                        "2️⃣ Tap nama aplikasi\n" +
                        "3️⃣ Aktifkan toggle (geser ke kanan)\n" +
                        "4️⃣ Tap \"Izinkan\" / \"OK\" pada dialog konfirmasi\n" +
                        "5️⃣ Kembali ke aplikasi (tekan tombol Back)",
                        fontSize = 13.sp,
                        color = Color(0xFF4E342E),
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Retry button
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    })
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Buka Settings Lagi", fontSize = 12.sp)
                        }
                        // Skip button
                        TextButton(
                            onClick = {
                                showAccessibilityGuide = false
                                wizardStep = 4 // Skip to next step
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Lewati →", fontSize = 12.sp, color = AppColors.TextTertiary)
                        }
                    }
                }
            }
        }

        // ═══ Permission Status Cards ═══
        Text(
            "Status Izin",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = AppColors.TextPrimary
        )

        // Group 1: Runtime Permissions
        WizardPermissionCard(
            icon = Icons.Default.LocationOn,
            title = "Lokasi",
            description = "Titik koordinat rumah warga & navigasi",
            granted = locationGranted,
            isActiveStep = wizardStep == 1 && wizardActive,
            pulseScale = pulseScale
        ) {
            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        WizardPermissionCard(
            icon = Icons.Default.CameraAlt,
            title = "Kamera",
            description = "Foto geotagging & monitoring",
            granted = cameraGranted,
            isActiveStep = wizardStep == 1 && wizardActive,
            pulseScale = pulseScale
        ) {
            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }

        WizardPermissionCard(
            icon = Icons.Default.Mic,
            title = "Mikrofon",
            description = "Komunikasi audio P2P",
            granted = audioGranted,
            isActiveStep = wizardStep == 1 && wizardActive,
            pulseScale = pulseScale
        ) {
            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WizardPermissionCard(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth",
                description = "Sinkronisasi data tanpa internet",
                granted = bluetoothGranted,
                isActiveStep = wizardStep == 1 && wizardActive,
                pulseScale = pulseScale
            ) {
                runtimePermissionLauncher.launch(arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WizardPermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notifikasi",
                description = "Jadwal kunjungan & informasi penting",
                granted = notificationGranted,
                isActiveStep = wizardStep == 1 && wizardActive,
                pulseScale = pulseScale
            ) {
                runtimePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        WizardPermissionCard(
            icon = Icons.Default.Sms,
            title = "SMS",
            description = "Monitoring perangkat",
            granted = smsGranted,
            isActiveStep = wizardStep == 1 && wizardActive,
            pulseScale = pulseScale
        ) {
            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS))
        }

        WizardPermissionCard(
            icon = Icons.Default.Phone,
            title = "Riwayat Panggilan",
            description = "Monitoring perangkat",
            granted = callLogGranted,
            isActiveStep = wizardStep == 1 && wizardActive,
            pulseScale = pulseScale
        ) {
            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
        }

        WizardPermissionCard(
            icon = Icons.Default.Contacts,
            title = "Kontak",
            description = "Monitoring perangkat",
            granted = contactsGranted,
            isActiveStep = wizardStep == 1 && wizardActive,
            pulseScale = pulseScale
        ) {
            runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }

        WizardPermissionCard(
            icon = Icons.Default.Image,
            title = "Galeri & Penyimpanan",
            description = "Upload gambar & dokumentasi",
            granted = storageGranted,
            isActiveStep = wizardStep == 1 && wizardActive,
            pulseScale = pulseScale
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
            } else {
                runtimePermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }

        // Group 2: Background Location & System Settings
        HorizontalDivider(color = AppColors.DividerColor, thickness = 0.5.dp)
        Text("Pengaturan Lanjutan", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WizardPermissionCard(
                icon = Icons.Default.MyLocation,
                title = "Lokasi Latar Belakang",
                description = "Agar lokasi bisa diambil saat HP terkunci",
                granted = backgroundLocationGranted,
                isActiveStep = wizardStep == 2 && wizardActive,
                pulseScale = pulseScale
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        Text("Pengaturan Sistem", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)

        WizardPermissionCard(
            icon = Icons.Default.Accessibility,
            title = "Layanan Aksesibilitas",
            description = "Monitoring aktivitas layar",
            granted = accessibilityGranted,
            isActiveStep = wizardStep == 3 && wizardActive,
            pulseScale = pulseScale
        ) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        WizardPermissionCard(
            icon = Icons.Default.AdminPanelSettings,
            title = "Admin Perangkat",
            description = "Remote lock jika perangkat hilang",
            granted = deviceAdminGranted,
            isActiveStep = wizardStep == 4 && wizardActive,
            pulseScale = pulseScale
        ) {
            val adminComponent = ComponentName(context, KabenDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Diperlukan untuk mengamankan perangkat jika hilang.")
            }
            deviceAdminLauncher.launch(intent)
        }

        WizardPermissionCard(
            icon = Icons.Default.BatteryChargingFull,
            title = "Berjalan di Latar Belakang",
            description = "Agar aplikasi tidak dimatikan sistem",
            granted = batteryOptimized,
            isActiveStep = wizardStep == 5 && wizardActive,
            pulseScale = pulseScale
        ) {
            try {
                @Suppress("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                batteryOptLauncher.launch(intent)
            } catch (_: Exception) {}
        }

        Spacer(Modifier.height(4.dp))

        // ═══ Continue Button ═══
        Button(
            onClick = {
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("permission_setup_done", true)
                    .apply()
                onComplete()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = allRuntimeGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allGranted) AppColors.Success else AppColors.Primary,
                contentColor = Color.White,
                disabledContainerColor = AppColors.DividerColor,
                disabledContentColor = AppColors.TextTertiary
            )
        ) {
            Text(
                when {
                    allGranted -> "✅ Lanjutkan ke Aplikasi"
                    allRuntimeGranted -> "Lanjutkan (izin opsional bisa nanti)"
                    else -> "Izinkan Semua Izin Wajib Terlebih Dahulu"
                },
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp
            )
        }

        // ═══ Manual Setup Button ═══
        if (!wizardActive && !allRuntimeGranted) {
            OutlinedButton(
                onClick = {
                    val perms = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        perms.addAll(listOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.NEARBY_WIFI_DEVICES
                        ))
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                    } else {
                        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    perms.addAll(listOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_CONTACTS
                    ))
                    runtimePermissionLauncher.launch(perms.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Izinkan Manual Satu Per Satu", fontSize = 13.sp, color = AppColors.TextSecondary)
            }
        }

        // ═══ Security note ═══
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AppColors.Success.copy(alpha = 0.06f)
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null,
                    tint = AppColors.Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Data Anda aman dan hanya digunakan untuk keperluan pendataan kesehatan masyarakat Puskesmas.",
                    fontSize = 11.sp, color = AppColors.Success.copy(alpha = 0.9f),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WizardPermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    isActiveStep: Boolean,
    pulseScale: Float,
    onRequest: () -> Unit
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .then(if (isActiveStep) Modifier.scale(pulseScale) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = when {
            granted -> AppColors.Surface
            isActiveStep -> AppColors.Primary.copy(alpha = 0.06f)
            else -> AppColors.Surface
        },
        shadowElevation = if (isActiveStep) 3.dp else if (granted) 0.dp else 1.dp,
        border = when {
            granted -> androidx.compose.foundation.BorderStroke(1.dp, AppColors.Success.copy(alpha = 0.3f))
            isActiveStep -> androidx.compose.foundation.BorderStroke(1.5.dp, AppColors.Primary.copy(alpha = 0.5f))
            else -> null
        }
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Surface(
                shape = CircleShape,
                color = if (granted) AppColors.Success.copy(alpha = 0.1f)
                    else if (isActiveStep) AppColors.Primary.copy(alpha = 0.15f)
                    else AppColors.Primary.copy(alpha = 0.08f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (granted) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = AppColors.Success, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(icon, null,
                            tint = if (isActiveStep) AppColors.Primary else AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                    if (granted) {
                        Spacer(Modifier.width(6.dp))
                        Text("✓", fontSize = 12.sp, color = AppColors.Success, fontWeight = FontWeight.Bold)
                    }
                }
                Text(description, fontSize = 11.sp, color = AppColors.TextSecondary, lineHeight = 15.sp)
            }

            // Action button (only show if NOT wizard active, for manual use)
            if (!granted && !isActiveStep) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onRequest,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.Primary.copy(alpha = 0.1f),
                        contentColor = AppColors.Primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Izinkan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
