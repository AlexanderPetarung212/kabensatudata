package com.example.kabensatudata.ui.screen.cms

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.kabensatudata.data.remote.dto.*
import com.example.kabensatudata.ui.theme.AppColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Post Editor Screen — Create or edit berita with rich text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditorScreen(
    postId: Int?,  // null = create mode
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

    // ─── State ───────────────────────────────────────────
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var excerpt by remember { mutableStateOf("") }
    var isPublished by remember { mutableStateOf(true) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var existingImageUrl by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<CategoryDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(postId != null) }
    var isSaving by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    // ─── Load existing post + categories ─────────────────
    LaunchedEffect(Unit) {
        try {
            val catResp = bokApi?.getCmsCategories("berita")
            if (catResp?.isSuccessful == true) {
                categories = catResp.body()?.data ?: emptyList()
            }
        } catch (_: Exception) {}

        if (postId != null) {
            try {
                val resp = bokApi?.getCmsPost(postId)
                if (resp?.isSuccessful == true) {
                    val post = resp.body()?.data
                    if (post != null) {
                        title = post.title
                        content = post.content ?: ""
                        excerpt = post.excerpt ?: ""
                        isPublished = post.isPublished
                        selectedCategoryId = post.category?.id
                        existingImageUrl = post.featuredImageUrl
                    }
                }
            } catch (_: Exception) {}
        }
        isLoading = false
    }

    // ─── Rich Text HTML ──────────────────────────────────
    val richTextHtml = remember { """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: -apple-system, sans-serif; font-size: 15px; padding: 12px; color: #1f2937; min-height: 200px; }
            #editor { outline: none; min-height: 200px; line-height: 1.6; }
            #editor:empty:before { content: 'Tulis konten berita disini...'; color: #9ca3af; }
            .toolbar { display: flex; gap: 4px; padding: 8px; background: #f3f4f6; border-bottom: 1px solid #e5e7eb; position: sticky; top: 0; z-index: 10; flex-wrap: wrap; }
            .toolbar button { border: none; background: #fff; padding: 6px 10px; border-radius: 6px; font-size: 14px; cursor: pointer; font-weight: 600; color: #374151; }
            .toolbar button:active { background: #3b82f6; color: #fff; }
            h1, h2, h3 { margin: 8px 0; }
            ul, ol { padding-left: 20px; margin: 8px 0; }
            a { color: #3b82f6; }
        </style>
        </head>
        <body>
        <div class="toolbar">
            <button onclick="exec('bold')"><b>B</b></button>
            <button onclick="exec('italic')"><i>I</i></button>
            <button onclick="exec('underline')"><u>U</u></button>
            <button onclick="exec('strikeThrough')"><s>S</s></button>
            <button onclick="exec('formatBlock','<h2>')">H2</button>
            <button onclick="exec('formatBlock','<h3>')">H3</button>
            <button onclick="exec('insertUnorderedList')">• List</button>
            <button onclick="exec('insertOrderedList')">1. List</button>
            <button onclick="addLink()">🔗</button>
            <button onclick="exec('formatBlock','<p>')">¶</button>
        </div>
        <div id="editor" contenteditable="true"></div>
        <script>
            function exec(cmd, val) { document.execCommand(cmd, false, val || null); }
            function addLink() {
                var url = prompt('URL:');
                if (url) exec('createLink', url);
            }
            function getContent() { return document.getElementById('editor').innerHTML; }
            function setContent(html) { document.getElementById('editor').innerHTML = html; }
        </script>
        </body>
        </html>
    """.trimIndent()
    }

    // ─── Save Function ───────────────────────────────────
    fun save() {
        if (title.isBlank()) {
            Toast.makeText(context, "Judul wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isSaving = true
            try {
                // Get content from WebView
                webView?.evaluateJavascript("getContent()") { rawHtml ->
                    val htmlContent = rawHtml?.trim('"')?.replace("\\u003C", "<")?.replace("\\\"", "\"")
                        ?.replace("\\n", "\n")?.replace("\\/", "/") ?: content

                    scope.launch {
                        val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                        val contentBody = htmlContent.toRequestBody("text/plain".toMediaTypeOrNull())
                        val catBody = selectedCategoryId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
                        val excerptBody = excerpt.ifBlank { null }?.toRequestBody("text/plain".toMediaTypeOrNull())
                        val publishBody = (if (isPublished) "1" else "0").toRequestBody("text/plain".toMediaTypeOrNull())

                        var imagePart: MultipartBody.Part? = null
                        if (imageUri != null) {
                            val bytes = com.example.kabensatudata.util.ImageCompressor.compressImage(context, imageUri!!)
                            val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                            imagePart = MultipartBody.Part.createFormData("featured_image", "photo.jpg", body)
                        }

                        try {
                            val resp = if (postId != null) {
                                bokApi?.updateCmsPost(postId, titleBody, contentBody, catBody, excerptBody, publishBody, null, imagePart)
                            } else {
                                bokApi?.createCmsPost(titleBody, contentBody, catBody, excerptBody, publishBody, null, imagePart)
                            }

                            if (resp?.isSuccessful == true) {
                                Toast.makeText(context, if (postId != null) "Berita diperbarui!" else "Berita dibuat!", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                Toast.makeText(context, "Gagal: ${resp?.code()}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        isSaving = false
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                isSaving = false
            }
        }
    }

    // ─── UI ──────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (postId != null) "Edit Berita" else "Buat Berita", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 4.dp),
                            color = AppColors.Primary, strokeWidth = 2.dp
                        )
                    }
                    TextButton(onClick = { save() }, enabled = !isSaving) {
                        Text("Simpan", fontWeight = FontWeight.Bold, color = AppColors.Primary)
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
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Judul Berita") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.Primary)
                )

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "Pilih Kategori",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(expanded = showCategoryDropdown, onDismissRequest = { showCategoryDropdown = false }) {
                        DropdownMenuItem(text = { Text("Tanpa Kategori") }, onClick = {
                            selectedCategoryId = null; showCategoryDropdown = false
                        })
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.name) }, onClick = {
                                selectedCategoryId = cat.id; showCategoryDropdown = false
                            })
                        }
                    }
                }

                // Featured image
                Text("Gambar Utama", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayUri = imageUri ?: existingImageUrl?.let { Uri.parse(it) }
                    if (displayUri != null) {
                        AsyncImage(
                            model = displayUri, contentDescription = null,
                            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (displayUri != null) "Ganti Gambar" else "Pilih Gambar")
                    }
                }

                // Rich Text Editor
                Text("Konten", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(350.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AppColors.DividerColor)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                loadDataWithBaseURL(null, richTextHtml, "text/html", "UTF-8", null)
                                webView = this
                            }
                        },
                        update = { wv ->
                            if (content.isNotEmpty() && postId != null) {
                                val escaped = content.replace("'", "\\'").replace("\n", "\\n")
                                wv.evaluateJavascript("setContent('$escaped')", null)
                            }
                        }
                    )
                }

                // Excerpt
                OutlinedTextField(
                    value = excerpt, onValueChange = { excerpt = it },
                    label = { Text("Ringkasan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                // Publish toggle
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(AppColors.Surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Publikasikan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (isPublished) "Berita akan tampil di website" else "Disimpan sebagai draft",
                            fontSize = 12.sp, color = AppColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = isPublished, onCheckedChange = { isPublished = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Primary)
                    )
                }

                Spacer(Modifier.height(60.dp))
            }
        }
    }
}
