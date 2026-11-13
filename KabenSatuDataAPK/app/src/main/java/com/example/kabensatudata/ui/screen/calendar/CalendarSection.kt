package com.example.kabensatudata.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.remote.dto.CalendarActivity
import com.example.kabensatudata.data.remote.dto.CalendarMonth
import com.example.kabensatudata.ui.theme.AppColors

// ─── Colors ────────────────────────────────────────────────────────────────────
private val TahapGreen = Color(0xFF22C55E)
private val TahapYellow = Color(0xFFEAB308)
private val TahapRed = Color(0xFFEF4444)

private fun tahapColor(tahap: Int?): Color = when (tahap) {
    1 -> TahapGreen
    2 -> TahapYellow
    3 -> TahapRed
    else -> Color.Gray
}

private fun tahapLabel(tahap: Int?): String = when (tahap) {
    1 -> "Tahap I"
    2 -> "Tahap II"
    3 -> "Tahap III"
    else -> ""
}

// ─── HomeScreen Preview Section ─────────────────────────────────────────────

@Composable
fun CalendarSection(
    activities: List<CalendarActivity>,
    currentMonth: Int,
    isLoading: Boolean,
    error: String?,
    onNavigateToCalendar: () -> Unit,
    onActivityClick: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Surface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth, null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Kalender Kegiatan BOK",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = AppColors.TextPrimary
                    )
                }
                TextButton(
                    onClick = onNavigateToCalendar,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Lihat Semua", fontSize = 12.sp, color = AppColors.Accent)
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, null,
                        modifier = Modifier.size(14.dp),
                        tint = AppColors.Accent
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (isLoading) {
                // Shimmer
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Memuat kalender...",
                        fontSize = 12.sp,
                        color = AppColors.TextTertiary
                    )
                    repeat(2) {
                        Box(
                            Modifier.fillMaxWidth().height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.Secondary.copy(alpha = 0.15f))
                        )
                    }
                }
            } else if (error != null) {
                Text(error, fontSize = 12.sp, color = AppColors.Error)
            } else {
                val monthNames = listOf(
                    "", "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
                )
                val monthName = if (currentMonth in 1..12) monthNames[currentMonth] else ""

                // Subtitle
                Text(
                    "$monthName ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} • ${activities.size} kegiatan aktif",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary
                )

                if (activities.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tidak ada kegiatan aktif bulan ini",
                        fontSize = 13.sp,
                        color = AppColors.TextTertiary
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    // Show max 3 activities
                    activities.take(3).forEach { activity ->
                        val monthData = activity.months[currentMonth.toString()]
                        CalendarPreviewItem(
                            kegiatan = activity.kegiatan,
                            monthData = monthData,
                            onClick = { onActivityClick(activity.id, currentMonth) }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    // Overflow indicator
                    if (activities.size > 3) {
                        Text(
                            "+${activities.size - 3} kegiatan lainnya...",
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary,
                            modifier = Modifier.clickable { onNavigateToCalendar() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPreviewItem(
    kegiatan: String,
    monthData: CalendarMonth?,
    onClick: () -> Unit
) {
    val tahap = monthData?.tahap
    val color = tahapColor(tahap)

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tahap dot
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(color)
            )
            Spacer(Modifier.width(10.dp))

            // Activity name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    kegiatan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tahapLabel(tahap),
                        fontSize = 11.sp,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (monthData?.is_mine == true) {
                        Spacer(Modifier.width(4.dp))
                        Text("👤", fontSize = 10.sp)
                    }
                }
            }

            // Claimed status
            if (monthData?.claimed == true) {
                Text("✅", fontSize = 14.sp)
            } else {
                Text("⏳", fontSize = 14.sp)
            }
        }
    }
}
