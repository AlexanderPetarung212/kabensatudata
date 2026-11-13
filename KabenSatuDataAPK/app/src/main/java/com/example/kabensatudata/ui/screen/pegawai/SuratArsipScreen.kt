package com.example.kabensatudata.ui.screen.pegawai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.remote.dto.DokumenDto
import com.example.kabensatudata.data.remote.dto.SuratArsipDto
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuratArsipScreen(
    onBack: () -> Unit,
    onCreateSuratTugas: () -> Unit,
    isAdmin: Boolean = false,
    viewModel: PegawaiViewModel = hiltViewModel()
) {
    val state by viewModel.arsipState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showUploadDialog by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<SuratArsipDto?>(null) }
    var editItem by remember { mutableStateOf<SuratArsipDto?>(null) }
    var deleteItem by remember { mutableStateOf<SuratArsipDto?>(null) }

    LaunchedEffect(Unit) { viewModel.loadSuratArsip() }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearArsipActionMessage()
        }
    }

    val filterOptions = listOf("Semua", "Surat Tugas", "Surat Keterangan", "SK", "Lainnya")

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "E-Arsip Surat",
                subtitle = "Dokumen & surat kepegawaian",
                onBack = onBack,
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = onCreateSuratTugas) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, "Buat Surat Tugas", tint = Color.White)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showUploadDialog = true },
                    containerColor = AppColors.Accent
                ) {
                    Icon(Icons.Default.Upload, "Upload Arsip", tint = Color.White)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppColors.Background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateArsipSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Cari arsip...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Accent,
                    unfocusedContainerColor = AppColors.Surface
                )
            )

            // Filters
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filterOptions.forEach { label ->
                    val filterVal = if (label == "Semua") null else label
                    val isSelected = state.selectedFilter == filterVal
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.updateArsipFilter(filterVal) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Accent.copy(alpha = 0.15f),
                            selectedLabelColor = AppColors.Accent
                        )
                    )
                }
            }

            // Offline indicator
            if (state.isOffline) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
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

            // Syncing files indicator
            if (state.isSyncingFiles) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(12.dp), color = Color(0xFF3B82F6), strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Menyimpan file untuk offline...", fontSize = 11.sp, color = Color(0xFF3B82F6))
                    }
                }
            }

            // Content
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.loadSuratArsip(state.selectedFilter, state.searchQuery.ifBlank { null }) },
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.list.isEmpty() && !state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Folder, null, Modifier.size(48.dp), tint = AppColors.TextTertiary)
                            Spacer(Modifier.height(8.dp))
                            Text("Belum ada arsip surat", color = AppColors.TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.list, key = { it.id }) { arsip ->
                                ArsipCard(
                                arsip = arsip,
                                isAdmin = isAdmin,
                                isFileCached = arsip.id in state.cachedFileIds,
                                onPreview = {
                                    if (arsip.hasFile == true) {
                                        previewItem = arsip
                                    } else {
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            snackbar.showSnackbar("Tidak ada file untuk di-preview")
                                        }
                                    }
                                },
                                onDownload = {
                                    val url = arsip.gdriveUrl ?: arsip.downloadUrl
                                    val skipAuth = arsip.gdriveUrl != null
                                    url?.let { u ->
                                        downloadFileWithAuth(context, u, arsip.perihal ?: "surat", skipAuth)
                                    }
                                },

                                onEdit = { editItem = arsip },
                                onDelete = { deleteItem = arsip }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // Upload Dialog
    if (showUploadDialog) {
        UploadArsipDialog(
            onDismiss = { showUploadDialog = false },
            onUpload = { jenis, perihal, nomor, file ->
                viewModel.uploadSuratArsip(jenis, perihal, nomor, file)
                showUploadDialog = false
            }
        )
    }

    // Preview Dialog
    previewItem?.let { arsip ->
        DocumentPreviewDialog(
            doc = arsip.toDokumenDto(),
            onDismiss = { previewItem = null }
        )
    }

    // Edit Dialog
    editItem?.let { arsip ->
        EditArsipDialog(
            arsip = arsip,
            onDismiss = { editItem = null },
            onSave = { jenis, perihal, nomor ->
                viewModel.updateSuratArsip(arsip.id, jenis, perihal, nomor)
                editItem = null
            }
        )
    }

    // Delete confirmation
    deleteItem?.let { arsip ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Hapus Arsip?", fontWeight = FontWeight.Bold) },
            text = { Text("Arsip \"${arsip.perihal}\" akan dihapus permanen.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteSuratArsip(arsip.id); deleteItem = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ArsipCard(
    arsip: SuratArsipDto,
    isAdmin: Boolean,
    isFileCached: Boolean = false,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = when (arsip.jenis?.lowercase()) {
        "surat tugas" -> Color(0xFF6366F1)
        "surat keterangan" -> Color(0xFF10B981)
        "sk" -> Color(0xFFF59E0B)
        else -> Color(0xFF64748B)
    }

    Surface(
        Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onPreview),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(typeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Description, null, tint = typeColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(arsip.perihal ?: "-", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        arsip.jenis?.let {
                            Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.12f)) {
                                Text(it, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 9.sp, fontWeight = FontWeight.Medium, color = typeColor)
                            }
                        }
                        Text(arsip.tanggal, fontSize = 10.sp, color = AppColors.TextTertiary)
                        // Offline cache indicator
                        if (arsip.hasFile == true) {
                            Icon(
                                imageVector = if (isFileCached) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                                contentDescription = if (isFileCached) "Tersedia offline" else "Online",
                                modifier = Modifier.size(12.dp),
                                tint = if (isFileCached) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // Bottom info
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                arsip.nomor?.let { Text("No: $it", fontSize = 10.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
                arsip.pegawaiNama?.let { Text(it, fontSize = 10.sp, color = AppColors.TextTertiary) }
            }

            // Actions
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                if (arsip.hasFile == true) {
                    TextButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp), tint = Color(0xFF3B82F6))
                        Spacer(Modifier.width(4.dp))
                        Text("Download", fontSize = 11.sp, color = Color(0xFF3B82F6))
                    }
                }
                if (isAdmin) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, null, Modifier.size(14.dp), tint = AppColors.Accent)
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", fontSize = 11.sp, color = AppColors.Accent)
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = Color(0xFFEF4444))
                        Spacer(Modifier.width(4.dp))
                        Text("Hapus", fontSize = 11.sp, color = Color(0xFFEF4444))
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadArsipDialog(
    onDismiss: () -> Unit,
    onUpload: (jenis: String, perihal: String, nomor: String?, file: File?) -> Unit
) {
    var jenis by remember { mutableStateOf("") }
    var perihal by remember { mutableStateOf("") }
    var nomor by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> fileUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Arsip Surat", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = jenis, onValueChange = { jenis = it }, label = { Text("Jenis Surat *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = perihal, onValueChange = { perihal = it }, label = { Text("Perihal *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nomor, onValueChange = { nomor = it }, label = { Text("Nomor Surat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (fileUri != null) "File dipilih ✓" else "Pilih File (opsional)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val file = fileUri?.let { uri ->
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val tempFile = File.createTempFile("arsip_", ".pdf", context.cacheDir)
                        inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                        tempFile
                    }
                    onUpload(jenis, perihal, nomor.ifBlank { null }, file)
                },
                enabled = jenis.isNotBlank() && perihal.isNotBlank()
            ) { Text("Upload") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun EditArsipDialog(
    arsip: SuratArsipDto,
    onDismiss: () -> Unit,
    onSave: (jenis: String?, perihal: String?, nomor: String?) -> Unit
) {
    var jenis by remember { mutableStateOf(arsip.jenis ?: "") }
    var perihal by remember { mutableStateOf(arsip.perihal ?: "") }
    var nomor by remember { mutableStateOf(arsip.nomor ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Arsip Surat", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = jenis, onValueChange = { jenis = it }, label = { Text("Jenis Surat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = perihal, onValueChange = { perihal = it }, label = { Text("Perihal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nomor, onValueChange = { nomor = it }, label = { Text("Nomor Surat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(jenis.ifBlank { null }, perihal.ifBlank { null }, nomor.ifBlank { null }) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ─── Mapper Helper ─────────────────────────────────────────────────────────

private fun SuratArsipDto.toDokumenDto() = DokumenDto(
    id = id,
    judul = perihal ?: "Surat $nomor",
    jenis = jenis ?: "Arsip Surat",
    issuedAt = tanggal,
    keterangan = null,
    fileName = fileName,
    fileExtension = fileExtension,
    gdriveUrl = gdriveUrl,
    downloadUrl = downloadUrl
)
