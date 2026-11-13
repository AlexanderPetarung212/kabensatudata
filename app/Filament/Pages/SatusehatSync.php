<?php

namespace App\Filament\Pages;

use App\Models\MedicalRecord;
use App\Models\FamilyMember;
use App\Models\SatusehatSyncLog;
use App\Services\SatusehatService;
use App\Services\SatusehatImportService;
use Filament\Pages\Page;
use Filament\Actions\Action;
use Filament\Notifications\Notification;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Log;

class SatusehatSync extends Page
{
    protected static ?string $navigationIcon = 'heroicon-o-arrow-path';
    protected static ?string $navigationLabel = 'SATUSEHAT Sync';
    protected static ?string $navigationGroup = 'Integrasi';
    protected static ?int $navigationSort = 100;
    protected static ?string $title = 'SATUSEHAT Sync';
    protected static string $view = 'filament.pages.satusehat-sync';

    public function getViewData(): array
    {
        $lastSync = SatusehatSyncLog::where('status', 'completed')
            ->latest('completed_at')
            ->first();

        $runningSync = SatusehatSyncLog::where('status', 'running')->first();

        return [
            'stats' => [
                'total_satusehat' => MedicalRecord::where('data_source', 'satusehat')->count(),
                'total_manual' => MedicalRecord::where('data_source', 'manual')->count(),
                'total_ihs' => FamilyMember::whereNotNull('ihs_number')->count(),
                'total_members' => FamilyMember::count(),
            ],
            'lastSync' => $lastSync,
            'runningSync' => $runningSync,
            'syncLogs' => SatusehatSyncLog::latest('started_at')->take(20)->get(),
        ];
    }

    /**
     * Run sync directly (synchronous) — no queue worker needed.
     */
    protected function runSync(?string $since = null): void
    {
        // Check if already running
        $running = SatusehatSyncLog::where('status', 'running')->first();
        if ($running) {
            // Auto-cleanup stale running syncs (older than 30 minutes)
            if ($running->started_at->diffInMinutes(now()) > 30) {
                $running->update([
                    'status' => 'failed',
                    'error_message' => 'Timeout: sync berjalan lebih dari 30 menit',
                    'completed_at' => now(),
                ]);
            } else {
                Notification::make()
                    ->title('⚠️ Sync Sedang Berjalan')
                    ->body('Tunggu sync sebelumnya selesai (dimulai ' . $running->started_at->diffForHumans() . ').')
                    ->warning()
                    ->send();
                return;
            }
        }

        // Create sync log
        $syncLog = SatusehatSyncLog::create([
            'type' => 'manual',
            'status' => 'running',
            'triggered_by' => Auth::id(),
            'started_at' => now(),
        ]);

        try {
            // Test connection
            $satusehat = app(SatusehatService::class);
            $health = $satusehat->healthCheck();

            if ($health['status'] !== 'connected') {
                throw new \Exception('Koneksi SATUSEHAT gagal: ' . ($health['error'] ?? 'unknown'));
            }

            // Run import
            $importer = app(SatusehatImportService::class);
            $summary = $importer->importEncounters([
                'since' => $since,
                'dryRun' => false,
            ]);

            // Count unmatched
            $unmatched = collect($summary['records'])->where('unmatched_patient', true)->count();

            // Update sync log
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

            Notification::make()
                ->title('✅ Sync SATUSEHAT Selesai')
                ->body("{$summary['imported']} rekam medis baru diimport, {$summary['skipped_duplicate']} di-skip (duplikat).")
                ->success()
                ->send();

            Log::info('SATUSEHAT sync completed via admin', [
                'imported' => $summary['imported'],
                'skipped' => $summary['skipped_duplicate'],
                'user' => Auth::user()?->name,
            ]);

        } catch (\Exception $e) {
            $syncLog->update([
                'status' => 'failed',
                'error_message' => $e->getMessage(),
                'completed_at' => now(),
            ]);

            Notification::make()
                ->title('❌ Sync SATUSEHAT Gagal')
                ->body($e->getMessage())
                ->danger()
                ->send();

            Log::error('SATUSEHAT sync failed via admin', [
                'error' => $e->getMessage(),
                'user' => Auth::user()?->name,
            ]);
        }
    }

    protected function getHeaderActions(): array
    {
        return [
            Action::make('syncNow')
                ->label('🔄 Sync Sekarang')
                ->color('primary')
                ->icon('heroicon-o-arrow-path')
                ->requiresConfirmation()
                ->modalHeading('Sync dari SATUSEHAT')
                ->modalDescription('Ini akan menarik data rekam medis 7 hari terakhir dari SATUSEHAT. Proses ini mungkin memakan waktu 30-60 detik.')
                ->modalSubmitActionLabel('Ya, Sync Sekarang')
                ->action(function () {
                    $since = now()->subDays(7)->format('Y-m-d');
                    $this->runSync($since);
                }),

            Action::make('syncAll')
                ->label('📥 Sync Semua Data')
                ->color('gray')
                ->icon('heroicon-o-arrow-down-tray')
                ->requiresConfirmation()
                ->modalHeading('Sync SEMUA Data dari SATUSEHAT')
                ->modalDescription('Ini akan menarik SEMUA data rekam medis dari SATUSEHAT. Proses ini mungkin memakan waktu 1-3 menit.')
                ->modalSubmitActionLabel('Ya, Sync Semua')
                ->action(function () {
                    $this->runSync(null);
                }),
        ];
    }
}
