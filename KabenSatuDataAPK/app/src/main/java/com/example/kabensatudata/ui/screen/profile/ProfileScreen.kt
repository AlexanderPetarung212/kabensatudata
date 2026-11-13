package com.example.kabensatudata.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.ui.navigation.AuthEntryPoint
import com.example.kabensatudata.ui.theme.AppColors
import dagger.hilt.android.EntryPointAccessors
import android.widget.Toast
import com.example.kabensatudata.nearby.MonitorVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSync: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember {
        EntryPointAccessors.fromApplication(context, AuthEntryPoint::class.java)
            .authTokenManager()
    }

    val userName = tokenManager.getUserName()
    val userEmail = tokenManager.getUserEmail()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Keluar", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin keluar dari aplikasi?", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    tokenManager.clear()
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Ya, Keluar", color = AppColors.Error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // About Dialog with secret triple-tap
    if (showAboutDialog) {
        var tapCount by remember { mutableIntStateOf(0) }
        var lastTapTime by remember { mutableLongStateOf(0L) }

        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    null,
                    tint = AppColors.Accent,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable {
                            val now = System.currentTimeMillis()
                            // Reset counter if more than 1.5s since last tap
                            if (now - lastTapTime > 1500) tapCount = 0
                            lastTapTime = now
                            tapCount++
                            if (tapCount >= 3) {
                                tapCount = 0
                                val nowVisible = MonitorVisibility.toggle()
                                Toast.makeText(
                                    context,
                                    if (nowVisible) "Mode monitoring diaktifkan"
                                    else "Mode monitoring dinonaktifkan",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                )
            },
            title = { Text("KabenSatuData", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary) },
            text = {
                Column {
                    Text("Versi 2.0", fontSize = 14.sp, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Aplikasi data terpadu Puskesmas untuk pendataan wilayah, kesehatan, dan anggaran BOK.",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Tutup", color = AppColors.Accent, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = AppColors.Surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Gradient Header dengan Specular Gloss ─────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(AppColors.HeaderGradientStart, AppColors.HeaderGradientEnd)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Specular shine di atas header
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(0f),
                                Color.White.copy(0.45f),
                                Color.White.copy(0f)
                            )
                        )
                    )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Avatar dengan glossy glow
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            spotColor   = Color.White.copy(0.35f),
                            ambientColor = AppColors.Accent.copy(0.20f)
                        )
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    userName.ifEmpty { "User" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    userEmail.ifEmpty { "-" },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Menu Items ───────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor   = AppColors.Accent.copy(0.20f),
                    ambientColor = AppColors.Accent.copy(0.08f)
                ),
            shape = RoundedCornerShape(16.dp),
            color = AppColors.Surface,
            border = BorderStroke(1.dp, AppColors.GlossBorder),
            shadowElevation = 0.dp
        ) {
            Column {
                ProfileMenuItem(
                    icon = Icons.Default.CloudSync,
                    iconColor = AppColors.ServiceBlue,
                    title = "Sinkronisasi Data",
                    subtitle = "Sinkronkan data ke server",
                    onClick = onNavigateToSync
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = AppColors.DividerColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                ProfileMenuItem(
                    icon = Icons.Default.Info,
                    iconColor = AppColors.ServiceTeal,
                    title = "Tentang Aplikasi",
                    subtitle = "Versi 2.0",
                    onClick = { showAboutDialog = true }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = AppColors.DividerColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                ProfileMenuItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconColor = AppColors.Error,
                    title = "Keluar",
                    subtitle = "Logout dari akun Anda",
                    onClick = { showLogoutDialog = true }
                )
            }
        }

        Spacer(Modifier.height(100.dp)) // Bottom nav padding
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = AppColors.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = AppColors.TextTertiary)
        }
        Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
    }
}
