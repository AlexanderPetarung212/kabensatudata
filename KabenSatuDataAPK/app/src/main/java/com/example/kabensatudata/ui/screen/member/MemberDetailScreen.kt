package com.example.kabensatudata.ui.screen.member

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.*
import com.example.kabensatudata.data.local.entity.*
import com.example.kabensatudata.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ──────────────────────────────────────────
@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memberDao: FamilyMemberDao,
    private val familyDao: FamilyDao,
    private val buildingDao: BuildingDao,
    private val villageDao: VillageDao,
    private val medicalRecordDao: MedicalRecordDao
) : ViewModel() {
    private val memberId: Long = savedStateHandle["memberId"] ?: 0L

    private val _member = MutableStateFlow<FamilyMemberEntity?>(null)
    val member: StateFlow<FamilyMemberEntity?> = _member

    private val _family = MutableStateFlow<FamilyEntity?>(null)
    val family: StateFlow<FamilyEntity?> = _family

    private val _village = MutableStateFlow<VillageEntity?>(null)
    val village: StateFlow<VillageEntity?> = _village

    private val _building = MutableStateFlow<BuildingEntity?>(null)
    val building: StateFlow<BuildingEntity?> = _building

    private val _recordCount = MutableStateFlow(0)
    val recordCount: StateFlow<Int> = _recordCount

    init {
        viewModelScope.launch {
            val m = memberDao.getById(memberId)
            _member.value = m
            if (m != null) {
                val f = familyDao.getById(m.familyId)
                _family.value = f
                if (f != null) {
                    val b = buildingDao.getById(f.buildingId)
                    _building.value = b
                    if (b != null) {
                        _village.value = villageDao.getById(b.villageId)
                    }
                }
                medicalRecordDao.getByMember(m.id).collect { records ->
                    _recordCount.value = records.size
                }
            }
        }
    }
}

// ─── Screen ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    memberId: Long,
    onNavigateToMedicalRecords: (Long) -> Unit,
    onEditMember: (Long, Long) -> Unit,
    onBack: () -> Unit,
    canEdit: Boolean = true,
    viewModel: MemberDetailViewModel = hiltViewModel()
) {
    val member by viewModel.member.collectAsState()
    val family by viewModel.family.collectAsState()
    val village by viewModel.village.collectAsState()
    val building by viewModel.building.collectAsState()
    val recordCount by viewModel.recordCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(Modifier.fillMaxSize().background(AppColors.Background)) {
            if (member == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Accent)
                }
                return@Scaffold
            }

            val m = member!!
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // ── Header ──────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(
                                AppColors.Accent.copy(alpha = 0.3f),
                                Color.Transparent
                            ))
                        )
                        .padding(top = padding.calculateTopPadding())
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(56.dp).clip(CircleShape)
                                    .background(AppColors.Accent.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (m.gender == "Laki-laki") Icons.Default.Man else Icons.Default.Woman,
                                    null, tint = Color.White, modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(m.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${m.relationship} · ${calculateAge(m.birthDate)} · ${m.gender}",
                                    color = AppColors.TextSecondary, fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            if (!m.nik.isNullOrEmpty()) HeaderBadge("NIK: ${m.nik}")
                            if (!m.rmNumber.isNullOrEmpty()) HeaderBadge("RM: ${m.rmNumber}")
                            if (m.pendingSync) HeaderBadge("⏳ Pending Sync")
                            if (m.nikVerifiedStatus == "verified") NikVerifiedBadge()
                        }
                    }
                }

                // ── Action Buttons ───────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onNavigateToMedicalRecords(memberId) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success)
                    ) {
                        Icon(Icons.Default.MedicalServices, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rekam Medis ($recordCount)")
                    }
                    if (canEdit) {
                        OutlinedButton(
                            onClick = { onEditMember(m.familyId, memberId) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Accent),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Data")
                        }
                    }
                }

                // ── Content Cards ────────────────────────
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoSection("Data Pribadi", Icons.Default.Person, AppColors.Accent) {
                        InfoRow("Nama", m.name)
                        InfoRow("NIK", m.nik ?: "-")
                        NikVerificationRow(m.nikVerifiedStatus, m.nikVerifiedAt)
                        if (!m.rmNumber.isNullOrEmpty()) InfoRow("No. RM", m.rmNumber)
                        InfoRow("Tempat Lahir", m.birthPlace ?: "-")
                        InfoRow("Tgl Lahir", m.birthDate ?: "-")
                        InfoRow("Jenis Kelamin", m.gender)
                        InfoRow("Status", m.maritalStatus ?: "-")
                        InfoRow("Agama", m.religion ?: "-")
                        InfoRow("Pendidikan", m.education ?: "-")
                        InfoRow("Pekerjaan", m.occupation ?: "-")
                    }

                    InfoSection("Keluarga & Lokasi", Icons.Default.Home, AppColors.Success) {
                        InfoRow("Kepala Keluarga", family?.headName ?: "-")
                        InfoRow("Hubungan", m.relationship)
                        InfoRow("Desa", village?.name ?: "-")
                        InfoRow("No. Bangunan", building?.buildingNumber ?: "-")
                        InfoRow("No. Keluarga", family?.familyNumber ?: "-")
                    }

                    InfoSection("Informasi Kesehatan", Icons.Default.Favorite, AppColors.Error) {
                        Text("Status Kesehatan", fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
                        HealthStatusItem("Memiliki JKN", m.hasJkn == true, positiveIsGood = true)
                        HealthStatusItem(
                            if (m.isSmoker == true) "Perokok" else "Tidak Merokok",
                            m.isSmoker != true, positiveIsGood = true
                        )
                        HealthStatusItem(
                            buildString {
                                append(if (m.hasTuberculosis == true) "TBC" else "Tidak TBC")
                                if (m.hasTuberculosis == true)
                                    append(if (m.takesTbMedicationRegularly == true) " (Obat Teratur)" else " (Tidak Teratur)")
                            },
                            m.hasTuberculosis != true, positiveIsGood = true
                        )
                        HealthStatusItem(
                            buildString {
                                append(if (m.hasHypertension == true) "Hipertensi" else "Tidak Hipertensi")
                                if (m.hasHypertension == true)
                                    append(if (m.takesHypertensionMedicationRegularly == true) " (Obat Teratur)" else " (Tidak Teratur)")
                            },
                            m.hasHypertension != true, positiveIsGood = true
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Status Khusus", fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
                        if (m.gender == "Perempuan" && m.isPregnant == true)
                            SpecialStatusItem("Sedang Hamil", Icons.Default.PregnantWoman, AppColors.Warning)
                        if (m.usesContraception == true)
                            SpecialStatusItem("Menggunakan KB", Icons.Default.CheckCircle, AppColors.Success)
                        if (m.gaveBirthInHealthFacility == true)
                            SpecialStatusItem("Melahirkan di Faskes", Icons.Default.LocalHospital, AppColors.Success)
                        if (m.exclusiveBreastfeeding == true)
                            SpecialStatusItem("ASI Eksklusif", Icons.Default.CheckCircle, AppColors.Success)
                        if (m.completeImmunization == true)
                            SpecialStatusItem("Imunisasi Lengkap", Icons.Default.Vaccines, AppColors.Success)
                        if (m.growthMonitoring == true)
                            SpecialStatusItem("Pemantauan Pertumbuhan", Icons.Default.TrendingUp, AppColors.Success)

                        Spacer(Modifier.height(12.dp))
                        Text("Sanitasi", fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
                        val f = family
                        if (f != null) {
                            HealthStatusItem(
                                buildString {
                                    append(if (f.hasCleanWater == true) "Air Bersih" else "Tidak Ada Air Bersih")
                                    if (f.hasCleanWater == true)
                                        append(if (f.isWaterProtected == true) " (Terlindungi)" else " (Tidak Terlindungi)")
                                },
                                f.hasCleanWater == true, positiveIsGood = true
                            )
                            HealthStatusItem(
                                buildString {
                                    append(if (f.hasToilet == true) "Jamban" else "Tidak Ada Jamban")
                                    if (f.hasToilet == true)
                                        append(if (f.isToiletSanitary == true) " (Saniter)" else " (Tidak Saniter)")
                                },
                                f.hasToilet == true, positiveIsGood = true
                            )
                            HealthStatusItem(
                                buildString {
                                    append(if (f.hasMentalIllness == true) "Ada Gangguan Jiwa" else "Tidak Ada Gangguan Jiwa")
                                    if (f.hasMentalIllness == true)
                                        append(if (f.takesMedicationRegularly == true) " (Berobat Teratur)" else " (Tidak Teratur)")
                                },
                                f.hasMentalIllness != true, positiveIsGood = true
                            )
                        }
                    }
                }

                Spacer(Modifier.height(90.dp)) // Room for bottom nav
            }
        }
    }
}

