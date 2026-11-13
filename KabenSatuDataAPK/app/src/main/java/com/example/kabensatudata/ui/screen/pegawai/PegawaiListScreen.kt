package com.example.kabensatudata.ui.screen.pegawai

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.kabensatudata.data.remote.dto.PegawaiDto
import com.example.kabensatudata.ui.components.AvatarCircle
import com.example.kabensatudata.ui.components.EmptyState
import com.example.kabensatudata.ui.components.StaggeredItem
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.PlusJakartaSansFamily
import com.example.kabensatudata.ui.theme.WaveTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PegawaiListScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToForm: () -> Unit,
    onBack: () -> Unit,
    isAdmin: Boolean = false,
    viewModel: PegawaiViewModel = hiltViewModel()
) {
    val state by viewModel.listState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadPegawaiList() }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Daftar Pegawai",
                subtitle = "Data kepegawaian Puskesmas",
                onBack = onBack
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = onNavigateToForm,
                    containerColor = Color(0xFF8B5CF6),
                    contentColor = Color.White,
                    modifier = Modifier.padding(bottom = 60.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, "Tambah")
                }
            }
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                placeholder = { Text("Cari nama / NIP / jabatan...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearch("") }) {
                            Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Accent,
                    unfocusedContainerColor = AppColors.Surface,
                    focusedContainerColor = AppColors.Surface,
                )
            )

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

            // Content with pull-to-refresh
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.loadPegawaiList(state.searchQuery.ifBlank { null }) },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.error != null && state.list.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ErrorOutline, null, Modifier.size(48.dp), tint = Color(0xFFEF4444))
                                Spacer(Modifier.height(8.dp))
                                Text(state.error ?: "", color = Color(0xFFEF4444), fontSize = 14.sp)
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = { viewModel.loadPegawaiList() }) { Text("Coba Lagi") }
                            }
                        }
                    }
                    state.list.isEmpty() && !state.isLoading -> {
                        EmptyState(
                            icon = Icons.Default.PeopleOutline,
                            title = "Belum ada data pegawai",
                            subtitle = "Sinkronisasi data terlebih dahulu"
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("${state.list.size} pegawai", fontSize = 12.sp, color = AppColors.TextTertiary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                            }
                            itemsIndexed(state.list, key = { _, it -> it.id }) { index, pegawai ->
                                StaggeredItem(index = index) {
                                    PegawaiCard(pegawai = pegawai, onClick = { onNavigateToDetail(pegawai.id) })
                                }
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PegawaiCard(pegawai: PegawaiDto, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar — foto jika ada, AvatarCircle jika tidak
            Box(Modifier.size(56.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (pegawai.fotoUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(pegawai.fotoUrl).crossfade(300).build(),
                        contentDescription = pegawai.nama,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))
                                )
                            )
                        },
                        error = {
                            AvatarCircle(
                                name = pegawai.nama,
                                size = 56.dp,
                                color = Color(0xFF6366F1)
                            )
                        }
                    )
                } else {
                    AvatarCircle(
                        name = pegawai.nama,
                        size = 56.dp,
                        color = Color(0xFF6366F1)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(pegawai.nama, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!pegawai.nip.isNullOrBlank()) {
                    Text("NIP: ${pegawai.nip}", fontSize = 11.sp, color = AppColors.TextSecondary)
                }
                if (!pegawai.jabatan.isNullOrBlank()) {
                    Text(
                        buildString {
                            append(pegawai.jabatan)
                            if (!pegawai.unit.isNullOrBlank()) append(" • ${pegawai.unit}")
                        },
                        fontSize = 11.sp, color = AppColors.TextTertiary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                // Badges
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pegawai.profesi?.let { BadgeChip(it, Color(0xFF22C55E)) }
                    pegawai.pangkatGol?.let { BadgeChip(it, Color(0xFF6366F1)) }
                    pegawai.pendidikanTerakhir?.let { BadgeChip(it, Color(0xFF06B6D4)) }
                }
            }

            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = AppColors.TextTertiary)
        }
    }
}


@Composable
private fun BadgeChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp, fontWeight = FontWeight.Medium, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
