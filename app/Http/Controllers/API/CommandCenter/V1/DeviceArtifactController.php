<?php

namespace App\Http\Controllers\API\CommandCenter\V1;

use App\Http\Controllers\Controller;
use App\Models\DeviceArtifact;
use App\Models\ManagedDevice;
use App\Services\CommandCenter\CommandCenterAuditService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\StreamedResponse;

class DeviceArtifactController extends Controller
{
    public function __construct(
        private readonly CommandCenterAuditService $audit,
    ) {
    }

    public function index(Request $request, ManagedDevice $device): JsonResponse
    {
        $this->authorize('view', $device);
        abort_unless($request->user()->can('artifact.download'), 403);

        $artifacts = $device->artifacts()
            ->whereNull('deleted_at')
            ->latest()
            ->limit(100)
            ->get();

        return response()->json([
            'data' => $artifacts->map(fn (DeviceArtifact $artifact) => [
                'uuid' => $artifact->uuid,
                'type' => $artifact->type,
                'original_name' => $artifact->original_name,
                'mime_type' => $artifact->mime_type,
                'size_bytes' => $artifact->size_bytes,
                'checksum_sha256' => $artifact->checksum_sha256,
                'created_at' => $artifact->created_at->toIso8601String(),
                'expires_at' => $artifact->expires_at?->toIso8601String(),
                'download_url' => route('command-center.api.artifacts.download', $artifact),
            ]),
        ]);
    }

    public function download(Request $request, DeviceArtifact $artifact): StreamedResponse
    {
        $this->authorize('view', $artifact);
        abort_if($artifact->deleted_at || !Storage::disk($artifact->disk)->exists($artifact->path), 404);

        $this->audit->record(
            'device.artifact.downloaded',
            'success',
            $request->user(),
            $artifact->device,
            metadata: [
                'artifact_uuid' => $artifact->uuid,
                'artifact_type' => $artifact->type,
            ],
            request: $request,
        );

        return Storage::disk($artifact->disk)->download(
            $artifact->path,
            $artifact->original_name ?: basename($artifact->path),
            [
                'Cache-Control' => 'private, no-store, max-age=0',
                'Pragma' => 'no-cache',
                'X-Content-Type-Options' => 'nosniff',
            ],
        );
    }

    public function destroy(Request $request, DeviceArtifact $artifact): JsonResponse
    {
        $this->authorize('delete', $artifact);

        Storage::disk($artifact->disk)->delete($artifact->path);
        $artifact->update(['deleted_at' => now()]);

        $this->audit->record(
            'device.artifact.deleted',
            'success',
            $request->user(),
            $artifact->device,
            metadata: [
                'artifact_uuid' => $artifact->uuid,
                'artifact_type' => $artifact->type,
            ],
            request: $request,
        );

        return response()->json(['data' => ['deleted' => true]]);
    }
}
