<?php

namespace App\Http\Controllers\API\Agent\V1;

use App\Http\Controllers\Controller;
use App\Models\DeviceMetric;
use App\Models\ManagedDevice;
use App\Services\CommandCenter\DeviceAlertService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class AgentTelemetryController extends Controller
{
    public function __construct(
        private readonly DeviceAlertService $alerts,
    ) {
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'recorded_at' => ['nullable', 'date'],
            'cpu_percent' => ['nullable', 'numeric', 'min:0', 'max:100'],
            'memory_percent' => ['nullable', 'numeric', 'min:0', 'max:100'],
            'memory_used_bytes' => ['nullable', 'integer', 'min:0'],
            'memory_total_bytes' => ['nullable', 'integer', 'min:0'],
            'disk_used_bytes' => ['nullable', 'integer', 'min:0'],
            'disk_total_bytes' => ['nullable', 'integer', 'min:0'],
            'network_rx_bytes' => ['nullable', 'integer', 'min:0'],
            'network_tx_bytes' => ['nullable', 'integer', 'min:0'],
            'details' => ['nullable', 'array', 'max:100'],
        ]);

        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');

        $recordedAt = isset($data['recorded_at'])
            ? \Illuminate\Support\Carbon::parse($data['recorded_at'])
            : now();
        if ($recordedAt->lt(now()->subDay()) || $recordedAt->gt(now()->addMinutes(5))) {
            return response()->json([
                'message' => 'Waktu telemetry berada di luar rentang yang diizinkan.',
                'errors' => ['recorded_at' => ['Gunakan waktu perangkat yang tersinkron.']],
            ], 422);
        }

        $metricValues = collect($data)->except('recorded_at')->all();
        DeviceMetric::create([
            'managed_device_id' => $device->id,
            'recorded_at' => $recordedAt,
            ...$metricValues,
        ]);
        $this->alerts->evaluateMetrics($device, $metricValues, $recordedAt);

        return response()->json(['data' => ['accepted' => true]], 202);
    }
}
