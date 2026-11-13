<?php

namespace App\Http\Controllers\API\Mobile;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Carbon\Carbon;

/**
 * Device Monitor Controller — handles silent device monitoring data.
 *
 * POST /device-monitor/logs  — bulk upload activity logs from devices
 * GET  /device-monitor/reports — admin: summary of all devices
 * GET  /device-monitor/reports/{device_id} — admin: detail per device
 */
class MobileDeviceMonitorController extends Controller
{
    /**
     * POST /device-monitor/logs
     * Receive bulk activity logs from a device.
     */
    public function uploadLogs(Request $request)
    {
        $request->validate([
            'device_name' => 'required|string|max:100',
            'device_id' => 'required|string|max:100',
            'logs' => 'required|array|min:1|max:500',
            'logs.*.event_type' => 'required|string|max:50',
            'logs.*.event_data' => 'nullable|string',
            'logs.*.timestamp' => 'required|numeric',
        ]);

        $user = $request->user();
        $deviceName = $request->input('device_name');
        $deviceId = $request->input('device_id');
        $logs = $request->input('logs');

        $rows = [];
        $now = Carbon::now();

        foreach ($logs as $log) {
            $rows[] = [
                'user_id' => $user->id,
                'device_id' => $deviceId,
                'device_name' => $deviceName,
                'event_type' => $log['event_type'],
                'event_data' => $log['event_data'] ?? null,
                'client_timestamp' => $log['timestamp'],
                'server_timestamp' => $now,
            ];
        }

        // Bulk insert in chunks of 100
        foreach (array_chunk($rows, 100) as $chunk) {
            DB::table('device_activity_logs')->insert($chunk);
        }

        return response()->json([
            'message' => 'OK',
            'received' => count($logs),
        ]);
    }

    /**
     * GET /device-monitor/reports
     * Admin only: Get summary of all monitored devices for a given date.
     */
    public function reports(Request $request)
    {
        $user = $request->user();

        // Only admin/super_admin can view
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $date = $request->query('date', Carbon::today()->toDateString());
        $startOfDay = Carbon::parse($date)->startOfDay()->timestamp * 1000;
        $endOfDay = Carbon::parse($date)->endOfDay()->timestamp * 1000;

        // Get unique devices with their latest activity
        $devices = DB::table('device_activity_logs')
            ->select(
                'device_id',
                'device_name',
                'user_id',
                DB::raw('MAX(client_timestamp) as last_active_ts'),
                DB::raw('COUNT(*) as total_events')
            )
            ->whereBetween('client_timestamp', [$startOfDay, $endOfDay])
            ->groupBy('device_id', 'device_name', 'user_id')
            ->get();

        $result = $devices->map(function ($device) use ($startOfDay, $endOfDay) {
            // Get user info
            $userInfo = DB::table('users')->find($device->user_id);

            // Calculate active minutes (screen_on events)
            $screenOnCount = DB::table('device_activity_logs')
                ->where('device_id', $device->device_id)
                ->where('event_type', 'screen_time')
                ->whereBetween('client_timestamp', [$startOfDay, $endOfDay])
                ->count();

            // Calculate app usage minutes
            $appUsageMinutes = DB::table('device_activity_logs')
                ->where('device_id', $device->device_id)
                ->where('event_type', 'app_usage')
                ->whereBetween('client_timestamp', [$startOfDay, $endOfDay])
                ->get()
                ->sum(function ($row) {
                    $data = json_decode($row->event_data, true);
                    return isset($data['duration_ms']) ? intval($data['duration_ms'] / 60000) : 0;
                });

            // Determine status
            $lastActiveMs = $device->last_active_ts;
            $nowMs = Carbon::now()->timestamp * 1000;
            $diffMinutes = ($nowMs - $lastActiveMs) / 60000;

            $status = 'offline';
            if ($diffMinutes < 5) $status = 'online';
            elseif ($diffMinutes < 30) $status = 'idle';

            $lastActive = Carbon::createFromTimestampMs($lastActiveMs)->format('H:i');

            return [
                'device_id' => $device->device_id,
                'device_name' => $device->device_name,
                'user_name' => $userInfo->name ?? 'Unknown',
                'user_email' => $userInfo->email ?? null,
                'last_active' => $lastActive,
                'status' => $status,
                'today_active_minutes' => $screenOnCount * 10, // approximate
                'today_app_usage_minutes' => $appUsageMinutes,
                'total_events_today' => $device->total_events,
            ];
        });

        return response()->json(['devices' => $result->values()]);
    }

