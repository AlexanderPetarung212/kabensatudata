<?php

namespace App\Http\Controllers\API\Agent\V1;

use App\Http\Controllers\Controller;
use App\Models\DeviceCommand;
use App\Models\ManagedDevice;
use App\Services\CommandCenter\DeviceArtifactService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class AgentArtifactController extends Controller
{
    public function __construct(
        private readonly DeviceArtifactService $artifacts,
    ) {
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'type' => ['required', Rule::in([
                'screenshot',
                'camera_snapshot',
                'diagnostic',
                'audio',
                'video',
                'file',
            ])],
            'command_uuid' => ['nullable', 'uuid'],
            'file' => [
                'required',
                'file',
                'max:'.config('command-center.artifacts.max_size_kb', 102400),
            ],
            'metadata' => ['nullable', 'array', 'max:100'],
        ]);

        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');
        $command = null;

        if (!empty($data['command_uuid'])) {
            $command = DeviceCommand::query()
                ->where('uuid', $data['command_uuid'])
                ->where('managed_device_id', $device->id)
                ->firstOrFail();
        }

        $artifact = $this->artifacts->store(
            $device,
            $request->file('file'),
            $data['type'],
            $command,
            $data['metadata'] ?? [],
        );

        return response()->json([
            'data' => [
                'uuid' => $artifact->uuid,
                'type' => $artifact->type,
                'size_bytes' => $artifact->size_bytes,
                'checksum_sha256' => $artifact->checksum_sha256,
                'expires_at' => $artifact->expires_at?->toIso8601String(),
            ],
        ], 201);
    }
}
