package com.example.kabensatudata.ui.screen.notification

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.NotificationDao
import com.example.kabensatudata.data.local.entity.NotificationEntity
import com.example.kabensatudata.ui.components.EmptyState
import com.example.kabensatudata.ui.components.StaggeredItem
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── ViewModel ──────────────────────────────────────────

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationDao: NotificationDao
) : ViewModel() {

    val notifications = notificationDao.getAll()
    val unreadCount = notificationDao.getUnreadCount()

    fun markAsRead(id: Long) {
        viewModelScope.launch { notificationDao.markAsRead(id) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { notificationDao.markAllAsRead() }
    }

    fun delete(notification: NotificationEntity) {
        viewModelScope.launch { notificationDao.delete(notification) }
    }
}

// ─── Screen ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onNavigateToCalendar: (Long, Int) -> Unit = { _, _ -> },
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState(initial = emptyList())
    val unreadCount by viewModel.unreadCount.collectAsState(initial = 0)

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Notifikasi",
                subtitle = if (unreadCount > 0) "$unreadCount belum dibaca" else "Semua sudah dibaca",
                onBack = onBack,
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = { viewModel.markAllAsRead() }) {
                            Text(
                                "Baca Semua",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        if (notifications.isEmpty()) {
            EmptyState(
                icon = Icons.Default.NotificationsNone,
                title = "Belum ada notifikasi",
                subtitle = "Notifikasi dari admin akan muncul di sini",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(notifications, key = { _, it -> it.id }) { index, notification ->
                    StaggeredItem(index = index) {
                        NotificationItem(
                            notification = notification,
                            onClick = {
                                if (!notification.isRead) viewModel.markAsRead(notification.id)
                                if (notification.type == "kegiatan" && notification.targetPoaId != null && notification.targetMonth != null) {
                                    onNavigateToCalendar(notification.targetPoaId, notification.targetMonth)
                                }
                            },
                            onDelete = { viewModel.delete(notification) }
                        )
                    }
                }
            }
        }
    }
}

// ─── Notification Item ──────────────────────────────────

@Composable
private fun NotificationItem(
    notification: NotificationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, color) = notificationTypeStyle(notification.type)
    val bgColor = if (notification.isRead) AppColors.Surface else AppColors.Accent.copy(alpha = 0.06f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        shadowElevation = if (notification.isRead) 1.dp else 3.dp
    ) {
        Column {
            // Gradient accent strip
            if (!notification.isRead) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(color, color.copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                )
            }
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Circular gradient icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.05f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            notification.title,
                            fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // Unread indicator dot
                        if (!notification.isRead) {
                            Box(
                                Modifier
                                    .padding(start = 6.dp, top = 4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.Accent)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        notification.message,
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatRelativeTime(notification.createdAt),
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary
                        )
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "Hapus",
                                tint = AppColors.TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────

private fun notificationTypeStyle(type: String): Pair<ImageVector, Color> {
    return when (type) {
        "warning"  -> Icons.Default.Warning to Color(0xFFF59E0B)
        "update"   -> Icons.Default.SystemUpdate to Color(0xFF10B981)
        "critical" -> Icons.Default.Error to Color(0xFFEF4444)
        "kegiatan" -> Icons.Default.CalendarMonth to Color(0xFF8B5CF6)
        else       -> Icons.Default.Info to Color(0xFF3B82F6)
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000

    return when {
        minutes < 1 -> "Baru saja"
        minutes < 60 -> "$minutes menit lalu"
        hours < 24 -> "$hours jam lalu"
        days < 7 -> "$days hari lalu"
        else -> {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            sdf.format(Date(timestamp))
        }
    }
}
