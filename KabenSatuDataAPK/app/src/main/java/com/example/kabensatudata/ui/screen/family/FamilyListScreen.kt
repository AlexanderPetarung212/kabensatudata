package com.example.kabensatudata.ui.screen.family

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.entity.FamilyEntity
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
class FamilyListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    familyDao: FamilyDao,
    private val familyMemberDao: FamilyMemberDao
) : ViewModel() {
    private val buildingId: Long = savedStateHandle["buildingId"] ?: 0L

    val families = familyDao.getByBuilding(buildingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _memberCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val memberCounts: StateFlow<Map<Long, Int>> = _memberCounts

    fun loadMemberCount(familyId: Long) {
        viewModelScope.launch {
            if (!_memberCounts.value.containsKey(familyId)) {
                val count = familyMemberDao.countByFamily(familyId)
                _memberCounts.value = _memberCounts.value + (familyId to count)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyListScreen(
    buildingId: Long,
    onFamilyClick: (Long) -> Unit,
    onAddFamily: (Long) -> Unit,
    onBack: () -> Unit,
    canEdit: Boolean = true,
    viewModel: FamilyListViewModel = hiltViewModel()
) {
    val families by viewModel.families.collectAsState()
    val memberCounts by viewModel.memberCounts.collectAsState()

    Scaffold(
        topBar = { AppTopBar("Daftar Keluarga", onBack) },
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { onAddFamily(buildingId) },
                    containerColor = AppColors.Accent,
                    modifier = Modifier.padding(bottom = 70.dp)
                ) { Icon(Icons.Default.Add, "Tambah Keluarga", tint = Color.White) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(AppColors.Background).padding(padding)) {
            if (families.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FamilyRestroom, null, Modifier.size(64.dp), tint = AppColors.TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada keluarga", fontSize = 16.sp, color = AppColors.TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(families, key = { it.id }) { family ->
                        viewModel.loadMemberCount(family.id)
                        FamilyItem(family, memberCounts[family.id] ?: 0) { onFamilyClick(family.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun FamilyItem(family: FamilyEntity, memberCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FamilyRestroom, null,
                    tint = AppColors.Accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(family.headName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("No. KK: ${family.familyNumber}", fontSize = 12.sp, color = AppColors.TextSecondary)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DarkBadge("$memberCount anggota", AppColors.Accent)
                    family.hasCleanWater?.let {
                        if (it) DarkBadge("Air ✓", AppColors.Success)
                        else DarkBadge("Air ✕", AppColors.Error)
                    }
                    family.hasToilet?.let {
                        if (it) DarkBadge("Jamban ✓", AppColors.Success)
                        else DarkBadge("Jamban ✕", AppColors.Error)
                    }
                    if (family.hasMentalIllness == true) {
                        DarkBadge("Jiwa !", AppColors.Warning)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (family.pendingSync) {
                    Icon(Icons.Default.CloudUpload, null, tint = AppColors.Warning, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DarkBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
