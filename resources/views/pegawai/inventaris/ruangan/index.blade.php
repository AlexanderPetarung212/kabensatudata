@extends('layouts.pegawai')

@section('title', 'Inventaris Ruangan')

@section('pegawai-content')
<div class="max-w-7xl mx-auto px-4 py-6 space-y-6">

    {{-- Flash Message --}}
    @if(session('success'))
        <div class="bg-emerald-500/15 border border-emerald-400/30 rounded-xl px-4 py-3 text-emerald-300 text-sm flex items-center gap-2">
            <svg class="w-5 h-5 shrink-0" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
            {{ session('success') }}
        </div>
    @endif

    {{-- Header --}}
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
            <h1 class="text-2xl font-bold text-white">📦 Inventaris Ruangan</h1>
            <p class="text-slate-400 text-sm mt-1">Kelola ruangan dan barang inventaris Puskesmas</p>
        </div>
        <a href="{{ route('pegawai.inventaris.ruangan.create') }}"
           class="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition shadow-lg shadow-blue-600/20">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/></svg>
            Tambah Ruangan
        </a>
    </div>

    {{-- Stats Cards --}}
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div class="bg-white/5 backdrop-blur border border-white/10 rounded-xl p-4">
            <div class="text-2xl font-bold text-white">{{ $stats['total_ruangan'] }}</div>
            <div class="text-xs text-slate-400 mt-1">Total Ruangan</div>
        </div>
        <div class="bg-white/5 backdrop-blur border border-white/10 rounded-xl p-4">
            <div class="text-2xl font-bold text-white">{{ $stats['total_unit'] }}</div>
            <div class="text-xs text-slate-400 mt-1">Total Unit Barang</div>
        </div>
        <div class="bg-emerald-500/10 border border-emerald-400/20 rounded-xl p-4">
            <div class="text-2xl font-bold text-emerald-400">{{ $stats['kondisi']['baik'] }}</div>
            <div class="text-xs text-emerald-400/70 mt-1">Kondisi Baik</div>
        </div>
        <div class="bg-red-500/10 border border-red-400/20 rounded-xl p-4">
            <div class="text-2xl font-bold text-red-400">{{ $stats['kondisi']['rusak_ringan'] + $stats['kondisi']['rusak_berat'] }}</div>
            <div class="text-xs text-red-400/70 mt-1">Rusak (Ringan+Berat)</div>
        </div>
    </div>

    {{-- Search --}}
    <form method="GET" class="relative">
        <input type="text" name="search" value="{{ $search }}" placeholder="Cari ruangan..."
               class="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 pl-10 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none">
        <svg class="w-4 h-4 text-slate-500 absolute left-3 top-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
    </form>

    {{-- Ruangan Grid --}}
    @if($ruangan->isEmpty())
        <div class="bg-white/5 border border-white/10 rounded-xl p-12 text-center">
            <div class="text-4xl mb-3">🏠</div>
            <p class="text-slate-400">Belum ada ruangan. Klik "Tambah Ruangan" untuk memulai.</p>
        </div>
    @else
        <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            @foreach($ruangan as $r)
                <a href="{{ route('pegawai.inventaris.ruangan.show', $r) }}"
                   class="group bg-white/5 backdrop-blur border border-white/10 rounded-xl overflow-hidden hover:border-blue-400/40 hover:bg-white/[0.07] transition-all duration-200">
                    {{-- Foto --}}
                    <div class="h-36 bg-gradient-to-br from-blue-600/20 to-purple-600/20 flex items-center justify-center overflow-hidden">
                        @if($r->foto_path)
                            <img src="{{ url('storage/' . $r->foto_path) }}" class="w-full h-full object-cover" alt="{{ $r->nama }}">
                        @else
                            <span class="text-5xl opacity-40">🏠</span>
                        @endif
                    </div>
                    {{-- Info --}}
                    <div class="p-4">
                        <div class="flex items-start justify-between gap-2">
                            <div>
                                <h3 class="font-semibold text-white group-hover:text-blue-300 transition">{{ $r->nama }}</h3>
                                <div class="flex items-center gap-2 mt-1 text-xs text-slate-400">
                                    @if($r->kode)<span class="px-1.5 py-0.5 rounded bg-white/10">{{ $r->kode }}</span>@endif
                                    @if($r->lantai)<span>Lantai {{ $r->lantai }}</span>@endif
                                </div>
                            </div>
                        </div>
                        <div class="mt-3 flex items-center gap-3 text-xs">
                            <span class="text-slate-300">
                                <span class="font-semibold text-white">{{ $r->barang_sum_jumlah ?? 0 }}</span> unit
                            </span>
                            <span class="text-slate-500">•</span>
                            <span class="text-slate-300">
                                <span class="font-semibold text-white">{{ $r->barang_count ?? 0 }}</span> jenis
                            </span>
                        </div>
                    </div>
                </a>
            @endforeach
        </div>
    @endif

</div>
@endsection
