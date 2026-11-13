package com.example.kabensatudata.ui.screen.monitor

import androidx.compose.foundation.background
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
import com.example.kabensatudata.data.remote.dto.DeviceAppUsage
import com.example.kabensatudata.data.remote.dto.DeviceReportDetail
import com.example.kabensatudata.data.remote.dto.DeviceReportEvent
import com.example.kabensatudata.ui.theme.AppColors

/**
 * Detailed view for a single device — shows timeline of events
 * and app usage breakdown for the selected date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    detail: DeviceReportDetail?,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(detail?.userName ?: "Detail Perangkat", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        detail?.deviceName?.let {
                            Text(it, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (detail == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Data tidak tersedia", color = Color.Gray)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Daily Summary
            detail.dailySummary?.let { summary ->
                item {
                    DailySummaryCard(summary)
                }
            }

            // App Usage Chart
            if (detail.appUsage.isNotEmpty()) {
                item {
                    Text("Penggunaan Aplikasi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(detail.appUsage.sortedByDescending { it.durationMinutes }) { usage ->
                    AppUsageRow(usage, totalMinutes = detail.appUsage.sumOf { it.durationMinutes })
                }
            }

            // Timeline
            if (detail.events.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Timeline Aktivitas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(detail.events) { event ->
                    TimelineItem(event)
                }
            }
        }
    }
}

@Composable
private fun DailySummaryCard(
    summary: com.example.kabensatudata.data.remote.dto.DeviceDailySummary
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Ringkasan Hari Ini", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Layar Aktif", "${summary.totalScreenOnMinutes}m", Color(0xFF4CAF50))
                StatItem("Dalam App", "${summary.totalAppMinutes}m", Color(0xFF2196F3))
                StatItem("Halaman", "${summary.screensVisited.size}", Color(0xFF9C27B0))
            }

            if (summary.firstActive != null || summary.lastActive != null) {
                Spacer(Modifier.height(8.dp))
                Divider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    summary.firstActive?.let {
                        Text("Mulai: $it", fontSize = 12.sp, color = Color.Gray)
                    }
                    summary.lastActive?.let {
                        Text("Terakhir: $it", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun AppUsageRow(usage: DeviceAppUsage, totalMinutes: Int) {
    val fraction = if (totalMinutes > 0) usage.durationMinutes.toFloat() / totalMinutes else 0f
    val barColor = if (usage.isOwnApp) Color(0xFF4CAF50) else Color(0xFF2196F3)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (usage.isOwnApp) Icons.Default.Verified else Icons.Default.Apps,
                        contentDescription = null,
                        tint = barColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(usage.appName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Text(
                    "${usage.durationMinutes}m",
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Gray.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
    }
}

@Composable
private fun TimelineItem(event: DeviceReportEvent) {
    val (icon, color, label) = when (event.eventType) {
        "login" -> Triple(Icons.Default.Login, Color(0xFF4CAF50), "Login")
        "logout" -> Triple(Icons.Default.Logout, Color(0xFFE53935), "Logout")
        "screen_on" -> Triple(Icons.Default.PhoneAndroid, Color(0xFF2196F3), "Layar Nyala")
        "screen_off" -> Triple(Icons.Default.PhonelinkOff, Color(0xFF757575), "Layar Mati")
        "user_unlock" -> Triple(Icons.Default.LockOpen, Color(0xFF00BCD4), "Unlock")
        "navigate" -> Triple(Icons.Default.Navigation, Color(0xFF9C27B0), "Navigasi")
        "screen_time" -> Triple(Icons.Default.Timer, Color(0xFFFF9800), "Waktu Layar")
        "app_usage" -> Triple(Icons.Default.Apps, Color(0xFF607D8B), "App Lain")
        "tracker_start" -> Triple(Icons.Default.PlayArrow, Color(0xFF4CAF50), "Tracker Start")
        else -> Triple(Icons.Default.Info, Color.Gray, event.eventType)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.width(8.dp))

        // Event info
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            if (event.eventData.isNotEmpty() && event.eventData != "{}") {
                Text(
                    event.eventData,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
        }

        // Timestamp
        Text(
            event.timestamp,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}
