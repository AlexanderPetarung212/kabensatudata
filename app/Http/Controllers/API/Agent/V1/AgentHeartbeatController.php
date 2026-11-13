<?php

namespace App\Http\Controllers\API\Agent\V1;

use App\Enums\ManagedDeviceStatus;
use App\Http\Controllers\Controller;
use App\Models\DeviceHeartbeat;
use App\Models\DeviceMetric;
use App\Models\ManagedDevice;
use App\Services\CommandCenter\DeviceAlertService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class AgentHeartbeatController extends Controller
{
    public function __construct(
        private readonly DeviceAlertService $alerts,
    ) {
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'uptime_seconds' => ['nullable', 'integer', 'min:0'],
            'user_session' => ['nullable', 'string', 'max:255'],
            'screen_locked' => ['nullable', 'boolean'],
            'agent_version' => ['nullable', 'string', 'max:50'],
            'local_ip' => ['nullable', 'ip'],
            'capabilities' => ['nullable', 'array', 'max:100'],
            'permissions_state' => ['nullable', 'array', 'max:100'],
            'health' => ['nullable', 'array', 'max:100'],
            'metrics' => ['nullable', 'array'],
            'metrics.cpu_percent' => ['nullable', 'numeric', 'min:0', 'max:100'],
            'metrics.memory_percent' => ['nullable', 'numeric', 'min:0', 'max:100'],
            'metrics.memory_used_bytes' => ['nullable', 'integer', 'min:0'],
            'metrics.memory_total_bytes' => ['nullable', 'integer', 'min:0'],
            'metrics.disk_used_bytes' => ['nullable', 'integer', 'min:0'],
            'metrics.disk_total_bytes' => ['nullable', 'integer', 'min:0'],
            'metrics.network_rx_bytes' => ['nullable', 'integer', 'min:0'],
            'metrics.network_tx_bytes' => ['nullable', 'integer', 'min:0'],
            'metrics.details' => ['nullable', 'array', 'max:100'],
        ]);

        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');
        $recordedAt = now();
        $status = ($data['screen_locked'] ?? false)
            ? ManagedDeviceStatus::Idle
            : ManagedDeviceStatus::Online;

        $device->update([
            'status' => $status,
            'last_seen_at' => $recordedAt,
            'last_ip' => $request->ip(),
            'agent_version' => $data['agent_version'] ?? $device->agent_version,
            'capabilities' => $data['capabilities'] ?? $device->capabilities,
            'permissions_state' => $data['permissions_state'] ?? $device->permissions_state,
        ]);
        $this->alerts->resolveOffline($device);

        DeviceHeartbeat::create([
            'managed_device_id' => $device->id,
            'recorded_at' => $recordedAt,
            'uptime_seconds' => $data['uptime_seconds'] ?? null,
            'user_session' => $data['user_session'] ?? null,
            'screen_locked' => $data['screen_locked'] ?? null,
            'agent_version' => $data['agent_version'] ?? null,
            'local_ip' => $data['local_ip'] ?? null,
            'health' => $data['health'] ?? null,
        ]);

        if (!empty($data['metrics'])) {
            DeviceMetric::create([
                'managed_device_id' => $device->id,
                'recorded_at' => $recordedAt,
                ...$data['metrics'],
            ]);
            $this->alerts->evaluateMetrics($device, $data['metrics'], $recordedAt);
        }

        return response()->json([
            'data' => [
                'status' => $status->value,
                'server_time' => $recordedAt->toIso8601String(),
                'next_heartbeat_seconds' => config('command-center.agent.heartbeat_seconds'),
                'next_command_poll_seconds' => config('command-center.agent.command_poll_seconds'),
            ],
        ]);
    }
}