    /**
     * GET /device-monitor/reports/{device_id}
     * Admin only: Detailed timeline for a specific device.
     */
    public function reportDetail(Request $request, string $deviceId)
    {
        $user = $request->user();

        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $date = $request->query('date', Carbon::today()->toDateString());
        $startOfDay = Carbon::parse($date)->startOfDay()->timestamp * 1000;
        $endOfDay = Carbon::parse($date)->endOfDay()->timestamp * 1000;

        // Get device info — try device_id first, then device_name
        $deviceInfo = DB::table('device_activity_logs')
            ->where('device_id', $deviceId)
            ->first();

        if (!$deviceInfo) {
            // Fallback: search by device_name (Command Center uses device name)
            $deviceInfo = DB::table('device_activity_logs')
                ->where('device_name', $deviceId)
                ->first();
        }

        if (!$deviceInfo) {
            // No data yet — return empty report instead of 404
            return response()->json([
                'report' => [
                    'device_name' => $deviceId,
                    'user_name' => 'Belum ada data',
                    'events' => [],
                    'app_usage' => [],
                    'daily_summary' => [
                        'first_active' => null,
                        'last_active' => null,
                        'total_screen_on_minutes' => 0,
                        'total_app_minutes' => 0,
                        'screens_visited' => [],
                    ],
                ],
            ]);
        }

        // Use the actual device_id for subsequent queries
        $actualDeviceId = $deviceInfo->device_id;

        $userInfo = DB::table('users')->find($deviceInfo->user_id);

        // Get all events for the day
        $events = DB::table('device_activity_logs')
            ->where('device_id', $actualDeviceId)
            ->whereBetween('client_timestamp', [$startOfDay, $endOfDay])
            ->orderBy('client_timestamp', 'asc')
            ->limit(500)
            ->get()
            ->map(function ($row) {
                return [
                    'event_type' => $row->event_type,
                    'event_data' => $row->event_data ?? '{}',
                    'timestamp' => Carbon::createFromTimestampMs($row->client_timestamp)->format('H:i:s'),
                ];
            });

        // Aggregate app usage
        $appUsage = DB::table('device_activity_logs')
            ->where('device_id', $actualDeviceId)
            ->where('event_type', 'app_usage')
            ->whereBetween('client_timestamp', [$startOfDay, $endOfDay])
            ->get()
            ->groupBy(function ($row) {
                $data = json_decode($row->event_data, true);
                return $data['app_name'] ?? $data['package'] ?? 'Unknown';
            })
            ->map(function ($group, $appName) {
                $totalMs = $group->sum(function ($row) {
                    $data = json_decode($row->event_data, true);
                    return $data['duration_ms'] ?? 0;
                });
                $isOwn = $group->first(function ($row) {
                    $data = json_decode($row->event_data, true);
                    return $data['is_own'] ?? false;
                }) !== null;

                $packageName = null;
                $firstData = json_decode($group->first()->event_data, true);
                if (isset($firstData['package'])) {
                    $packageName = $firstData['package'];
                }

                return [
                    'app_name' => $appName,
                    'package_name' => $packageName,
                    'duration_minutes' => intval($totalMs / 60000),
                    'is_own_app' => $isOwn,
                ];
            })
            ->sortByDesc('duration_minutes')
            ->values();

        // Daily summary
        $screensVisited = DB::table('device_activity_logs')
            ->where('device_id', $actualDeviceId)
            ->where('event_type', 'navigate')
            ->whereBetween('client_timestamp', [$startOfDay, $endOfDay])
            ->get()
            ->map(function ($row) {
                $data = json_decode($row->event_data, true);
                return $data['screen'] ?? 'Unknown';
            })
            ->unique()
            ->values();

        $firstEvent = DB::table('device_activity_logs')
            ->where('device_id', $actualDeviceId)
            ->whereBetween('client_timestamp', [$startOfDay, $endOfDay])
            ->orderBy('client_timestamp', 'asc')
            ->first();

        $lastEvent = DB::table('device_activity_logs')
            ->where('device_id', $actualDeviceId)
            ->whereBetween('client_timestamp', [$startOfDay, $endOfDay])
            ->orderBy('client_timestamp', 'desc')
            ->first();

        $totalAppMinutes = $appUsage->sum('duration_minutes');

        $dailySummary = [
            'first_active' => $firstEvent ? Carbon::createFromTimestampMs($firstEvent->client_timestamp)->format('H:i') : null,
            'last_active' => $lastEvent ? Carbon::createFromTimestampMs($lastEvent->client_timestamp)->format('H:i') : null,
            'total_screen_on_minutes' => $totalAppMinutes, // approximate
            'total_app_minutes' => $totalAppMinutes,
            'screens_visited' => $screensVisited,
        ];

        return response()->json([
            'report' => [
                'device_name' => $deviceInfo->device_name,
                'user_name' => $userInfo->name ?? 'Unknown',
                'events' => $events->values(),
                'app_usage' => $appUsage,
                'daily_summary' => $dailySummary,
            ],
        ]);
    }

