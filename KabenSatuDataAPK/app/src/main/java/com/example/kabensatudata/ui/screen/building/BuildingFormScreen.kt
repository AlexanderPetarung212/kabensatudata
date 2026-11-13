package com.example.kabensatudata.ui.screen.building

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.AppTopBar
import com.example.kabensatudata.ui.theme.appTextFieldColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.BuildingDao
import com.example.kabensatudata.data.local.entity.BuildingEntity
import com.example.kabensatudata.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BuildingFormState(
    val buildingNumber: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val description: String = "",
    val isGettingLocation: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class BuildingFormViewModel @Inject constructor(
    private val buildingDao: BuildingDao
) : ViewModel() {

    private val _state = MutableStateFlow(BuildingFormState())
    val state: StateFlow<BuildingFormState> = _state

    private var villageId: Long = 0
    private var editingBuilding: BuildingEntity? = null

    fun init(villageId: Long, buildingId: Long? = null) {
        this.villageId = villageId
        if (buildingId != null && buildingId > 0) {
            viewModelScope.launch {
                buildingDao.getById(buildingId)?.let { building ->
                    editingBuilding = building
                    _state.update {
                        it.copy(
                            buildingNumber = building.buildingNumber,
                            latitude = building.latitude?.toString() ?: "",
                            longitude = building.longitude?.toString() ?: "",
                            description = building.description ?: ""
                        )
                    }
                }
            }
        } else {
            viewModelScope.launch {
                val count = buildingDao.countByVillage(villageId)
                _state.update { it.copy(buildingNumber = "${count + 1}") }
            }
        }
    }

    fun onFieldChange(field: String, value: String) {
        _state.update {
            when (field) {
                "buildingNumber" -> it.copy(buildingNumber = value)
                "latitude" -> it.copy(latitude = value)
                "longitude" -> it.copy(longitude = value)
                "description" -> it.copy(description = value)
                else -> it
            }
        }
    }

    fun getGpsLocation(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isGettingLocation = true) }
            val location = LocationHelper.getCurrentLocation(context)
            if (location != null) {
                _state.update {
                    it.copy(
                        latitude = String.format("%.7f", location.latitude),
                        longitude = String.format("%.7f", location.longitude),
                        isGettingLocation = false
                    )
                }
            } else {
                _state.update {
                    it.copy(isGettingLocation = false, error = "Gagal mendapatkan lokasi GPS")
                }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.buildingNumber.isBlank()) {
            _state.update { it.copy(error = "Nomor bangunan harus diisi") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val lat = s.latitude.toDoubleOrNull()
                val lng = s.longitude.toDoubleOrNull()

                if (editingBuilding != null) {
                    val updated = editingBuilding!!.copy(
                        buildingNumber = s.buildingNumber,
                        latitude = lat,
                        longitude = lng,
                        description = s.description.ifBlank { null },
                        pendingSync = true,
                        pendingAction = "update"
                    )
                    buildingDao.update(updated)
                } else {
                    val newBuilding = BuildingEntity(
                        id = 0,
                        uuid = java.util.UUID.randomUUID().toString(),
                        villageId = villageId,
                        buildingNumber = s.buildingNumber,
                        latitude = lat,
                        longitude = lng,
                        description = s.description.ifBlank { null },
                        pendingSync = true,
                        pendingAction = "create"
                    )
                    buildingDao.insert(newBuilding)
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingFormScreen(
    villageId: Long,
    buildingId: Long? = null,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: BuildingFormViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var hasPermission by remember { mutableStateOf(LocationHelper.hasLocationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasPermission) viewModel.getGpsLocation(context)
    }

    LaunchedEffect(Unit) { viewModel.init(villageId, buildingId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = { AppTopBar(if (buildingId != null) "Edit Bangunan" else "Tambah Bangunan", onBack) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.buildingNumber,
                onValueChange = { viewModel.onFieldChange("buildingNumber", it) },
                label = { Text("Nomor Bangunan *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = appTextFieldColors()
            )

            // GPS Section
            Text("Koordinat GPS", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.latitude,
                    onValueChange = { viewModel.onFieldChange("latitude", it) },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = appTextFieldColors()
                )
                OutlinedTextField(
                    value = state.longitude,
                    onValueChange = { viewModel.onFieldChange("longitude", it) },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = appTextFieldColors()
                )
            }

            // GPS button
            OutlinedButton(
                onClick = {
                    if (hasPermission) viewModel.getGpsLocation(context)
                    else permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isGettingLocation,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.3f))
            ) {
                if (state.isGettingLocation) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Mencari lokasi...")
                } else {
                    Icon(Icons.Default.MyLocation, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ambil dari GPS")
                }
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.onFieldChange("description", it) },
                label = { Text("Deskripsi / Catatan") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                colors = appTextFieldColors()
            )

            if (state.error != null) {
                Text(state.error!!, color = AppColors.Error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    disabledContainerColor = AppColors.Surface
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Simpan")
                }
            }
        }
    }
}
