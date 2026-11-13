@extends('layouts.pegawai')

@section('title', 'Kelola Pegawai — ' . $ruangan->nama)

@section('pegawai-content')
<div class="max-w-3xl mx-auto px-4 py-6 space-y-6">

    {{-- Header --}}
    <div class="flex items-center gap-3">
        <a href="{{ route('pegawai.inventaris.ruangan.show', $ruangan) }}" class="p-2 rounded-lg bg-white/5 border border-white/10 hover:bg-white/10 transition">
            <svg class="w-5 h-5 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/></svg>
        </a>
        <div>
            <h1 class="text-xl font-bold text-white">Kelola Pegawai</h1>
            <p class="text-sm text-slate-400">{{ $ruangan->nama }} @if($ruangan->kode)· {{ $ruangan->kode }}@endif</p>
        </div>
    </div>

    <form action="{{ route('pegawai.inventaris.ruangan.pegawai.store', $ruangan) }}" method="POST"
          class="bg-white/5 backdrop-blur border border-white/10 rounded-xl p-6 space-y-6">
        @csrf

        {{-- PJ Selection --}}
        <div>
            <label class="block text-sm font-semibold text-white mb-2">
                👤 Penanggung Jawab (PJ)
            </label>
            <p class="text-xs text-slate-400 mb-3">Pilih satu pegawai sebagai PJ ruangan ini.</p>
            <select name="pj_id"
                    class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none">
                <option value="" class="bg-slate-800">— Tidak ada PJ —</option>
                @foreach($allPegawai as $p)
                    <option value="{{ $p->id }}" class="bg-slate-800"
                        {{ $currentPjId == $p->id ? 'selected' : '' }}>
                        {{ $p->nama }} — {{ $p->jabatan ?? 'Tidak ada jabatan' }}
                    </option>
                @endforeach
            </select>
            @error('pj_id')<p class="text-red-400 text-xs mt-1">{{ $message }}</p>@enderror
        </div>

        {{-- Anggota Selection --}}
        <div>
            <label class="block text-sm font-semibold text-white mb-2">
                👥 Pegawai Bertugas
            </label>
            <p class="text-xs text-slate-400 mb-3">Centang pegawai yang bertugas di ruangan ini (selain PJ).</p>

            @if($allPegawai->isEmpty())
                <p class="text-slate-500 text-sm">Belum ada data pegawai.</p>
            @else
                <div class="grid sm:grid-cols-2 gap-2 max-h-72 overflow-y-auto pr-1">
                    @foreach($allPegawai as $p)
                        <label class="flex items-center gap-3 p-3 rounded-lg bg-white/[0.03] border border-white/5 hover:bg-white/[0.06] transition cursor-pointer">
                            <input type="checkbox" name="anggota_ids[]" value="{{ $p->id }}"
                                   class="rounded border-slate-600 text-blue-500 focus:ring-blue-500 bg-white/5"
                                {{ in_array($p->id, $currentAnggotaIds) ? 'checked' : '' }}>
                            <div class="flex items-center gap-2 min-w-0">
                                @if($p->foto_path)
                                    <img src="{{ url('storage/' . $p->foto_path) }}" class="w-8 h-8 rounded-full object-cover flex-shrink-0" alt="">
                                @else
                                    <div class="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center flex-shrink-0">
                                        <span class="text-xs font-semibold text-blue-400">{{ substr($p->nama, 0, 1) }}</span>
                                    </div>
                                @endif
                                <div class="min-w-0">
                                    <div class="text-sm text-white font-medium truncate">{{ $p->nama }}</div>
                                    <div class="text-[10px] text-slate-400 truncate">{{ $p->jabatan ?? '-' }}</div>
                                </div>
                            </div>
                        </label>
                    @endforeach
                </div>
            @endif
            @error('anggota_ids')<p class="text-red-400 text-xs mt-1">{{ $message }}</p>@enderror
        </div>

        {{-- Actions --}}
        <div class="flex gap-3 pt-2">
            <button type="submit"
                    class="px-6 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition shadow-lg shadow-blue-600/20">
                Simpan Perubahan
            </button>
            <a href="{{ route('pegawai.inventaris.ruangan.show', $ruangan) }}"
               class="px-6 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 text-slate-300 text-sm font-medium transition">
                Batal
            </a>
        </div>
    </form>
</div>
@endsection
