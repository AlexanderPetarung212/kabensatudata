<?php

namespace App\Console\Commands;

use App\Models\SatusehatSyncLog;
use App\Services\SatusehatImportService;
use App\Services\SatusehatService;
use Illuminate\Console\Command;

/**
 * Import Rekam Medis Elektronik dari SATUSEHAT ke KabenSatuData.
 *
 * Operasi READ-ONLY: hanya GET dari SATUSEHAT, insert ke database lokal.
 */
class SatusehatImportRmeCommand extends Command
{
    protected $signature = 'satusehat:import-rme
        {--dry-run : Simulasi saja, tidak menyimpan ke database}
        {--limit=0 : Batasi jumlah encounter yang diimport (0 = semua)}
        {--since= : Hanya import encounter setelah tanggal ini (YYYY-MM-DD)}
        {--type=manual : Tipe sync: manual atau cron}';

    protected $description = 'Import data Rekam Medis Elektronik dari SATUSEHAT ke KabenSatuData (READ-ONLY)';

    public function handle(SatusehatService $satusehat, SatusehatImportService $importer): int
    {
        $dryRun = $this->option('dry-run');
        $limit = (int) $this->option('limit');
        $since = $this->option('since');
        $type = $this->option('type');

        // Create sync log (unless dry-run)
        $syncLog = null;
        if (!$dryRun) {
            $syncLog = SatusehatSyncLog::create([
                'type' => $type,
                'status' => 'running',
                'started_at' => now(),
            ]);
        }

        // Header
        $this->newLine();
        $this->info('╔══════════════════════════════════════════════════╗');
        $this->info('║     SATUSEHAT RME Import — KabenSatuData        ║');
        $this->info('╚══════════════════════════════════════════════════╝');
        $this->newLine();

        if ($dryRun) {
            $this->warn('🔍 Mode: DRY RUN — tidak ada data yang disimpan');
            $this->newLine();
        }

        // Test connection
        $this->info('Menghubungi SATUSEHAT...');
        $health = $satusehat->healthCheck();
        if ($health['status'] !== 'connected') {
            $errorMsg = 'Koneksi gagal: ' . ($health['error'] ?? 'unknown');
            $this->error("❌ {$errorMsg}");
            $syncLog?->update([
                'status' => 'failed',
                'error_message' => $errorMsg,
                'completed_at' => now(),
            ]);
            return 1;
        }
        $this->info("✅ Connected: {$health['organization_name']}");
        $this->info("   Mode: READ-ONLY | Type: {$type}");
        $this->newLine();

        // Pre-import stats
        $totalMembers = \App\Models\FamilyMember::whereNotNull('ihs_number')->count();
        $totalUsers = \App\Models\User::whereNotNull('ihs_number')->count();
        $this->info("📊 Pre-import status:");
        $this->info("   Family members dengan IHS: {$totalMembers}");
        $this->info("   Users (nakes) dengan IHS:  {$totalUsers}");
        $this->newLine();

        if ($totalMembers === 0 && $type !== 'cron') {
            $this->warn('⚠️  Belum ada family_members dengan IHS Number.');
            $this->warn('   Jalankan "php artisan satusehat:sync-ihs --type=patients" terlebih dahulu.');
            $this->warn('   Import tetap berjalan, tapi pasien tidak bisa di-match (family_member_id = null).');
            $this->newLine();

            if (!$dryRun && !$this->confirm('Lanjutkan tanpa IHS matching?', true)) {
                return 0;
            }
        }

        // Run import
        $this->info('📥 Mengambil data dari SATUSEHAT...');

        $bar = null;

        try {
            $summary = $importer->importEncounters([
                'limit' => $limit,
                'since' => $since,
                'dryRun' => $dryRun,
                'onProgress' => function ($current, $total, $result) use (&$bar) {
                    if (!$bar) {
                        $bar = $this->output->createProgressBar($total);
                        $bar->setFormat(" %current%/%max% [%bar%] %percent:3s%% %message%");
                        $bar->start();
                    }

                    $icon = match ($result['action']) {
                        'imported' => '✅',
                        'skipped_duplicate' => '⏭️',
                        'skipped_existing' => '📋',
                        'skipped_cancelled' => '🚫',
                        'errors' => '❌',
                        default => '  ',
                    };

                    $bar->setMessage("{$icon} {$result['patient_name']}");
                    $bar->advance();
                },
            ]);
        } catch (\Exception $e) {
            $syncLog?->update([
                'status' => 'failed',
                'error_message' => $e->getMessage(),
                'completed_at' => now(),
            ]);
            $this->error("❌ Import gagal: {$e->getMessage()}");
            return 1;
        }

        if ($bar) {
            $bar->finish();
        }
        $this->newLine(2);

        // Count unmatched
        $unmatched = collect($summary['records'])->where('unmatched_patient', true)->count();

        // Update sync log
        $syncLog?->update([
            'status' => 'completed',
            'total_fetched' => $summary['total_fetched'],
            'imported' => $summary['imported'],
            'skipped_duplicate' => $summary['skipped_duplicate'],
            'skipped_existing' => $summary['skipped_existing'],
            'unmatched_patient' => $unmatched,
            'errors' => $summary['errors'],
            'completed_at' => now(),
        ]);

        // Summary
        $dryLabel = $dryRun ? ' (DRY RUN)' : '';
        $this->info("📊 Hasil Import{$dryLabel}:");
        $this->table(
            ['Metrik', 'Jumlah'],
            [
                ['Total dari SATUSEHAT', $summary['total_fetched']],
                ['✅ Berhasil diimport', $summary['imported']],
                ['⏭️ Skip (sudah diimport sebelumnya)', $summary['skipped_duplicate']],
                ['📋 Skip (record manual sudah ada)', $summary['skipped_existing']],
                ['🚫 Skip (cancelled)', $summary['skipped_cancelled']],
                ['⚠️ Pasien tidak ter-match', $unmatched],
                ['❌ Error', $summary['errors']],
            ]
        );

        // Show unmatched patients (only for manual, not cron)
        if ($unmatched > 0 && $type !== 'cron') {
            $this->newLine();
            $this->warn("⚠️  {$unmatched} pasien tidak ter-match ke family_members:");
            $unmatchedRecords = collect($summary['records'])
                ->where('unmatched_patient', true)
                ->take(20);
            foreach ($unmatchedRecords as $r) {
                $this->line("   - {$r['patient_name']} (IHS: {$r['patient_ihs']}, {$r['visit_date']})");
            }
            if ($unmatched > 20) {
                $this->line("   ... dan " . ($unmatched - 20) . " lainnya");
            }
            $this->newLine();
            $this->info('💡 Jalankan "php artisan satusehat:sync-ihs --type=patients" untuk mencocokkan pasien.');
        }

        $this->newLine();
        if (!$dryRun && $summary['imported'] > 0) {
            $this->info("✅ Selesai! {$summary['imported']} rekam medis berhasil diimport dari SATUSEHAT.");
        } elseif ($dryRun) {
            $this->info("🔍 Dry run selesai. Jalankan tanpa --dry-run untuk import sesungguhnya.");
        } else {
            $this->info("ℹ️  Tidak ada data baru untuk diimport.");
        }

        return 0;
    }
}
