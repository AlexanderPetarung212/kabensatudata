package com.example.kabensatudata.ui.screen.nearby

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.local.entity.ChatMessageEntity
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.appTextFieldColors

private val Teal = AppColors.Primary
private val Green = AppColors.Success
private val BubbleMine = AppColors.Primary
private val BubbleTheirs = Color(0xFFF0F4F8)
private val ReadBlue = Color(0xFF53BDEB)

// ═════════════════════════════════════════════════════════
// ChatListScreen — contains both conversations list AND
// inline chat view (no separate ChatScreen/route needed)
// ═════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    onOpenChat: (peerId: String, peerName: String) -> Unit = { _, _ -> },
    viewModel: ChatViewModel = hiltViewModel()
) {
    val listState by viewModel.listState.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    var showPeerPicker by remember { mutableStateOf(false) }

    // Are we viewing a conversation?
    val isInChat = chatState.peerId.isNotBlank()

    Scaffold(
        topBar = {
            if (isInChat) {
                // Chat TopBar
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center) {
                                Text(chatState.peerName.take(2).uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(chatState.peerName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                                if (chatState.peerIsTyping) {
                                    Text("Sedang mengetik...", fontSize = 11.sp, color = Color(0xFF81C784), fontStyle = FontStyle.Italic)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (chatState.isOnline) {
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF81C784)))
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            if (chatState.isOnline) "Online" else "Offline",
                                            fontSize = 11.sp,
                                            color = if (chatState.isOnline) Color(0xFF81C784) else Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeChatView() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.TopBarBg, titleContentColor = Color.White, navigationIconContentColor = Color.White
                    )
                )
            } else {
                // List TopBar
                TopAppBar(
                    title = { Text("Chat P2P", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.TopBarBg, titleContentColor = Color.White, navigationIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            if (isInChat) {
                val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    uri?.let { viewModel.sendImageFromUri(it) }
                }
                // ─── Chat Input Bar ──────────────────────────
                Surface(color = AppColors.Surface, shadowElevation = 8.dp) {
                    Row(
                        Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                            .padding(bottom = 56.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attach button (image picker)
                        IconButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Image, "Lampiran", tint = Teal)
                        }
                        OutlinedTextField(
                            value = chatState.inputText,
                            onValueChange = { viewModel.updateInput(it) },
                            placeholder = { Text("Ketik pesan...", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = false,
                            maxLines = 4,
                            colors = appTextFieldColors()
                        )
                        Spacer(Modifier.width(4.dp))
                        FilledIconButton(
                            onClick = { viewModel.sendMessage() },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Teal),
                            enabled = chatState.inputText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isInChat) {
                FloatingActionButton(
                    onClick = { showPeerPicker = true },
                    containerColor = Teal,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 56.dp)
                ) { Icon(Icons.Default.Add, "Chat Baru") }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (isInChat) {
            // ─── Chat View ───────────────────────────────
            ChatContent(
                chatState = chatState,
                modifier = Modifier.fillMaxSize().padding(padding),
                viewModel = viewModel
            )
        } else {
            // ─── Conversations List ──────────────────────
            ConversationsContent(
                state = listState,
                onOpenChat = { peerId, peerName ->
                    viewModel.startChatWithPeer(peerId)
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }

    // Peer picker
    if (showPeerPicker) {
        PeerPickerDialog(
            peers = listState.peers,
            onSelect = { peerId, _ ->
                showPeerPicker = false
                viewModel.startChatWithPeer(peerId)
            },
            onDismiss = { showPeerPicker = false }
        )
    }

    // Handle system back when in chat
    androidx.activity.compose.BackHandler(enabled = isInChat) {
        viewModel.closeChatView()
    }
}

// Also keep ChatScreen composable for route-based navigation (backward compat)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerId: String,
    peerName: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    LaunchedEffect(peerId) { viewModel.openExistingChat(peerId, peerName) }
    DisposableEffect(Unit) { onDispose { viewModel.closeChatView() } }

    val state by viewModel.chatState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center) {
                            Text(peerName.take(2).uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                            Text(
                                if (state.isOnline) "Online" else "Offline",
                                fontSize = 11.sp, color = if (state.isOnline) Color(0xFF81C784) else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.TopBarBg, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        bottomBar = {
            val imagePicker2 = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let { viewModel.sendImageFromUri(it) }
            }
            Surface(color = AppColors.Surface, shadowElevation = 8.dp) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 10.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { imagePicker2.launch("image/*") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Image, "Lampiran", tint = Teal)
                    }
                    OutlinedTextField(
                        value = state.inputText, onValueChange = { viewModel.updateInput(it) },
                        placeholder = { Text("Ketik pesan...", fontSize = 14.sp) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                        singleLine = false, maxLines = 4, colors = appTextFieldColors()
                    )
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(onClick = { viewModel.sendMessage() }, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Teal),
                        enabled = state.inputText.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White)
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        ChatContent(chatState = state, modifier = Modifier.fillMaxSize().padding(padding), viewModel = viewModel)
    }
}

// ═════════════════════════════════════════════════════════
// ─── Shared Chat Content (Messages Area) ────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun ChatContent(chatState: ChatState, modifier: Modifier = Modifier, viewModel: ChatViewModel? = null) {
    val lazyState = rememberLazyListState()
    var msgToDelete by remember { mutableStateOf<ChatMessageEntity?>(null) }

    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) lazyState.animateScrollToItem(chatState.messages.lastIndex)
    }

    Box(modifier.background(AppColors.PrimaryLight)) {
        if (chatState.messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Forum, null, modifier = Modifier.size(36.dp), tint = Teal)
                        Spacer(Modifier.height(10.dp))
                        Text("Mulai percakapan", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("Ketik pesan di bawah untuk memulai \u2193", fontSize = 13.sp, color = AppColors.TextSecondary)
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(), state = lazyState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(chatState.messages) { msg ->
                    ChatBubble(msg,
                        onLongPress = { msgToDelete = msg },
                        onRetry = if (msg.sendFailed) {{ viewModel?.retryMessage(msg) }} else null
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    msgToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { msgToDelete = null },
            title = { Text("Hapus Pesan?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "\"${msg.content.take(60)}${if (msg.content.length > 60) "..." else ""}\"",
                    fontSize = 13.sp, color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel?.deleteMessage(msg.id)
                    msgToDelete = null
                }) { Text("Hapus", color = AppColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { msgToDelete = null }) { Text("Batal") }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════
// ─── Conversations List Content ─────────────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun ConversationsContent(
    state: ChatListState,
    onOpenChat: (peerId: String, peerName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(AppColors.Background)) {
        // Online peers row
        if (state.peers.isNotEmpty()) {
            Text("Online", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextTertiary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                state.peers.values.forEach { peer ->
                    Column(
                        Modifier.clickable { onOpenChat(peer.endpointId, peer.name) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(Teal.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center) {
                                Text(peer.name.take(2).uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Teal)
                            }
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White).align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Green))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(peer.name.take(12), fontSize = 10.sp, color = AppColors.TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 0.5.dp, color = AppColors.DividerColor)
        }

        // Conversation list
        if (state.conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Forum, null, modifier = Modifier.size(56.dp), tint = AppColors.TextTertiary)
                    Spacer(Modifier.height(12.dp))
                    Text("Belum ada percakapan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("Klik peer online atau tekan + untuk mulai", fontSize = 13.sp, color = AppColors.TextTertiary)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(state.conversations) { convo ->
                    ConvoRow(convo = convo, onClick = { onOpenChat(convo.peerId, convo.peerName) })
                }
            }
        }
    }
}

// ─── Row ─────────────────────────────────────────────────

@Composable
private fun ConvoRow(convo: ConversationItem, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = AppColors.Surface) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.size(50.dp).clip(CircleShape).background(Teal.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) {
                    Text(convo.peerName.take(2).uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Teal)
                }
                if (convo.isOnline) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White).align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Green))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(convo.peerName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                Text(convo.lastMessage, fontSize = 13.sp, color = AppColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatChatTime(convo.lastTimestamp), fontSize = 10.sp,
                    color = if (convo.unreadCount > 0) Teal else AppColors.TextTertiary,
                    fontWeight = if (convo.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal)
                AnimatedVisibility(
                    visible = convo.unreadCount > 0,
                    enter = scaleIn(tween(300)) + fadeIn(tween(300)),
                    exit = scaleOut(tween(200)) + fadeOut(tween(200))
                ) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(Teal),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${convo.unreadCount}", fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = AppColors.DividerColor)
}

// ─── Peer Picker ─────────────────────────────────────────

@Composable
private fun PeerPickerDialog(
    peers: Map<String, com.example.kabensatudata.nearby.PeerInfo>,
    onSelect: (peerId: String, peerName: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mulai Chat Baru", fontWeight = FontWeight.SemiBold) },
        text = {
            if (peers.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.BluetoothSearching, null, modifier = Modifier.size(40.dp), tint = AppColors.TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    Text("Tidak ada perangkat terhubung", fontSize = 14.sp, color = AppColors.TextSecondary)
                    Text("Pastikan P2P aktif dan perangkat lain terhubung", fontSize = 12.sp, color = AppColors.TextTertiary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pilih perangkat:", fontSize = 13.sp, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    peers.values.forEach { peer ->
                        Surface(
                            Modifier.fillMaxWidth().clickable { onSelect(peer.endpointId, peer.name) },
                            shape = RoundedCornerShape(10.dp), color = AppColors.Background
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Teal.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = Teal, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(peer.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Online", fontSize = 11.sp, color = Green)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

// ─── Bubble ──────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(msg: ChatMessageEntity, onLongPress: () -> Unit = {}, onRetry: (() -> Unit)? = null) {
    val isMine = msg.isMine
    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(msg.id) { visible.value = true }

    AnimatedVisibility(visible = visible.value,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 }) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom) {
            // Retry icon for failed messages
            if (isMine && msg.sendFailed) {
                Icon(Icons.Default.ErrorOutline, "Gagal kirim",
                    tint = AppColors.Error, modifier = Modifier.size(18.dp).padding(end = 4.dp)
                        .clickable { onRetry?.invoke() })
            }
            Surface(
                modifier = Modifier.widthIn(max = 280.dp)
                    .combinedClickable(onLongClick = onLongPress, onClick = {}),
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                ),
                color = if (isMine) BubbleMine else BubbleTheirs,
                shadowElevation = 0.5.dp
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Image media
                    if (msg.mediaType == "image" && msg.mediaBase64.isNotBlank()) {
                        val bitmap = remember(msg.id) {
                            try {
                                val bytes = Base64.decode(msg.mediaBase64, Base64.NO_WRAP)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) { null }
                        }
                        bitmap?.let {
                            Image(it.asImageBitmap(), "Foto",
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    // Voice note
                    else if (msg.mediaType == "voice") {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Default.PlayArrow, "Play",
                                tint = if (isMine) Color.White else Teal,
                                modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            // Simple waveform placeholder
                            Box(Modifier.weight(1f).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isMine) Color.White.copy(alpha = 0.4f) else Teal.copy(alpha = 0.3f)))
                            Spacer(Modifier.width(8.dp))
                            Text("${msg.mediaDuration}s", fontSize = 11.sp,
                                color = if (isMine) Color.White.copy(alpha = 0.7f) else AppColors.TextTertiary)
                        }
                    }
                    // Text content (skip for image-only)
                    if (msg.mediaType == "text" || (msg.mediaType != "voice" && msg.content.isNotBlank() && !msg.content.startsWith("\uD83D"))) {
                        Text(msg.content, fontSize = 14.sp, lineHeight = 20.sp,
                            color = if (isMine) Color.White else AppColors.TextPrimary)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(formatChatTime(msg.timestamp), fontSize = 10.sp,
                            color = if (isMine) Color.White.copy(alpha = 0.7f) else AppColors.TextTertiary)
                        if (isMine) {
                            Text(
                                when {
                                    msg.sendFailed -> "✗"
                                    msg.read -> "\u2713\u2713"
                                    msg.delivered -> "\u2713\u2713"
                                    else -> "\u2713"
                                },
                                fontSize = 10.sp,
                                color = when {
                                    msg.sendFailed -> AppColors.Error
                                    msg.read -> ReadBlue      // Blue for read
                                    msg.delivered -> Color.White
                                    else -> Color.White.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatChatTime(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}
