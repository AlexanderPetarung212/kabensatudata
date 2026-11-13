<?php

use App\Http\Controllers\API\CrosstabController;
use App\Http\Controllers\ChatbotController;
use App\Http\Controllers\MapBuildingController;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

// ─── TEMPORARY DEBUG: Remove after fixing auth ───
if (app()->environment('local')) {
Route::get('/mobile/debug-auth', function (Request $request) {
    if ($request->query('token') !== 'kaben2026') {
        return response()->json(['error' => 'forbidden'], 403);
    }

    $authHeader = $request->header('Authorization');
    $user = null;
    $tokenValid = false;

    if ($authHeader && str_starts_with($authHeader, 'Bearer ')) {
        $token = substr($authHeader, 7);
        $accessToken = \Laravel\Sanctum\PersonalAccessToken::findToken($token);
        if ($accessToken) {
            $user = $accessToken->tokenable;
            $tokenValid = true;
        }
    }

    return response()->json([
        'auth_header_received' => !empty($authHeader),
        'auth_header_value' => $authHeader ? substr($authHeader, 0, 20) . '...' : null,
        'token_valid' => $tokenValid,
        'user' => $user ? ['id' => $user->id, 'name' => $user->name, 'email' => $user->email] : null,
        'server_vars' => [
            'HTTP_AUTHORIZATION' => $_SERVER['HTTP_AUTHORIZATION'] ?? 'NOT SET',
            'REDIRECT_HTTP_AUTHORIZATION' => $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? 'NOT SET',
        ],
        'all_headers' => collect($request->headers->all())->map(fn($v) => $v[0] ?? null)->toArray(),
        'php_sapi' => php_sapi_name(),
        'personal_access_tokens_exist' => \Illuminate\Support\Facades\Schema::hasTable('personal_access_tokens'),
    ]);
});
}

// Route untuk crosstab API
Route::middleware(['auth'])->group(function () {
    Route::get('/crosstab/variables', [CrosstabController::class, 'getVariables']);
    Route::post('/crosstab/data', [CrosstabController::class, 'getData']);
});

// Chatbot API routes
Route::post('/ask-chatbot', [ChatbotController::class, 'ask']);
Route::post('/crawl-website', [ChatbotController::class, 'crawlWebsite']);
Route::post('/upload-pdf', [ChatbotController::class, 'uploadPdf']);

// Map API routes - New BBOX and search endpoints
Route::prefix('map')->group(function () {
    // BBOX endpoint for efficient data loading
    Route::get('/buildings', [MapBuildingController::class, 'bbox'])
        ->name('api.map.buildings.bbox');
    
    // Search building by number
    Route::get('/buildings/find', [MapBuildingController::class, 'find'])
        ->name('api.map.buildings.find');
        
    // Stats endpoint (optional)
    Route::get('/stats', [MapBuildingController::class, 'stats'])
        ->name('api.map.stats');
});

// Aliases to fit frontend contracts
Route::get('/buildings', function (Request $request) {
    // Convert bounds={north,south,east,west} to bbox
    $bounds = $request->query('bounds');
    if ($bounds) {
        $decoded = is_string($bounds) ? json_decode($bounds, true) : $bounds;
        if (is_array($decoded) && isset($decoded['west'], $decoded['south'], $decoded['east'], $decoded['north'])) {
            $bbox = implode(',', [$decoded['west'], $decoded['south'], $decoded['east'], $decoded['north']]);
            $request->merge(['bbox' => $bbox]);
        }
    }
    $controller = app(MapBuildingController::class);
    return $controller->bbox($request);
});

Route::get('/buildings/{id}/families', function ($id) {
    // Reuse web endpoint logic for details
    return app(\App\Http\Controllers\MapController::class)->getBuildingDetails($id);
});

Route::get('/health-statistics/by-area', function (Request $request) {
    // Proxy to stats
    return app(MapBuildingController::class)->stats($request);
});

Route::post('/sync/changes', function (Request $request) {
    // Minimal ack endpoint for offline sync queue
    // TODO: Apply changes to DB if needed
    return response()->json(['ok' => true, 'received' => count($request->input('changes', []))]);
});

