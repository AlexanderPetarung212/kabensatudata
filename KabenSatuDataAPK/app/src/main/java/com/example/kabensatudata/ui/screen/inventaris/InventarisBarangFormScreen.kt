package com.example.kabensatudata.ui.screen.inventaris

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.WaveTopBar

private val kondisiOptions = listOf(
    "baik" to "Baik",
    "rusak_ringan" to "Rusak Ringan",
    "rusak_berat" to "Rusak Berat",
    "tidak_aktif" to "Tidak Aktif"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarisBarangFormScreen(
    ruanganId: Long,
    editBarangId: Long?,
    onBack: () -> Unit,
    viewModel: InventarisViewModel = hiltViewModel()
) {
    val state by viewModel.barangFormState.collectAsState()
    val detailState by viewModel.detailState.collectAsState()
    var kondisiExpanded by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.updateBarangForm { copy(fotoUri = it) } }
    }

    // Populate form for edit
    LaunchedEffect(editBarangId) {
        if (editBarangId != null) {
            detailState.ruangan?.barang?.find { it.id == editBarangId }?.let {
                viewModel.populateBarangForm(it)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.submitMessage) {
        state.submitMessage?.let {
            if (it.contains("berhasil", true)) {
                snackbarHostState.showSnackbar(it)
                onBack()
            } else {
                snackbarHostState.showSnackbar(it)
            }
        }
    }

    Scaffold(
        topBar = {
            WaveTopBar(
                title = if (editBarangId != null) "Edit Barang" else "Tambah Barang",
                subtitle = detailState.ruangan?.nama?.let { "Ruangan: $it" } ?: "",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LightFormField("Nama Barang *", state.nama) { viewModel.updateBarangForm { copy(nama = it) } }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    LightFormField("Kode Inventaris", state.kodeInventaris) { viewModel.updateBarangForm { copy(kodeInventaris = it) } }
                }
                Box(Modifier.weight(1f)) {
                    LightFormField("Merek/Tipe", state.merek) { viewModel.updateBarangForm { copy(merek = it) } }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    LightFormField("Jumlah *", state.jumlah) { viewModel.updateBarangForm { copy(jumlah = it) } }
                }
                Box(Modifier.weight(1f)) {
                    // Kondisi dropdown
                    Column {
                        Text("Kondisi *", fontSize = 12.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = kondisiExpanded,
                            onExpandedChange = { kondisiExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = kondisiOptions.find { it.first == state.kondisi }?.second ?: "Baik",
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kondisiExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = AppColors.CardBorder,
                                    focusedBorderColor = AppColors.Primary,
                                    unfocusedContainerColor = AppColors.Surface,
                                    unfocusedTextColor = AppColors.TextPrimary,
                                    focusedTextColor = AppColors.TextPrimary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = kondisiExpanded,
                                onDismissRequest = { kondisiExpanded = false }
                            ) {
                                kondisiOptions.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, color = AppColors.TextPrimary) },
                                        onClick = {
                                            viewModel.updateBarangForm { copy(kondisi = value) }
                                            kondisiExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    LightFormField("Tahun Perolehan", state.tahunPerolehan) { viewModel.updateBarangForm { copy(tahunPerolehan = it) } }
                }
                Box(Modifier.weight(1f)) {
                    LightFormField("Sumber Dana", state.sumberDana) { viewModel.updateBarangForm { copy(sumberDana = it) } }
                }
            }

            LightFormField("Keterangan", state.keterangan, singleLine = false) {
                viewModel.updateBarangForm { copy(keterangan = it) }
            }

            OutlinedButton(
                onClick = { launcher.launch("image/*") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AppColors.CardBorder)
                )
            ) {
                Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp), tint = Color(0xFF06B6D4))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.fotoUri != null) "Foto dipilih ✅" else "Pilih Foto Barang",
                    color = AppColors.TextSecondary
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.submitBarang(ruanganId, editBarangId) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (editBarangId != null) "Simpan Perubahan" else "Simpan Barang", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
