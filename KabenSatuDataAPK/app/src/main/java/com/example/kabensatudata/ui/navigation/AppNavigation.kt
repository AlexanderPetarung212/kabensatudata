package com.example.kabensatudata.ui.navigation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.theme.AppColors
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kabensatudata.ui.screen.login.LoginScreen
import com.example.kabensatudata.ui.screen.home.HomeScreen
import com.example.kabensatudata.ui.screen.home.AnalysisScreen
import com.example.kabensatudata.ui.screen.village.VillageListScreen
import com.example.kabensatudata.ui.screen.building.BuildingListScreen
import com.example.kabensatudata.ui.screen.building.BuildingFormScreen
import com.example.kabensatudata.ui.screen.family.FamilyListScreen
import com.example.kabensatudata.ui.screen.family.FamilyFormScreen
import com.example.kabensatudata.ui.screen.member.MemberListScreen
import com.example.kabensatudata.ui.screen.member.MemberFormScreen
import com.example.kabensatudata.ui.screen.member.MemberDetailScreen
import com.example.kabensatudata.ui.screen.medical.MedicalFormScreen
import com.example.kabensatudata.ui.screen.medical.MedicalRecordListScreen
import com.example.kabensatudata.ui.screen.medical.MedicalHubScreen
import com.example.kabensatudata.ui.screen.medical.PatientRecordsScreen
import com.example.kabensatudata.ui.screen.medical.MedicineListScreen
import com.example.kabensatudata.ui.screen.medical.MedicineFormScreen
import com.example.kabensatudata.ui.screen.medical.MedicineViewModel
import com.example.kabensatudata.ui.screen.notification.NotificationScreen
import com.example.kabensatudata.ui.screen.map.MapScreen
import com.example.kabensatudata.ui.screen.sync.SyncScreen
import com.example.kabensatudata.ui.screen.calendar.CalendarScreen
import com.example.kabensatudata.ui.screen.calendar.KelolaBulanScreen
import com.example.kabensatudata.ui.screen.poa.PoaListScreen
import com.example.kabensatudata.ui.screen.poa.PoaDetailScreen
import com.example.kabensatudata.ui.screen.poa.PoaFormScreen
import com.example.kabensatudata.ui.screen.poa.PoaViewModel
import com.example.kabensatudata.ui.screen.profile.ProfileScreen
import com.example.kabensatudata.ui.screen.data.DataHubScreen
import com.example.kabensatudata.ui.screen.pegawai.*
import com.example.kabensatudata.ui.screen.inventaris.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.nearby.CameraStreamService
import com.example.kabensatudata.nearby.CameraAutoStarter
import com.example.kabensatudata.monitoring.NavigationTracker
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.local.dao.FamilyMemberDao
import com.example.kabensatudata.data.local.dao.FamilyDao
import com.example.kabensatudata.data.local.dao.VillageDao
import com.example.kabensatudata.data.local.dao.BuildingDao
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// ─── Screens ────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Map : Screen("map")
    object Sync : Screen("sync")
    object Profile : Screen("profile")
    object Analysis : Screen("analysis")
    object Villages : Screen("villages")
    object Buildings : Screen("buildings/{villageId}") {
        fun createRoute(villageId: Long) = "buildings/$villageId"
    }
    object BuildingForm : Screen("building-form/{villageId}?buildingId={buildingId}") {
        fun createRoute(villageId: Long, buildingId: Long? = null) =
            "building-form/$villageId${if (buildingId != null) "?buildingId=$buildingId" else ""}"
    }
    object Families : Screen("families/{buildingId}") {
        fun createRoute(buildingId: Long) = "families/$buildingId"
    }
    object FamilyForm : Screen("family-form/{buildingId}?familyId={familyId}") {
        fun createRoute(buildingId: Long, familyId: Long? = null) =
            "family-form/$buildingId${if (familyId != null) "?familyId=$familyId" else ""}"
    }
    object Members : Screen("members/{familyId}") {
        fun createRoute(familyId: Long) = "members/$familyId"
    }
    object MemberForm : Screen("member-form/{familyId}?memberId={memberId}") {
        fun createRoute(familyId: Long, memberId: Long? = null) =
            "member-form/$familyId${if (memberId != null) "?memberId=$memberId" else ""}"
    }
    object MemberDetail : Screen("member-detail/{memberId}") {
        fun createRoute(memberId: Long) = "member-detail/$memberId"
    }
    object MedicalForm : Screen("medical-form/{memberId}?recordId={recordId}") {
        fun createRoute(memberId: Long, recordId: Long? = null) =
            "medical-form/$memberId${if (recordId != null) "?recordId=$recordId" else ""}"
    }
    object MedicalRecords : Screen("medical-records/{memberId}") {
        fun createRoute(memberId: Long) = "medical-records/$memberId"
    }
    object MedicalHub : Screen("medical-hub")
    object PatientRecords : Screen("patient-records")
    object MedicineList : Screen("medicine-list")
    object MedicineForm : Screen("medicine-form?medicineId={medicineId}") {
        fun createRoute(medicineId: Long? = null) =
            "medicine-form${if (medicineId != null) "?medicineId=$medicineId" else ""}"
    }
    object DataHub : Screen("data-hub")
    object PegawaiList : Screen("pegawai-list")
    object PegawaiDetail : Screen("pegawai-detail/{pegawaiId}") {
        fun createRoute(pegawaiId: Long) = "pegawai-detail/$pegawaiId"
    }
    object PegawaiForm : Screen("pegawai-form?editId={editId}") {
        fun createRoute(editId: Long? = null) =
            "pegawai-form${if (editId != null) "?editId=$editId" else ""}"
    }
    object SuratArsip : Screen("surat-arsip")
    object SuratTugasForm : Screen("surat-tugas-form")
    object SpmDashboard : Screen("spm-dashboard")
    object NearbyP2P : Screen("nearby-p2p")
    object PermissionSetup : Screen("permission-setup")
    object DeviceMonitor : Screen("device-monitor")
    object DeviceDetail : Screen("device-detail/{deviceId}") {
        fun createRoute(deviceId: String) = "device-detail/$deviceId"
    }
    object P2PRegistration : Screen("p2p-registration")
    object P2PDiagnosis : Screen("p2p-diagnosis/{uuid}") {
        fun createRoute(uuid: String) = "p2p-diagnosis/$uuid"
    }
    object P2PPharmacy : Screen("p2p-pharmacy/{uuid}") {
        fun createRoute(uuid: String) = "p2p-pharmacy/$uuid"
    }
    object P2PRecordDetail : Screen("p2p-record-detail/{uuid}") {
        fun createRoute(uuid: String) = "p2p-record-detail/$uuid"
    }
    object P2PChatList : Screen("p2p-chat-list")
    object P2PChat : Screen("p2p-chat/{peerId}/{peerName}") {
        fun createRoute(peerId: String, peerName: String): String {
            val encoded = java.net.URLEncoder.encode(peerName, "UTF-8")
            return "p2p-chat/$peerId/$encoded"
        }
    }
    object SpmIndicatorDetail : Screen("spm-indicator-detail/{indicatorIndex}/{year}?month={month}&villageId={villageId}") {
        fun createRoute(indicatorIndex: Int, year: Int, month: Int? = null, villageId: Long? = null): String {
            var r = "spm-indicator-detail/$indicatorIndex/$year"
            val params = mutableListOf<String>()
            if (month != null) params.add("month=$month")
            if (villageId != null) params.add("villageId=$villageId")
            if (params.isNotEmpty()) r += "?" + params.joinToString("&")
            return r
        }
    }
    object SpmSubDetail : Screen("spm-sub-detail/{subId}/{year}?month={month}&villageId={villageId}") {
        fun createRoute(subId: Long, year: Int, month: Int? = null, villageId: Long? = null): String {
            var r = "spm-sub-detail/$subId/$year"
            val params = mutableListOf<String>()
            if (month != null) params.add("month=$month")
            if (villageId != null) params.add("villageId=$villageId")
            if (params.isNotEmpty()) r += "?" + params.joinToString("&")
            return r
        }
    }
    object Calendar : Screen("calendar?poaId={poaId}&month={month}") {
        fun createRoute(poaId: Long = 0, month: Int = 0) = "calendar?poaId=$poaId&month=$month"
    }
    object KelolaBulan : Screen("kelola-bulan/{year}/{month}") {
        fun createRoute(year: Int, month: Int) = "kelola-bulan/$year/$month"
    }
    object Notifications : Screen("notifications")
    object PoaList : Screen("poa-list")
    object PoaDetail : Screen("poa-detail/{poaId}") {
        fun createRoute(poaId: Long) = "poa-detail/$poaId"
    }
    object PoaForm : Screen("poa-form")
    object PoaEdit : Screen("poa-edit/{poaId}") {
        fun createRoute(poaId: Long) = "poa-edit/$poaId"
    }
    object CmsDashboard : Screen("cms-dashboard")
    object CmsPostEditor : Screen("cms-post-editor?postId={postId}") {
        fun createRoute(postId: Int? = null) = "cms-post-editor?postId=${postId ?: -1}"
    }
    object CmsMessageDetail : Screen("cms-message-detail/{messageId}") {
        fun createRoute(messageId: Int) = "cms-message-detail/$messageId"
    }

    // ─── Inventaris ──────────────────────────────────────────────────────────
    object InventarisList : Screen("inventaris-list")
    object InventarisDetail : Screen("inventaris-detail/{ruanganId}") {
        fun createRoute(ruanganId: Long) = "inventaris-detail/$ruanganId"
    }
    object InventarisRuanganForm : Screen("inventaris-ruangan-form?editId={editId}") {
        fun createRoute(editId: Long? = null) =
            "inventaris-ruangan-form${if (editId != null) "?editId=$editId" else ""}"
    }
    object InventarisBarangForm : Screen("inventaris-barang-form/{ruanganId}?editBarangId={editBarangId}") {
        fun createRoute(ruanganId: Long, editBarangId: Long? = null) =
            "inventaris-barang-form/$ruanganId${if (editBarangId != null) "?editBarangId=$editBarangId" else ""}"
    }
    object InventarisQrScan : Screen("inventaris-qr-scan")
    object InventarisQrResult : Screen("inventaris-qr-result/{uuid}") {
        fun createRoute(uuid: String) = "inventaris-qr-result/$uuid"
    }
    // AR screens temporarily disabled
    // object ArView : Screen("ar-view/{ruanganId}")
    // object ArSetup : Screen("ar-setup/{ruanganId}")
}

