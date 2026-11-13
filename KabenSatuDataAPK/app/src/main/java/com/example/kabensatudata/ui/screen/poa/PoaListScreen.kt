package com.example.kabensatudata.ui.screen.poa

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.remote.dto.PoaListItem
import com.example.kabensatudata.data.remote.dto.PoaMonthSummary
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import java.text.NumberFormat
import java.util.Locale

private val monthLabels = listOf("Jan","Feb","Mar","Apr","Mei","Jun","Jul","Ags","Sep","Okt","Nov","Des")
private fun tahapColor(tahap: Int?): Color = when(tahap) { 1 -> Color(0xFF22C55E); 2 -> Color(0xFFEAB308); 3 -> Color(0xFFEF4444); else -> Color.Gray }
private fun fmtRp(v: Double): String = NumberFormat.getCurrencyInstance(Locale("id","ID")).format(v).replace("Rp","Rp ").replace(",00","")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoaListScreen(
    viewModel: PoaViewModel,
    onPoaClick: (Long) -> Unit,
    onCreatePoa: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-load more
    val lastVisible = remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(lastVisible.value) {
        if (lastVisible.value >= uiState.poaList.size - 3) viewModel.loadNextPage()
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "POA",
                subtitle = "Perencanaan Operasional Anggaran",
                onBack = onBack
            )
        },
        floatingActionButton = {
            if (uiState.isBokAdmin) {
                FloatingActionButton(onClick = onCreatePoa, containerColor = AppColors.Accent) {
                    Icon(Icons.Default.Add, "Buat POA", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filters
            PoaFilterBar(
                years = uiState.availableYears.ifEmpty { listOf(uiState.selectedYear) },
                selectedYear = uiState.selectedYear,
                komponen = uiState.availableKomponen,
                selectedKomponen = uiState.selectedKomponen,
                searchQuery = uiState.searchQuery,
                onYearChange = viewModel::setYear,
                onKomponenChange = viewModel::setKomponen,
                onSearchChange = viewModel::setSearchQuery
            )

            // List
            if (uiState.isLoading && uiState.poaList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Accent)
                }
            } else if (uiState.error != null && uiState.poaList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, null, tint = AppColors.Error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(uiState.error!!, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadPoaList() }) { Text("Coba Lagi") }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.poaList, key = { it.id }) { poa ->
                        PoaCard(poa = poa, onClick = { onPoaClick(poa.id) })
                    }
                    if (uiState.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AppColors.Accent)
                            }
                        }
                    }
                    item {
                        Text("${uiState.total} POA", fontSize = 11.sp, color = AppColors.TextTertiary,
                            modifier = Modifier.fillMaxWidth().padding(8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoaFilterBar(
    years: List<Int>, selectedYear: Int,
    komponen: List<String>, selectedKomponen: String,
    searchQuery: String,
    onYearChange: (Int) -> Unit, onKomponenChange: (String) -> Unit, onSearchChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Cari kegiatan...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent,
                unfocusedBorderColor = AppColors.CardBorder.copy(alpha = 0.5f),
                focusedContainerColor = AppColors.Surface,
                unfocusedContainerColor = AppColors.Surface
            )
        )
        Spacer(Modifier.height(8.dp))
        // Year + Komponen chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            years.forEach { year ->
                FilterChip(
                    selected = year == selectedYear,
                    onClick = { onYearChange(year) },
                    label = { Text("$year", fontSize = 12.sp) },
                    modifier = Modifier.height(32.dp)
                )
            }
            FilterChip(
                selected = selectedKomponen.isBlank(),
                onClick = { onKomponenChange("") },
                label = { Text("Semua", fontSize = 12.sp) },
                modifier = Modifier.height(32.dp)
            )
            komponen.forEach { k ->
                FilterChip(
                    selected = k == selectedKomponen,
                    onClick = { onKomponenChange(k) },
                    label = { Text(k, fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }
}

@Composable
private fun PoaCard(poa: PoaListItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.CardBorder.copy(alpha = 0.2f)),
        shadowElevation = 2.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title
            Text(poa.kegiatan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = AppColors.Accent.copy(alpha = 0.1f)) {
                    Text(poa.komponen, fontSize = 10.sp, color = AppColors.Accent, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text(fmtRp(poa.planned_total), fontSize = 11.sp, color = AppColors.TextTertiary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            // Month grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (m in 1..12) {
                    val ms = poa.months[m.toString()] ?: PoaMonthSummary()
                    MiniMonthCell(month = m, data = ms)
                }
            }
        }
    }
}

@Composable
private fun MiniMonthCell(month: Int, data: PoaMonthSummary) {
    val color = tahapColor(data.tahap)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(monthLabels[month - 1], fontSize = 7.sp, color = AppColors.TextTertiary)
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape)
                .background(when {
                    data.claimed -> Color(0xFF22C55E).copy(alpha = 0.18f)
                    data.locked -> Color(0xFFEF4444).copy(alpha = 0.1f)
                    data.has_activity -> color.copy(alpha = 0.12f)
                    else -> AppColors.Secondary.copy(alpha = 0.08f)
                })
                .then(when {
                    data.claimed -> Modifier.border(1.5.dp, Color(0xFF22C55E), CircleShape)
                    data.locked -> Modifier.border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), CircleShape)
                    data.has_activity -> Modifier.border(1.dp, color.copy(alpha = 0.4f), CircleShape)
                    else -> Modifier
                }),
            contentAlignment = Alignment.Center
        ) {
            when {
                data.claimed -> Text("✅", fontSize = 8.sp)
                data.locked -> Text("🔒", fontSize = 8.sp)
                data.has_activity -> Text("${data.count}x", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = color)
                else -> Text("—", fontSize = 7.sp, color = AppColors.TextTertiary)
            }
        }
        if (data.is_mine) Text("👤", fontSize = 7.sp)
    }
}