// ─── Helper composables ─────────────────────────────────
@Composable
fun HeaderBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppColors.CardBorder
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            color = AppColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun InfoSection(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CardBorder)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = accentColor)
            }
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = AppColors.TextTertiary, fontSize = 13.sp,
            modifier = Modifier.width(110.dp))
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp,
            color = AppColors.TextPrimary)
    }
}

@Composable
fun HealthStatusItem(label: String, isPositive: Boolean, positiveIsGood: Boolean) {
    val isGood = if (positiveIsGood) isPositive else !isPositive
    val color = if (isGood) AppColors.Success else AppColors.Error
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isGood) "✓" else "✕", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = AppColors.TextPrimary)
    }
}

@Composable
fun SpecialStatusItem(label: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = AppColors.TextPrimary)
    }
}

// ─── NIK Verification Composables ───────────────────────
@Composable
fun NikVerifiedBadge() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppColors.Success.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Verified, null,
                tint = AppColors.Success, modifier = Modifier.size(12.dp)
            )
            Text(
                "Terverifikasi SATUSEHAT",
                color = AppColors.Success, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun NikVerificationRow(status: String?, verifiedAt: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Verifikasi NIK", color = AppColors.TextTertiary, fontSize = 13.sp,
            modifier = Modifier.width(110.dp)
        )
        when (status) {
            "verified" -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Success.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Verified, null,
                            tint = AppColors.Success, modifier = Modifier.size(14.dp)
                        )
                        Text(
                            buildString {
                                append("Terverifikasi SATUSEHAT")
                                if (!verifiedAt.isNullOrEmpty()) {
                                    append(" · ${verifiedAt.take(10)}")
                                }
                            },
                            color = AppColors.Success,
                            fontSize = 12.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            "ihs_only" -> {
                Text("🟡 IHS Terdaftar", color = Color(0xFFF59E0B), fontSize = 13.sp,
                    fontWeight = FontWeight.Medium)
            }
            else -> {
                Text("⚪ Belum Terverifikasi", color = AppColors.TextTertiary, fontSize = 13.sp)
            }
        }
    }
}

// ─── Utility ────────────────────────────────────────────
private fun calculateAge(birthDate: String?): String {
    if (birthDate.isNullOrEmpty()) return "-"
    return try {
        val parts = birthDate.split("-")
        if (parts.size >= 3) {
            val year = parts[0].toInt()
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            "${currentYear - year} thn"
        } else birthDate
    } catch (_: Exception) { birthDate }
}
