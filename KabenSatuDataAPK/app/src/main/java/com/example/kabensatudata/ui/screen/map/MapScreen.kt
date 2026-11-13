package com.example.kabensatudata.ui.screen.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kabensatudata.data.local.dao.*
import com.example.kabensatudata.data.local.entity.*
import com.example.kabensatudata.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// MapLibre native SDK imports
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// ─── IKS Status ─────────────────────────────────────────────────────────────

enum class IksStatus(val label: String, val color: Color, val argbColor: Int) {
    HEALTHY("Sehat", Color(0xFF10B981), 0xFF10B981.toInt()),
    PRA_HEALTHY("Pra-Sehat", Color(0xFFF59E0B), 0xFFF59E0B.toInt()),
    UNHEALTHY("Tidak Sehat", Color(0xFFEF4444), 0xFFEF4444.toInt()),
    UNKNOWN("Belum Diketahui", Color(0xFF3498DB), 0xFF3498DB.toInt());
}

data class BuildingMapInfo(
    val building: BuildingEntity,
    val iksStatus: IksStatus,
    val families: List<FamilyEntity>,
    val memberCount: Int
)

// ─── ViewModel ──────────────────────────────────────────────────────────────

data class NavigationTarget(
    val lat: Double,
    val lng: Double,
    val buildingNumber: String,
    val distanceMeters: Float? = null
)

