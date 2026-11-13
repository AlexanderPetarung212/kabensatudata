package com.example.kabensatudata.ui.screen.medical

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineFormScreen(
    viewModel: MedicineViewModel,
    medicineId: Long? = null,
    onBack: () -> Unit
) {
    val state by viewModel.formState.collectAsState()

    LaunchedEffect(medicineId) { viewModel.initForm(medicineId) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val unitOptions = listOf("tablet", "kapsul", "botol", "tube", "ampul", "vial", "sachet", "strip")
    var unitExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(AppColors.Background)
    ) {
        // Header
        Surface(Modifier.fillMaxWidth(), color = AppColors.PrimaryDark) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Text(
                    if (medicineId != null) "Edit Obat" else "Tambah Obat",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
        }

        // Form
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Name
            OutlinedTextField(
                value = state.name, onValueChange = { viewModel.onFormName(it) },
                label = { Text("Nama Obat *") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(10.dp)
            )

            // Generic name
            OutlinedTextField(
                value = state.genericName, onValueChange = { viewModel.onFormGenericName(it) },
                label = { Text("Nama Generik") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(10.dp)
            )

            // Strength
            OutlinedTextField(
                value = state.strength, onValueChange = { viewModel.onFormStrength(it) },
                label = { Text("Kekuatan") }, placeholder = { Text("500mg, 250mg, dll") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp)
            )

            // Unit dropdown
            ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }) {
                OutlinedTextField(
                    value = state.unit.replaceFirstChar { it.uppercase() },
                    onValueChange = {}, readOnly = true,
                    label = { Text("Satuan *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                    unitOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.replaceFirstChar { it.uppercase() }) },
                            onClick = { viewModel.onFormUnit(opt); unitExpanded = false }
                        )
                    }
                }
            }

            // Stock fields
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.stockQuantity, onValueChange = { viewModel.onFormStock(it) },
                    label = { Text("Jumlah Stok *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = state.minimumStock, onValueChange = { viewModel.onFormMinStock(it) },
                    label = { Text("Stok Minimum *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
            }

            // Description
            OutlinedTextField(
                value = state.description, onValueChange = { viewModel.onFormDescription(it) },
                label = { Text("Deskripsi") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4, shape = RoundedCornerShape(10.dp)
            )

            // Active toggle
            Surface(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                color = AppColors.Surface, tonalElevation = 1.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Status Aktif", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Text("Obat tidak aktif tidak muncul dalam pilihan resep", fontSize = 11.sp, color = AppColors.TextTertiary)
                    }
                    Switch(
                        checked = state.isActive,
                        onCheckedChange = { viewModel.onFormActive(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF22C55E))
                    )
                }
            }

            // Error
            if (state.error != null) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color(0xFFFEE2E2)) {
                    Text(state.error!!, Modifier.padding(12.dp), fontSize = 12.sp, color = Color(0xFFDC2626))
                }
            }

            // Save button
            Button(
                onClick = { viewModel.saveMedicine() },
                enabled = !state.isSaving && state.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Menyimpan…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (medicineId != null) "Perbarui" else "Simpan", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
