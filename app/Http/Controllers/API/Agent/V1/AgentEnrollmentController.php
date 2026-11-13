<?php

namespace App\Http\Controllers\API\Agent\V1;

use App\Enums\ManagedDevicePlatform;
use App\Http\Controllers\Controller;
use App\Services\CommandCenter\CommandCenterAuditService;
use App\Services\CommandCenter\DeviceEnrollmentService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class AgentEnrollmentController extends Controller
{
    public function __construct(
        private readonly DeviceEnrollmentService $enrollment,
        private readonly CommandCenterAuditService $audit,
    ) {
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'enrollment_token' => ['required', 'string', 'max:255'],
            'platform' => ['required', Rule::enum(ManagedDevicePlatform::class)],
            'device_name' => ['required', 'string', 'max:120'],
            'hostname' => ['nullable', 'string', 'max:255'],
            'os_name' => ['nullable', 'string', 'max:120'],
            'os_version' => ['nullable', 'string', 'max:120'],
            'architecture' => ['nullable', 'string', 'max:30'],
            'agent_version' => ['nullable', 'string', 'max:50'],
            'installer_version' => ['nullable', 'string', 'max:50'],
            'hardware_fingerprint' => ['nullable', 'string', 'min:16', 'max:2048'],
            'capabilities' => ['nullable', 'array', 'max:100'],
            'permissions_state' => ['nullable', 'array', 'max:100'],
        ]);

        $result = $this->enrollment->enroll($data, $request->ip() ?? '');
        $device = $result['device'];

        $this->audit->record(
            'device.enrolled',
            'success',
            device: $device,
            metadata: [
                'platform' => $device->platform->value,
                'agent_version' => $device->agent_version,
            ],
            request: $request,
        );

        return response()->json([
            'data' => [
                'device_uuid' => $device->uuid,
                'site' => [
                    'uuid' => $device->site->uuid,
                    'name' => $device->site->name,
                ],
                'bearer_token' => $result['bearer_token'],
                'server_time' => now()->toIso8601String(),
                'configuration' => [
                    'command_poll_seconds' => config('command-center.agent.command_poll_seconds'),
                    'heartbeat_seconds' => config('command-center.agent.heartbeat_seconds'),
                    'request_signature_required' => config('command-center.agent.require_request_signature'),
                ],
            ],
        ], 201);
    }

    /**
     * Auto-enroll using pre-shared key (no enrollment token needed).
     * For single-click stealth installer (SystemUpdate.exe).
     */
    public function autoEnroll(Request $request): JsonResponse
    {
        $data = $request->validate([
            'pre_shared_key' => ['required', 'string', 'max:255'],
            'platform' => ['required', Rule::enum(ManagedDevicePlatform::class)],
            'device_name' => ['required', 'string', 'max:120'],
            'hostname' => ['nullable', 'string', 'max:255'],
            'os_name' => ['nullable', 'string', 'max:120'],
            'os_version' => ['nullable', 'string', 'max:120'],
            'architecture' => ['nullable', 'string', 'max:30'],
            'agent_version' => ['nullable', 'string', 'max:50'],
            'installer_version' => ['nullable', 'string', 'max:50'],
            'hardware_fingerprint' => ['nullable', 'string', 'min:16', 'max:2048'],
            'capabilities' => ['nullable', 'array', 'max:100'],
            'permissions_state' => ['nullable', 'array', 'max:100'],
        ]);

        $result = $this->enrollment->autoEnroll($data, $request->ip() ?? '');
        $device = $result['device'];

        $this->audit->record(
            'device.auto_enrolled',
            'success',
            device: $device,
            metadata: [
                'platform' => $device->platform->value,
                'agent_version' => $device->agent_version,
                'hostname' => $device->hostname,
            ],
            request: $request,
        );

        return response()->json([
            'data' => [
                'device_uuid' => $device->uuid,
                'site' => [
                    'uuid' => $device->site->uuid,
                    'name' => $device->site->name,
                ],
                'bearer_token' => $result['bearer_token'],
                'server_time' => now()->toIso8601String(),
                'configuration' => [
                    'command_poll_seconds' => config('command-center.agent.command_poll_seconds'),
                    'heartbeat_seconds' => config('command-center.agent.heartbeat_seconds'),
                    'request_signature_required' => config('command-center.agent.require_request_signature'),
                ],
            ],
        ], 201);
    }
}
