package com.example.kabensatudata.ui.screen.pegawai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuratTugasFormScreen(
    onBack: () -> Unit,
    viewModel: PegawaiViewModel = hiltViewModel()
) {
    val listState by viewModel.listState.collectAsState()
    val formState by viewModel.formState.collectAsState()

    // Local form fields
    var pegawaiId by remember { mutableStateOf(0L) }
    var nomorSurat by remember { mutableStateOf("") }
    var dasar by remember { mutableStateOf("") }
    var maksudTugas by remember { mutableStateOf("") }
    var tanggalMulai by remember { mutableStateOf("") }
    var tanggalSelesai by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

    // Load pegawai list for dropdown
    LaunchedEffect(Unit) { viewModel.loadPegawaiList() }

    LaunchedEffect(formState.submitMessage) {
        formState.submitMessage?.let {
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buat Surat Tugas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.PrimaryDark, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Pegawai Dropdown
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = listState.list.find { it.id == pegawaiId }?.nama ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pegawai *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listState.list.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.nama}${if (!p.nip.isNullOrBlank()) " (${p.nip})" else ""}") },
                            onClick = {
                                pegawaiId = p.id
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Nomor Surat
            OutlinedTextField(
                value = nomorSurat,
                onValueChange = { nomorSurat = it },
                label = { Text("Nomor Surat") },
                placeholder = { Text("Otomatis jika kosong", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Dasar
            OutlinedTextField(
                value = dasar,
                onValueChange = { dasar = it },
                label = { Text("Dasar (opsional)") },
                placeholder = { Text("Berdasarkan ...", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2, maxLines = 4
            )

            // Maksud Tugas
            OutlinedTextField(
                value = maksudTugas,
                onValueChange = { maksudTugas = it },
                label = { Text("Maksud Tugas *") },
                placeholder = { Text("Mengikuti ...", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3, maxLines = 5
            )

            // Dates Row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = tanggalMulai,
                    onValueChange = { tanggalMulai = it },
                    label = { Text("Tanggal Mulai *") },
                    placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp)) }
                )
                OutlinedTextField(
                    value = tanggalSelesai,
                    onValueChange = { tanggalSelesai = it },
                    label = { Text("Tanggal Selesai *") },
                    placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Event, null, Modifier.size(18.dp)) }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Submit
            val canSubmit = pegawaiId > 0 && maksudTugas.isNotBlank() && tanggalMulai.isNotBlank() && tanggalSelesai.isNotBlank()
            Button(
                onClick = {
                    viewModel.createSuratTugas(
                        pegawaiId = pegawaiId,
                        nomorSurat = nomorSurat.ifBlank { null },
                        dasar = dasar.ifBlank { null },
                        maksudTugas = maksudTugas,
                        tanggalMulai = tanggalMulai,
                        tanggalSelesai = tanggalSelesai,
                        onSuccess = { onBack() }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = canSubmit && !formState.isSubmitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Buat Surat Tugas", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
