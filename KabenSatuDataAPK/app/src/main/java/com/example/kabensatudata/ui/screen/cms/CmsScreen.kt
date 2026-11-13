package com.example.kabensatudata.ui.screen.cms

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kabensatudata.data.remote.dto.*
import com.example.kabensatudata.ui.theme.AppColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * CMS Dashboard — 3-tab screen for managing Berita, Galeri, Pesan Masuk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CmsScreen(
    onBack: () -> Unit,
    onEditPost: (Int?) -> Unit,    // null = create new
    onViewMessage: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    // ─── Resolve BokApiService ───────────────────────────
    val bokApi = remember {
        try {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.kabensatudata.nearby.RemoteControlHandler.RemoteEntryPoint::class.java
            )
            ep.bokApiService()
        } catch (_: Exception) { null }
    }

    // ─── Unread count for badge ──────────────────────────
    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            bokApi?.getCmsUnreadCount()?.body()?.let { unreadCount = it.count }
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CMS Puskesmas", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Surface,
                    titleContentColor = AppColors.TextPrimary
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ─── Tab Row ─────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AppColors.Surface,
                contentColor = AppColors.Primary
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📰", fontSize = 16.sp)
                        Text("Berita", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🖼️", fontSize = 16.sp)
                        Text("Galeri", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📩", fontSize = 16.sp)
                        Text("Pesan", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                        if (unreadCount > 0) {
                            Badge(containerColor = Color(0xFFEF4444)) {
                                Text("$unreadCount", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // ─── Tab Content ─────────────────────────────
            when (selectedTab) {
                0 -> BeritaTab(bokApi, onEditPost)
                1 -> GaleriTab(bokApi)
                2 -> PesanTab(bokApi, onViewMessage) { unreadCount = it }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Tab 1: Berita
// ═══════════════════════════════════════════════════════════
@Composable
private fun BeritaTab(
    bokApi: com.example.kabensatudata.data.remote.BokApiService?,
    onEditPost: (Int?) -> Unit
) {
    var posts by remember { mutableStateOf<List<PostDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun loadPosts() {
        scope.launch {
            isLoading = true; error = null
            try {
                val resp = bokApi?.getCmsPosts(
                    search = searchQuery.ifBlank { null }
                )
                if (resp?.isSuccessful == true) {
                    posts = resp.body()?.data ?: emptyList()
                } else {
                    error = "Error: ${resp?.code()}"
                }
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadPosts() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Cari berita...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; loadPosts() }) {
                            Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = AppColors.DividerColor
                )
            )

            // Search trigger on enter
            LaunchedEffect(searchQuery) {
                kotlinx.coroutines.delay(500)
                loadPosts()
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            } else if (error != null) {
                ErrorState(error!!) { loadPosts() }
            } else if (posts.isEmpty()) {
                EmptyState("Belum ada berita")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        PostCard(post, onEditPost, onDelete = {
                            scope.launch {
                                try {
                                    bokApi?.deleteCmsPost(post.id)
                                    loadPosts()
                                    Toast.makeText(context, "Berita dihapus", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {}
                            }
                        })
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { onEditPost(null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp),
            containerColor = AppColors.Primary, contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, "Buat Berita")
        }
    }
}

@Composable
private fun PostCard(post: PostDto, onEdit: (Int) -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        Modifier.fillMaxWidth().clickable { onEdit(post.id) },
        shape = RoundedCornerShape(12.dp), color = AppColors.Surface,
        shadowElevation = 1.dp, border = BorderStroke(0.5.dp, AppColors.DividerColor)
    ) {
        Row(Modifier.padding(12.dp)) {
            // Thumbnail
            if (post.featuredImageUrl != null) {
                AsyncImage(
                    model = post.featuredImageUrl, contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(Modifier.weight(1f)) {
                // Status badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (post.isPublished) Color(0xFF10B981).copy(alpha = 0.1f)
                               else Color(0xFFF59E0B).copy(alpha = 0.1f)
                    ) {
                        Text(
                            if (post.isPublished) "Published" else "Draft",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                            color = if (post.isPublished) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    post.category?.let {
                        Text("• ${it.name}", fontSize = 9.sp, color = AppColors.TextTertiary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("👁 ${post.viewsCount}", fontSize = 9.sp, color = AppColors.TextTertiary)
                }

                Spacer(Modifier.height(4.dp))
                Text(post.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)

                post.excerpt?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, fontSize = 11.sp, color = AppColors.TextSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.author ?: "-", fontSize = 10.sp, color = AppColors.TextTertiary)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showDeleteDialog = true }, Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Berita?") },
            text = { Text("\"${post.title}\" akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Hapus", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Tab 2: Galeri
// ═══════════════════════════════════════════════════════════
@Composable
private fun GaleriTab(bokApi: com.example.kabensatudata.data.remote.BokApiService?) {
    var galleries by remember { mutableStateOf<List<GalleryDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isUploading = true
                try {
                    val parts = uris.mapIndexed { idx, uri ->
                        val bytes = com.example.kabensatudata.util.ImageCompressor.compressImage(context, uri)
                        val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        MultipartBody.Part.createFormData("images[]", "photo_$idx.jpg", body)
                    }
                    val titleBody = "Galeri ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id")).format(java.util.Date())}".toRequestBody("text/plain".toMediaTypeOrNull())
                    val publishedBody = "1".toRequestBody("text/plain".toMediaTypeOrNull())

                    val resp = bokApi?.createCmsGallery(
                        title = titleBody, categoryId = null, description = null,
                        eventDate = null, isPublished = publishedBody, images = parts
                    )
                    if (resp?.isSuccessful == true) {
                        Toast.makeText(context, "${uris.size} foto diupload!", Toast.LENGTH_SHORT).show()
                        loadGalleries(bokApi, { galleries = it }, { error = it }, { isLoading = it })
                    } else {
                        Toast.makeText(context, "Upload gagal: ${resp?.code()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                isUploading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadGalleries(bokApi, { galleries = it }, { error = it }, { isLoading = it })
    }

    Box(Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        } else if (error != null) {
            ErrorState(error!!) {
                scope.launch { loadGalleries(bokApi, { galleries = it }, { error = it }, { isLoading = it }) }
            }
        } else if (galleries.isEmpty()) {
            EmptyState("Belum ada foto di galeri")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(galleries, key = { it.id }) { gallery ->
                    GalleryGridItem(gallery) {
                        scope.launch {
                            try {
                                bokApi?.deleteCmsGallery(gallery.id)
                                loadGalleries(bokApi, { galleries = it }, { error = it }, { isLoading = it })
                                Toast.makeText(context, "Foto dihapus", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        // Upload overlay
        if (isUploading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("Mengupload foto...", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { imagePicker.launch("image/*") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp),
            containerColor = AppColors.Primary, contentColor = Color.White
        ) {
            Icon(Icons.Default.AddPhotoAlternate, "Upload Foto")
        }
    }
}

@Composable
private fun GalleryGridItem(gallery: GalleryDto, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
            .clickable { showMenu = !showMenu }
    ) {
        AsyncImage(
            model = gallery.imageUrl, contentDescription = gallery.title,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        )

        // Title overlay
        Box(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            Text(gallery.title, fontSize = 9.sp, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Status dot
        if (!gallery.isPublished) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp)
                    .size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B))
            )
        }

        // Delete menu
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("🗑️ Hapus", color = Color(0xFFEF4444)) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}

private suspend fun loadGalleries(
    api: com.example.kabensatudata.data.remote.BokApiService?,
    onData: (List<GalleryDto>) -> Unit,
    onError: (String?) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    onLoading(true); onError(null)
    try {
        val resp = api?.getCmsGalleries()
        if (resp?.isSuccessful == true) {
            onData(resp.body()?.data ?: emptyList())
        } else {
            onError("Error: ${resp?.code()}")
        }
    } catch (e: Exception) {
        onError(e.message)
    }
    onLoading(false)
}

// ═══════════════════════════════════════════════════════════
// Tab 3: Pesan Masuk
// ═══════════════════════════════════════════════════════════
@Composable
private fun PesanTab(
    bokApi: com.example.kabensatudata.data.remote.BokApiService?,
    onViewMessage: (Int) -> Unit,
    onUnreadUpdate: (Int) -> Unit
) {
    var messages by remember { mutableStateOf<List<MessageDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun loadMessages() {
        scope.launch {
            isLoading = true; error = null
            try {
                val resp = bokApi?.getCmsMessages()
                if (resp?.isSuccessful == true) {
                    messages = resp.body()?.data ?: emptyList()
                    val unread = messages.count { !it.isRead }
                    onUnreadUpdate(unread)
                } else {
                    error = "Error: ${resp?.code()}"
                }
            } catch (e: Exception) {
                error = e.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadMessages() }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.Primary)
        }
    } else if (error != null) {
        ErrorState(error!!) { loadMessages() }
    } else if (messages.isEmpty()) {
        EmptyState("Belum ada pesan masuk")
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageCard(msg, onClick = { onViewMessage(msg.id) }, onDelete = {
                    scope.launch {
                        try {
                            bokApi?.deleteCmsMessage(msg.id)
                            loadMessages()
                            Toast.makeText(context, "Pesan dihapus", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                    }
                })
            }
        }
    }
}

@Composable
private fun MessageCard(msg: MessageDto, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (!msg.isRead) AppColors.Primary.copy(alpha = 0.04f) else AppColors.Surface,
        shadowElevation = if (!msg.isRead) 2.dp else 0.dp,
        border = BorderStroke(0.5.dp, if (!msg.isRead) AppColors.Primary.copy(alpha = 0.2f) else AppColors.DividerColor)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Unread dot
            if (!msg.isRead) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.Primary))
                Spacer(Modifier.width(10.dp))
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text(
                        msg.createdAt?.take(10) ?: "",
                        fontSize = 10.sp, color = AppColors.TextTertiary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(msg.subject, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(msg.message, fontSize = 11.sp, color = AppColors.TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            IconButton(onClick = onDelete, Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Shared Components
// ═══════════════════════════════════════════════════════════

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚠️", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, fontSize = 14.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("Coba Lagi") }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📭", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, fontSize = 14.sp, color = AppColors.TextSecondary)
    }
}