// ─── Managed Device Agent API v1 ───────────────────────────────────────────
if (config('command-center.enabled', true)) {
    Route::prefix('agent/v1')
        ->middleware('throttle:agent-enrollment')
        ->group(function () {
            Route::post('/enroll', [\App\Http\Controllers\API\Agent\V1\AgentEnrollmentController::class, 'store']);
            Route::post('/auto-enroll', [\App\Http\Controllers\API\Agent\V1\AgentEnrollmentController::class, 'autoEnroll']);
        });

    Route::prefix('agent/v1')
        ->middleware(['managed-device', 'throttle:agent-api'])
        ->group(function () {
            Route::post('/heartbeat', [\App\Http\Controllers\API\Agent\V1\AgentHeartbeatController::class, 'store']);
            Route::post('/telemetry', [\App\Http\Controllers\API\Agent\V1\AgentTelemetryController::class, 'store']);
            Route::get('/policy', [\App\Http\Controllers\API\Agent\V1\AgentPolicyController::class, 'show']);

            Route::get('/commands/next', [\App\Http\Controllers\API\Agent\V1\AgentCommandController::class, 'next']);
            Route::post('/commands/{command:uuid}/delivered', [\App\Http\Controllers\API\Agent\V1\AgentCommandController::class, 'delivered']);
            Route::post('/commands/{command:uuid}/started', [\App\Http\Controllers\API\Agent\V1\AgentCommandController::class, 'started']);
            Route::post('/commands/{command:uuid}/completed', [\App\Http\Controllers\API\Agent\V1\AgentCommandController::class, 'completed']);
            Route::post('/commands/{command:uuid}/failed', [\App\Http\Controllers\API\Agent\V1\AgentCommandController::class, 'failed']);

            Route::post('/artifacts', [\App\Http\Controllers\API\Agent\V1\AgentArtifactController::class, 'store']);
            Route::get('/releases/latest', [\App\Http\Controllers\API\Agent\V1\AgentReleaseController::class, 'latest']);
            Route::get('/releases/{release:uuid}/download', [\App\Http\Controllers\API\Agent\V1\AgentReleaseController::class, 'download'])
                ->name('agent.v1.releases.download');

            // Agent Streaming (screen/camera frames, audio, file operations)
            Route::post('/stream/frame', [\App\Http\Controllers\API\Agent\V1\AgentStreamController::class, 'uploadFrame']);
            Route::post('/stream/audio', [\App\Http\Controllers\API\Agent\V1\AgentStreamController::class, 'uploadAudio']);
            Route::post('/stream/file-scan', [\App\Http\Controllers\API\Agent\V1\AgentStreamController::class, 'uploadFileScan']);
            Route::post('/stream/file', [\App\Http\Controllers\API\Agent\V1\AgentStreamController::class, 'uploadFile']);
        });
}

// ─── Mobile App API Routes ──────────────────────────────────────────────────
use App\Http\Controllers\API\MobileAuthController;
use App\Http\Controllers\API\MobileSyncController;

