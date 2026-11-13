<?php

use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

// ─── SATUSEHAT Auto-Sync ────────────────────────────────────
// Import RME dari SATUSEHAT setiap hari jam 06:00 pagi
// Hanya ambil data 2 hari terakhir agar cepat
Schedule::command('satusehat:import-rme --type=cron --since=' . now()->subDays(2)->format('Y-m-d'))
    ->dailyAt('06:00')
    ->withoutOverlapping()
    ->appendOutputTo(storage_path('logs/satusehat-sync.log'))
    ->onFailure(function () {
        \Illuminate\Support\Facades\Log::error('SATUSEHAT cron sync failed');
    });

if (config('command-center.enabled', true)) {
    // Command Center device status and command expiry.
    Schedule::command('command-center:maintain')
        ->everyMinute()
        ->withoutOverlapping();

    // Retention cleanup is separated so regular heartbeat maintenance stays fast.
    Schedule::command('command-center:maintain --prune')
        ->dailyAt('02:30')
        ->withoutOverlapping();
}
