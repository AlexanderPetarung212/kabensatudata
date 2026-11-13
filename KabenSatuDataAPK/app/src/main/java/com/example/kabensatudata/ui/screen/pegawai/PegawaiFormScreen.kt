package com.example.kabensatudata.ui.screen.pegawai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.ui.theme.AppColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PegawaiFormScreen(
    editId: Long? = null,
    onBack: () -> Unit,
    viewModel: PegawaiViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsState()
    val context = LocalContext.current

    val fotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.updateFormField { copy(fotoUri = it) } }
    }
    val ktpPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.updateFormField { copy(ktpUri = it) } }
    }

    // Prefill if editing
    val detail by viewModel.detailState.collectAsState()
    LaunchedEffect(editId) {
        if (editId != null) {
            viewModel.loadDetail(editId)
        } else {
            viewModel.resetForm()
        }
    }
    LaunchedEffect(detail.pegawai) {
        if (editId != null && detail.pegawai != null) {
            viewModel.populateFormFromDetail()
        }
    }

    // Success feedback
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.submitMessage) {
        state.submitMessage?.let {
            if (it.contains("Berhasil")) onBack()
            else snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId != null) "Edit Pegawai" else "Tambah Pegawai", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Nama *
            OutlinedTextField(
                value = state.nama, onValueChange = { viewModel.updateFormField { copy(nama = it) } },
                label = { Text("Nama Lengkap *") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )

            // NIP & NIK
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.nip, onValueChange = { viewModel.updateFormField { copy(nip = it) } },
                    label = { Text("NIP") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
                OutlinedTextField(
                    value = state.nik, onValueChange = { viewModel.updateFormField { copy(nik = it) } },
                    label = { Text("NIK") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
            }

            // Gender Dropdown
            var genderExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = it }) {
                OutlinedTextField(
                    value = state.jenisKelamin, onValueChange = {}, readOnly = true,
                    label = { Text("Jenis Kelamin") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                    listOf("Laki-laki", "Perempuan").forEach { g ->
                        DropdownMenuItem(text = { Text(g) }, onClick = {
                            viewModel.updateFormField { copy(jenisKelamin = g) }
                            genderExpanded = false
                        })
                    }
                }
            }

            // Jabatan & Unit
            OutlinedTextField(
                value = state.jabatan, onValueChange = { viewModel.updateFormField { copy(jabatan = it) } },
                label = { Text("Jabatan") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            OutlinedTextField(
                value = state.unit, onValueChange = { viewModel.updateFormField { copy(unit = it) } },
                label = { Text("Unit") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )

            // Pangkat & Pendidikan
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.pangkatGol, onValueChange = { viewModel.updateFormField { copy(pangkatGol = it) } },
                    label = { Text("Pangkat/Gol") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
                OutlinedTextField(
                    value = state.pendidikanTerakhir, onValueChange = { viewModel.updateFormField { copy(pendidikanTerakhir = it) } },
                    label = { Text("Pendidikan") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
            }

            // Profesi
            OutlinedTextField(
                value = state.profesi, onValueChange = { viewModel.updateFormField { copy(profesi = it) } },
                label = { Text("Profesi") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), singleLine = true
            )

            // Tanggal Lahir, Alamat, No HP
            OutlinedTextField(
                value = state.tanggalLahir, onValueChange = { viewModel.updateFormField { copy(tanggalLahir = it) } },
                label = { Text("Tanggal Lahir") }, placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp)) }
            )
            OutlinedTextField(
                value = state.alamat, onValueChange = { viewModel.updateFormField { copy(alamat = it) } },
                label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), minLines = 2, maxLines = 3
            )
            OutlinedTextField(
                value = state.noHp, onValueChange = { viewModel.updateFormField { copy(noHp = it) } },
                label = { Text("No. HP") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Default.Phone, null, Modifier.size(18.dp)) }
            )

            // Photo & KTP upload
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { fotoPicker.launch("image/*") },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.fotoUri != null) "Foto ✓" else "Upload Foto", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { ktpPicker.launch("image/*") },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CreditCard, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.ktpUri != null) "KTP ✓" else "Upload KTP", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Submit
            Button(
                onClick = {
                    val fotoFile = state.fotoUri?.let { uri ->
                        val input = context.contentResolver.openInputStream(uri)
                        val tmp = File.createTempFile("foto_", ".jpg", context.cacheDir)
                        input?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                        tmp
                    }
                    val ktpFile = state.ktpUri?.let { uri ->
                        val input = context.contentResolver.openInputStream(uri)
                        val tmp = File.createTempFile("ktp_", ".jpg", context.cacheDir)
                        input?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                        tmp
                    }
                    viewModel.submitPegawai(editId, fotoFile, ktpFile)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !state.isSubmitting && state.nama.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (editId != null) "Perbarui" else "Simpan", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