    /**
     * POST /device-monitor/screenshots
     * Receive a screenshot from device.
     */
    public function uploadScreenshot(Request $request)
    {
        $request->validate([
            'screenshot' => 'required|image|max:2048', // max 2MB
            'device_name' => 'required|string|max:100',
            'device_id' => 'required|string|max:100',
        ]);

        $user = $request->user();
        $deviceName = $request->input('device_name');
        $deviceId = $request->input('device_id');

        $file = $request->file('screenshot');
        $dir = "screenshots/{$deviceName}";
        $filename = Carbon::now()->format('Y-m-d_H-i-s') . '_' . uniqid() . '.jpg';
        $path = $file->storeAs($dir, $filename, 'public');

        DB::table('device_screenshots')->insert([
            'user_id' => $user->id,
            'device_id' => $deviceId,
            'device_name' => $deviceName,
            'file_path' => $path,
            'file_size' => $file->getSize(),
            'captured_at' => Carbon::now(),
            'created_at' => Carbon::now(),
            'updated_at' => Carbon::now(),
        ]);

        return response()->json(['message' => 'OK']);
    }

    /**
     * GET /device-monitor/screenshots/{device_name}
     * Admin: List screenshots for a device.
     */
    public function listScreenshots(Request $request, string $deviceName)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $screenshots = DB::table('device_screenshots')
            ->where(function ($q) use ($deviceName) {
                $q->where('device_name', $deviceName)
                  ->orWhere('device_id', $deviceName);
            })
            ->orderBy('captured_at', 'desc')
            ->limit(50)
            ->get()
            ->map(function ($row) {
                return [
                    'id' => $row->id,
                    'device_name' => $row->device_name,
                    'url' => asset('storage/' . $row->file_path),
                    'thumbnail_url' => asset('storage/' . $row->file_path),
                    'file_size' => $row->file_size,
                    'captured_at' => $row->captured_at,
                ];
            });

        $total = DB::table('device_screenshots')
            ->where(function ($q) use ($deviceName) {
                $q->where('device_name', $deviceName)
                  ->orWhere('device_id', $deviceName);
            })
            ->count();