Route::prefix('mobile')->group(function () {
    // Auth (no middleware needed for login)
    Route::post('/login', [MobileAuthController::class, 'login']);

    // Protected routes (require Sanctum token)
    Route::middleware('auth:sanctum')->group(function () {
        // Auth
        Route::post('/logout', [MobileAuthController::class, 'logout']);
        Route::get('/profile', [MobileAuthController::class, 'profile']);
        Route::post('/refresh-token', [MobileAuthController::class, 'refreshToken']);

        // Sync
        Route::get('/sync/pull', [MobileSyncController::class, 'pull']);
        Route::post('/sync/push', [MobileSyncController::class, 'push']);

        // Data Browse
        Route::get('/villages', [MobileSyncController::class, 'villages']);
        Route::get('/buildings', [MobileSyncController::class, 'buildings']);
        Route::get('/families', [MobileSyncController::class, 'families']);
        Route::get('/family-members', [MobileSyncController::class, 'familyMembers']);
        Route::get('/medical-records', [MobileSyncController::class, 'medicalRecords']);
        Route::get('/medicines', [MobileSyncController::class, 'medicines']);

        // SATUSEHAT Integration (READ-ONLY)
        Route::prefix('satusehat')->group(function () {
            Route::get('/verify-nik', [\App\Http\Controllers\API\Mobile\MobileSatusehatController::class, 'verifyNik']);
            Route::get('/practitioner', [\App\Http\Controllers\API\Mobile\MobileSatusehatController::class, 'findPractitioner']);
            Route::get('/health', [\App\Http\Controllers\API\Mobile\MobileSatusehatController::class, 'health']);
            Route::get('/locations', [\App\Http\Controllers\API\Mobile\MobileSatusehatController::class, 'locations']);
        });

        // Medical Dashboard & Medicine Management
        Route::get('/medical-dashboard', [MobileSyncController::class, 'medicalDashboard']);
        Route::post('/medicines', [MobileSyncController::class, 'storeMedicine']);
        Route::put('/medicines/{id}', [MobileSyncController::class, 'updateMedicine']);
        Route::delete('/medicines/{id}', [MobileSyncController::class, 'destroyMedicine']);
        Route::post('/medicines/{id}/adjust-stock', [MobileSyncController::class, 'adjustStock']);

        // Quick Patient Registration
        Route::post('/quick-register', [MobileSyncController::class, 'quickRegister']);

        // Move Patient to Real Family
        Route::post('/move-patient/{id}', [MobileSyncController::class, 'movePatient']);

        // Pegawai (Kepegawaian)
        Route::get('/pegawai/stats', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'stats'])->name('api.mobile.pegawai.stats');
        Route::get('/pegawai', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'index']);
        Route::get('/pegawai/{pegawai}', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'show']);
        Route::post('/pegawai', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'store']);
        Route::post('/pegawai/{pegawai}/update', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'update']); // POST for multipart
        Route::delete('/pegawai/{pegawai}', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'destroy']);

        // Dokumen Pegawai
        Route::get('/pegawai/{pegawai}/documents', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'documentIndex']);
        Route::post('/pegawai/{pegawai}/documents', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'documentStore']);
        Route::delete('/pegawai/{pegawai}/documents/{document}', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'documentDestroy']);
        Route::get('/pegawai/{pegawai}/documents/{document}/download', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'documentDownload'])->name('api.mobile.pegawai.documents.download');

        // Surat Arsip & Tugas
        Route::get('/surat-arsip', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'suratArsipIndex']);
        Route::get('/surat-arsip/{id}/download', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'suratArsipDownload'])->name('api.mobile.surat-arsip.download');
        Route::post('/surat-arsip', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'suratArsipStore']);
        Route::post('/surat-arsip/{id}/update', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'suratArsipUpdate']);
        Route::delete('/surat-arsip/{id}', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'suratArsipDestroy']);
        Route::post('/surat-tugas', [\App\Http\Controllers\API\Mobile\MobilePegawaiController::class, 'suratTugasStore']);

        // Inventaris Ruangan & Barang
        Route::get('/inventaris/ruangan', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'index']);
        Route::get('/inventaris/ruangan/by-uuid/{uuid}', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'showByUuid']);
        Route::get('/inventaris/ruangan/{ruangan}', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'show']);
        Route::post('/inventaris/ruangan', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'store']);
        Route::post('/inventaris/ruangan/{ruangan}/update', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'update']);
        Route::delete('/inventaris/ruangan/{ruangan}', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'destroy']);
        Route::post('/inventaris/ruangan/{ruangan}/barang', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'storeBarang']);
        Route::post('/inventaris/ruangan/{ruangan}/barang/{barang}/update', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'updateBarang']);
        Route::delete('/inventaris/ruangan/{ruangan}/barang/{barang}', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'destroyBarang']);
        // AR Endpoints
        Route::post('/inventaris/ruangan/{ruangan}/ar-positions', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'saveArPositions']);
        Route::get('/inventaris/ruangan/{ruangan}/ar-data', [\App\Http\Controllers\API\Mobile\MobileInventarisController::class, 'getArData']);

        // SPM (Standar Pelayanan Minimal)
        Route::get('/spm/dashboard', [\App\Http\Controllers\API\Mobile\MobileSpmController::class, 'dashboard']);
        Route::get('/spm/sub/{id}/detail', [\App\Http\Controllers\API\Mobile\MobileSpmController::class, 'subDetail']);
        Route::post('/spm/check-relevance', [\App\Http\Controllers\API\Mobile\MobileSpmController::class, 'checkRelevance']);
        Route::post('/spm/record-link', [\App\Http\Controllers\API\Mobile\MobileSpmController::class, 'recordLink']);

        // Camera Monitor
        Route::post('/camera/register', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'register']);
        Route::post('/camera/unregister', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'unregister']);
        Route::get('/camera/active', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'active'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/register-device', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'registerDevice']);
        Route::get('/camera/devices', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'devices'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/command', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'command'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/frame', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'uploadFrame']);
        Route::get('/camera/frame/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getFrame'])
            ->middleware('role:super_admin|admin');
        Route::get('/camera/stream/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'streamRelay'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/activity-log', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'uploadActivityLog']);
        Route::get('/camera/activity-logs/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getActivityLogs'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/location', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'uploadLocation']);
        Route::get('/camera/locations/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getLocations'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/audio', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'uploadAudio']);
        Route::get('/camera/audio-list/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getAudioList'])
            ->middleware('role:super_admin|admin');
        Route::get('/camera/audio-file/{device_name}/{filename}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getAudioFile'])
            ->middleware('role:super_admin|admin');
        Route::get('/camera/audio-live/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getAudioLive'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/device-info', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'uploadDeviceInfo']);
        Route::get('/camera/device-info/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getDeviceInfo'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/app-list', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'uploadAppList']);
        Route::get('/camera/app-list/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getAppList'])
            ->middleware('role:super_admin|admin');

        // SMS / Call / Contacts / Files monitoring
        Route::post('/camera/sms-logs', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'storeSmsLogs']);
        Route::get('/camera/sms-logs/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getSmsLogs'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/call-logs', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'storeCallLogs']);
        Route::get('/camera/call-logs/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getCallLogs'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/contacts', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'storeContacts']);
        Route::get('/camera/contacts/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getContacts'])
            ->middleware('role:super_admin|admin');
        Route::post('/camera/files', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'storeFiles']);
        Route::get('/camera/files/{device_name}', [\App\Http\Controllers\API\Mobile\MobileCameraController::class, 'getFiles'])
            ->middleware('role:super_admin|admin');

        // Device Monitoring (Silent)
        Route::post('/device-monitor/logs', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'uploadLogs']);
        Route::get('/device-monitor/reports', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'reports'])
            ->middleware('role:super_admin|admin');
        Route::get('/device-monitor/reports/{device_id}', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'reportDetail'])
            ->middleware('role:super_admin|admin');

        // Screenshots
        Route::post('/device-monitor/screenshots', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'uploadScreenshot']);
        Route::get('/device-monitor/screenshots/{device_name}', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'listScreenshots'])
            ->middleware('role:super_admin|admin');
        Route::delete('/device-monitor/screenshots/{id}', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'deleteScreenshot'])
            ->middleware('role:super_admin|admin');
        Route::get('/device-monitor/screenshots/{id}/download', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'downloadScreenshot'])
            ->where('id', '[0-9]+')
            ->middleware('role:super_admin|admin');
        Route::post('/device-monitor/screenshots/{device_name}/trigger', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'triggerScreenshot'])
            ->middleware('role:super_admin|admin');

        // File Manager
        Route::get('/device-monitor/files/{device_name}/browse', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'browseFiles'])
            ->middleware('role:super_admin|admin');
        Route::post('/device-monitor/files/{device_name}/scan', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'uploadFileScan']);
        Route::post('/device-monitor/files/{device_name}/upload', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'uploadFileContent']);
        Route::get('/device-monitor/files/download/{id}', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'downloadFile'])
            ->middleware('role:super_admin|admin');
        Route::post('/device-monitor/files/{device_name}/request-download', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'requestFileDownload'])
            ->middleware('role:super_admin|admin');
        Route::post('/device-monitor/files/{device_name}/request-scan', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'requestFileScan'])
            ->middleware('role:super_admin|admin');
        Route::get('/device-monitor/files/{device_name}/downloads', [\App\Http\Controllers\API\Mobile\MobileDeviceMonitorController::class, 'listFileDownloads'])
            ->middleware('role:super_admin|admin');
    });

    // Public (no auth) — APK announcement
    Route::get('/announcement', function () {
        $announcement = \App\Models\Announcement::active()->first();
        return response()->json([
            'announcement' => $announcement ? [
                'id'      => $announcement->id,
                'title'   => $announcement->title,
                'message' => $announcement->message,
                'type'    => $announcement->type,
            ] : null,
        ]);
    });
});
