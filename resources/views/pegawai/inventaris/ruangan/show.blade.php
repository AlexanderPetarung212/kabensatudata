@extends('layouts.pegawai')

@section('title', $ruangan->nama . ' — Inventaris')

@section('pegawai-content')
<div class="max-w-5xl mx-auto px-4 py-6 space-y-6">

    {{-- Flash --}}
    @if(session('success'))
        <div class="bg-emerald-500/15 border border-emerald-400/30 rounded-xl px-4 py-3 text-emerald-300 text-sm flex items-center gap-2">
            <svg class="w-5 h-5 shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
            {{ session('success') }}
        </div>
    @endif

    {{-- Header --}}
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div class="flex items-center gap-3">
            <a href="{{ route('pegawai.inventaris.ruangan.index') }}" class="p-2 rounded-lg bg-white/5 border border-white/10 hover:bg-white/10 transition">
                <svg class="w-5 h-5 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/></svg>
            </a>
            <div>
                <h1 class="text-xl font-bold text-white">{{ $ruangan->nama }}</h1>
                <div class="flex items-center gap-2 text-xs text-slate-400 mt-0.5">
                    @if($ruangan->kode)<span class="px-1.5 py-0.5 rounded bg-white/10">{{ $ruangan->kode }}</span>@endif
                    @if($ruangan->lantai)<span>Lantai {{ $ruangan->lantai }}</span>@endif
                </div>
            </div>
        </div>
        <div class="flex items-center gap-2 flex-wrap">
            <a href="{{ route('pegawai.inventaris.barang.create', $ruangan) }}"
               class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium transition">
                <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/></svg>
                Tambah Barang
            </a>
            <a href="{{ route('pegawai.inventaris.ruangan.pegawai', $ruangan) }}"
               class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-cyan-600/20 hover:bg-cyan-600/30 text-cyan-300 text-xs font-medium transition border border-cyan-400/20">
                👥 Kelola Pegawai
            </a>
            <a href="{{ route('pegawai.inventaris.ruangan.edit', $ruangan) }}"
               class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-white/10 hover:bg-white/15 text-slate-300 text-xs font-medium transition">
                ✏️ Edit
            </a>
            <a href="{{ route('pegawai.inventaris.barang.export', $ruangan) }}"
               class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-300 text-xs font-medium transition border border-emerald-400/20">
                📥 Export Excel
            </a>
            <form action="{{ route('pegawai.inventaris.ruangan.destroy', $ruangan) }}" method="POST"
                  onsubmit="return confirm('Hapus ruangan ini dan SEMUA barang di dalamnya?')">
                @csrf @method('DELETE')
                <button type="submit" class="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-red-600/20 hover:bg-red-600/30 text-red-300 text-xs font-medium transition border border-red-400/20">
                    🗑️ Hapus
                </button>
            </form>
        </div>
    </div>

    {{-- PJ & Pegawai Section --}}
    @php
        $pj = $ruangan->penanggungJawab->first();
        $anggota = $ruangan->pegawaiAnggota;
    @endphp
    @if($pj || $anggota->isNotEmpty())
    <div class="bg-white/5 backdrop-blur border border-white/10 rounded-xl p-5 space-y-4">
        {{-- PJ --}}
        @if($pj)
        <div class="flex items-center gap-4">
            @if($pj->foto_path)
                <img src="{{ url('storage/' . $pj->foto_path) }}" class="w-14 h-14 rounded-full object-cover ring-2 ring-emerald-400/40" alt="">
            @else
                <div class="w-14 h-14 rounded-full bg-emerald-500/20 flex items-center justify-center ring-2 ring-emerald-400/30">
                    <span class="text-xl font-bold text-emerald-400">{{ substr($pj->nama, 0, 1) }}</span>
                </div>
            @endif
            <div>
                <div class="text-[10px] text-emerald-400 font-semibold uppercase tracking-wider">Penanggung Jawab</div>
                <div class="text-white font-semibold text-base">{{ $pj->nama }}</div>
                <div class="text-xs text-slate-400">{{ $pj->jabatan ?? '-' }}</div>
            </div>
        </div>
        @endif

        {{-- Anggota --}}
        @if($anggota->isNotEmpty())
        <div>
            <div class="text-xs text-slate-400 font-medium mb-2">Petugas Bertugas</div>
            <div class="flex flex-wrap gap-2">
                @foreach($anggota as $a)
                <div class="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/5 border border-white/10">
                    @if($a->foto_path)
                        <img src="{{ url('storage/' . $a->foto_path) }}" class="w-6 h-6 rounded-full object-cover" alt="">
                    @else
                        <div class="w-6 h-6 rounded-full bg-blue-500/20 flex items-center justify-center">
                            <span class="text-[9px] font-semibold text-blue-400">{{ substr($a->nama, 0, 1) }}</span>
                        </div>
                    @endif
                    <span class="text-xs text-slate-300">{{ $a->nama }}</span>
                </div>
                @endforeach
            </div>
        </div>
        @endif
    </div>
    @endif

    {{-- Stats + QR --}}
    <div class="grid sm:grid-cols-2 gap-4">
        {{-- Kondisi Summary --}}
        <div class="bg-white/5 backdrop-blur border border-white/10 rounded-xl p-5">
            <h3 class="text-sm font-semibold text-slate-300 mb-3">Ringkasan Kondisi</h3>
            <div class="grid grid-cols-2 gap-3">
                <div class="bg-emerald-500/10 rounded-lg p-3 text-center">
                    <div class="text-xl font-bold text-emerald-400">{{ $kondisiSummary['baik'] }}</div>
                    <div class="text-[10px] text-emerald-400/70 mt-0.5">Baik</div>
                </div>
                <div class="bg-amber-500/10 rounded-lg p-3 text-center">
                    <div class="text-xl font-bold text-amber-400">{{ $kondisiSummary['rusak_ringan'] }}</div>
                    <div class="text-[10px] text-amber-400/70 mt-0.5">Rusak Ringan</div>
                </div>
                <div class="bg-red-500/10 rounded-lg p-3 text-center">
                    <div class="text-xl font-bold text-red-400">{{ $kondisiSummary['rusak_berat'] }}</div>
                    <div class="text-[10px] text-red-400/70 mt-0.5">Rusak Berat</div>
                </div>
                <div class="bg-slate-500/10 rounded-lg p-3 text-center">
                    <div class="text-xl font-bold text-slate-400">{{ $kondisiSummary['tidak_aktif'] }}</div>
                    <div class="text-[10px] text-slate-400/70 mt-0.5">Tidak Aktif</div>
                </div>
            </div>
        </div>

        {{-- QR Code --}}
        <div class="bg-white/5 backdrop-blur border border-white/10 rounded-xl p-5 flex flex-col items-center justify-center">
            <h3 class="text-sm font-semibold text-slate-300 mb-3">QR Code Ruangan</h3>
            <div class="bg-white rounded-xl p-3">
                {!! \SimpleSoftwareIO\QrCode\Facades\QrCode::size(150)->margin(1)->generate($ruangan->getPublicUrl()) !!}
            </div>
            <p class="text-[10px] text-slate-500 mt-2 text-center max-w-[200px] break-all">{{ $ruangan->getPublicUrl() }}</p>
            <a href="{{ route('pegawai.inventaris.ruangan.qr-print', $ruangan) }}" target="_blank"
               class="mt-3 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/10 hover:bg-white/15 text-slate-300 text-xs font-medium transition">
                🖨️ Cetak QR
            </a>
        </div>
    </div>

    {{-- Deskripsi --}}
    @if($ruangan->deskripsi)
        <div class="bg-white/5 border border-white/10 rounded-xl p-4">
            <p class="text-sm text-slate-300">{{ $ruangan->deskripsi }}</p>
        </div>
    @endif

    {{-- Daftar Barang --}}
    <div>
        <h2 class="text-lg font-bold text-white mb-3">Daftar Barang ({{ $ruangan->barang->count() }} jenis)</h2>

        @if($ruangan->barang->isEmpty())
            <div class="bg-white/5 border border-white/10 rounded-xl p-8 text-center">
                <div class="text-3xl mb-2">📦</div>
                <p class="text-slate-400 text-sm">Belum ada barang di ruangan ini.</p>
                <a href="{{ route('pegawai.inventaris.barang.create', $ruangan) }}"
                   class="inline-flex items-center gap-1.5 mt-3 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium transition">
                    + Tambah Barang Pertama
                </a>
            </div>
        @else
            <div class="bg-white/5 border border-white/10 rounded-xl overflow-hidden">
                <div class="overflow-x-auto">
                    <table class="w-full text-sm">
                        <thead>
                            <tr class="border-b border-white/10 text-left">
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase">#</th>
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase">Nama Barang</th>
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase">Kode</th>
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase">Merek</th>
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase text-center">Jumlah</th>
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase">Kondisi</th>
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase">Tahun</th>
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase">Sumber Dana</th>
                                <th class="px-4 py-3 text-xs font-semibold text-slate-400 uppercase text-right">Aksi</th>
                            </tr>
                        </thead>
                        <tbody class="divide-y divide-white/5">
                            @foreach($ruangan->barang as $i => $b)
                                @php
                                    $kondisiColors = [
                                        'baik' => 'bg-emerald-400/15 text-emerald-300 border-emerald-400/30',
                                        'rusak_ringan' => 'bg-amber-400/15 text-amber-300 border-amber-400/30',
                                        'rusak_berat' => 'bg-red-400/15 text-red-300 border-red-400/30',
                                        'tidak_aktif' => 'bg-slate-400/15 text-slate-400 border-slate-400/30',
                                    ];
                                @endphp
                                <tr class="hover:bg-white/[0.03] transition">
                                    <td class="px-4 py-3 text-slate-500">{{ $i + 1 }}</td>
                                    <td class="px-4 py-3 text-white font-medium">{{ $b->nama }}</td>
                                    <td class="px-4 py-3 text-slate-400 text-xs">{{ $b->kode_inventaris ?? '-' }}</td>
                                    <td class="px-4 py-3 text-slate-400 text-xs">{{ $b->merek ?? '-' }}</td>
                                    <td class="px-4 py-3 text-white font-semibold text-center">{{ $b->jumlah }}</td>
                                    <td class="px-4 py-3">
                                        <span class="inline-block px-2 py-0.5 rounded-full text-[10px] font-medium border {{ $kondisiColors[$b->kondisi] ?? '' }}">
                                            {{ $b->kondisi_label }}
                                        </span>
                                    </td>
                                    <td class="px-4 py-3 text-slate-400 text-xs">{{ $b->tahun_perolehan ?? '-' }}</td>
                                    <td class="px-4 py-3 text-slate-400 text-xs">{{ $b->sumber_dana ?? '-' }}</td>
                                    <td class="px-4 py-3 text-right">
                                        <div class="flex items-center justify-end gap-1">
                                            <a href="{{ route('pegawai.inventaris.barang.edit', [$ruangan, $b]) }}"
                                               class="p-1.5 rounded-lg hover:bg-white/10 text-blue-400 transition" title="Edit">
                                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
                                            </a>
                                            <form action="{{ route('pegawai.inventaris.barang.destroy', [$ruangan, $b]) }}" method="POST"
                                                  onsubmit="return confirm('Hapus barang ini?')">
                                                @csrf @method('DELETE')
                                                <button type="submit" class="p-1.5 rounded-lg hover:bg-white/10 text-red-400 transition" title="Hapus">
                                                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
                                                </button>
                                            </form>
                                        </div>
                                    </td>
                                </tr>
                            @endforeach
                        </tbody>
                    </table>
                </div>
            </div>
        @endif
    </div>

</div>
@endsection
