@extends('layouts.admin')

@section('admin-content')
<div class="flex items-center justify-between mb-4">
    <h1 class="text-2xl font-bold text-slate-100">📢 Pemberitahuan APK</h1>
    <a href="{{ route('panel.announcements.create') }}" class="px-3 py-2 rounded-lg text-white bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 transition">Buat Baru</a>
</div>

<div class="mb-4 p-3 rounded-xl border border-white/10 bg-white/5 backdrop-blur text-sm text-slate-300">
    ℹ️ Hanya <strong>1 pemberitahuan aktif</strong> yang akan ditampilkan di APK. Mengaktifkan satu akan menonaktifkan lainnya.
</div>

<div class="relative overflow-x-auto rounded-xl border border-white/10 bg-white/5 backdrop-blur">
    <table class="min-w-full text-sm text-slate-200">
        <thead class="bg-white/5 text-slate-300">
            <tr>
                <th class="text-left px-3 py-2">Status</th>
                <th class="text-left px-3 py-2">Judul</th>
                <th class="text-left px-3 py-2">Tipe</th>
                <th class="text-left px-3 py-2">Pesan</th>
                <th class="text-left px-3 py-2">Terakhir Diubah</th>
                <th class="px-3 py-2">Aksi</th>
            </tr>
        </thead>
        <tbody>
            @forelse($announcements as $a)
                <tr class="border-t border-white/10 {{ $a->is_active ? 'bg-emerald-500/10' : '' }}">
                    <td class="px-3 py-2">
                        <form action="{{ route('panel.announcements.toggle', $a) }}" method="POST" class="inline">
                            @csrf
                            <button type="submit" class="px-2 py-1 rounded text-xs font-semibold transition {{ $a->is_active ? 'bg-emerald-500/20 text-emerald-200 border border-emerald-400/30 hover:bg-emerald-500/30' : 'bg-white/10 text-slate-400 border border-white/10 hover:bg-white/15' }}">
                                {{ $a->is_active ? '✅ Aktif' : '⬜ Nonaktif' }}
                            </button>
                        </form>
                    </td>
                    <td class="px-3 py-2 font-medium">{{ $a->title }}</td>
                    <td class="px-3 py-2">
                        @php
                            $typeColors = [
                                'info' => 'bg-blue-500/20 text-blue-200',
                                'warning' => 'bg-yellow-500/20 text-yellow-200',
                                'update' => 'bg-emerald-500/20 text-emerald-200',
                                'critical' => 'bg-red-500/20 text-red-200',
                            ];
                            $typeLabels = [
                                'info' => 'ℹ️ Info',
                                'warning' => '⚠️ Warning',
                                'update' => '🆕 Update',
                                'critical' => '🚨 Critical',
                            ];
                        @endphp
                        <span class="px-2 py-1 rounded text-xs font-semibold {{ $typeColors[$a->type] ?? '' }}">
                            {{ $typeLabels[$a->type] ?? $a->type }}
                        </span>
                    </td>
                    <td class="px-3 py-2 max-w-xs truncate text-slate-400">{{ Str::limit($a->message, 60) }}</td>
                    <td class="px-3 py-2 text-slate-400 text-xs">{{ $a->updated_at->diffForHumans() }}</td>
                    <td class="px-3 py-2 space-x-2 whitespace-nowrap">
                        <a href="{{ route('panel.announcements.edit', $a) }}" class="px-2 py-1 rounded text-white bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 transition">Ubah</a>
                        <form action="{{ route('panel.announcements.destroy', $a) }}" method="POST" class="inline" onsubmit="return confirm('Hapus pemberitahuan ini?')">
                            @csrf @method('DELETE')
                            <button class="px-2 py-1 rounded bg-red-500/20 text-red-200 border border-red-400/20 hover:bg-red-500/30 transition">Hapus</button>
                        </form>
                    </td>
                </tr>
            @empty
                <tr>
                    <td colspan="6" class="px-3 py-6 text-center text-slate-400">Belum ada pemberitahuan. Klik "Buat Baru" untuk memulai.</td>
                </tr>
            @endforelse
        </tbody>
    </table>
</div>

<div class="mt-4 text-slate-300">{{ $announcements->links() }}</div>
@endsection
