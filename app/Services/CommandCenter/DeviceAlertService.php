<?php

namespace App\Services\CommandCenter;

use App\Models\DeviceAlert;
use App\Models\ManagedDevice;
use Carbon\CarbonInterface;

class DeviceAlertService
{
    public function markOffline(ManagedDevice $device, ?CarbonInterface $detectedAt = null): DeviceAlert
    {
        return $this->openOrRefresh(
            $device,
            'device_offline',
            'critical',
            'Perangkat offline',
            'Perangkat tidak mengirim heartbeat sesuai batas waktu.',
            ['last_seen_at' => $device->last_seen_at?->toIso8601String()],
            $detectedAt,
        );
    }

    public function resolveOffline(ManagedDevice $device): void
    {
        $this->resolve($device, 'device_offline');
    }

    public function evaluateMetrics(
        ManagedDevice $device,
        array $metrics,
        ?CarbonInterface $detectedAt = null,
    ): void {
        $this->evaluatePercentage(
            $device,
            'cpu_high',
            'CPU tinggi',
            isset($metrics['cpu_percent']) ? (float) $metrics['cpu_percent'] : null,
            (float) config('command-center.alerts.cpu_percent', 95),
            $detectedAt,
        );
        $this->evaluatePercentage(
            $device,
            'memory_high',
            'Pemakaian memori tinggi',
            isset($metrics['memory_percent']) ? (float) $metrics['memory_percent'] : null,
            (float) config('command-center.alerts.memory_percent', 90),
            $detectedAt,
        );

        $diskTotal = (int) ($metrics['disk_total_bytes'] ?? 0);
        $diskPercent = $diskTotal > 0
            ? ((int) ($metrics['disk_used_bytes'] ?? 0) * 100 / $diskTotal)
            : null;
        $this->evaluatePercentage(
            $device,
            'disk_high',
            'Kapasitas disk menipis',
            $diskPercent,
            (float) config('command-center.alerts.disk_percent', 90),
            $detectedAt,
        );
    }

    private function evaluatePercentage(
        ManagedDevice $device,
        string $type,
        string $title,
        ?float $value,
        float $threshold,
        ?CarbonInterface $detectedAt,
    ): void {
        if ($value === null) {
            return;
        }

        if ($value < $threshold) {
            $this->resolve($device, $type);

            return;
        }

        $rounded = round($value, 2);
        $this->openOrRefresh(
            $device,
            $type,
            $value >= min(100, $threshold + 3) ? 'critical' : 'warning',
            $title,
            "{$title}: {$rounded}% (batas {$threshold}%).",
            [
                'value_percent' => $rounded,
                'threshold_percent' => $threshold,
            ],
            $detectedAt,
        );
    }

    private function openOrRefresh(
        ManagedDevice $device,
        string $type,
        string $severity,
        string $title,
        string $message,
        array $metadata,
        ?CarbonInterface $detectedAt,
    ): DeviceAlert {
        $alert = DeviceAlert::query()
            ->where('managed_device_id', $device->id)
            ->where('type', $type)
            ->where('status', 'open')
            ->first();

        if ($alert) {
            $alert->update([
                'severity' => $severity,
                'message' => $message,
                'metadata' => $metadata,
            ]);

            return $alert->fresh();
        }

        return DeviceAlert::create([
            'managed_device_id' => $device->id,
            'type' => $type,
            'severity' => $severity,
            'status' => 'open',
            'title' => $title,
            'message' => $message,
            'metadata' => $metadata,
            'detected_at' => $detectedAt ?? now(),
        ]);
    }

    private function resolve(ManagedDevice $device, string $type): void
    {
        DeviceAlert::query()
            ->where('managed_device_id', $device->id)
            ->where('type', $type)
            ->where('status', 'open')
            ->update([
                'status' => 'resolved',
                'resolved_at' => now(),
            ]);
    }
}
