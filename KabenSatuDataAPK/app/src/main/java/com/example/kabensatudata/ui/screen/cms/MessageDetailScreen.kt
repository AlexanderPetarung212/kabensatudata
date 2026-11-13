package com.example.kabensatudata.ui.screen.cms

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.remote.dto.MessageDto
import com.example.kabensatudata.ui.theme.AppColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * Message Detail Screen — shows full message content with read status and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    messageId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bokApi = remember {
        try {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.kabensatudata.nearby.RemoteControlHandler.RemoteEntryPoint::class.java
            )
            ep.bokApiService()
        } catch (_: Exception) { null }
    }

    var message by remember { mutableStateOf<MessageDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messageId) {
        try {
            val resp = bokApi?.getCmsMessage(messageId)
            if (resp?.isSuccessful == true) {
                message = resp.body()?.data
            }
        } catch (_: Exception) {}
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Pesan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Hapus", tint = Color(0xFFEF4444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        } else if (message == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Pesan tidak ditemukan", color = AppColors.TextSecondary)
            }
        } else {
            val msg = message!!
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sender info card
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    color = AppColors.Primary.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.1f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(AppColors.Primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                msg.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Primary
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(msg.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                            msg.phone?.let {
                                if (it.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("📞 $it", fontSize = 13.sp, color = AppColors.TextSecondary)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "📅 ${msg.createdAt?.take(10) ?: "-"}",
                                fontSize = 12.sp, color = AppColors.TextTertiary
                            )
                        }
                    }
                }

                // Subject
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = AppColors.Surface, border = BorderStroke(0.5.dp, AppColors.DividerColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Subjek", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextTertiary)
                        Spacer(Modifier.height(4.dp))
                        Text(msg.subject, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    }
                }

                // Message body
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = AppColors.Surface, border = BorderStroke(0.5.dp, AppColors.DividerColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Pesan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text(msg.message, fontSize = 15.sp, color = AppColors.TextPrimary, lineHeight = 22.sp)
                    }
                }

                // Read status
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.06f)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DoneAll, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sudah dibaca", fontSize = 12.sp, color = Color(0xFF10B981))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Pesan?") },
            text = { Text("Pesan dari \"${message?.name ?: ""}\" akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        try {
                            bokApi?.deleteCmsMessage(messageId)
                            Toast.makeText(context, "Pesan dihapus", Toast.LENGTH_SHORT).show()
                            onBack()
                        } catch (_: Exception) {}
                    }
                }) {
                    Text("Hapus", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
            }
        )
    }
}