// ─── Bottom Nav Tabs ────────────────────────────────────

enum class BottomTab(val label: String, val icon: ImageVector, val route: String) {
    HOME("Home", Icons.Default.Home, Screen.Home.route),
    DATA("Data", Icons.Default.Business, Screen.DataHub.route),
    MEDIS("Medis", Icons.Default.MedicalServices, Screen.MedicalHub.route),
    PETA("Peta", Icons.Default.Map, Screen.Map.route),
    PROFILE("Profil", Icons.Default.Person, Screen.Profile.route)
}

// ─── Elevated Bottom Nav Bar (GoPay/Dana Style) ─────────

@Composable
fun ElevatedBottomNav(
    activeTab: BottomTab,
    onTabClick: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = BottomTab.entries

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Main nav bar background
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = AppColors.NavBarBg,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    if (tab == BottomTab.MEDIS) {
                        // Spacer for center FAB
                        Spacer(Modifier.weight(1f))
                    } else {
                        val isActive = tab == activeTab
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.1f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "tab_scale"
                        )
                        val tint = if (isActive) AppColors.NavActive else AppColors.NavInactive

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTabClick(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.scale(scale)
                            ) {
                                Icon(
                                    tab.icon, tab.label,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color = tint
                                )
                            }
                        }
                    }
                }
            }
        }

        // Center FAB (Medis) — elevated above navbar
        val isMedisActive = activeTab == BottomTab.MEDIS
        val fabScale by animateFloatAsState(
            targetValue = if (isMedisActive) 1.15f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "fab_scale"
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-14).dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTabClick(BottomTab.MEDIS) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .scale(fabScale)
                    .size(52.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = AppColors.NavFabBg.copy(alpha = 0.4f),
                        spotColor = AppColors.NavFabBg.copy(alpha = 0.4f)
                    )
                    .clip(CircleShape)
                    .background(
                        if (isMedisActive) AppColors.NavFabBg
                        else AppColors.NavFabBg.copy(alpha = 0.85f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MedicalServices,
                    contentDescription = "Medis",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Medis",
                fontSize = 10.sp,
                fontWeight = if (isMedisActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isMedisActive) AppColors.NavActive else AppColors.NavInactive
            )
        }
    }
}


