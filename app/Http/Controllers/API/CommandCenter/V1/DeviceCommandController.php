<?php

namespace App\Http\Controllers\API\CommandCenter\V1;

use App\Http\Controllers\Controller;
use App\Models\DeviceCommand;
use App\Models\ManagedDevice;
use App\Services\CommandCenter\DeviceCommandService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class DeviceCommandController extends Controller
{
    public function __construct(
        private readonly DeviceCommandService $commands,
    ) {
    }

    public function store(Request $request, ManagedDevice $device): JsonResponse
    {
        $this->authorize('view', $device);

        $data = $request->validate([
            'name' => ['required', 'string', 'max:80'],
            'payload' => ['nullable', 'array', 'max:100'],
            'reason' => ['nullable', 'string', 'max:500'],
            'idempotency_key' => ['nullable', 'string', 'max:100'],
        ]);

        $command = $this->commands->create(
            $device,
            $request->user(),
            $data['name'],
            $data['payload'] ?? [],
            $data['reason'] ?? null,
            $data['idempotency_key'] ?? null,
        );

        return response()->json(['data' => $this->serialize($command)], 201);
    }

    public function show(Request $request, DeviceCommand $command): JsonResponse
    {
        $this->authorize('view', $command->device);

        return response()->json([
            'data' => $this->serialize($command->load('events')),
        ]);
    }

    public function cancel(Request $request, DeviceCommand $command): JsonResponse
    {
        $this->authorize('view', $command->device);

        $definition = config("command-center.commands.{$command->name}");
        abort_unless($definition && $request->user()->can($definition['permission']), 403);

        return response()->json([
            'data' => $this->serialize($this->commands->cancel($command, $request->user())),
        ]);
    }

    private function serialize(DeviceCommand $command): array
    {
        return [
            'uuid' => $command->uuid,
            'device_uuid' => $command->device?->uuid,
            'name' => $command->name,
            'executor' => $command->executor,
            'status' => $command->status->value,
            'reason' => $command->reason,
            'sensitive' => $command->sensitive,
            'payload' => $command->payload ?? (object) [],
            'result_summary' => $command->result_summary,
            'error_code' => $command->error_code,
            'error_message' => $command->error_message,
            'expires_at' => $command->expires_at?->toIso8601String(),
            'created_at' => $command->created_at?->toIso8601String(),
            'sent_at' => $command->sent_at?->toIso8601String(),
            'delivered_at' => $command->delivered_at?->toIso8601String(),
            'started_at' => $command->started_at?->toIso8601String(),
            'completed_at' => $command->completed_at?->toIso8601String(),
            'failed_at' => $command->failed_at?->toIso8601String(),
            'events' => $command->relationLoaded('events') ? $command->events : null,
        ];
    }
}
