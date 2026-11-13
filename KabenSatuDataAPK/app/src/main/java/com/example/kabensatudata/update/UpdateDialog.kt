package com.example.kabensatudata.update

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kabensatudata.ui.theme.AppColors

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.updateAvailable || state.dismissed) return
    val info = state.versionInfo ?: return

    Dialog(
        onDismissRequest = { if (!info.forceUpdate) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !info.forceUpdate,
            dismissOnClickOutside = !info.forceUpdate
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AppColors.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🚀", fontSize = 28.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    "Pembaruan Tersedia!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Versi ${info.versionName}",
                    fontSize = 14.sp,
                    color = AppColors.Accent,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(12.dp))

                // Changelog
                if (info.changelog.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Yang baru:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                info.changelog,
                                fontSize = 12.sp,
                                color = AppColors.TextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Download / Install states
                when {
                    state.downloadComplete -> {
                        // Install button
                        Text(
                            "✅ Unduhan selesai!",
                            fontSize = 13.sp,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16A34A)
                            )
                        ) {
                            Text("📦 Install Sekarang", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                    state.isDownloading -> {
                        // Progress
                        Text(
                            "Mengunduh... ${state.downloadProgress}%",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { state.downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = AppColors.Accent,
                            trackColor = Color(0xFFE2E8F0)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Mohon tunggu...",
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary
                        )
                    }
                    else -> {
                        // Download button
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Text("⬇️ Update Sekarang", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        // Dismiss (skip) button
                        if (!info.forceUpdate) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onDismiss) {
                                Text("Nanti saja", fontSize = 13.sp, color = AppColors.TextTertiary)
                            }
                        }
                    }
                }

                // Error message
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error,
                        fontSize = 11.sp,
                        color = Color(0xFFDC2626),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
