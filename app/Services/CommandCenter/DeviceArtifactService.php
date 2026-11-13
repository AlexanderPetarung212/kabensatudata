<?php

namespace App\Services\CommandCenter;

use App\Models\DeviceArtifact;
use App\Models\DeviceCommand;
use App\Models\ManagedDevice;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class DeviceArtifactService
{
    public function store(
        ManagedDevice $device,
        UploadedFile $file,
        string $type,
        ?DeviceCommand $command = null,
        array $metadata = [],
    ): DeviceArtifact {
        if ($command && $command->managed_device_id !== $device->id) {
            throw ValidationException::withMessages([
                'command_uuid' => 'Command bukan milik perangkat ini.',
            ]);
        }

        $mime = $file->getMimeType() ?: 'application/octet-stream';
        if (!in_array($mime, config('command-center.artifacts.allowed_mime_types', []), true)) {
            throw ValidationException::withMessages([
                'file' => "Tipe file {$mime} tidak diizinkan.",
            ]);
        }

        $disk = config('command-center.artifacts.disk', 'command_center_private');
        $extension = $file->guessExtension() ?: 'bin';
        $path = sprintf(
            'devices/%s/%s/%s/%s.%s',
            $device->uuid,
            now()->format('Y/m'),
            Str::slug($type),
            Str::uuid(),
            $extension,
        );

        $checksum = hash_file('sha256', $file->getRealPath());
        Storage::disk($disk)->putFileAs(dirname($path), $file, basename($path));

        $retentionDays = str_starts_with($mime, 'image/')
            ? config('command-center.artifacts.screenshot_retention_days', 7)
            : config('command-center.artifacts.media_retention_days', 3);

        try {
            return DeviceArtifact::create([
                'managed_device_id' => $device->id,
                'device_command_id' => $command?->id,
                'type' => $type,
                'disk' => $disk,
                'path' => $path,
                'original_name' => mb_substr($file->getClientOriginalName(), 0, 255),
                'mime_type' => $mime,
                'size_bytes' => $file->getSize(),
                'checksum_sha256' => $checksum,
                'metadata' => $metadata,
                'expires_at' => now()->addDays($retentionDays),
            ]);
        } catch (\Throwable $exception) {
            Storage::disk($disk)->delete($path);

            throw $exception;
        }
    }
}
