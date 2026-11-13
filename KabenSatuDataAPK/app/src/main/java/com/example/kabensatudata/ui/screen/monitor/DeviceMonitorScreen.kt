package com.example.kabensatudata.ui.screen.monitor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.remote.dto.DeviceReportSummary
import com.example.kabensatudata.ui.theme.AppColors

/**
 * Admin-only screen showing all monitored devices and their status.
 * Only accessible by super_admin / admin roles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceMonitorScreen(
    onBack: () -> Unit,
    onDeviceClick: (String) -> Unit,
    viewModel: DeviceMonitorViewModel = hiltViewModel()
) {
    val state = viewModel.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitor Perangkat", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDevices() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Background)
        ) {
            // Summary cards
            if (state.devices.isNotEmpty()) {
                SummaryRow(state.devices)
            }

            // Device list
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(state.error, color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadDevices() }) {
                            Text("Coba Lagi")
                        }
                    }
                }
            } else if (state.devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhonelinkOff, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada data perangkat", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.devices) { device ->
                        DeviceCard(device = device, onClick = { onDeviceClick(device.deviceId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(devices: List<DeviceReportSummary>) {
    val online = devices.count { it.status == "online" }
    val idle = devices.count { it.status == "idle" }
    val offline = devices.count { it.status == "offline" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryChip("Online", online, Color(0xFF4CAF50), Modifier.weight(1f))
        SummaryChip("Idle", idle, Color(0xFFFF9800), Modifier.weight(1f))
        SummaryChip("Offline", offline, Color(0xFFE53935), Modifier.weight(1f))
    }
}

@Composable
private fun SummaryChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceReportSummary, onClick: () -> Unit) {
    val statusColor = when (device.status) {
        "online" -> Color(0xFF4CAF50)
        "idle" -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.userName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    device.deviceName,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                device.lastActive?.let {
                    Text(
                        "Terakhir: $it",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            // Stats
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${device.todayActiveMinutes}m",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = statusColor
                )
                Text(
                    "aktif hari ini",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Text(
                    "${device.totalEventsToday} event",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}
