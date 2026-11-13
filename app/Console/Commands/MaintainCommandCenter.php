<?php

namespace App\Console\Commands;

use App\Enums\DeviceCommandStatus;
use App\Enums\ManagedDeviceStatus;
use App\Models\CommandCenterAuditLog;
use App\Models\DeviceArtifact;
use App\Models\DeviceCommand;
use App\Models\DeviceHeartbeat;
use App\Models\DeviceMetric;
use App\Models\ManagedDevice;
use App\Services\CommandCenter\DeviceAlertService;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\Schema;

class MaintainCommandCenter extends Command
{
    protected $signature = 'command-center:maintain {--prune : Hapus data yang melewati retention}';

    protected $description = 'Perbarui status perangkat, kedaluwarsa command, dan bersihkan data Command Center.';

    public function handle(DeviceAlertService $alerts): int
    {
        if (!Schema::hasTable('managed_devices')) {
            $this->warn('Tabel Command Center belum tersedia; maintenance dilewati.');

            return self::SUCCESS;
        }

        $offlineBefore = now()->subSeconds(config('command-center.agent.offline_after_seconds', 180));

        $offline = 0;
        ManagedDevice::query()
            ->whereNull('revoked_at')
            ->where('status', '!=', ManagedDeviceStatus::Offline->value)
            ->where(function ($query) use ($offlineBefore): void {
                $query->whereNull('last_seen_at')
                    ->orWhere('last_seen_at', '<', $offlineBefore);
            })
            ->chunkById(200, function ($devices) use (&$offline, $alerts): void {
                foreach ($devices as $device) {
                    $device->update(['status' => ManagedDeviceStatus::Offline]);
                    $alerts->markOffline($device);
                    $offline++;
                }
            });

        $expired = 0;
        DeviceCommand::query()
            ->whereIn('status', [
                DeviceCommandStatus::Requested->value,
                DeviceCommandStatus::Authorized->value,
                DeviceCommandStatus::Queued->value,
                DeviceCommandStatus::Sent->value,
                DeviceCommandStatus::Delivered->value,
                DeviceCommandStatus::Executing->value,
            ])
            ->where('expires_at', '<=', now())
            ->chunkById(200, function ($commands) use (&$expired): void {
                foreach ($commands as $command) {
                    $command->update([
                        'status' => DeviceCommandStatus::Expired,
                        'failed_at' => now(),
                        'error_code' => 'COMMAND_EXPIRED',
                    ]);
                    $command->events()->create([
                        'event' => DeviceCommandStatus::Expired->value,
                        'source' => 'server',
                        'occurred_at' => now(),
                    ]);
                    $expired++;
                }
            });

        if ($this->option('prune')) {
            $this->prune();
        }

        $this->info("Command Center maintenance selesai: {$offline} offline, {$expired} command expired.");

        return self::SUCCESS;
    }

    private function prune(): void
    {
        DeviceHeartbeat::query()
            ->where('recorded_at', '<', now()->subDays(config('command-center.retention.heartbeat_days', 7)))
            ->delete();

        DeviceMetric::query()
            ->where('recorded_at', '<', now()->subDays(config('command-center.retention.metric_days', 30)))
            ->delete();

        CommandCenterAuditLog::query()
            ->where('occurred_at', '<', now()->subDays(config('command-center.retention.audit_days', 365)))
            ->delete();

        DeviceArtifact::query()
            ->whereNull('deleted_at')
            ->whereNotNull('expires_at')
            ->where('expires_at', '<=', now())
            ->chunkById(100, function ($artifacts): void {
                foreach ($artifacts as $artifact) {
                    Storage::disk($artifact->disk)->delete($artifact->path);
                    $artifact->update(['deleted_at' => now()]);
                }
            });
    }
}