data class MapUiState(
    val villages: List<VillageEntity> = emptyList(),
    val buildingInfos: List<BuildingMapInfo> = emptyList(),
    val selectedVillageId: Long? = null,
    val currentLocation: Location? = null,
    val isLocating: Boolean = false,
    val isLoading: Boolean = false,
    val isAddingBuilding: Boolean = false,
    val isDarkMode: Boolean = false,
    val isSatellite: Boolean = false,
    val canEdit: Boolean = true,
    val searchQuery: String = "",
    val error: String? = null,
    val message: String? = null,
    val navTarget: NavigationTarget? = null,
    val isNavigating: Boolean = false
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val villageDao: VillageDao,
    private val buildingDao: BuildingDao,
    private val familyDao: FamilyDao,
    private val familyMemberDao: FamilyMemberDao,
    private val authTokenManager: com.example.kabensatudata.data.local.AuthTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState(canEdit = authTokenManager.canEdit()))
    val uiState: StateFlow<MapUiState> = _uiState
    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            villageDao.getAll().collect { villages ->
                _uiState.update { it.copy(villages = villages) }
                if (villages.isNotEmpty() && _uiState.value.selectedVillageId == null) {
                    selectVillage(villages.first().id)
                }
            }
        }
    }

    fun selectVillage(villageId: Long) {
        _uiState.update { it.copy(selectedVillageId = villageId, isLoading = true) }
        viewModelScope.launch {
            buildingDao.getByVillage(villageId).collect { buildings ->
                val infos = buildings.map { building -> computeBuildingInfo(building) }
                _uiState.update { it.copy(buildingInfos = infos, isLoading = false) }
            }
        }
    }

    private suspend fun computeBuildingInfo(building: BuildingEntity): BuildingMapInfo {
        val families = familyDao.getByBuildingSuspend(building.id)
        if (families.isEmpty()) {
            return BuildingMapInfo(building, IksStatus.UNKNOWN, families, 0)
        }
        val members = familyMemberDao.getByBuilding(building.id)
        val memberCount = members.size
        var totalRelevant = 0
        var totalPositive = 0

        for (family in families) {
            val familyMembers = members.filter { it.familyId == family.id }

            val hasPregnantWife = familyMembers.any {
                it.gender == "Perempuan" && it.relationship in listOf("Istri", "Kepala Keluarga") && it.isPregnant == true
            }
            val hasMarriedCouple = familyMembers.any { it.relationship == "Istri" || it.relationship == "Suami" }
            val kbRelevant = hasMarriedCouple && !hasPregnantWife
            if (kbRelevant) { totalRelevant++; if (familyMembers.any { it.usesContraception == true }) totalPositive++ }

            val recentBirths = familyMembers.filter { m ->
                m.relationship in listOf("Anak", "Cucu") && m.birthDate != null &&
                        try { val b = java.time.LocalDate.parse(m.birthDate.take(10)); java.time.temporal.ChronoUnit.MONTHS.between(b, java.time.LocalDate.now()) <= 12 } catch (_: Exception) { false }
            }
            if (recentBirths.isNotEmpty()) { totalRelevant++; if (recentBirths.all { it.gaveBirthInHealthFacility == true }) totalPositive++ }

            val immunChildren = familyMembers.filter { m ->
                m.relationship in listOf("Anak", "Cucu") && m.birthDate != null &&
                        try { val mo = java.time.temporal.ChronoUnit.MONTHS.between(java.time.LocalDate.parse(m.birthDate.take(10)), java.time.LocalDate.now()); mo in 12..23 } catch (_: Exception) { false }
            }
            if (immunChildren.isNotEmpty()) { totalRelevant++; if (immunChildren.all { it.completeImmunization == true }) totalPositive++ }

            val bfChildren = familyMembers.filter { m ->
                m.relationship in listOf("Anak", "Cucu") && m.birthDate != null &&
                        try { val mo = java.time.temporal.ChronoUnit.MONTHS.between(java.time.LocalDate.parse(m.birthDate.take(10)), java.time.LocalDate.now()); mo in 7..23 } catch (_: Exception) { false }
            }
            if (bfChildren.isNotEmpty()) { totalRelevant++; if (bfChildren.all { it.exclusiveBreastfeeding == true }) totalPositive++ }

            val growthChildren = familyMembers.filter { m ->
                m.relationship in listOf("Anak", "Cucu") && m.birthDate != null &&
                        try { val mo = java.time.temporal.ChronoUnit.MONTHS.between(java.time.LocalDate.parse(m.birthDate.take(10)), java.time.LocalDate.now()); mo in 2..59 } catch (_: Exception) { false }
            }
            if (growthChildren.isNotEmpty()) { totalRelevant++; if (growthChildren.all { it.growthMonitoring == true }) totalPositive++ }

            val tbPatients = familyMembers.filter { it.hasTuberculosis == true }
            if (tbPatients.isNotEmpty()) { totalRelevant++; if (tbPatients.all { it.takesTbMedicationRegularly == true }) totalPositive++ }

            val htnPatients = familyMembers.filter { it.hasHypertension == true }
            if (htnPatients.isNotEmpty()) { totalRelevant++; if (htnPatients.all { it.takesHypertensionMedicationRegularly == true }) totalPositive++ }

            if (family.hasMentalIllness == true) { totalRelevant++; if (family.takesMedicationRegularly == true && family.hasRestrainedMember != true) totalPositive++ }

            totalRelevant++; if (familyMembers.none { it.isSmoker == true }) totalPositive++
            totalRelevant++; if (familyMembers.isNotEmpty() && familyMembers.all { it.hasJkn == true }) totalPositive++
            totalRelevant++; if (family.hasCleanWater == true && family.isWaterProtected == true) totalPositive++
            totalRelevant++; if (family.hasToilet == true && family.isToiletSanitary == true) totalPositive++
        }

        val ratio = if (totalRelevant > 0) totalPositive.toFloat() / totalRelevant else 0f
        val iksStatus = when {
            ratio > 0.8f -> IksStatus.HEALTHY
            ratio >= 0.5f -> IksStatus.PRA_HEALTHY
            else -> IksStatus.UNHEALTHY
        }
        return BuildingMapInfo(building, iksStatus, families, memberCount)
    }

    // GPS streaming — starts continuous location updates
    fun startLocationTracking(context: Context) {
        locationJob?.cancel()
        _uiState.update { it.copy(isLocating = true, error = null) }
        locationJob = viewModelScope.launch {
            LocationHelper.locationUpdates(context, intervalMs = 2000L)
                .collect { location ->
                    _uiState.update { it.copy(
                        currentLocation = location,
                        isLocating = false
                    ) }
                    // Auto-update navigation distance if navigating
                    val target = _uiState.value.navTarget
                    if (_uiState.value.isNavigating && target != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude, location.longitude,
                            target.lat, target.lng, results
                        )
                        _uiState.update { it.copy(
                            navTarget = it.navTarget?.copy(distanceMeters = results[0])
                        ) }
                    }
                }
        }
    }

    fun stopLocationTracking() {
        locationJob?.cancel()
        locationJob = null
    }

    // One-shot locate (fallback if streaming hasn't started)
    fun locateMe(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, error = null) }
            val location = LocationHelper.getCurrentLocation(context)
            if (location != null) {
                _uiState.update { it.copy(currentLocation = location, isLocating = false) }
            } else {
                _uiState.update { it.copy(isLocating = false, error = "Tidak dapat menentukan lokasi") }
            }
        }
    }

    fun toggleAddBuildingMode() { _uiState.update { it.copy(isAddingBuilding = !it.isAddingBuilding) } }

    fun addBuildingAtLocation(lat: Double, lng: Double) {
        val villageId = _uiState.value.selectedVillageId ?: return
        viewModelScope.launch {
            val count = buildingDao.countByVillage(villageId)
            val newBuilding = BuildingEntity(
                id = 0, uuid = java.util.UUID.randomUUID().toString(),
                villageId = villageId, buildingNumber = "${count + 1}",
                latitude = lat, longitude = lng,
                pendingSync = true, pendingAction = "create"
            )
            buildingDao.insert(newBuilding)
            _uiState.update { it.copy(isAddingBuilding = false, message = "Bangunan baru ditambahkan!") }
        }
    }

    fun toggleSatellite() { _uiState.update { it.copy(isSatellite = !it.isSatellite) } }
    fun toggleDarkMode() { _uiState.update { it.copy(isDarkMode = !it.isDarkMode) } }
    fun updateSearch(query: String) { _uiState.update { it.copy(searchQuery = query) } }
    fun dismissMessage() { _uiState.update { it.copy(message = null, error = null) } }

    fun getFilteredBuildings(): List<BuildingMapInfo> {
        val query = _uiState.value.searchQuery.trim().lowercase()
        if (query.isEmpty()) return _uiState.value.buildingInfos
        return _uiState.value.buildingInfos.filter { info ->
            info.building.buildingNumber.lowercase().contains(query) ||
            info.families.any { it.headName.lowercase().contains(query) }
        }
    }

    fun startInAppNavigation(lat: Double, lng: Double, buildingNumber: String) {
        _uiState.update { it.copy(navTarget = NavigationTarget(lat, lng, buildingNumber), isNavigating = true) }
    }

    fun stopNavigation() { _uiState.update { it.copy(navTarget = null, isNavigating = false) } }
}

