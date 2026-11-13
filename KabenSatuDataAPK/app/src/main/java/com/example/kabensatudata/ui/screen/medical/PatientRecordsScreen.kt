package com.example.kabensatudata.ui.screen.medical

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.dao.MemberWithRecordCount
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.ui.theme.AppColors
import java.time.LocalDate
import java.time.Period

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientRecordsScreen(
    onMemberClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MedicalHubViewModel = hiltViewModel(),
    apiService: ApiService,
    familyMemberDao: FamilyMemberDao,
    familyDao: FamilyDao
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var showQuickRegister by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
            // Header
            item {
                Surface(Modifier.fillMaxWidth(), color = AppColors.PrimaryDark) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Rekam Medis", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Riwayat kunjungan dan diagnosis pasien", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cari nama pasien atau NIK…", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged(""); focusManager.clearFocus() }) {
                                        Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true, shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                focusedBorderColor = AppColors.Accent.copy(alpha = 0.5f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                cursorColor = AppColors.Accent,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                    }
                }
            }

            // Stats
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip("${uiState.totalRecords} Kunjungan", Icons.Default.Assignment, Color(0xFF3B82F6), Modifier.weight(1f))
                    StatChip("${uiState.totalPatients} Pasien", Icons.Default.Person, Color(0xFF10B981), Modifier.weight(1f))
                }
            }

            // Section header
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pasien dengan Rekam Medis", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                    Text("${uiState.members.size} pasien", fontSize = 12.sp, color = AppColors.TextTertiary)
                }
            }

            // Loading
            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                    }
                }
            } else if (uiState.members.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, tint = AppColors.TextTertiary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (uiState.searchQuery.length >= 2) "Pasien tidak ditemukan" else "Belum ada rekam medis",
                                fontSize = 14.sp, color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(uiState.members, key = { it.memberId }) { member ->
                    PatientCardItem(member = member, onClick = { onMemberClick(member.memberId) })
                }
            }
        }

        // ─── FAB: Pasien Baru ─────────────────────────────
        ExtendedFloatingActionButton(
            onClick = { showQuickRegister = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 16.dp, end = 16.dp, bottom = 80.dp),
            containerColor = Color(0xFF0EA5E9),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pasien Baru", fontWeight = FontWeight.SemiBold)
        }
    }

    // ─── Quick Register Dialog ────────────────────────────
    if (showQuickRegister) {
        QuickRegisterDialog(
            apiService = apiService,
            familyMemberDao = familyMemberDao,
            familyDao = familyDao,
            onDismiss = { showQuickRegister = false },
            onPatientRegistered = { memberId ->
                showQuickRegister = false
                onMemberClick(memberId)
            }
        )
    }
}

@Composable
private fun StatChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 2.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        }
    }
}

@Composable
private fun PatientCardItem(member: MemberWithRecordCount, onClick: () -> Unit) {
    val age = member.memberBirthDate?.let {
        try { Period.between(LocalDate.parse(it.take(10)), LocalDate.now()).years } catch (_: Exception) { null }
    }
    val genderColor = when (member.memberGender) {
        "Laki-laki" -> Color(0xFF3B82F6); "Perempuan" -> Color(0xFFEC4899); else -> AppColors.TextTertiary
    }

    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp,
        border = BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(genderColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(
                    if (member.memberGender == "Perempuan") Icons.Default.Female else Icons.Default.Male,
                    null, tint = genderColor, modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(member.memberName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (age != null) Text("$age tahun", fontSize = 12.sp, color = AppColors.TextSecondary)
                    if (member.memberGender != null) Text(member.memberGender, fontSize = 12.sp, color = genderColor)
                }
                Text("NIK: ${member.memberNik ?: "-"}", fontSize = 11.sp, color = AppColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = RoundedCornerShape(8.dp), color = AppColors.Accent.copy(alpha = 0.12f)) {
                    Text("${member.recordCount} kunjungan", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
                }
                if (member.lastVisitDate != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(formatDateLocal(member.lastVisitDate), fontSize = 10.sp, color = AppColors.TextTertiary)
                }
            }
        }
    }
}

private val BULAN_ID = arrayOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")

private fun formatDateLocal(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return "—"
    return try {
        val d = LocalDate.parse(dateStr.take(10))
        "${d.dayOfMonth} ${BULAN_ID[d.monthValue - 1]} ${d.year}"
    } catch (_: Exception) { dateStr.take(10) }
}
