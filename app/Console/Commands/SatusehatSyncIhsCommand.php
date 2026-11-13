<?php

namespace App\Console\Commands;

use App\Models\FamilyMember;
use App\Models\User;
use App\Services\SatusehatService;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Log;

/**
 * Batch-verify NIK dan simpan IHS Number dari SATUSEHAT.
 *
 * Operasi READ-ONLY: hanya GET ke SATUSEHAT, update lokal.
 */
class SatusehatSyncIhsCommand extends Command
{
    protected $signature = 'satusehat:sync-ihs
        {--type=patients : Tipe data: patients atau practitioners}
        {--limit=0 : Batasi jumlah yang diproses (0 = semua)}
        {--dry-run : Simulasi saja, tidak menyimpan}';

    protected $description = 'Sync IHS Number dari SATUSEHAT untuk family_members dan users';

    protected SatusehatService $satusehat;

    public function handle(SatusehatService $satusehat): int
    {
        $this->satusehat = $satusehat;
        $type = $this->option('type');

        // Test connection first
        $this->info('Testing SATUSEHAT connection...');
        $health = $satusehat->healthCheck();
        if ($health['status'] !== 'connected') {
            $this->error('SATUSEHAT connection failed: ' . ($health['error'] ?? 'unknown'));
            return 1;
        }
        $this->info("✅ Connected: {$health['organization_name']}");
        $this->newLine();

        return match ($type) {
            'patients' => $this->syncPatients(),
            'practitioners' => $this->syncPractitioners(),
            default => $this->error("Type harus 'patients' atau 'practitioners'") ?? 1,
        };
    }

    protected function syncPatients(): int
    {
        $query = FamilyMember::whereNotNull('nik')
            ->where('nik', '!=', '')
            ->whereNull('ihs_number');

        $limit = (int) $this->option('limit');
        if ($limit > 0) {
            $query->limit($limit);
        }

        $members = $query->get();
        $total = $members->count();

        if ($total === 0) {
            $this->info('Semua family_members sudah punya IHS Number, atau tidak ada NIK.');
            return 0;
        }

        $this->info("Processing {$total} family members tanpa IHS Number...");
        $this->newLine();

        $bar = $this->output->createProgressBar($total);
        $bar->start();

        $found = 0;
        $notFound = 0;
        $errors = 0;

        foreach ($members as $member) {
            $nik = str_pad((string) $member->nik, 16, '0', STR_PAD_LEFT);

            // Skip invalid NIK
            if (!preg_match('/^\d{16}$/', $nik)) {
                $bar->advance();
                $errors++;
                continue;
            }

            try {
                $result = $this->satusehat->verifyNik($nik);

                if ($result['success'] && ($result['found'] ?? false)) {
                    $ihsNumber = $result['patient']['ihs_number'] ?? null;

                    if ($ihsNumber && !$this->option('dry-run')) {
                        $member->update([
                            'ihs_number'          => $ihsNumber,
                            'satusehat_synced_at' => now(),
                            // Tandai NIK sebagai terverifikasi SATUSEHAT
                            'nik_verified_status' => 'verified',
                            'nik_verified_at'     => now(),
                            'nik_verified_source' => 'satusehat',
                        ]);
                    }

                    $found++;
                } else {
                    $notFound++;
                }
            } catch (\Exception $e) {
                $errors++;
                Log::warning("SATUSEHAT sync-ihs error for NIK {$nik}", [
                    'member_id' => $member->id,
                    'error' => $e->getMessage(),
                ]);
            }

            // Rate limit: ~2 requests/second
            usleep(500000);

            $bar->advance();
        }

        $bar->finish();
        $this->newLine(2);

        $dryLabel = $this->option('dry-run') ? ' (DRY RUN — tidak disimpan)' : '';
        $this->info("Hasil{$dryLabel}:");
        $this->table(
            ['Metrik', 'Jumlah'],
            [
                ['Total diproses', $total],
                ['Ditemukan di SATUSEHAT', $found],
                ['Tidak ditemukan', $notFound],
                ['Error/Invalid NIK', $errors],
            ]
        );

        return 0;
    }

    protected function syncPractitioners(): int
    {
        $query = User::whereNotNull('nik_nakes')
            ->where('nik_nakes', '!=', '')
            ->whereNull('ihs_number');

        $users = $query->get();
        $total = $users->count();

        if ($total === 0) {
            $this->info('Semua users sudah punya IHS Number, atau tidak ada nik_nakes.');
            $this->info('Pastikan field nik_nakes sudah diisi untuk tenaga kesehatan.');
            return 0;
        }

        $this->info("Processing {$total} users (nakes) tanpa IHS Number...");
        $this->newLine();

        $found = 0;
        $notFound = 0;
        $errors = 0;

        foreach ($users as $user) {
            $nik = $user->nik_nakes;

            if (!preg_match('/^\d{16}$/', $nik)) {
                $this->warn("  Skip User#{$user->id} {$user->name}: NIK invalid ({$nik})");
                $errors++;
                continue;
            }

            try {
                $result = $this->satusehat->findPractitioner($nik);

                if ($result['success'] && ($result['found'] ?? false)) {
                    $ihsNumber = $result['practitioner']['ihs_number'] ?? null;
                    $this->info("  ✅ User#{$user->id} {$user->name} → IHS: {$ihsNumber}");

                    if ($ihsNumber && !$this->option('dry-run')) {
                        $user->update(['ihs_number' => $ihsNumber]);
                    }

                    $found++;
                } else {
                    $this->warn("  ❌ User#{$user->id} {$user->name} → Tidak ditemukan di SATUSEHAT");
                    $notFound++;
                }
            } catch (\Exception $e) {
                $errors++;
                $this->error("  ⚠ User#{$user->id} {$user->name} → Error: {$e->getMessage()}");
            }

            usleep(500000);
        }

        $this->newLine();
        $dryLabel = $this->option('dry-run') ? ' (DRY RUN)' : '';
        $this->info("Hasil{$dryLabel}:");
        $this->table(
            ['Metrik', 'Jumlah'],
            [
                ['Total diproses', $total],
                ['Ditemukan', $found],
                ['Tidak ditemukan', $notFound],
                ['Error', $errors],
            ]
        );

        return 0;
    }
}
