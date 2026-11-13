package com.example.kabensatudata.ui.screen.member

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.entity.FamilyMemberEntity
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.AppTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private val MaleColor = Color(0xFF42A5F5)
private val FemaleColor = Color(0xFFEC407A)

@HiltViewModel
class MemberListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    familyMemberDao: FamilyMemberDao
) : ViewModel() {
    private val familyId: Long = savedStateHandle["familyId"] ?: 0L

    val members = familyMemberDao.getByFamily(familyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberListScreen(
    familyId: Long,
    onMemberClick: (Long) -> Unit,
    onAddMember: (Long) -> Unit,
    onBack: () -> Unit,
    canEdit: Boolean = true,
    viewModel: MemberListViewModel = hiltViewModel()
) {
    val members by viewModel.members.collectAsState()

    Scaffold(
        topBar = { AppTopBar("Anggota Keluarga", onBack) },
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { onAddMember(familyId) },
                    containerColor = AppColors.Accent,
                    modifier = Modifier.padding(bottom = 70.dp)
                ) { Icon(Icons.Default.PersonAdd, "Tambah Anggota", tint = Color.White) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(AppColors.Background).padding(padding)) {
            if (members.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.People, null, Modifier.size(64.dp), tint = AppColors.TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada anggota", fontSize = 16.sp, color = AppColors.TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(members, key = { it.id }) { member ->
                        MemberItem(member) { onMemberClick(member.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun MemberItem(member: FamilyMemberEntity, onClick: () -> Unit) {
    val isMale = member.gender == "Laki-laki"
    val genderColor = if (isMale) MaleColor else FemaleColor
    val age = calculateMemberAge(member.birthDate)

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
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(genderColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isMale) Icons.Default.Man else Icons.Default.Woman,
                    null, tint = genderColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.relationship, fontSize = 12.sp, color = AppColors.TextSecondary)
                    if (age.isNotEmpty()) {
                        Text(" · ", fontSize = 12.sp, color = AppColors.TextTertiary)
                        Text(age, fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                    if (!member.education.isNullOrEmpty()) {
                        Text(" · ", fontSize = 12.sp, color = AppColors.TextTertiary)
                        Text(member.education, fontSize = 12.sp, color = AppColors.TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (member.hasJkn == true) HealthChip("JKN", AppColors.Success)
                    if (member.isSmoker == true) HealthChip("Perokok", AppColors.Error)
                    if (member.hasTuberculosis == true) HealthChip("TB", AppColors.Error)
                    if (member.hasHypertension == true) HealthChip("HT", AppColors.Error)
                    if (member.isPregnant == true) HealthChip("Hamil", AppColors.Warning)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (member.pendingSync) {
                    Icon(Icons.Default.CloudUpload, null, tint = AppColors.Warning, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(8.dp))
                Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun HealthChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun calculateMemberAge(birthDate: String?): String {
    if (birthDate.isNullOrEmpty()) return ""
    return try {
        val parts = birthDate.split("-")
        if (parts.size >= 3) {
            val year = parts[0].toInt()
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            "${currentYear - year} thn"
        } else ""
    } catch (_: Exception) { "" }
}
