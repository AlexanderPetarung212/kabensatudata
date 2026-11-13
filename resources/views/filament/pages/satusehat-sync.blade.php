<x-filament-panels::page>
    {{-- Stats Cards --}}
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
        {{-- Total SATUSEHAT Records --}}
        <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 p-4">
            <div class="flex items-center gap-3">
                <div class="p-2 rounded-lg bg-primary-50 dark:bg-primary-500/10">
                    <x-heroicon-o-document-text class="w-6 h-6 text-primary-500" />
                </div>
                <div>
                    <p class="text-sm text-gray-500 dark:text-gray-400">RME SATUSEHAT</p>
                    <p class="text-2xl font-bold text-gray-900 dark:text-white">{{ $stats['total_satusehat'] }}</p>
                </div>
            </div>
        </div>

        {{-- Total Manual Records --}}
        <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 p-4">
            <div class="flex items-center gap-3">
                <div class="p-2 rounded-lg bg-success-50 dark:bg-success-500/10">
                    <x-heroicon-o-pencil-square class="w-6 h-6 text-success-500" />
                </div>
                <div>
                    <p class="text-sm text-gray-500 dark:text-gray-400">RME Manual</p>
                    <p class="text-2xl font-bold text-gray-900 dark:text-white">{{ $stats['total_manual'] }}</p>
                </div>
            </div>
        </div>

        {{-- IHS Matched --}}
        <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 p-4">
            <div class="flex items-center gap-3">
                <div class="p-2 rounded-lg bg-warning-50 dark:bg-warning-500/10">
                    <x-heroicon-o-identification class="w-6 h-6 text-warning-500" />
                </div>
                <div>
                    <p class="text-sm text-gray-500 dark:text-gray-400">IHS Matched</p>
                    <p class="text-2xl font-bold text-gray-900 dark:text-white">
                        {{ $stats['total_ihs'] }}<span class="text-sm font-normal text-gray-400"> / {{ $stats['total_members'] }}</span>
                    </p>
                </div>
            </div>
        </div>

        {{-- Cron Status --}}
        <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 p-4">
            <div class="flex items-center gap-3">
                <div class="p-2 rounded-lg bg-info-50 dark:bg-info-500/10">
                    <x-heroicon-o-clock class="w-6 h-6 text-info-500" />
                </div>
                <div>
                    <p class="text-sm text-gray-500 dark:text-gray-400">Cron Schedule</p>
                    <p class="text-2xl font-bold text-gray-900 dark:text-white">06:00</p>
                    <p class="text-xs text-gray-400">Setiap hari</p>
                </div>
            </div>
        </div>
    </div>

    {{-- Last Sync Status --}}
    @if($runningSync)
        <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-4 mb-6">
            <div class="flex items-center gap-3">
                <svg class="animate-spin h-5 w-5 text-blue-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <div>
                    <p class="font-semibold text-blue-700 dark:text-blue-300">🔄 Sync Sedang Berjalan...</p>
                    <p class="text-sm text-blue-600 dark:text-blue-400">
                        Dimulai {{ $runningSync->started_at->diffForHumans() }}
                        @if($runningSync->triggeredByUser)
                            oleh {{ $runningSync->triggeredByUser->name }}
                        @endif
                    </p>
                </div>
            </div>
        </div>
    @elseif($lastSync)
        <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-xl p-4 mb-6">
            <div class="flex items-center gap-3">
                <x-heroicon-o-check-circle class="w-6 h-6 text-green-500" />
                <div>
                    <p class="font-semibold text-green-700 dark:text-green-300">
                        ✅ Sync terakhir: {{ $lastSync->completed_at->translatedFormat('d M Y, H:i') }}
                        ({{ $lastSync->completed_at->diffForHumans() }})
                    </p>
                    <p class="text-sm text-green-600 dark:text-green-400">
                        {{ $lastSync->imported }} record diimport,
                        {{ $lastSync->skipped_duplicate }} di-skip
                        @if($lastSync->duration) | Durasi: {{ $lastSync->duration }} @endif
                        | Tipe: {{ ucfirst($lastSync->type) }}
                    </p>
                </div>
            </div>
        </div>
    @else
        <div class="bg-gray-50 dark:bg-gray-900/20 border border-gray-200 dark:border-gray-700 rounded-xl p-4 mb-6">
            <p class="text-gray-500 dark:text-gray-400">Belum ada riwayat sync. Klik "Sync Sekarang" untuk memulai.</p>
        </div>
    @endif

    {{-- Sync History Table --}}
    <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div class="p-4 border-b border-gray-200 dark:border-gray-700">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white">📋 Riwayat Sync</h3>
        </div>

        <div class="overflow-x-auto">
            <table class="w-full text-sm">
                <thead class="bg-gray-50 dark:bg-gray-700">
                    <tr>
                        <th class="px-4 py-3 text-left text-gray-600 dark:text-gray-300 font-medium">Waktu</th>
                        <th class="px-4 py-3 text-left text-gray-600 dark:text-gray-300 font-medium">Tipe</th>
                        <th class="px-4 py-3 text-center text-gray-600 dark:text-gray-300 font-medium">Imported</th>
                        <th class="px-4 py-3 text-center text-gray-600 dark:text-gray-300 font-medium">Skipped</th>
                        <th class="px-4 py-3 text-center text-gray-600 dark:text-gray-300 font-medium">Unmatched</th>
                        <th class="px-4 py-3 text-center text-gray-600 dark:text-gray-300 font-medium">Error</th>
                        <th class="px-4 py-3 text-center text-gray-600 dark:text-gray-300 font-medium">Durasi</th>
                        <th class="px-4 py-3 text-center text-gray-600 dark:text-gray-300 font-medium">Status</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
                    @forelse($syncLogs as $log)
                        <tr class="hover:bg-gray-50 dark:hover:bg-gray-700/50">
                            <td class="px-4 py-3 text-gray-900 dark:text-white">
                                {{ $log->started_at->translatedFormat('d M Y, H:i') }}
                                @if($log->triggeredByUser)
                                    <br><span class="text-xs text-gray-400">oleh {{ $log->triggeredByUser->name }}</span>
                                @endif
                            </td>
                            <td class="px-4 py-3">
                                @if($log->type === 'cron')
                                    <span class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
                                        ⏰ Cron
                                    </span>
                                @else
                                    <span class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400">
                                        👤 Manual
                                    </span>
                                @endif
                            </td>
                            <td class="px-4 py-3 text-center font-semibold text-green-600 dark:text-green-400">
                                {{ $log->imported }}
                            </td>
                            <td class="px-4 py-3 text-center text-gray-500 dark:text-gray-400">
                                {{ $log->skipped_duplicate }}
                            </td>
                            <td class="px-4 py-3 text-center text-yellow-600 dark:text-yellow-400">
                                {{ $log->unmatched_patient }}
                            </td>
                            <td class="px-4 py-3 text-center {{ $log->errors > 0 ? 'text-red-600 dark:text-red-400 font-semibold' : 'text-gray-400' }}">
                                {{ $log->errors }}
                            </td>
                            <td class="px-4 py-3 text-center text-gray-500 dark:text-gray-400">
                                {{ $log->duration ?? '-' }}
                            </td>
                            <td class="px-4 py-3 text-center">
                                @if($log->status === 'completed')
                                    <span class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">✅</span>
                                @elseif($log->status === 'running')
                                    <span class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
                                        <svg class="animate-spin h-3 w-3 mr-1" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path></svg>
                                        Running
                                    </span>
                                @else
                                    <span class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
                                        title="{{ $log->error_message }}">❌</span>
                                @endif
                            </td>
                        </tr>
                    @empty
                        <tr>
                            <td colspan="8" class="px-4 py-8 text-center text-gray-400">
                                Belum ada riwayat sync.
                            </td>
                        </tr>
                    @endforelse
                </tbody>
            </table>
        </div>
    </div>

    {{-- Server Setup Info --}}
    <div class="mt-6 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-xl p-4">
        <h4 class="font-semibold text-amber-700 dark:text-amber-300 mb-2">ℹ️ Setup Server (untuk Admin Server)</h4>
        <div class="text-sm text-amber-600 dark:text-amber-400 space-y-1">
            <p><strong>Cron Scheduler:</strong> Pastikan crontab server memiliki entry:</p>
            <code class="block bg-amber-100 dark:bg-amber-900/40 px-3 py-1 rounded text-xs mt-1">
                * * * * * cd /path/to/kabensatudata && php artisan schedule:run >> /dev/null 2>&1
            </code>
            <p class="mt-2"><strong>Queue Worker:</strong> Untuk sync manual via tombol, jalankan:</p>
            <code class="block bg-amber-100 dark:bg-amber-900/40 px-3 py-1 rounded text-xs mt-1">
                php artisan queue:work --timeout=600
            </code>
        </div>
    </div>
</x-filament-panels::page>
