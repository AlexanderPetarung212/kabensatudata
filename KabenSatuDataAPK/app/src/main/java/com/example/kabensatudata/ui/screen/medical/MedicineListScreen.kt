package com.example.kabensatudata.ui.screen.medical

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kabensatudata.data.remote.dto.MedicineFullDto
import com.example.kabensatudata.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineListScreen(
    viewModel: MedicineViewModel,
    onBack: () -> Unit,
    onAddMedicine: () -> Unit,
    onEditMedicine: (Long) -> Unit
) {
    val state by viewModel.listState.collectAsState()
    var showAdjustDialog by remember { mutableStateOf<MedicineFullDto?>(null) }
    var showDeleteDialog by remember { mutableStateOf<MedicineFullDto?>(null) }

    LaunchedEffect(Unit) { viewModel.loadMedicines() }

    // Success snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddMedicine,
                containerColor = AppColors.Accent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 72.dp)
            ) { Icon(Icons.Default.Add, "Tambah Obat") }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(AppColors.Background)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
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
                                    Text("Manajemen Obat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("${state.medicines.size} obat terdaftar", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            // Search
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.onSearchChanged(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Cari obat…", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f)) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
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

                // Filter chips
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = state.filterStock == null,
                            onClick = { viewModel.onFilterStock(null) },
                            label = { Text("Semua", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF6366F1), selectedLabelColor = Color.White),
                            modifier = Modifier.height(30.dp)
                        )
                        listOf("available" to "Tersedia", "low" to "Menipis", "out" to "Habis").forEach { (key, label) ->
                            val chipColor = when (key) { "available" -> Color(0xFF22C55E); "low" -> Color(0xFFF59E0B); else -> Color(0xFFEF4444) }
                            FilterChip(
                                selected = state.filterStock == key,
                                onClick = { viewModel.onFilterStock(if (state.filterStock == key) null else key) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = chipColor, selectedLabelColor = Color.White),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                }

                // Loading
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                        }
                    }
                }

                // Medicine List
                val filtered = state.filteredMedicines
                items(filtered, key = { it.id }) { med ->
                    MedicineCard(
                        medicine = med,
                        onEdit = { onEditMedicine(med.id) },
                        onAdjust = { showAdjustDialog = med },
                        onDelete = { showDeleteDialog = med }
                    )
                }

                // Empty
                if (!state.isLoading && filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Medication, null, tint = AppColors.TextTertiary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Tidak ada obat ditemukan", fontSize = 14.sp, color = AppColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Adjust Stock Dialog
    showAdjustDialog?.let { med ->
        StockAdjustDialog(
            medicine = med,
            onDismiss = { showAdjustDialog = null },
            onConfirm = { adj, reason ->
                viewModel.adjustStock(med.id, adj, reason)
                showAdjustDialog = null
            }
        )
    }

    // Delete Confirmation
    showDeleteDialog?.let { med ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Hapus Obat") },
            text = { Text("Hapus ${med.name}? Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMedicine(med.id); showDeleteDialog = null }) {
                    Text("Hapus", color = Color(0xFFEF4444))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun MedicineCard(
    medicine: MedicineFullDto,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit
) {
    val stockColor = when {
        medicine.stockQuantity <= 0 -> Color(0xFFEF4444)
        medicine.stockQuantity <= medicine.minimumStock -> Color(0xFFF59E0B)
        else -> Color(0xFF22C55E)
    }
    val stockLabel = when {
        medicine.stockQuantity <= 0 -> "Habis"
        medicine.stockQuantity <= medicine.minimumStock -> "Menipis"
        else -> "Tersedia"
    }

    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp), color = AppColors.Surface, shadowElevation = 1.dp,
        border = BorderStroke(1.dp, stockColor.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(stockColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Medication, null, tint = stockColor, modifier = Modifier.size(22.dp)) }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(medicine.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!medicine.genericName.isNullOrBlank()) {
                        Text(medicine.genericName, fontSize = 11.sp, color = AppColors.TextTertiary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!medicine.strength.isNullOrBlank()) {
                            Text(medicine.strength, fontSize = 11.sp, color = AppColors.TextSecondary)
                        }
                        Text(medicine.unit.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = AppColors.Accent)
                    }
                }

                // Stock badge
                Column(horizontalAlignment = Alignment.End) {
                    Surface(shape = RoundedCornerShape(8.dp), color = stockColor.copy(alpha = 0.12f)) {
                        Text(
                            stockLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = stockColor
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("Stok: ${medicine.stockQuantity}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = stockColor)
                    Text("Min: ${medicine.minimumStock}", fontSize = 10.sp, color = AppColors.TextTertiary)
                }
            }

            Spacer(Modifier.height(8.dp))
            Divider(color = AppColors.Background)
            Spacer(Modifier.height(6.dp))

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onAdjust, label = { Text("Sesuaikan Stok", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(14.dp)) },
                    modifier = Modifier.height(30.dp))
                AssistChip(onClick = onEdit, label = { Text("Edit", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(14.dp)) },
                    modifier = Modifier.height(30.dp))
                AssistChip(onClick = onDelete, label = { Text("Hapus", fontSize = 11.sp, color = Color(0xFFEF4444)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = Color(0xFFEF4444)) },
                    modifier = Modifier.height(30.dp))
            }
        }
    }
}

// ─── Adjust Stock Dialog ────────────────────────────────

@Composable
fun StockAdjustDialog(
    medicine: MedicineFullDto,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var adjustment by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val adjVal = adjustment.toIntOrNull() ?: 0
    val newStock = (medicine.stockQuantity + adjVal).coerceAtLeast(0)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier.fillMaxWidth(0.9f), shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Sesuaikan Stok", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text(medicine.name, fontSize = 14.sp, color = AppColors.Accent, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))

                // Current stock
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Stok saat ini:", fontSize = 13.sp, color = AppColors.TextSecondary)
                    Text("${medicine.stockQuantity} ${medicine.unit}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                }
                Spacer(Modifier.height(12.dp))

                // Adjustment input
                OutlinedTextField(
                    value = adjustment, onValueChange = { adjustment = it },
                    label = { Text("Penyesuaian (+/-)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    supportingText = { Text("Positif = menambah, Negatif = mengurangi") }
                )
                Spacer(Modifier.height(8.dp))

                // Reason
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Alasan") }, placeholder = { Text("Pembelian, expired, rusak…") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(12.dp))

                // Preview
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F5F9)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stok baru:", fontSize = 13.sp, color = AppColors.TextSecondary)
                        Text(
                            "$newStock ${medicine.unit}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (newStock <= medicine.minimumStock) Color(0xFFF59E0B) else Color(0xFF22C55E)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(adjVal, reason) },
                        enabled = adjVal != 0 && reason.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Simpan") }
                }
            }
        }
    }
}
