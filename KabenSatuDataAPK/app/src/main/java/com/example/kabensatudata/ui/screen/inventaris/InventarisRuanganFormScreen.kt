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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarisRuanganFormScreen(
    editId: Long?,
    onBack: () -> Unit,
    viewModel: InventarisViewModel = hiltViewModel()
) {
    val state by viewModel.ruanganFormState.collectAsState()
    val detailState by viewModel.detailState.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.updateRuanganForm { copy(fotoUri = it) } }
    }

    // Populate form for edit
    LaunchedEffect(editId) {
        if (editId != null) {
            detailState.ruangan?.let { viewModel.populateRuanganForm(it) }
        }
    }

    // Success → navigate back
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
                title = if (editId != null) "Edit Ruangan" else "Tambah Ruangan",
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
            LightFormField("Nama Ruangan *", state.nama) { viewModel.updateRuanganForm { copy(nama = it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { LightFormField("Kode", state.kode) { viewModel.updateRuanganForm { copy(kode = it) } } }
                Box(Modifier.weight(1f)) { LightFormField("Lantai", state.lantai) { viewModel.updateRuanganForm { copy(lantai = it) } } }
            }
            LightFormField("Deskripsi", state.deskripsi, singleLine = false) { viewModel.updateRuanganForm { copy(deskripsi = it) } }

            // Foto picker
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
                    if (state.fotoUri != null) "Foto dipilih ✅" else "Pilih Foto Ruangan",
                    color = AppColors.TextSecondary
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.submitRuangan(editId) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (editId != null) "Simpan Perubahan" else "Simpan Ruangan", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun LightFormField(label: String, value: String, singleLine: Boolean = true, onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = AppColors.CardBorder,
                focusedBorderColor = AppColors.Primary,
                unfocusedContainerColor = AppColors.Surface,
                focusedContainerColor = AppColors.Surface,
                unfocusedTextColor = AppColors.TextPrimary,
                focusedTextColor = AppColors.TextPrimary
            )
        )
    }
}
