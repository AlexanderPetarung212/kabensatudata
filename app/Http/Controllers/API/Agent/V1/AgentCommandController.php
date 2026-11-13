<?php

namespace App\Http\Controllers\API\Agent\V1;

use App\Enums\DeviceCommandStatus;
use App\Http\Controllers\Controller;
use App\Models\DeviceCommand;
use App\Models\ManagedDevice;
use App\Services\CommandCenter\DeviceCommandService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class AgentCommandController extends Controller
{
    public function __construct(
        private readonly DeviceCommandService $commands,
    ) {
    }

    public function next(Request $request): JsonResponse
    {
        $data = $request->validate([
            'executor' => ['nullable', 'in:service,companion'],
        ]);

        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');
        $command = $this->commands->pullNext($device, $data['executor'] ?? 'service');

        return response()->json([
            'data' => $command ? $this->serialize($command) : null,
            'poll_after_seconds' => config('command-center.agent.command_poll_seconds'),
            'server_time' => now()->toIso8601String(),
        ]);
    }

    public function delivered(Request $request, DeviceCommand $command): JsonResponse
    {
        return $this->transition($request, $command, DeviceCommandStatus::Delivered);
    }

    public function started(Request $request, DeviceCommand $command): JsonResponse
    {
        return $this->transition($request, $command, DeviceCommandStatus::Executing);
    }

    public function completed(Request $request, DeviceCommand $command): JsonResponse
    {
        $request->validate([
            'result' => ['nullable', 'array', 'max:200'],
        ]);

        return $this->transition(
            $request,
            $command,
            DeviceCommandStatus::Completed,
            $request->input('result', []),
        );
    }

    public function failed(Request $request, DeviceCommand $command): JsonResponse
    {
        $data = $request->validate([
            'error_code' => ['required', 'string', 'max:80'],
            'error_message' => ['nullable', 'string', 'max:2000'],
            'result' => ['nullable', 'array', 'max:200'],
        ]);

        return $this->transition(
            $request,
            $command,
            DeviceCommandStatus::Failed,
            $data['result'] ?? [],
            $data['error_code'],
            $data['error_message'] ?? null,
        );
    }

    private function transition(
        Request $request,
        DeviceCommand $command,
        DeviceCommandStatus $status,
        array $result = [],
        ?string $errorCode = null,
        ?string $errorMessage = null,
    ): JsonResponse {
        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');
        $updated = $this->commands->transitionFromAgent(
            $device,
            $command,
            $status,
            $result,
            $errorCode,
            $errorMessage,
        );

        return response()->json([
            'data' => [
                'uuid' => $updated->uuid,
                'status' => $updated->status->value,
            ],
        ]);
    }

    private function serialize(DeviceCommand $command): array
    {
        return [
            'uuid' => $command->uuid,
            'name' => $command->name,
            'version' => $command->version,
            'executor' => $command->executor,
            'payload' => $command->payload ?? (object) [],
            'sensitive' => $command->sensitive,
            'reason' => $command->reason,
            'expires_at' => $command->expires_at->toIso8601String(),
            'sent_at' => $command->sent_at?->toIso8601String(),
        ];
    }
}
