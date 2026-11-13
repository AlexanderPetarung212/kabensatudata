package com.example.kabensatudata.ui.screen.building

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.BuildingDao
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.entity.BuildingEntity
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.AppTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BuildingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    buildingDao: BuildingDao,
    private val familyDao: FamilyDao
) : ViewModel() {
    private val villageId: Long = savedStateHandle["villageId"] ?: 0L

    val buildings = buildingDao.getByVillage(villageId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _familyCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val familyCounts: StateFlow<Map<Long, Int>> = _familyCounts

    fun loadFamilyCount(buildingId: Long) {
        viewModelScope.launch {
            if (!_familyCounts.value.containsKey(buildingId)) {
                val count = familyDao.countByBuilding(buildingId)
                _familyCounts.value = _familyCounts.value + (buildingId to count)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingListScreen(
    villageId: Long,
    onBuildingClick: (Long) -> Unit,
    onAddBuilding: (Long) -> Unit,
    onEditBuilding: (Long, Long) -> Unit,
    onBack: () -> Unit,
    canEdit: Boolean = true,
    viewModel: BuildingListViewModel = hiltViewModel()
) {
    val buildings by viewModel.buildings.collectAsState()
    val familyCounts by viewModel.familyCounts.collectAsState()

    Scaffold(
        topBar = { AppTopBar("Daftar Bangunan", onBack) },
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { onAddBuilding(villageId) },
                    containerColor = AppColors.Accent,
                    modifier = Modifier.padding(bottom = 70.dp)
                ) {
                    Icon(Icons.Default.Add, "Tambah Bangunan", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(padding)
        ) {
            if (buildings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Home, null, Modifier.size(64.dp), tint = AppColors.TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada bangunan", fontSize = 16.sp, color = AppColors.TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(buildings, key = { it.id }) { building ->
                        viewModel.loadFamilyCount(building.id)
                        BuildingItem(
                            building = building,
                            familyCount = familyCounts[building.id] ?: 0,
                            onClick = { onBuildingClick(building.id) },
                            onEdit = if (canEdit) {{ onEditBuilding(villageId, building.id) }} else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BuildingItem(building: BuildingEntity, familyCount: Int, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    val hasCoords = building.latitude != null && building.longitude != null

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Warning.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Home, null, tint = AppColors.Warning, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bangunan ${building.buildingNumber}", fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, color = AppColors.TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$familyCount keluarga", fontSize = 12.sp, color = AppColors.TextSecondary)
                    Text(" · ", fontSize = 12.sp, color = AppColors.TextTertiary)
                    if (hasCoords) {
                        Icon(Icons.Default.LocationOn, null,
                            tint = AppColors.Success, modifier = Modifier.size(14.dp))
                        Text(
                            "${String.format("%.4f", building.latitude)}, ${String.format("%.4f", building.longitude)}",
                            fontSize = 11.sp, color = AppColors.TextSecondary
                        )
                    } else {
                        Icon(Icons.Default.LocationOff, null,
                            tint = AppColors.Error, modifier = Modifier.size(14.dp))
                        Text("Belum ada GPS", fontSize = 11.sp, color = AppColors.Error)
                    }
                }
            }
            if (building.pendingSync) {
                Icon(Icons.Default.CloudUpload, "Pending sync",
                    tint = AppColors.Warning, modifier = Modifier.size(18.dp))
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
