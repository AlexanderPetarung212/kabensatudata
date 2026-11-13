package com.example.kabensatudata.ui.screen.village

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.BuildingDao
import com.example.kabensatudata.data.local.dao.VillageDao
import com.example.kabensatudata.data.local.entity.VillageEntity
import com.example.kabensatudata.ui.components.EmptyState
import com.example.kabensatudata.ui.components.StaggeredItem
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VillageListViewModel @Inject constructor(
    villageDao: VillageDao,
    private val buildingDao: BuildingDao
) : ViewModel() {

    val villages = villageDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _buildingCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val buildingCounts: StateFlow<Map<Long, Int>> = _buildingCounts

    fun loadBuildingCount(villageId: Long) {
        if (_buildingCounts.value.containsKey(villageId)) return
        viewModelScope.launch {
            val count = buildingDao.countByVillage(villageId)
            _buildingCounts.value = _buildingCounts.value + (villageId to count)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VillageListScreen(
    onVillageClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: VillageListViewModel = hiltViewModel()
) {
    val villages by viewModel.villages.collectAsState()
    val buildingCounts by viewModel.buildingCounts.collectAsState()

    Scaffold(
        topBar = {
            WaveTopBar(
                title = "Daftar Desa",
                subtitle = "Data wilayah kerja Puskesmas",
                onBack = onBack
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
        ) {
            if (villages.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.CloudDownload,
                    title = "Belum ada data desa",
                    subtitle = "Sinkronisasi data terlebih dahulu"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(villages, key = { _, v -> v.id }) { index, village ->
                        viewModel.loadBuildingCount(village.id)
                        StaggeredItem(index = index) {
                            VillageItem(
                                village = village,
                                buildingCount = buildingCounts[village.id] ?: 0,
                                onClick = { onVillageClick(village.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VillageItem(village: VillageEntity, buildingCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Success.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationCity, null,
                    tint = AppColors.Success, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(village.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!village.district.isNullOrEmpty()) {
                        Text("Kec. ${village.district}", fontSize = 12.sp, color = AppColors.TextSecondary)
                        Text(" · ", fontSize = 12.sp, color = AppColors.TextTertiary)
                    }
                    Text("$buildingCount bangunan", fontSize = 12.sp, color = AppColors.TextSecondary)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
