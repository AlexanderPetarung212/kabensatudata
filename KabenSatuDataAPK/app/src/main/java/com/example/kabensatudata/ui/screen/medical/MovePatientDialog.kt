package com.example.kabensatudata.ui.screen.medical

import android.util.Log
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.window.Dialog
import com.example.kabensatudata.data.local.dao.BuildingDao
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.dao.VillageDao
import com.example.kabensatudata.data.local.entity.BuildingEntity
import com.example.kabensatudata.data.local.entity.FamilyEntity
import com.example.kabensatudata.data.local.entity.FamilyMemberEntity
import com.example.kabensatudata.data.local.entity.VillageEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.MovePatientRequest
import com.example.kabensatudata.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Dialog for moving a patient from "Keluarga Umum" to a real family.
 * Uses cascading dropdowns: Village → Building → Family.
 * Data is loaded from local Room DB (already synced).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovePatientDialog(
    member: FamilyMemberEntity,
    apiService: ApiService,
    villageDao: VillageDao,
    buildingDao: BuildingDao,
    familyDao: FamilyDao,
    onSuccess: (newRmNumber: String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // ── State ──
    var villages by remember { mutableStateOf<List<VillageEntity>>(emptyList()) }
    var buildings by remember { mutableStateOf<List<BuildingEntity>>(emptyList()) }
    var families by remember { mutableStateOf<List<FamilyEntity>>(emptyList()) }

    var selectedVillage by remember { mutableStateOf<VillageEntity?>(null) }
    var selectedBuilding by remember { mutableStateOf<BuildingEntity?>(null) }
    var selectedFamily by remember { mutableStateOf<FamilyEntity?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successData by remember { mutableStateOf<Pair<String, String>?>(null) } // old, new RM

    // Dropdown expanded state
    var villageExpanded by remember { mutableStateOf(false) }
    var buildingExpanded by remember { mutableStateOf(false) }
    var familyExpanded by remember { mutableStateOf(false) }

    // ── Load villages on mount ──
    LaunchedEffect(Unit) {
        // Exclude "Pasien Umum" village (code 0000000000)
        villages = villageDao.getAllSuspend().filter { it.code != "0000000000" }
    }

    // ── Load buildings when village changes ──
    LaunchedEffect(selectedVillage) {
        selectedBuilding = null
        selectedFamily = null
        buildings = emptyList()
        families = emptyList()
        selectedVillage?.let {
            buildings = buildingDao.getByVillageSuspend(it.id)
        }
    }

    // ── Load families when building changes ──
    LaunchedEffect(selectedBuilding) {
        selectedFamily = null
        families = emptyList()
        selectedBuilding?.let {
            families = familyDao.getByBuildingSuspend(it.id)
        }
    }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.Surface,
            border = BorderStroke(1.dp, AppColors.CardBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // ── Header ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz, null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Pindahkan Pasien",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            "ke Keluarga Terdaftar",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Patient Info ──
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Background,
                    border = BorderStroke(1.dp, AppColors.CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person, null,
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                member.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                "RM: ${member.rmNumber ?: "—"}",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Warning ──
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning, null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "No. Rekam Medis akan berubah setelah dipindahkan. Semua catatan rekam medis tetap terhubung.",
                            fontSize = 12.sp,
                            color = Color(0xFFF59E0B),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Success state ──
                if (successData != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Berhasil Dipindahkan!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF10B981)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "RM Lama: ${successData!!.first}",
                                fontSize = 13.sp,
                                color = AppColors.TextSecondary
                            )
                            Text(
                                "RM Baru: ${successData!!.second}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onSuccess(successData!!.second) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Selesai", fontWeight = FontWeight.SemiBold)
                    }
                    return@Column
                }

                // ── Step 1: Pilih Desa ──
                Text(
                    "Step 1: Pilih Desa",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = villageExpanded,
                    onExpandedChange = { villageExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedVillage?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Pilih desa...", fontSize = 14.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = villageExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = AppColors.CardBorder,
                            focusedContainerColor = AppColors.Background,
                            unfocusedContainerColor = AppColors.Background,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedPlaceholderColor = AppColors.TextTertiary,
                            unfocusedPlaceholderColor = AppColors.TextTertiary
                        ),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = villageExpanded,
                        onDismissRequest = { villageExpanded = false },
                        modifier = Modifier.background(AppColors.Surface)
                    ) {
                        villages.forEach { village ->
                            DropdownMenuItem(
                                text = {
                                    Text(village.name, color = AppColors.TextPrimary, fontSize = 14.sp)
                                },
                                onClick = {
                                    selectedVillage = village
                                    villageExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Step 2: Pilih Bangunan ──
                Text(
                    "Step 2: Pilih Bangunan",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedVillage != null) AppColors.TextSecondary else AppColors.TextTertiary,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = buildingExpanded && selectedVillage != null,
                    onExpandedChange = { if (selectedVillage != null) buildingExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedBuilding?.let { "${it.buildingNumber}${if (!it.description.isNullOrEmpty()) " — ${it.description}" else ""}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedVillage != null,
                        placeholder = { Text("Pilih bangunan...", fontSize = 14.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = buildingExpanded && selectedVillage != null) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = AppColors.CardBorder,
                            focusedContainerColor = AppColors.Background,
                            unfocusedContainerColor = AppColors.Background,
                            disabledContainerColor = AppColors.Background.copy(alpha = 0.5f),
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            disabledTextColor = AppColors.TextTertiary,
                            focusedPlaceholderColor = AppColors.TextTertiary,
                            unfocusedPlaceholderColor = AppColors.TextTertiary,
                            disabledPlaceholderColor = AppColors.TextTertiary,
                            disabledBorderColor = AppColors.CardBorder.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = buildingExpanded && selectedVillage != null,
                        onDismissRequest = { buildingExpanded = false },
                        modifier = Modifier.background(AppColors.Surface)
                    ) {
                        buildings.forEach { building ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(building.buildingNumber, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        if (!building.description.isNullOrEmpty()) {
                                            Text(building.description, color = AppColors.TextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    selectedBuilding = building
                                    buildingExpanded = false
                                }
                            )
                        }
                        if (buildings.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Tidak ada bangunan di desa ini", color = AppColors.TextTertiary, fontSize = 13.sp) },
                                onClick = {},
                                enabled = false
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Step 3: Pilih Keluarga ──
                Text(
                    "Step 3: Pilih Keluarga",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedBuilding != null) AppColors.TextSecondary else AppColors.TextTertiary,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = familyExpanded && selectedBuilding != null,
                    onExpandedChange = { if (selectedBuilding != null) familyExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedFamily?.let { "KK ${it.headName}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedBuilding != null,
                        placeholder = { Text("Pilih keluarga...", fontSize = 14.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = familyExpanded && selectedBuilding != null) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = AppColors.CardBorder,
                            focusedContainerColor = AppColors.Background,
                            unfocusedContainerColor = AppColors.Background,
                            disabledContainerColor = AppColors.Background.copy(alpha = 0.5f),
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            disabledTextColor = AppColors.TextTertiary,
                            focusedPlaceholderColor = AppColors.TextTertiary,
                            unfocusedPlaceholderColor = AppColors.TextTertiary,
                            disabledPlaceholderColor = AppColors.TextTertiary,
                            disabledBorderColor = AppColors.CardBorder.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = familyExpanded && selectedBuilding != null,
                        onDismissRequest = { familyExpanded = false },
                        modifier = Modifier.background(AppColors.Surface)
                    ) {
                        families.forEach { family ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("KK ${family.headName}", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text("No: ${family.familyNumber}", color = AppColors.TextSecondary, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    selectedFamily = family
                                    familyExpanded = false
                                }
                            )
                        }
                        if (families.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Tidak ada keluarga di bangunan ini", color = AppColors.TextTertiary, fontSize = 13.sp) },
                                onClick = {},
                                enabled = false
                            )
                        }
                    }
                }

                // ── Error ──
                if (errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                    ) {
                        Text(
                            errorMessage!!,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Buttons ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AppColors.CardBorder)
                    ) {
                        Text("Batal", color = AppColors.TextSecondary)
                    }

                    Button(
                        onClick = {
                            val targetFamilyId = selectedFamily?.id ?: return@Button
                            isLoading = true
                            errorMessage = null

                            scope.launch {
                                try {
                                    val response = apiService.movePatient(
                                        id = member.id,
                                        request = MovePatientRequest(familyId = targetFamilyId)
                                    )

                                    if (response.isSuccessful) {
                                        val data = response.body()?.data
                                        if (data != null) {
                                            successData = Pair(data.oldRmNumber, data.newRmNumber)
                                        }
                                    } else {
                                        val errorBody = response.errorBody()?.string()
                                        errorMessage = when (response.code()) {
                                            404 -> "Pasien tidak ditemukan di server."
                                            422 -> {
                                                // Try to parse message from response
                                                try {
                                                    val msg = com.google.gson.Gson()
                                                        .fromJson(errorBody, Map::class.java)["message"] as? String
                                                    msg ?: "Data tidak valid."
                                                } catch (_: Exception) {
                                                    "Data tidak valid."
                                                }
                                            }
                                            else -> "Gagal memindahkan pasien. HTTP ${response.code()}"
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MovePatient", "Error moving patient", e)
                                    errorMessage = "Koneksi gagal. Pastikan terhubung ke internet."
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f).height(46.dp),
                        enabled = selectedFamily != null && !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6),
                            disabledContainerColor = AppColors.Surface
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Memproses...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pindahkan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
