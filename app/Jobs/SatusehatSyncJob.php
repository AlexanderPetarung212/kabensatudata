<?php

namespace App\Jobs;

use App\Models\SatusehatSyncLog;
use App\Services\SatusehatImportService;
use App\Services\SatusehatService;
use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Bus\Dispatchable;
use Illuminate\Queue\InteractsWithQueue;
use Illuminate\Queue\SerializesModels;
use Illuminate\Support\Facades\Log;
use Filament\Notifications\Notification;
use App\Models\User;

/**
 * Background job untuk sync manual RME dari SATUSEHAT.
 * Dipanggil dari halaman admin Filament.
 */
class SatusehatSyncJob implements ShouldQueue
{
    use Dispatchable, InteractsWithQueue, Queueable, SerializesModels;

    public int $timeout = 600; // 10 minutes max

    protected ?int $triggeredBy;
    protected ?string $since;

    public function __construct(?int $triggeredBy = null, ?string $since = null)
    {
        $this->triggeredBy = $triggeredBy;
        $this->since = $since;
    }

    public function handle(SatusehatService $satusehat, SatusehatImportService $importer): void
    {
        // 1. Create sync log
        $syncLog = SatusehatSyncLog::create([
            'type' => 'manual',
            'status' => 'running',
            'triggered_by' => $this->triggeredBy,
            'started_at' => now(),
        ]);

        try {
            // 2. Test connection
            $health = $satusehat->healthCheck();
            if ($health['status'] !== 'connected') {
                throw new \Exception('SATUSEHAT connection failed: ' . ($health['error'] ?? 'unknown'));
            }

            // 3. Run import
            $summary = $importer->importEncounters([
                'since' => $this->since,
                'dryRun' => false,
            ]);

            // 4. Count unmatched
            $unmatched = collect($summary['records'])->where('unmatched_patient', true)->count();

            // 5. Update sync log
            $syncLog->update([
                'status' => 'completed',
                'total_fetched' => $summary['total_fetched'],
                'imported' => $summary['imported'],
                'skipped_duplicate' => $summary['skipped_duplicate'],
                'skipped_existing' => $summary['skipped_existing'],
                'unmatched_patient' => $unmatched,
                'errors' => $summary['errors'],
                'completed_at' => now(),
            ]);

            // 6. Send notification to user
            if ($this->triggeredBy) {
                $user = User::find($this->triggeredBy);
                if ($user) {
                    Notification::make()
                        ->title('✅ Sync SATUSEHAT Selesai')
                        ->body("{$summary['imported']} rekam medis baru diimport, {$summary['skipped_duplicate']} di-skip (duplikat).")
                        ->success()
                        ->sendToDatabase($user);
                }
            }

            Log::info('SATUSEHAT sync completed', [
                'imported' => $summary['imported'],
                'skipped' => $summary['skipped_duplicate'],
                'type' => 'manual',
            ]);

        } catch (\Exception $e) {
            $syncLog->update([
                'status' => 'failed',
                'error_message' => $e->getMessage(),
                'completed_at' => now(),
            ]);

            if ($this->triggeredBy) {
                $user = User::find($this->triggeredBy);
                if ($user) {
                    Notification::make()
                        ->title('❌ Sync SATUSEHAT Gagal')
                        ->body($e->getMessage())
                        ->danger()
                        ->sendToDatabase($user);
                }
            }

            Log::error('SATUSEHAT sync failed', ['error' => $e->getMessage()]);
        }
    }
}