// ─── Style URLs ─────────────────────────────────────────────────────────────

object MapStyles {
    const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"

    // ESRI World Imagery — free satellite raster tiles (no API key required)
    val SATELLITE_JSON = """
    {
        "version": 8,
        "name": "ESRI Satellite",
        "sources": {
            "esri-satellite": {
                "type": "raster",
                "tiles": [
                    "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                ],
                "tileSize": 256,
                "maxzoom": 19
            }
        },
        "layers": [{
            "id": "esri-satellite-layer",
            "type": "raster",
            "source": "esri-satellite"
        }]
    }
    """.trimIndent()
}

// ─── Marker Bitmap Helper ───────────────────────────────────────────────────

private fun createMarkerBitmap(label: String, color: Int): Bitmap {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.FILL
        setShadowLayer(4f, 0f, 2f, android.graphics.Color.argb(80, 0, 0, 0))
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    val rect = android.graphics.RectF(4f, 4f, (size - 4).toFloat(), (size - 4).toFloat())
    canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
    canvas.drawRoundRect(rect, 12f, 12f, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE; textSize = 24f
        textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText(label, size / 2f, size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    return bitmap
}

// ─── Update Markers on MapLibre ─────────────────────────────────────────────

private fun updateMapMarkers(
    map: MapLibreMap,
    style: Style,
    context: Context,
    buildingInfos: List<BuildingMapInfo>,
    onMarkerClick: (BuildingMapInfo) -> Unit
) {
    // Remove old markers source/layer
    style.removeLayer("buildings-labels")
    style.removeLayer("buildings-circles")
    style.removeSource("buildings-source")

    val withCoords = buildingInfos.filter { it.building.latitude != null && it.building.longitude != null }
    if (withCoords.isEmpty()) return

    // Add marker icons to style
    withCoords.forEach { info ->
        val iconId = "marker_${info.building.id}"
        if (style.getImage(iconId) == null) {
            style.addImage(iconId, createMarkerBitmap(info.building.buildingNumber, info.iksStatus.argbColor))
        }
    }

    // Build GeoJSON features
    val features = withCoords.map { info ->
        val b = info.building
        Feature.fromGeometry(Point.fromLngLat(b.longitude!!, b.latitude!!)).also { f ->
            f.addStringProperty("id", b.id.toString())
            f.addStringProperty("icon", "marker_${b.id}")
            f.addStringProperty("label", b.buildingNumber)
            f.addStringProperty("iks", info.iksStatus.name)
        }
    }

    val source = GeoJsonSource("buildings-source", FeatureCollection.fromFeatures(features))
    style.addSource(source)

    // Symbol layer for markers
    val symbolLayer = org.maplibre.android.style.layers.SymbolLayer("buildings-labels", "buildings-source")
        .withProperties(
            PropertyFactory.iconImage(org.maplibre.android.style.expressions.Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(0.5f)
        )
    style.addLayer(symbolLayer)

    // Map click listener for marker selection
    map.addOnMapClickListener { point ->
        val screenPoint = map.projection.toScreenLocation(point)
        val features2 = map.queryRenderedFeatures(screenPoint, "buildings-labels")
        if (features2.isNotEmpty()) {
            val clickedId = features2.first().getStringProperty("id")
            val info = buildingInfos.find { it.building.id.toString() == clickedId }
            if (info != null) {
                onMarkerClick(info)
                return@addOnMapClickListener true
            }
        }
        false
    }
}

// ─── User Location Blue Dot ─────────────────────────────────────────────────

private fun updateUserLocationLayer(style: Style, location: Location) {
    val point = Point.fromLngLat(location.longitude, location.latitude)
    val feature = Feature.fromGeometry(point)

    val existingSource = style.getSourceAs<GeoJsonSource>("user-location-source")
    if (existingSource != null) {
        existingSource.setGeoJson(feature)
    } else {
        style.addSource(GeoJsonSource("user-location-source", feature))

        // Layer 1: Outer pulsing ring (accuracy circle)
        style.addLayer(CircleLayer("user-loc-pulse", "user-location-source")
            .withProperties(
                PropertyFactory.circleRadius(20f),
                PropertyFactory.circleColor("#4285F4"),
                PropertyFactory.circleOpacity(0.15f),
                PropertyFactory.circleStrokeWidth(0f)
            ))

        // Layer 2: Inner solid dot
        style.addLayer(CircleLayer("user-loc-dot", "user-location-source")
            .withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor("#4285F4"),
                PropertyFactory.circleOpacity(1f),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#FFFFFF")
            ))
    }
}

// ─── Navigation Direction Line ──────────────────────────────────────────────

private fun updateNavigationLine(
    style: Style,
    userLocation: Location,
    target: NavigationTarget
) {
    val lineCoords = listOf(
        Point.fromLngLat(userLocation.longitude, userLocation.latitude),
        Point.fromLngLat(target.lng, target.lat)
    )
    val lineFeature = Feature.fromGeometry(
        LineString.fromLngLats(lineCoords)
    )

    val existingSource = style.getSourceAs<GeoJsonSource>("nav-line-source")
    if (existingSource != null) {
        existingSource.setGeoJson(lineFeature)
    } else {
        style.addSource(GeoJsonSource("nav-line-source", lineFeature))
        style.addLayer(LineLayer("nav-line-layer", "nav-line-source")
            .withProperties(
                PropertyFactory.lineColor("#10B981"),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                PropertyFactory.lineOpacity(0.8f)
            ))
    }
}

private fun removeNavigationLine(style: Style) {
    try {
        style.removeLayer("nav-line-layer")
        style.removeSource("nav-line-source")
    } catch (_: Exception) { }
}

// ─── Map Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onBuildingClick: ((Long) -> Unit)? = null,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var hasPermission by remember { mutableStateOf(LocationHelper.hasLocationPermission(context)) }
    var selectedBuilding by remember { mutableStateOf<BuildingMapInfo?>(null) }
    var showNavDialog by remember { mutableStateOf(false) }
    var navDialogTarget by remember { mutableStateOf<Triple<Double, Double, String>?>(null) }

    // MapLibre references
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentStyle by remember { mutableStateOf<Style?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasPermission) viewModel.startLocationTracking(context)
    }

    // Auto-start GPS streaming when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.startLocationTracking(context)
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Stop GPS tracking when screen is disposed
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.stopLocationTracking() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message, uiState.error) {
        val msg = uiState.message ?: uiState.error
        if (msg != null) { snackbarHostState.showSnackbar(msg); viewModel.dismissMessage() }
    }

    // Determine style — 3-way priority: Satellite > Dark > Liberty
    val isSatellite = uiState.isSatellite
    val isDarkMode = uiState.isDarkMode

    // Update style when toggle changes
    LaunchedEffect(isSatellite, isDarkMode) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (isSatellite) {
            // Satellite: load from JSON string (raster tiles)
            map.setStyle(Style.Builder().fromJson(MapStyles.SATELLITE_JSON)) { style ->
                currentStyle = style
            }
        } else {
            // Vector: load from URL
            val url = if (isDarkMode) MapStyles.DARK else MapStyles.LIBERTY
            map.setStyle(url) { style ->
                currentStyle = style
            }
        }
    }

    // Update markers when buildings change
    val filteredBuildings = viewModel.getFilteredBuildings()
    LaunchedEffect(filteredBuildings, currentStyle) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = currentStyle ?: return@LaunchedEffect
        updateMapMarkers(map, style, context, filteredBuildings) { info ->
            selectedBuilding = info
        }
        // Auto-center
        val withCoords = filteredBuildings.filter { it.building.latitude != null && it.building.longitude != null }
        if (withCoords.isNotEmpty()) {
            if (uiState.searchQuery.isNotBlank() && withCoords.size == 1) {
                val b = withCoords.first().building
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(b.latitude!!, b.longitude!!), 18.0), 800)
            } else {
                val avgLat = withCoords.map { it.building.latitude!! }.average()
                val avgLng = withCoords.map { it.building.longitude!! }.average()
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(avgLat, avgLng), 15.0), 800)
            }
        }
    }

    // Update blue dot + navigation line when location changes
    LaunchedEffect(uiState.currentLocation, currentStyle) {
        val style = currentStyle ?: return@LaunchedEffect
        uiState.currentLocation?.let { loc ->
            updateUserLocationLayer(style, loc)
            // Update direction line if navigating
            if (uiState.isNavigating && uiState.navTarget != null) {
                updateNavigationLine(style, loc, uiState.navTarget!!)
            }
        }
    }

    // Remove navigation line when navigation stops
    LaunchedEffect(uiState.isNavigating) {
        if (!uiState.isNavigating) {
            currentStyle?.let { removeNavigationLine(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Peta & GPS", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Dark mode toggle
                SmallFloatingActionButton(
                    onClick = { viewModel.toggleDarkMode() },
                    containerColor = if (uiState.isDarkMode) Color(0xFF1A1A2E) else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        if (uiState.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (uiState.isDarkMode) "Light Mode" else "Dark Mode",
                        tint = if (uiState.isDarkMode) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Satellite toggle
                SmallFloatingActionButton(
                    onClick = { viewModel.toggleSatellite() },
                    containerColor = if (uiState.isSatellite)
                        Color(0xFF1B5E20)
                    else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.Satellite,
                        contentDescription = if (uiState.isSatellite) "Peta Jalan" else "Satelit",
                        tint = if (uiState.isSatellite) Color.White
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Add building
                if (uiState.canEdit) {
                    SmallFloatingActionButton(
                        onClick = { viewModel.toggleAddBuildingMode() },
                        containerColor = if (uiState.isAddingBuilding) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            if (uiState.isAddingBuilding) Icons.Default.Close else Icons.Default.AddLocation,
                            contentDescription = if (uiState.isAddingBuilding) "Batal" else "Tambah Bangunan"
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // Locate me (re-center camera to current GPS position)
                FloatingActionButton(onClick = {
                    if (hasPermission) {
                        // Re-center camera to latest known position
                        uiState.currentLocation?.let { loc ->
                            mapLibreMap?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(loc.latitude, loc.longitude), 17.0
                                ), 800
                            )
                        } ?: viewModel.locateMe(context)
                    } else {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                }) {
                    if (uiState.isLocating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.MyLocation, "Lokasi Saya")
                }
                Spacer(Modifier.height(72.dp))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ═══ MapLibre GL AndroidView ═══
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        getMapAsync { map ->
                            mapLibreMap = map
                            map.setStyle(MapStyles.LIBERTY) { style ->
                                currentStyle = style
                            }
                            map.uiSettings.isRotateGesturesEnabled = true
                            map.uiSettings.isTiltGesturesEnabled = true
                            map.uiSettings.isCompassEnabled = true
                            map.uiSettings.setAttributionMargins(0, 0, 0, 0)
                            map.uiSettings.isAttributionEnabled = false
                            map.uiSettings.isLogoEnabled = false

                            // Initial camera
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(-5.739, 134.794))
                                .zoom(15.0)
                                .build()

                            // Tap-to-add building
                            map.addOnMapClickListener { point ->
                                if (uiState.isAddingBuilding) {
                                    viewModel.addBuildingAtLocation(point.latitude, point.longitude)
                                    true
                                } else false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Village quick-select buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = 0.9f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Desa:", modifier = Modifier.align(Alignment.CenterVertically),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
                uiState.villages.forEach { village ->
                    val isSelected = village.id == uiState.selectedVillageId
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectVillage(village.id) },
                        label = { Text(village.name, fontSize = 12.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    placeholder = { Text("Cari no. bangunan atau nama...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearch("") }) {
                                Icon(Icons.Default.Close, "Hapus", Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.White.copy(alpha = 0.95f),
                        focusedContainerColor = Color.White,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // IKS Legend
            Card(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 80.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Legenda", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF374151))
                    Spacer(Modifier.height(4.dp))
                    IksStatus.entries.forEach { status ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(status.color))
                            Spacer(Modifier.width(6.dp))
                            Text(status.label, fontSize = 10.sp, color = Color(0xFF4B5563))
                        }
                    }
                }
            }

            // Style badge
            if (uiState.isDarkMode || uiState.isSatellite) {
                Card(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 170.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isSatellite) Color(0xFF1B5E20).copy(alpha = 0.9f)
                        else Color(0xFF1A1A2E).copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = if (uiState.isSatellite) "🛰️ Satelit" else "🌙 Dark Mode",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 10.sp, color = Color.White
                    )
                }
            }

            // Add building mode banner
            if (uiState.isAddingBuilding) {
                Surface(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 96.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer, tonalElevation = 4.dp
                ) {
                    Text("📍 Ketuk peta untuk menandai lokasi bangunan baru",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }

            // Loading
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(36.dp), strokeWidth = 3.dp)
            }

            // In-app Navigation Panel
            if (uiState.isNavigating && uiState.navTarget != null) {
                NavigationPanel(target = uiState.navTarget!!, onStop = { viewModel.stopNavigation() }, context = context)
            }

            // Attribution
            Text(
                "© OpenFreeMap · © OpenMapTiles · © OpenStreetMap",
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                fontSize = 7.sp, color = Color(0xFF64748B)
            )
        }
    }

    // Building detail bottom sheet
    if (selectedBuilding != null) {
        BuildingDetailSheet(
            info = selectedBuilding!!,
            onDismiss = { selectedBuilding = null },
            onNavigate = { lat, lng ->
                selectedBuilding?.let { bld ->
                    navDialogTarget = Triple(lat, lng, bld.building.buildingNumber)
                    showNavDialog = true
                    selectedBuilding = null
                }
            },
            onViewFamilies = { buildingId ->
                selectedBuilding = null
                onBuildingClick?.invoke(buildingId)
            }
        )
    }

    // Navigation Choice Dialog
    if (showNavDialog && navDialogTarget != null) {
        val (lat, lng, bldNum) = navDialogTarget!!
        NavigationChoiceDialog(
            buildingNumber = bldNum,
            onGoogleMaps = {
                showNavDialog = false; navDialogTarget = null
                val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
                if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
            },
            onInAppNav = {
                showNavDialog = false; navDialogTarget = null
                if (hasPermission) viewModel.locateMe(context)
                viewModel.startInAppNavigation(lat, lng, bldNum)
            },
            onDismiss = { showNavDialog = false; navDialogTarget = null }
        )
    }

    // (GPS streaming handles nav distance updates automatically via ViewModel)
}

// ─── Building Detail Bottom Sheet ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingDetailSheet(
    info: BuildingMapInfo, onDismiss: () -> Unit,
    onNavigate: (Double, Double) -> Unit, onViewFamilies: (Long) -> Unit
) {
    val b = info.building
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(info.iksStatus.color),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Bangunan ${b.buildingNumber}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(info.iksStatus.color))
                        Spacer(Modifier.width(6.dp))
                        Text("IKS: ${info.iksStatus.label}", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (b.latitude != null && b.longitude != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${String.format("%.6f", b.latitude)}, ${String.format("%.6f", b.longitude)}",
                        fontSize = 12.sp, color = Color(0xFF64748B))
                }
                Spacer(Modifier.height(12.dp))
            }
            Text("Keluarga (${info.families.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF374151))
            Spacer(Modifier.height(6.dp))
            if (info.families.isEmpty()) {
                Text("Belum ada data keluarga", fontSize = 13.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(vertical = 8.dp))
            } else {
                info.families.forEach { family ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(family.headName, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("No. ${family.familyNumber}", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onViewFamilies(b.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Lihat Detail", fontSize = 13.sp)
                }
                if (b.latitude != null && b.longitude != null) {
                    Button(onClick = { onNavigate(b.latitude, b.longitude) }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                        Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("Navigasi", fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Navigation Choice Dialog ───────────────────────────────────────────────

@Composable
fun NavigationChoiceDialog(buildingNumber: String, onGoogleMaps: () -> Unit, onInAppNav: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Navigation, null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp)) },
        title = { Text("Navigasi ke Bangunan $buildingNumber", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = { Text("Pilih metode navigasi:", fontSize = 14.sp, color = Color(0xFF64748B)) },
        confirmButton = {
            Button(onClick = onGoogleMaps, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Google Maps", fontSize = 13.sp)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onInAppNav, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF10B981))) {
                Icon(Icons.Default.NearMe, null, Modifier.size(18.dp), tint = Color(0xFF10B981))
                Spacer(Modifier.width(6.dp)); Text("Peta Saat Ini", fontSize = 13.sp, color = Color(0xFF10B981))
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

// ─── Navigation Panel ───────────────────────────────────────────────────────

@Composable
fun NavigationPanel(target: NavigationTarget, onStop: () -> Unit, context: Context) {
    val distance = target.distanceMeters

    LaunchedEffect(distance) {
        if (distance != null && distance < 200f) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else { @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
                if (vibrator != null) {
                    val intensity = when { distance < 10f -> 255; distance < 30f -> 200; distance < 50f -> 150; distance < 100f -> 100; else -> 60 }
                    val duration = when { distance < 10f -> 500L; distance < 30f -> 300L; distance < 50f -> 200L; distance < 100f -> 150L; else -> 100L }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, intensity))
                    else { @Suppress("DEPRECATION") vibrator.vibrate(duration) }
                }
            } catch (_: Exception) {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "navPulse")
    val pulseAlpha by infiniteTransition.animateFloat(0.7f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    val isClose = distance != null && distance < 50f
    val panelColor = when {
        distance == null -> Color(0xFF6366F1); distance < 10f -> Color(0xFF059669)
        distance < 50f -> Color(0xFF10B981); distance < 100f -> Color(0xFFF59E0B); else -> Color(0xFF6366F1)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 80.dp)
            .then(if (isClose) Modifier.alpha(pulseAlpha) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = panelColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NearMe, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Navigasi ke Bangunan ${target.buildingNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    if (distance != null) {
                        val distText = if (distance >= 1000f) String.format("%.1f km", distance / 1000f) else String.format("%.0f meter", distance)
                        val statusText = when {
                            distance < 10f -> "\uD83D\uDCCD Anda sudah sampai!"; distance < 30f -> "\uD83D\uDD25 Sangat dekat!"
                            distance < 50f -> "\u26A1 Hampir sampai"; distance < 100f -> "\uD83D\uDEB6 Mendekat..."
                            else -> "\uD83D\uDCCF Jarak: $distText"
                        }
                        Text(statusText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f),
                            fontWeight = if (distance < 50f) FontWeight.SemiBold else FontWeight.Normal)
                    } else Text("Menghitung jarak...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
                FilledTonalButton(onClick = onStop,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (distance != null && distance < 500f) {
                Spacer(Modifier.height(8.dp))
                val progress = (1f - (distance / 500f)).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
            }
        }
    }
}