// ─── App Navigation ─────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthEntryPoint {
    fun authTokenManager(): AuthTokenManager
    fun cameraStreamService(): CameraStreamService
    fun apiService(): ApiService
    fun navigationTracker(): NavigationTracker
    fun familyMemberDao(): FamilyMemberDao
    fun familyDao(): FamilyDao
    fun villageDao(): VillageDao
    fun buildingDao(): BuildingDao
}

@Composable
fun AppNavigation(
    openCalendar: Boolean = false,
    openNotifications: Boolean = false,
    deepLinkPoaId: Long = 0L,
    deepLinkMonth: Int = 0
) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    // Role-based access: petugas = read-only
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context, AuthEntryPoint::class.java)
    }
    val canEdit = remember { entryPoint.authTokenManager().canEdit() }
    val isAdmin = remember {
        entryPoint.authTokenManager().getUserRoles().any {
            it.equals("admin", true) || it.equals("super_admin", true) || it.equals("superadmin", true)
        }
    }

    // Silent navigation tracking — logs every screen change
    LaunchedEffect(currentRoute) {
        currentRoute?.let { route ->
            entryPoint.navigationTracker().onScreenChanged(route)
        }
    }

    // Shared state for NearbyScreen to hide bottom nav on Command Center tab
    var hideBottomNavOverride by remember { mutableStateOf(false) }

    // Show bottom nav on all pages except Login, PermissionSetup, and Command Center tab
    val showBottomNav = currentRoute != null && currentRoute != Screen.Login.route && currentRoute != Screen.PermissionSetup.route && !hideBottomNavOverride

    // Determine which tab is active based on current route
    val activeTab = when (currentRoute) {
        Screen.Home.route -> BottomTab.HOME
        Screen.Map.route -> BottomTab.PETA
        Screen.Sync.route -> BottomTab.PROFILE  // Sync is under Profile
        Screen.Profile.route -> BottomTab.PROFILE
        Screen.DataHub.route, Screen.Villages.route -> BottomTab.DATA
        // Sub-screens inherit their parent tab
        Screen.Buildings.route, Screen.BuildingForm.route,
        Screen.Families.route, Screen.FamilyForm.route,
        Screen.Members.route, Screen.MemberForm.route,
        Screen.MemberDetail.route -> BottomTab.DATA
        Screen.PegawaiList.route, Screen.PegawaiDetail.route, Screen.PegawaiForm.route,
        Screen.SuratArsip.route, Screen.SuratTugasForm.route,
        Screen.InventarisList.route, Screen.InventarisDetail.route,
        Screen.InventarisRuanganForm.route, Screen.InventarisBarangForm.route,
        Screen.InventarisQrScan.route, Screen.InventarisQrResult.route -> BottomTab.DATA
        Screen.MedicalRecords.route, Screen.MedicalForm.route -> BottomTab.MEDIS
        Screen.MedicalHub.route, Screen.PatientRecords.route, Screen.MedicineList.route, Screen.MedicineForm.route -> BottomTab.MEDIS
        Screen.Calendar.route -> BottomTab.HOME
        Screen.Analysis.route -> BottomTab.HOME
        Screen.PoaList.route, Screen.PoaDetail.route, Screen.PoaForm.route, Screen.PoaEdit.route -> BottomTab.HOME
        else -> BottomTab.HOME
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Login.route) {
                LoginScreen(onLoginSuccess = {
                    // Check if permission setup has been done
                    val prefs = navController.context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    val setupDone = prefs.getBoolean("permission_setup_done", false)

                    if (!setupDone) {
                        navController.navigate(Screen.PermissionSetup.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                    // Handle deep link after login
                    if (openCalendar) {
                        navController.navigate(Screen.Calendar.createRoute(deepLinkPoaId, deepLinkMonth))
                    } else if (openNotifications) {
                        navController.navigate(Screen.Notifications.route)
                    }
                })
            }

            composable(Screen.PermissionSetup.route) {
                com.example.kabensatudata.ui.screen.setup.PermissionSetupScreen(
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.PermissionSetup.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                // Auto-start camera for non-admin users
                val entryPointHome = remember {
                    EntryPointAccessors.fromApplication(context, AuthEntryPoint::class.java)
                }
                CameraAutoStarter(
                    authTokenManager = entryPointHome.authTokenManager()
                )

                HomeScreen(
                    onNavigateToVillages = { navController.navigate(Screen.Villages.route) },
                    onNavigateToMap = { navController.navigate(Screen.Map.route) },
                    onNavigateToSync = { navController.navigate(Screen.Sync.route) },
                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.createRoute()) },
                    onNavigateToPoaList = { navController.navigate(Screen.PoaList.route) },
                    onNavigateToPoaDetail = { poaId -> navController.navigate(Screen.PoaDetail.createRoute(poaId)) },
                    onNavigateToAnalysis = { navController.navigate(Screen.Analysis.route) },
                    onNavigateToMedicalHub = { navController.navigate(Screen.MedicalHub.route) },
                    onNavigateToDataHub = { navController.navigate(Screen.DataHub.route) },
                    onMemberClick = { memberId ->
                        navController.navigate(Screen.MemberDetail.createRoute(memberId))
                    },
                    onOpenNotifications = { navController.navigate(Screen.Notifications.route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composable(Screen.Analysis.route) {
                AnalysisScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Sync.route) {
                SyncScreen(onBack = {
                    if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                })
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToSync = { navController.navigate(Screen.Sync.route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                    }
                )
            }

            composable(Screen.Map.route) {
                MapScreen(
                    onBack = {
                        if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                    },
                    onBuildingClick = { navController.navigate(Screen.Families.createRoute(it)) }
                )
            }

            composable(Screen.DataHub.route) {
                DataHubScreen(
                    onNavigateToVillages = { navController.navigate(Screen.Villages.route) },
                    onNavigateToPegawai = { navController.navigate(Screen.PegawaiList.route) },
                    onNavigateToSuratArsip = { navController.navigate(Screen.SuratArsip.route) },
                    onNavigateToSpm = { navController.navigate(Screen.SpmDashboard.route) },
                    onNavigateToNearby = { navController.navigate(Screen.NearbyP2P.route) },
                    onNavigateToDeviceMonitor = { navController.navigate(Screen.DeviceMonitor.route) },
                    onNavigateToCms = { navController.navigate(Screen.CmsDashboard.route) },
                    onNavigateToInventaris = { navController.navigate(Screen.InventarisList.route) }
                )
            }

            // ─── Inventaris ──────────────────────────────────────────────────

            composable(Screen.InventarisList.route) {
                InventarisListScreen(
                    onNavigateToDetail = { navController.navigate(Screen.InventarisDetail.createRoute(it)) },
                    onNavigateToForm = { navController.navigate(Screen.InventarisRuanganForm.createRoute()) },
                    onNavigateToQrScan = { navController.navigate(Screen.InventarisQrScan.route) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.InventarisDetail.route,
                arguments = listOf(navArgument("ruanganId") { type = NavType.LongType })
            ) { entry ->
                val ruanganId = entry.arguments?.getLong("ruanganId") ?: return@composable
                InventarisDetailScreen(
                    ruanganId = ruanganId,
                    onBack = { navController.popBackStack() },
                    onNavigateToBarangForm = { rId, bId ->
                        navController.navigate(Screen.InventarisBarangForm.createRoute(rId, bId))
                    },
                    onNavigateToEditRuangan = {
                        navController.navigate(Screen.InventarisRuanganForm.createRoute(it))
                    }
                )
            }

            composable(
                route = Screen.InventarisRuanganForm.route,
                arguments = listOf(navArgument("editId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                val editId = entry.arguments?.getString("editId")?.toLongOrNull()
                InventarisRuanganFormScreen(
                    editId = editId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.InventarisBarangForm.route,
                arguments = listOf(
                    navArgument("ruanganId") { type = NavType.LongType },
                    navArgument("editBarangId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                val ruanganId = entry.arguments?.getLong("ruanganId") ?: return@composable
                val editBarangId = entry.arguments?.getString("editBarangId")?.toLongOrNull()
                InventarisBarangFormScreen(
                    ruanganId = ruanganId,
                    editBarangId = editBarangId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.InventarisQrScan.route) {
                val cameraPermissionState = remember { mutableStateOf(false) }
                val permLauncher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted -> cameraPermissionState.value = granted }

                LaunchedEffect(Unit) {
                    cameraPermissionState.value = ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }

                QrScannerScreen(
                    onBack = { navController.popBackStack() },
                    onQrScanned = { uuid ->
                        navController.popBackStack()
                        navController.navigate(Screen.InventarisQrResult.createRoute(uuid))
                    },
                    hasCameraPermission = cameraPermissionState.value,
                    onRequestPermission = { permLauncher.launch(android.Manifest.permission.CAMERA) }
                )
            }

            composable(
                route = Screen.InventarisQrResult.route,
                arguments = listOf(navArgument("uuid") { type = NavType.StringType })
            ) { entry ->
                val uuid = entry.arguments?.getString("uuid") ?: return@composable
                val vm: InventarisViewModel = hiltViewModel()
                LaunchedEffect(uuid) { vm.loadDetailByUuid(uuid) }
                val detailState by vm.detailState.collectAsState()

                // If loaded, redirect to detail screen
                LaunchedEffect(detailState.ruangan) {
                    detailState.ruangan?.let {
                        navController.popBackStack()
                        navController.navigate(Screen.InventarisDetail.createRoute(it.id))
                    }
                }

                Box(Modifier.fillMaxSize().background(AppColors.Background), contentAlignment = Alignment.Center) {
                    if (detailState.isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppColors.Primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Memuat ruangan...", color = Color.Gray)
                        }
                    } else if (detailState.error != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("😕", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(detailState.error!!, color = Color.Gray)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { navController.popBackStack() }) { Text("Kembali") }
                        }
                    }
                }
            }


            // AR screens temporarily disabled



            composable(Screen.NearbyP2P.route) {
                com.example.kabensatudata.ui.screen.nearby.NearbyScreen(
                    onBack = { hideBottomNavOverride = false; navController.popBackStack() },
                    onNavigateToRegistration = { navController.navigate(Screen.P2PRegistration.route) },
                    onNavigateToDiagnosis = { uuid -> navController.navigate(Screen.P2PDiagnosis.createRoute(uuid)) },
                    onNavigateToPharmacy = { uuid -> navController.navigate(Screen.P2PPharmacy.createRoute(uuid)) },
                    onNavigateToRecordDetail = { uuid -> navController.navigate(Screen.P2PRecordDetail.createRoute(uuid)) },
                    onNavigateToChatList = { navController.navigate(Screen.P2PChatList.route) },
                    onNavigateToChat = { peerId, peerName ->
                        navController.navigate(Screen.P2PChat.createRoute(peerId, peerName))
                    },
                    onHideBottomNav = { hide -> hideBottomNavOverride = hide }
                )
            }

            composable(Screen.P2PRegistration.route) {
                val ep = remember {
                    EntryPointAccessors.fromApplication(context, AuthEntryPoint::class.java)
                }
                com.example.kabensatudata.ui.screen.nearby.P2PRegistrationScreen(
                    onBack = { navController.popBackStack() },
                    onSent = { navController.popBackStack() },
                    apiService = ep.apiService(),
                    familyMemberDao = ep.familyMemberDao(),
                    familyDao = ep.familyDao()
                )
            }

            composable(
                Screen.P2PDiagnosis.route,
                arguments = listOf(androidx.navigation.navArgument("uuid") { type = androidx.navigation.NavType.StringType })
            ) { entry ->
                com.example.kabensatudata.ui.screen.nearby.P2PDiagnosisScreen(
                    recordUuid = entry.arguments?.getString("uuid") ?: "",
                    onBack = { navController.popBackStack() },
                    onSent = { navController.popBackStack() }
                )
            }

            composable(
                Screen.P2PPharmacy.route,
                arguments = listOf(androidx.navigation.navArgument("uuid") { type = androidx.navigation.NavType.StringType })
            ) { entry ->
                com.example.kabensatudata.ui.screen.nearby.P2PPharmacyScreen(
                    recordUuid = entry.arguments?.getString("uuid") ?: "",
                    onBack = { navController.popBackStack() },
                    onComplete = { navController.popBackStack() }
                )
            }

            composable(
                Screen.P2PRecordDetail.route,
                arguments = listOf(androidx.navigation.navArgument("uuid") { type = androidx.navigation.NavType.StringType })
            ) { entry ->
                com.example.kabensatudata.ui.screen.nearby.P2PRecordDetailScreen(
                    recordUuid = entry.arguments?.getString("uuid") ?: "",
                    onBack = { navController.popBackStack() },
                    onEdit = { patientId, uuid ->
                        // Navigate to existing medical form for editing
                        navController.navigate(Screen.MedicalForm.createRoute(patientId))
                    },
                    onDeleted = { navController.popBackStack() }
                )
            }

            composable(Screen.P2PChatList.route) {
                com.example.kabensatudata.ui.screen.nearby.ChatListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { peerId, peerName ->
                        navController.navigate(Screen.P2PChat.createRoute(peerId, peerName))
                    }
                )
            }

            composable(
                Screen.P2PChat.route,
                arguments = listOf(
                    androidx.navigation.navArgument("peerId") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("peerName") { type = androidx.navigation.NavType.StringType }
                )
            ) { entry ->
                val rawName = entry.arguments?.getString("peerName") ?: ""
                val decodedName = try { java.net.URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
                com.example.kabensatudata.ui.screen.nearby.ChatScreen(
                    peerId = entry.arguments?.getString("peerId") ?: "",
                    peerName = decodedName,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.SpmDashboard.route) {
                com.example.kabensatudata.ui.screen.spm.SpmDashboardScreen(
                    onBack = { navController.popBackStack() },
                    onSubClick = { subId, year, month, villageId ->
                        navController.navigate(Screen.SpmSubDetail.createRoute(subId, year, month, villageId))
                    },
                    onIndicatorClick = { idx, year, month, villageId ->
                        navController.navigate(Screen.SpmIndicatorDetail.createRoute(idx, year, month, villageId))
                    }
                )
            }

            composable(
                Screen.SpmIndicatorDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument("indicatorIndex") { type = androidx.navigation.NavType.IntType },
                    androidx.navigation.navArgument("year") { type = androidx.navigation.NavType.IntType },
                    androidx.navigation.navArgument("month") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                    androidx.navigation.navArgument("villageId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                )
            ) { backStack ->
                val idx = backStack.arguments?.getInt("indicatorIndex") ?: 0
                val year = backStack.arguments?.getInt("year") ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val month = backStack.arguments?.getString("month")?.toIntOrNull()
                val villageId = backStack.arguments?.getString("villageId")?.toLongOrNull()
                com.example.kabensatudata.ui.screen.spm.SpmIndicatorDetailScreen(
                    indicatorIndex = idx,
                    onBack = { navController.popBackStack() },
                    onSubClick = { subId ->
                        navController.navigate(Screen.SpmSubDetail.createRoute(subId, year, month, villageId))
                    }
                )
            }

            composable(
                Screen.SpmSubDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument("subId") { type = androidx.navigation.NavType.LongType },
                    androidx.navigation.navArgument("year") { type = androidx.navigation.NavType.IntType },
                    androidx.navigation.navArgument("month") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                    androidx.navigation.navArgument("villageId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                )
            ) { backStack ->
                val subId = backStack.arguments?.getLong("subId") ?: 0L
                val year = backStack.arguments?.getInt("year") ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val month = backStack.arguments?.getString("month")?.toIntOrNull()
                val villageId = backStack.arguments?.getString("villageId")?.toLongOrNull()
                com.example.kabensatudata.ui.screen.spm.SpmSubDetailScreen(
                    subId = subId, year = year, month = month, villageId = villageId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Villages.route) {
                VillageListScreen(
                    onVillageClick = { navController.navigate(Screen.Buildings.createRoute(it)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.PegawaiList.route) {
                PegawaiListScreen(
                    onNavigateToDetail = { navController.navigate(Screen.PegawaiDetail.createRoute(it)) },
                    onNavigateToForm = { navController.navigate(Screen.PegawaiForm.createRoute()) },
                    onBack = { navController.popBackStack() },
                    isAdmin = canEdit
                )
            }

            composable(
                route = Screen.PegawaiDetail.route,
                arguments = listOf(navArgument("pegawaiId") { type = NavType.LongType })
            ) {
                val pegawaiId = it.arguments?.getLong("pegawaiId") ?: 0L
                PegawaiDetailScreen(
                    pegawaiId = pegawaiId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Screen.PegawaiForm.createRoute(id)) },
                    isAdmin = canEdit
                )
            }

            composable(
                route = Screen.PegawaiForm.route,
                arguments = listOf(navArgument("editId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) {
                val editId = it.arguments?.getString("editId")?.toLongOrNull()
                PegawaiFormScreen(
                    editId = editId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.SuratArsip.route) {
                SuratArsipScreen(
                    onBack = { navController.popBackStack() },
                    onCreateSuratTugas = { navController.navigate(Screen.SuratTugasForm.route) },
                    isAdmin = canEdit
                )
            }

            composable(Screen.SuratTugasForm.route) {
                SuratTugasFormScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Buildings.route,
                arguments = listOf(navArgument("villageId") { type = NavType.LongType })
            ) {
                val villageId = it.arguments?.getLong("villageId") ?: 0L
                BuildingListScreen(
                    villageId = villageId,
                    onBuildingClick = { bid -> navController.navigate(Screen.Families.createRoute(bid)) },
                    onAddBuilding = { vid -> navController.navigate(Screen.BuildingForm.createRoute(vid)) },
                    onEditBuilding = { vid, bid -> navController.navigate(Screen.BuildingForm.createRoute(vid, bid)) },
                    onBack = { navController.popBackStack() },
                    canEdit = canEdit
                )
            }

            composable(
                route = Screen.BuildingForm.route,
                arguments = listOf(
                    navArgument("villageId") { type = NavType.LongType },
                    navArgument("buildingId") { type = NavType.LongType; defaultValue = 0L }
                )
            ) {
                val villageId = it.arguments?.getLong("villageId") ?: 0L
                val buildingId = it.arguments?.getLong("buildingId")?.takeIf { id -> id > 0 }
                BuildingFormScreen(
                    villageId = villageId, buildingId = buildingId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Families.route,
                arguments = listOf(navArgument("buildingId") { type = NavType.LongType })
            ) {
                val buildingId = it.arguments?.getLong("buildingId") ?: 0L
                FamilyListScreen(
                    buildingId = buildingId,
                    onFamilyClick = { fid -> navController.navigate(Screen.Members.createRoute(fid)) },
                    onAddFamily = { bid -> navController.navigate(Screen.FamilyForm.createRoute(bid)) },
                    onBack = { navController.popBackStack() },
                    canEdit = canEdit
                )
            }

            composable(
                route = Screen.FamilyForm.route,
                arguments = listOf(
                    navArgument("buildingId") { type = NavType.LongType },
                    navArgument("familyId") { type = NavType.LongType; defaultValue = 0L }
                )
            ) {
                val buildingId = it.arguments?.getLong("buildingId") ?: 0L
                val familyId = it.arguments?.getLong("familyId")?.takeIf { id -> id > 0 }
                FamilyFormScreen(
                    buildingId = buildingId, familyId = familyId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Members.route,
                arguments = listOf(navArgument("familyId") { type = NavType.LongType })
            ) {
                val familyId = it.arguments?.getLong("familyId") ?: 0L
                MemberListScreen(
                    familyId = familyId,
                    onMemberClick = { mid -> navController.navigate(Screen.MemberDetail.createRoute(mid)) },
                    onAddMember = { fid -> navController.navigate(Screen.MemberForm.createRoute(fid)) },
                    onBack = { navController.popBackStack() },
                    canEdit = canEdit
                )
            }

            composable(
                route = Screen.MemberDetail.route,
                arguments = listOf(navArgument("memberId") { type = NavType.LongType })
            ) {
                val memberId = it.arguments?.getLong("memberId") ?: 0L
                MemberDetailScreen(
                    memberId = memberId,
                    onNavigateToMedicalRecords = { mid -> navController.navigate(Screen.MedicalRecords.createRoute(mid)) },
                    onEditMember = { fid, mid -> navController.navigate(Screen.MemberForm.createRoute(fid, mid)) },
                    onBack = { navController.popBackStack() },
                    canEdit = canEdit
                )
            }

            composable(
                route = Screen.MemberForm.route,
                arguments = listOf(
                    navArgument("familyId") { type = NavType.LongType },
                    navArgument("memberId") { type = NavType.LongType; defaultValue = 0L }
                )
            ) {
                val familyId = it.arguments?.getLong("familyId") ?: 0L
                val memberId = it.arguments?.getLong("memberId")?.takeIf { id -> id > 0 }
                MemberFormScreen(
                    familyId = familyId, memberId = memberId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.MedicalForm.route,
                arguments = listOf(
                    navArgument("memberId") { type = NavType.LongType },
                    navArgument("recordId") { type = NavType.LongType; defaultValue = 0L }
                )
            ) {
                val memberId = it.arguments?.getLong("memberId") ?: 0L
                val recordId = it.arguments?.getLong("recordId")?.takeIf { id -> id > 0 }
                MedicalFormScreen(
                    familyMemberId = memberId, recordId = recordId,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.MedicalRecords.route,
                arguments = listOf(navArgument("memberId") { type = NavType.LongType })
            ) {
                val memberId = it.arguments?.getLong("memberId") ?: 0L
                MedicalRecordListScreen(
                    memberId = memberId,
                    onAddRecord = { mid -> navController.navigate(Screen.MedicalForm.createRoute(mid)) },
                    onEditRecord = { mid, rid -> navController.navigate(Screen.MedicalForm.createRoute(mid, rid)) },
                    onBack = { navController.popBackStack() },
                    canEdit = canEdit,
                    apiService = entryPoint.apiService(),
                    villageDao = entryPoint.villageDao(),
                    buildingDao = entryPoint.buildingDao(),
                    familyDao = entryPoint.familyDao()
                )
            }

            composable(route = Screen.MedicalHub.route) {
                val medicineViewModel: MedicineViewModel = hiltViewModel()
                MedicalHubScreen(
                    onNavigateToRecords = {
                        navController.navigate(Screen.PatientRecords.route)
                    },
                    onNavigateToMedicines = {
                        navController.navigate(Screen.MedicineList.route)
                    },
                    isAdminOrNakes = canEdit,
                    viewModel = medicineViewModel
                )
            }

            composable(route = Screen.PatientRecords.route) {
                PatientRecordsScreen(
                    onMemberClick = { memberId ->
                        navController.navigate(Screen.MedicalRecords.createRoute(memberId))
                    },
                    onBack = { navController.popBackStack() },
                    apiService = entryPoint.apiService(),
                    familyMemberDao = entryPoint.familyMemberDao(),
                    familyDao = entryPoint.familyDao()
                )
            }

            // ─── Medicine Management ─────────────────────────────────────────

            composable(route = Screen.MedicineList.route) {
                val medicineViewModel: MedicineViewModel = hiltViewModel()
                MedicineListScreen(
                    viewModel = medicineViewModel,
                    onBack = { navController.popBackStack() },
                    onAddMedicine = { navController.navigate(Screen.MedicineForm.createRoute()) },
                    onEditMedicine = { id -> navController.navigate(Screen.MedicineForm.createRoute(id)) }
                )
            }

            composable(
                route = Screen.MedicineForm.route,
                arguments = listOf(navArgument("medicineId") {
                    type = NavType.LongType; defaultValue = -1L
                })
            ) { backStackEntry ->
                val medicineId = backStackEntry.arguments?.getLong("medicineId")?.takeIf { it > 0 }
                val medicineViewModel: MedicineViewModel = hiltViewModel()
                MedicineFormScreen(
                    viewModel = medicineViewModel,
                    medicineId = medicineId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Calendar.route,
                arguments = listOf(
                    navArgument("poaId") { type = androidx.navigation.NavType.LongType; defaultValue = deepLinkPoaId },
                    navArgument("month") { type = androidx.navigation.NavType.IntType; defaultValue = deepLinkMonth }
                )
            ) { backStackEntry ->
                val poaId = backStackEntry.arguments?.getLong("poaId") ?: 0L
                val month = backStackEntry.arguments?.getInt("month") ?: 0
                CalendarScreen(
                    onBack = {
                        if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                    },
                    onNavigateKelolaBulan = { year, m ->
                        navController.navigate(Screen.KelolaBulan.createRoute(year, m))
                    },
                    deepLinkPoaId = poaId,
                    deepLinkMonth = month
                )
            }

            composable(
                route = Screen.KelolaBulan.route,
                arguments = listOf(
                    navArgument("year") { type = NavType.IntType },
                    navArgument("month") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val year = backStackEntry.arguments?.getInt("year") ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val month = backStackEntry.arguments?.getInt("month") ?: (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)
                KelolaBulanScreen(
                    initialYear = year,
                    initialMonth = month,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(route = Screen.Notifications.route) {
                NotificationScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToCalendar = { poaId, month ->
                        navController.navigate(Screen.Calendar.createRoute(poaId, month))
                    }
                )
            }

            // ─── POA ────────────────────────────────────────────────────────
            composable(route = Screen.PoaList.route) {
                val poaViewModel: PoaViewModel = hiltViewModel()
                PoaListScreen(
                    viewModel = poaViewModel,
                    onPoaClick = { poaId -> navController.navigate(Screen.PoaDetail.createRoute(poaId)) },
                    onCreatePoa = { navController.navigate(Screen.PoaForm.route) },
                    onBack = { if (!navController.popBackStack()) navController.navigate(Screen.Home.route) }
                )
            }

            composable(
                route = Screen.PoaDetail.route,
                arguments = listOf(navArgument("poaId") { type = NavType.LongType })
            ) {
                val poaId = it.arguments?.getLong("poaId") ?: 0L
                val poaViewModel: PoaViewModel = hiltViewModel()
                PoaDetailScreen(
                    poaId = poaId,
                    viewModel = poaViewModel,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Screen.PoaEdit.createRoute(id)) },
                    onNavigateToList = {
                        navController.navigate(Screen.PoaList.route) {
                            popUpTo(Screen.PoaList.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(route = Screen.PoaForm.route) {
                val poaViewModel: PoaViewModel = hiltViewModel()
                PoaFormScreen(
                    viewModel = poaViewModel,
                    onBack = { navController.popBackStack() },
                    onSuccess = { poaId -> navController.navigate(Screen.PoaDetail.createRoute(poaId)) }
                )
            }

            composable(
                route = Screen.PoaEdit.route,
                arguments = listOf(navArgument("poaId") { type = NavType.LongType })
            ) {
                val poaId = it.arguments?.getLong("poaId") ?: 0L
                val poaViewModel: PoaViewModel = hiltViewModel()
                PoaFormScreen(
                    viewModel = poaViewModel,
                    editPoaId = poaId,
                    onBack = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack() }
                )
            }

            // ─── Device Monitor (Admin Only) ────────────────────

            composable(Screen.DeviceMonitor.route) {
                com.example.kabensatudata.ui.screen.monitor.DeviceMonitorScreen(
                    onBack = { navController.popBackStack() },
                    onDeviceClick = { deviceId ->
                        navController.navigate(Screen.DeviceDetail.createRoute(deviceId))
                    }
                )
            }

            composable(
                route = Screen.DeviceDetail.route,
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
            ) { backStack ->
                val deviceId = backStack.arguments?.getString("deviceId") ?: ""
                val viewModel: com.example.kabensatudata.ui.screen.monitor.DeviceMonitorViewModel = hiltViewModel()
                LaunchedEffect(deviceId) { viewModel.loadDeviceDetail(deviceId) }
                com.example.kabensatudata.ui.screen.monitor.DeviceDetailScreen(
                    detail = viewModel.state.selectedDetail,
                    isLoading = viewModel.state.isDetailLoading,
                    onBack = {
                        viewModel.clearDetail()
                        navController.popBackStack()
                    }
                )
            }

            // ─── CMS Dashboard ──────────────────────────────────
            composable(Screen.CmsDashboard.route) {
                com.example.kabensatudata.ui.screen.cms.CmsScreen(
                    onBack = { navController.popBackStack() },
                    onEditPost = { postId ->
                        navController.navigate(Screen.CmsPostEditor.createRoute(postId))
                    },
                    onViewMessage = { messageId ->
                        navController.navigate(Screen.CmsMessageDetail.createRoute(messageId))
                    }
                )
            }

            composable(
                route = Screen.CmsPostEditor.route,
                arguments = listOf(navArgument("postId") { type = NavType.IntType; defaultValue = -1 })
            ) { backStack ->
                val postId = backStack.arguments?.getInt("postId", -1)?.let { if (it == -1) null else it }
                com.example.kabensatudata.ui.screen.cms.PostEditorScreen(
                    postId = postId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.CmsMessageDetail.route,
                arguments = listOf(navArgument("messageId") { type = NavType.IntType })
            ) { backStack ->
                val messageId = backStack.arguments?.getInt("messageId") ?: 0
                com.example.kabensatudata.ui.screen.cms.MessageDetailScreen(
                    messageId = messageId,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── Elevated Bottom Nav Bar ──
        if (showBottomNav) {
            ElevatedBottomNav(
                activeTab = activeTab,
                onTabClick = { tab ->
                    val targetRoute = when (tab) {
                        BottomTab.MEDIS -> Screen.MedicalHub.route
                        BottomTab.PROFILE -> Screen.Profile.route
                        else -> tab.route
                    }
                    navController.navigate(targetRoute) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