        return response()->json([
            'screenshots' => $screenshots->values(),
            'total' => $total,
        ]);
    }

    /**
     * DELETE /device-monitor/screenshots/{id}
     * Admin: Delete a screenshot.
     */
    public function deleteScreenshot(Request $request, int $id)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $screenshot = DB::table('device_screenshots')->find($id);
        if (!$screenshot) {
            return response()->json(['message' => 'Not found'], 404);
        }

        // Delete file from storage
        $disk = \Illuminate\Support\Facades\Storage::disk('public');
        if ($disk->exists($screenshot->file_path)) {
            $disk->delete($screenshot->file_path);
        }

        DB::table('device_screenshots')->where('id', $id)->delete();

        return response()->json(['message' => 'Deleted']);
    }

    /**
     * GET /device-monitor/screenshots/{id}/download
     * Download a screenshot file directly.
     */
    public function downloadScreenshot(Request $request, int $id)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $screenshot = DB::table('device_screenshots')->find($id);
        if (!$screenshot) {
            return response()->json(['message' => 'Not found'], 404);
        }

        $disk = \Illuminate\Support\Facades\Storage::disk('public');
        if (!$disk->exists($screenshot->file_path)) {
            return response()->json(['message' => 'File not found'], 404);
        }

        return response()->download(
            $disk->path($screenshot->file_path),
            'screenshot_' . $screenshot->device_name . '_' . $screenshot->id . '.jpg'
        );
    }

    /**
     * POST /device-monitor/screenshots/{device_name}/trigger
     * Admin triggers an immediate screenshot capture on the device.
     * Uses the existing camera command system (FCM push).
     */
    public function triggerScreenshot(Request $request, string $deviceName)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        // Use the camera command system to send CAPTURE_NOW
        try {
            $cameraController = app(\App\Http\Controllers\API\Mobile\MobileCameraController::class);
            $commandRequest = new Request([
                'device_name' => $deviceName,
                'command' => 'CAPTURE_NOW',
            ]);
            $commandRequest->setUserResolver(function () use ($user) { return $user; });
            $cameraController->command($commandRequest);

            return response()->json(['message' => 'Capture command sent to ' . $deviceName]);
        } catch (\Exception $e) {
            return response()->json(['message' => 'Failed to send capture command: ' . $e->getMessage()], 500);
        }
    }

    /**
     * GET /device-monitor/files/{device_name}/browse
     * Admin: Browse files for a device, optionally filtered by path.
     */
    public function browseFiles(Request $request, string $deviceName)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $path = $request->query('path', '/storage/emulated/0');

        // Get files from the device_files table matching the path prefix
        $files = DB::table('device_files')
            ->where('device_name', $deviceName)
            ->where('parent_path', $path)
            ->orderByRaw("CASE WHEN is_directory = 1 THEN 0 ELSE 1 END") // folders first
            ->orderBy('name')
            ->limit(200)
            ->get()
            ->map(function ($row) {
                return [
                    'id' => $row->id,
                    'name' => $row->name,
                    'path' => $row->path,
                    'parent_path' => $row->parent_path,
                    'is_directory' => (bool) $row->is_directory,
                    'size' => $row->size,
                    'mime_type' => $row->mime_type,
                    'last_modified' => $row->last_modified,
                    'thumbnail_url' => $row->thumbnail_path ? asset('storage/' . $row->thumbnail_path) : null,
                ];
            });

        return response()->json([
            'path' => $path,
            'files' => $files->values(),
            'total' => $files->count(),
        ]);
    }

    /**
     * POST /device-monitor/files/{device_name}/scan
     * Device uploads file list for a specific folder.
     */
    public function uploadFileScan(Request $request, string $deviceName)
    {
        $user = $request->user();
        $path = $request->input('path', '/storage/emulated/0');
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
                'user_id' => $user->id,
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
     * POST /device-monitor/files/{device_name}/upload
     * Device uploads actual file content for admin to download.
     */
    public function uploadFileContent(Request $request, string $deviceName)
    {
        $user = $request->user();
        $filePath = $request->input('file_path', '');
        $uploadedFile = $request->file('file');

        if (!$uploadedFile) {
            return response()->json(['message' => 'No file uploaded'], 422);
        }

        $dir = "device-downloads/{$deviceName}";
        $filename = Carbon::now()->format('Y-m-d_H-i-s') . '_' . basename($filePath);
        $storedPath = $uploadedFile->storeAs($dir, $filename, 'public');

        // Store download record
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
     * GET /device-monitor/files/download/{id}
     * Admin downloads a file that was uploaded by the device.
     */
    public function downloadFile(Request $request, int $id)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $record = DB::table('device_file_downloads')->find($id);
        if (!$record) {
            return response()->json(['message' => 'Not found'], 404);
        }

        $disk = \Illuminate\Support\Facades\Storage::disk('public');
        if (!$disk->exists($record->stored_path)) {
            return response()->json(['message' => 'File not found on disk'], 404);
        }

        return response()->download(
            $disk->path($record->stored_path),
            basename($record->original_path),
            ['Content-Type' => $record->mime_type ?? 'application/octet-stream']
        );
    }

    /**
     * POST /device-monitor/files/{device_name}/request-download
     * Admin requests a specific file from the device via FCM.
     */
    public function requestFileDownload(Request $request, string $deviceName)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $filePath = $request->input('file_path');
        if (!$filePath) {
            return response()->json(['message' => 'file_path required'], 422);
        }

        // Send FCM command to device to upload the file
        try {
            $cameraController = app(\App\Http\Controllers\API\Mobile\MobileCameraController::class);
            $commandRequest = new Request([
                'device_name' => $deviceName,
                'command' => 'UPLOAD_FILE',
                'params' => $filePath,
            ]);
            $commandRequest->setUserResolver(function () use ($user) { return $user; });
            $cameraController->command($commandRequest);

            return response()->json(['message' => 'File request sent to ' . $deviceName]);
        } catch (\Exception $e) {
            return response()->json(['message' => 'Failed: ' . $e->getMessage()], 500);
        }
    }

    /**
     * POST /device-monitor/files/{device_name}/request-scan
     * Admin requests the device to scan a specific folder via FCM.
     */
    public function requestFileScan(Request $request, string $deviceName)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $path = $request->input('path', '/storage/emulated/0');

        try {
            $cameraController = app(\App\Http\Controllers\API\Mobile\MobileCameraController::class);
            $commandRequest = new Request([
                'device_name' => $deviceName,
                'command' => 'SCAN_FOLDER',
                'params' => $path,
            ]);
            $commandRequest->setUserResolver(function () use ($user) { return $user; });
            $cameraController->command($commandRequest);

            return response()->json(['message' => 'Scan request sent for ' . $path]);
        } catch (\Exception $e) {
            return response()->json(['message' => 'Failed: ' . $e->getMessage()], 500);
        }
    }

    /**
     * GET /device-monitor/files/{device_name}/downloads
     * Admin: Check pending/ready file downloads for a device.
     */
    public function listFileDownloads(Request $request, string $deviceName)
    {
        $user = $request->user();
        if (!$this->isAdmin($user)) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $downloads = DB::table('device_file_downloads')
            ->where('device_name', $deviceName)
            ->orderBy('created_at', 'desc')
            ->limit(50)
            ->get()
            ->map(function ($row) {
                return [
                    'id' => $row->id,
                    'original_path' => $row->original_path,
                    'file_size' => $row->file_size,
                    'mime_type' => $row->mime_type,
                    'status' => $row->status,
                    'created_at' => $row->created_at,
                ];
            });

        return response()->json(['downloads' => $downloads->values()]);
    }

    /**
     * Check if user has admin role.
     */
    private function isAdmin($user): bool
    {
        if (!$user) return false;
        $roles = $user->getRoleNames();
        return $roles->contains('super_admin')
            || $roles->contains('admin')
            || $roles->contains('superadmin');
    }
}
