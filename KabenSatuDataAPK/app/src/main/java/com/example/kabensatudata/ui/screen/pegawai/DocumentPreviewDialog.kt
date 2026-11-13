package com.example.kabensatudata.ui.screen.pegawai

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.dto.DokumenDto
import com.example.kabensatudata.ui.theme.AppColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface DocPreviewAuthEntryPoint {
    fun authTokenManager(): AuthTokenManager
    fun arsipOfflineManager(): com.example.kabensatudata.util.ArsipOfflineManager
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentPreviewDialog(
    doc: DokumenDto,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val gdriveUrl = doc.gdriveUrl
    val downloadUrl = gdriveUrl ?: doc.downloadUrl ?: return
    val isGDrive = gdriveUrl != null

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context, DocPreviewAuthEntryPoint::class.java)
    }
    val token = remember { entryPoint.authTokenManager().getToken() }
    val offlineManager = remember { entryPoint.arsipOfflineManager() }

    // Check if file is available locally
    val localFile = remember(doc.id) {
        offlineManager.getLocalFile(doc.id, doc.fileExtension)
    }

    // Smart file type detection: fileExtension > fileName > URL > judul
    val detectedExt = remember(doc) {
        doc.fileExtension?.lowercase()
            ?: doc.fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..5 }
            ?: downloadUrl.substringBefore("?").substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }
            ?: doc.judul.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }
            ?: ""
    }

    val isImage = detectedExt in listOf("jpg", "jpeg", "png", "gif", "webp") ||
        doc.jenis.lowercase().let { it.contains("foto") || it.contains("gambar") }

    val isPdf = detectedExt == "pdf" || doc.jenis.lowercase().contains("pdf")

    val isOfficeDoc = detectedExt in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "odt", "ods")

    // For office docs, open with external app (local file first, then download)
    if (isOfficeDoc) {
        LaunchedEffect(Unit) {
            if (localFile != null) {
                // Open from local cache
                com.example.kabensatudata.util.ArsipFileHelper.openWithExternalApp(context, localFile)
            } else {
                openInExternalApp(context, downloadUrl, doc.fileName ?: "${doc.judul}.$detectedExt", isGDrive)
            }
            onDismiss()
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxSize().padding(0.dp),
            shape = RoundedCornerShape(0.dp),
            color = AppColors.Background
        ) {
            Column(Modifier.fillMaxSize()) {
                // Toolbar
                Surface(Modifier.fillMaxWidth(), color = AppColors.PrimaryDark) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Tutup", tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(doc.judul, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                            Text(
                                if (detectedExt.isNotEmpty()) "${doc.jenis} • .$detectedExt" else doc.jenis,
                                fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        // Open in external app
                        IconButton(onClick = {
                            openInExternalApp(context, downloadUrl, doc.fileName ?: "${doc.judul}.$detectedExt", isGDrive)
                        }) {
                            Icon(Icons.Default.OpenInNew, "Buka di App Lain", tint = Color.White)
                        }
                        // Share
                        IconButton(onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, downloadUrl)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan"))
                        }) {
                            Icon(Icons.Default.Share, "Share", tint = Color.White)
                        }
                        // Download
                        IconButton(onClick = { downloadFileWithAuth(context, downloadUrl, doc.fileName ?: doc.judul, isGDrive) }) {
                            Icon(Icons.Default.Download, "Download", tint = Color.White)
                        }
                    }
                }

                // Preview content
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        isImage -> ImagePreview(downloadUrl, if (isGDrive) null else token, doc.judul)
                        isPdf -> PdfPreview(downloadUrl, if (isGDrive) null else token, context, localFile)
                        else -> UnsupportedPreview(doc, context, downloadUrl, isGDrive)
                    }
                }
            }
        }
    }
}

// ─── Image Preview with Auth ────────────────────────────────────────────────

@Composable
private fun ImagePreview(url: String, token: String?, title: String) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading && !hasError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(32.dp), color = AppColors.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text("Memuat gambar...", fontSize = 12.sp, color = AppColors.TextTertiary)
            }
        }

        val requestBuilder = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .listener(
                onSuccess = { _, _ -> isLoading = false },
                onError = { _, _ -> isLoading = false; hasError = true }
            )
        
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        AsyncImage(
            model = requestBuilder.build(),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        if (hasError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, null, tint = AppColors.Error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Gagal memuat gambar", fontSize = 13.sp, color = AppColors.TextSecondary)
            }
        }
    }
}

