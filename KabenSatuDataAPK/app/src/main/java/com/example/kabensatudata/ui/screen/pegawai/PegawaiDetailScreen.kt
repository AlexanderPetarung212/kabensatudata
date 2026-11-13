package com.example.kabensatudata.ui.screen.pegawai

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.kabensatudata.data.remote.dto.DokumenDto
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PegawaiDetailScreen(
    pegawaiId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    isAdmin: Boolean = false,
    isOwner: Boolean = false,
    viewModel: PegawaiViewModel = hiltViewModel()
) {
    val state by viewModel.detailState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var previewDoc by remember { mutableStateOf<DokumenDto?>(null) }

    LaunchedEffect(pegawaiId) { viewModel.loadDetail(pegawaiId) }

    // Snackbar for action messages
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    // Document Preview
    previewDoc?.let { doc ->
        DocumentPreviewDialog(doc = doc, onDismiss = { previewDoc = null })
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Detail Pegawai",
                subtitle = state.pegawai?.nama ?: "Data Kepegawaian",
                onBack = onBack,
                actions = {
                    if (isAdmin || isOwner) {
                        IconButton(onClick = { onEdit(pegawaiId) }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                        }
                    }
                    if (isAdmin) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Hapus", tint = Color.White.copy(0.85f))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppColors.Background
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp), color = AppColors.Accent)
                }
            }
            state.pegawai == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Data tidak ditemukan", color = AppColors.TextSecondary)
                }
            }
            else -> {
                val p = state.pegawai!!
                Column(
                    Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    // Offline indicator
                    if (state.isOffline) {
                        Surface(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WifiOff, null, Modifier.size(14.dp), tint = Color(0xFFD97706))
                                Spacer(Modifier.width(6.dp))
                                Text("Offline — menampilkan data dari cache", fontSize = 11.sp, color = Color(0xFFD97706))
                            }
                        }
                    }

                    // Profile Header — Glossy
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        AppColors.Accent.copy(0.85f),
                                        AppColors.PrimaryDark
                                    )
                                )
                            )
                    ) {
                        // Specular overlay
                        Box(
                            Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.White.copy(0f), Color.White.copy(0.35f), Color.White.copy(0f))
                                    )
                                )
                        )
                        Column(
                            Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(80.dp)
                                    .shadow(8.dp, CircleShape, spotColor = Color.White.copy(0.35f))
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (p.fotoUrl != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(p.fotoUrl).crossfade(true).build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        Modifier.fillMaxSize().background(
                                            Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            p.nama.firstOrNull()?.uppercase() ?: "?",
                                            fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(p.nama, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            p.jabatan?.let { Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f)) }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Info Fields
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(14.dp), color = AppColors.Surface) {
                        Column(Modifier.padding(16.dp)) {
                            InfoRow("NIP", p.nip)
                            InfoRow("NIK", p.nik)
                            InfoRow("Jenis Kelamin", p.jenisKelamin)
                            InfoRow("Unit", p.unit)
                            InfoRow("Pangkat/Gol", p.pangkatGol)
                            InfoRow("Pendidikan", p.pendidikanTerakhir)
                            InfoRow("Profesi", p.profesi)
                            InfoRow("Tanggal Lahir", p.tanggalLahir)
                            InfoRow("Alamat", p.alamat)
                            InfoRow("No. HP", p.noHp)
                            InfoRow("Email", p.email)
                        }
                    }

                    // Documents
                    Spacer(Modifier.height(16.dp))
                    Text("Dokumen", Modifier.padding(horizontal = 20.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))

                    if (p.dokumen.isNullOrEmpty()) {
                        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), color = AppColors.Surface) {
                            Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("Belum ada dokumen", fontSize = 13.sp, color = AppColors.TextTertiary)
                            }
                        }
                    } else {
                        p.dokumen.forEach { doc ->
                            DocumentCard(
                                doc = doc,
                                onPreview = { previewDoc = doc },
                                onDownload = {
                                    val url = doc.gdriveUrl ?: doc.downloadUrl
                                    url?.let { u ->
                                        downloadFileWithAuth(context, u, doc.judul, doc.gdriveUrl != null)
                                    }
                                },
                                onDelete = if (isAdmin || isOwner) {{ viewModel.deleteDocument(pegawaiId, doc.id) }} else null
                            )
                        }
                    }

                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    // Delete Confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Pegawai?", fontWeight = FontWeight.Bold) },
            text = { Text("Data pegawai dan semua dokumen terkait akan dihapus permanen.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePegawai(pegawaiId) { onBack() }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                    Text("Hapus")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.width(120.dp), fontSize = 12.sp, color = AppColors.TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DocumentCard(doc: DokumenDto, onPreview: () -> Unit, onDownload: () -> Unit, onDelete: (() -> Unit)?) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor   = Color(0xFF3B82F6).copy(0.20f),
                ambientColor = Color(0xFF3B82F6).copy(0.08f)
            )
            .clickable(onClick = onPreview),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.GlossBorder),
        shadowElevation = 0.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.judul, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${doc.jenis}${if (!doc.issuedAt.isNullOrBlank()) " • ${doc.issuedAt}" else ""}", fontSize = 11.sp, color = AppColors.TextTertiary)
                Text("Tap untuk preview", fontSize = 9.sp, color = AppColors.Accent)
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, "Download", Modifier.size(20.dp), tint = Color(0xFF3B82F6))
            }
            onDelete?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Default.DeleteOutline, "Hapus", Modifier.size(18.dp), tint = Color(0xFFEF4444))
                }
            }
        }
    }
}

