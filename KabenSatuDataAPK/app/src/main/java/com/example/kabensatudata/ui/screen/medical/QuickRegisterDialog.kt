package com.example.kabensatudata.ui.screen.medical

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.appTextFieldColors
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.entity.FamilyMemberEntity
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.QuickRegisterRequest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// ─── State ────────────────────────────────────────────

data class QuickRegisterState(
    val name: String = "",
    val gender: String = "Laki-laki",
    val birthDate: String = "",
    val nik: String = "",
    val birthPlace: String = "",
    val address: String = "",
    val occupation: String = "",
    val religion: String = "",
    val hasJkn: Boolean = false,
    // UI states
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val duplicateMessage: String? = null,
    val duplicateMemberId: Long? = null,
    val successMemberId: Long? = null,
    val showDatePicker: Boolean = false
)

// ─── Quick Register Dialog ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRegisterDialog(
    apiService: ApiService,
    familyMemberDao: FamilyMemberDao,
    familyDao: FamilyDao,
    onDismiss: () -> Unit,
    onPatientRegistered: (memberId: Long) -> Unit
) {
    var state by remember { mutableStateOf(QuickRegisterState()) }
    val scope = rememberCoroutineScope()

    // Date picker
    val datePickerState = rememberDatePickerState()

    Dialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = AppColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ─── Header ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF0EA5E9),
                                    Color(0xFF6366F1)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Registrasi Pasien Baru",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "Daftarkan pasien tanpa data kependudukan",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { if (!state.isSubmitting) onDismiss() }) {
                                Icon(Icons.Default.Close, "Tutup", tint = Color.White)
                            }
                        }
                    }
                }

                // ─── Form ───────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Error message
                    state.error?.let { error ->
                        Surface(
                            color = Color(0x22F44336),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(error, color = Color(0xFFF44336), fontSize = 13.sp)
                            }
                        }
                    }

                    // Duplicate notification
                    state.duplicateMessage?.let { msg ->
                        Surface(
                            color = Color(0x22FF9800),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(msg, color = Color(0xFFFF9800), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        state.duplicateMemberId?.let { onPatientRegistered(it) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gunakan Pasien Ini")
                                }
                            }
                        }
                    }

                    // ─── Required Fields ─────────────────────

                    Text(
                        "WAJIB DIISI",
                        color = AppColors.TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )

                    // Nama
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { state = state.copy(name = it, error = null, duplicateMessage = null) },
                        label = { Text("Nama Lengkap") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Gender
                    Text("Jenis Kelamin", color = AppColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Laki-laki", "Perempuan").forEach { g ->
                            val selected = state.gender == g
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { state = state.copy(gender = g) },
                                color = if (selected) Color(0xFF0EA5E9).copy(alpha = 0.15f) else AppColors.Background,
                                shape = RoundedCornerShape(12.dp),
                                border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF0EA5E9)) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        if (g == "Laki-laki") Icons.Default.Male else Icons.Default.Female,
                                        null,
                                        tint = if (selected) Color(0xFF0EA5E9) else AppColors.TextTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        g,
                                        color = if (selected) Color(0xFF0EA5E9) else AppColors.TextSecondary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = AppColors.TextTertiary.copy(alpha = 0.2f))

                    // ─── Optional Fields ─────────────────────

                    Text(
                        "DATA TAMBAHAN",
                        color = AppColors.TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )

                    // Tanggal Lahir with date picker
                    OutlinedTextField(
                        value = state.birthDate,
                        onValueChange = {},
                        label = { Text("Tanggal Lahir") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state = state.copy(showDatePicker = true) },
                        enabled = false,
                        colors = appTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Alamat
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = { state = state.copy(address = it) },
                        label = { Text("Alamat") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // NIK
                    OutlinedTextField(
                        value = state.nik,
                        onValueChange = { if (it.length <= 16 && it.all(Char::isDigit)) state = state.copy(nik = it) },
                        label = { Text("NIK (16 digit)") },
                        leadingIcon = { Icon(Icons.Default.Badge, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = if (state.nik.isNotEmpty() && state.nik.length != 16) {
                            { Text("NIK harus 16 digit", color = Color(0xFFFF9800)) }
                        } else null
                    )

                    // Tempat Lahir
                    OutlinedTextField(
                        value = state.birthPlace,
                        onValueChange = { state = state.copy(birthPlace = it) },
                        label = { Text("Tempat Lahir") },
                        leadingIcon = { Icon(Icons.Default.Place, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Pekerjaan
                    OutlinedTextField(
                        value = state.occupation,
                        onValueChange = { state = state.copy(occupation = it) },
                        label = { Text("Pekerjaan") },
                        leadingIcon = { Icon(Icons.Default.Work, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = appTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Peserta JKN/BPJS
                    Surface(
                        color = AppColors.Background,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HealthAndSafety, null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Peserta JKN/BPJS", color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
                            Switch(
                                checked = state.hasJkn,
                                onCheckedChange = { state = state.copy(hasJkn = it) },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color(0xFF0EA5E9),
                                    checkedThumbColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // ─── Bottom Action ──────────────────────────
                Surface(
                    color = AppColors.Surface,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Button(
                            onClick = {
                                // Validate
                                if (state.name.isBlank()) {
                                    state = state.copy(error = "Nama pasien harus diisi")
                                    return@Button
                                }
                                if (state.nik.isNotEmpty() && state.nik.length != 16) {
                                    state = state.copy(error = "NIK harus 16 digit")
                                    return@Button
                                }

                                state = state.copy(isSubmitting = true, error = null, duplicateMessage = null)

                                scope.launch {
                                    try {
                                        // Try API first (online)
                                        val request = QuickRegisterRequest(
                                            name = state.name.trim(),
                                            gender = state.gender,
                                            birthDate = state.birthDate.ifBlank { null },
                                            nik = state.nik.ifBlank { null },
                                            birthPlace = state.birthPlace.ifBlank { null },
                                            address = state.address.ifBlank { null },
                                            occupation = state.occupation.ifBlank { null },
                                            religion = state.religion.ifBlank { null },
                                            hasJkn = state.hasJkn
                                        )

                                        val response = apiService.quickRegister(request)

                                        if (response.isSuccessful) {
                                            val body = response.body()!!
                                            val patientData = body.data

                                            // Save to local DB too
                                            familyMemberDao.insert(
                                                FamilyMemberEntity(
                                                    id = patientData.id,
                                                    uuid = patientData.uuid,
                                                    familyId = patientData.familyId,
                                                    name = patientData.name,
                                                    nik = patientData.nik,
                                                    rmNumber = patientData.rmNumber,
                                                    sequenceNumberInFamily = patientData.sequenceNumberInFamily,
                                                    relationship = patientData.relationship ?: "Lainnya",
                                                    birthPlace = patientData.birthPlace,
                                                    birthDate = patientData.birthDate,
                                                    gender = patientData.gender,
                                                    address = patientData.address,
                                                    occupation = patientData.occupation,
                                                    religion = patientData.religion,
                                                    hasJkn = patientData.hasJkn,
                                                    updatedAt = patientData.updatedAt,
                                                    pendingSync = false
                                                )
                                            )

                                            if (body.isExisting) {
                                                state = state.copy(
                                                    isSubmitting = false,
                                                    duplicateMessage = "Pasien \"${patientData.name}\" sudah terdaftar (RM: ${patientData.rmNumber ?: "-"})",
                                                    duplicateMemberId = patientData.id
                                                )
                                            } else {
                                                onPatientRegistered(patientData.id)
                                            }
                                        } else {
                                            // API error — fallback to offline
                                            saveOffline(state, familyMemberDao, familyDao, onPatientRegistered)
                                        }
                                    } catch (e: Exception) {
                                        // Network error — fallback to offline
                                        try {
                                            saveOffline(state, familyMemberDao, familyDao, onPatientRegistered)
                                        } catch (offlineError: Exception) {
                                            state = state.copy(
                                                isSubmitting = false,
                                                error = "Gagal menyimpan: ${offlineError.message}"
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = !state.isSubmitting && state.name.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0EA5E9),
                                disabledContainerColor = Color(0xFF0EA5E9).copy(alpha = 0.4f)
                            )
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Menyimpan...", color = Color.White)
                            } else {
                                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Daftarkan Pasien", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pasien akan langsung bisa dibuatkan rekam medis",
                            color = AppColors.TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }

    // ─── Date Picker ────────────────────────────────────
    if (state.showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { state = state.copy(showDatePicker = false) },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        state = state.copy(
                            birthDate = date.toString(),
                            showDatePicker = false
                        )
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { state = state.copy(showDatePicker = false) }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Save patient locally when offline.
 * Finds the general family (UMUM-001) in local DB, creates FamilyMember with pendingSync=true.
 */
private suspend fun saveOffline(
    state: QuickRegisterState,
    familyMemberDao: FamilyMemberDao,
    familyDao: FamilyDao,
    onSuccess: (Long) -> Unit
) {
    // Find general family locally
    val generalFamily = familyDao.getByFamilyNumber("UMUM-001")

    if (generalFamily == null) {
        throw Exception("Struktur 'Pasien Umum' belum tersinkronisasi. Hubungkan ke internet dan sync terlebih dahulu.")
    }

    // Generate temporary local ID (negative to avoid collision with server IDs)
    val tempId = -(System.currentTimeMillis() / 1000)
    val uuid = UUID.randomUUID().toString()

    val member = FamilyMemberEntity(
        id = tempId,
        uuid = uuid,
        familyId = generalFamily.id,
        name = state.name.trim(),
        gender = state.gender,
        birthDate = state.birthDate.ifBlank { null },
        nik = state.nik.ifBlank { null },
        birthPlace = state.birthPlace.ifBlank { null },
        address = state.address.ifBlank { null },
        occupation = state.occupation.ifBlank { null },
        religion = state.religion.ifBlank { null },
        hasJkn = state.hasJkn,
        relationship = "Lainnya",
        pendingSync = true,
        pendingAction = "create"
    )

    familyMemberDao.insert(member)
    onSuccess(tempId)
}