// ─── PDF Preview with PdfRenderer ───────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfPreview(url: String, token: String?, context: Context, localFile: File? = null) {
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Use local file if available, otherwise download
    LaunchedEffect(url) {
        isLoading = true
        error = null
        try {
            val pages = withContext(Dispatchers.IO) {
                val pdfFile = if (localFile != null && localFile.exists() && localFile.length() > 0) {
                    // Use offline cached file
                    localFile
                } else {
                    // Download to cache
                    val cacheFile = File(context.cacheDir, "preview_${url.hashCode()}.pdf")
                    if (!cacheFile.exists() || cacheFile.length() == 0L) {
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.instanceFollowRedirects = true
                        if (!token.isNullOrBlank()) {
                            conn.setRequestProperty("Authorization", "Bearer $token")
                            conn.setRequestProperty("Accept", "application/pdf,application/octet-stream")
                        }
                        conn.connectTimeout = 15000
                        conn.readTimeout = 30000
                        conn.connect()

                        if (conn.responseCode == 200) {
                            conn.inputStream.use { input ->
                                cacheFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } else {
                            throw Exception("HTTP ${conn.responseCode}")
                        }
                        conn.disconnect()
                    }
                    cacheFile
                }

                // Render PDF pages
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val bitmaps = mutableListOf<Bitmap>()

                val maxPages = minOf(renderer.pageCount, 20) // Limit to 20 pages
                for (i in 0 until maxPages) {
                    val page = renderer.openPage(i)
                    val scale = 2f // 2x resolution
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bitmap)
                }
                renderer.close()
                fd.close()
                bitmaps
            }
            pdfPages = pages
        } catch (e: Exception) {
            error = e.message ?: "Gagal memuat PDF"
        }
        isLoading = false
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(32.dp), color = AppColors.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Memuat PDF...", fontSize = 12.sp, color = AppColors.TextTertiary)
                }
            }
            error != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = AppColors.Error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Gagal memuat PDF", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Text(error!!, fontSize = 11.sp, color = AppColors.TextTertiary, textAlign = TextAlign.Center)
                }
            }
            pdfPages.isNotEmpty() -> {
                Column(Modifier.fillMaxSize()) {
                    // Page indicator
                    val pagerState = rememberPagerState(pageCount = { pdfPages.size })
                    Surface(Modifier.fillMaxWidth(), color = AppColors.Surface) {
                        Text(
                            "Halaman ${pagerState.currentPage + 1} / ${pdfPages.size}",
                            fontSize = 11.sp, color = AppColors.TextTertiary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // PDF pages
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        Image(
                            bitmap = pdfPages[pageIndex].asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}

// ─── Unsupported File Preview ───────────────────────────────────────────────

@Composable
private fun UnsupportedPreview(doc: DokumenDto, context: Context, downloadUrl: String, isGDrive: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val icon = when {
            doc.judul.lowercase().let { it.endsWith(".doc") || it.endsWith(".docx") } -> Icons.Default.Description
            doc.judul.lowercase().let { it.endsWith(".xls") || it.endsWith(".xlsx") } -> Icons.Default.TableChart
            doc.judul.lowercase().let { it.endsWith(".ppt") || it.endsWith(".pptx") } -> Icons.Default.Slideshow
            else -> Icons.Default.InsertDriveFile
        }

        Icon(icon, null, tint = AppColors.Accent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(doc.judul, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(doc.jenis, fontSize = 12.sp, color = AppColors.TextTertiary)
        Spacer(Modifier.height(24.dp))
        Text("Preview tidak tersedia untuk tipe file ini.", fontSize = 13.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { downloadFileWithAuth(context, downloadUrl, doc.judul, isGDrive) },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Accent),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp), tint = AppColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("Download File", color = AppColors.Accent)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Button(
                onClick = { openInExternalApp(context, downloadUrl, doc.judul, isGDrive) },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buka di Aplikasi Lain")
            }
        }
    }
}

// ─── App Viewer Helper ──────────────────────────────────────────────────────

fun openInExternalApp(context: Context, url: String, title: String, isGDrive: Boolean = false) {
    Toast.makeText(context, "Membuka file...", Toast.LENGTH_SHORT).show()
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(context, DocPreviewAuthEntryPoint::class.java)
            val token = entryPoint.authTokenManager().getToken()

            var finalTitle = title
            if (!finalTitle.contains(".")) {
                val ext = when {
                    url.contains(".doc", ignoreCase = true) || url.contains(".docx", ignoreCase = true) -> ".docx"
                    url.contains(".xls", ignoreCase = true) || url.contains(".xlsx", ignoreCase = true) -> ".xlsx"
                    url.contains(".ppt", ignoreCase = true) || url.contains(".pptx", ignoreCase = true) -> ".pptx"
                    else -> ""
                }
                finalTitle += ext
            }

            val cacheFile = File(context.cacheDir, finalTitle)
            
            // Re-download only if cache missing or empty to save time
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                if (!isGDrive && !token.isNullOrBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Accept", "application/octet-stream")
                }
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.connect()

                if (conn.responseCode == 200) {
                    conn.inputStream.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal memuat: HTTP ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                    conn.disconnect()
                    return@launch
                }
                conn.disconnect()
            }
            
            withContext(Dispatchers.Main) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                // Determine mime type from extension
                val ext = finalTitle.substringAfterLast('.', "")
                val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "*/*"
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Tidak ada aplikasi yang mendukung untuk membuka file ini.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ─── Download Helper ────────────────────────────────────────────────────────

fun downloadFileWithAuth(context: Context, url: String, title: String, skipAuth: Boolean = false) {
    try {
        val entryPoint = EntryPointAccessors.fromApplication(context, DocPreviewAuthEntryPoint::class.java)
        val token = entryPoint.authTokenManager().getToken()

        // Auto-append extension if missing based on URL or common knowledge
        var finalTitle = title
        if (!finalTitle.contains(".")) {
            val ext = when {
                url.contains(".pdf", ignoreCase = true) -> ".pdf"
                url.contains(".jpg", ignoreCase = true) || url.contains(".jpeg", ignoreCase = true) -> ".jpg"
                url.contains(".png", ignoreCase = true) -> ".png"
                url.contains(".doc", ignoreCase = true) || url.contains(".docx", ignoreCase = true) -> ".docx"
                url.contains(".xls", ignoreCase = true) || url.contains(".xlsx", ignoreCase = true) -> ".xlsx"
                else -> "" // Fallback
            }
            finalTitle += ext
        }

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(finalTitle)
            setDescription("Mengunduh $finalTitle")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "KabenSatuData/$finalTitle")
            if (!skipAuth && token != null) {
                addRequestHeader("Authorization", "Bearer $token")
            }
            if (!skipAuth) {
                // Only send Accept header for internal server, it breaks Google Drive UC export
                addRequestHeader("Accept", "application/octet-stream")
            }
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Download dimulai: $finalTitle", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download gagal: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
