package com.example.kabensatudata.nearby

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

/**
 * Composable helper that requests all permissions needed for Nearby Connections.
 * Checks SDK version to request appropriate Bluetooth/WiFi permissions.
 */
@Composable
fun RequestNearbyPermissions(
    onGranted: () -> Unit,
    onDenied: () -> Unit = {}
) {
    val permissions = remember { buildNearbyPermissions() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) onGranted() else onDenied()
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }
}

fun buildNearbyPermissions(): List<String> {
    val perms = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+
        perms.addAll(listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ))
    } else {
        // Android ≤11
        perms.addAll(listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+
        perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    perms.addAll(listOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    ))

    return perms
}
