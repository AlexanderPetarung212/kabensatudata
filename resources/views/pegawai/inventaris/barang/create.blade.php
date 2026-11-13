@extends('layouts.pegawai')

@section('title', 'Tambah Barang — ' . $ruangan->nama)

@section('pegawai-content')
<div class="max-w-2xl mx-auto px-4 py-6">
    <div class="flex items-center gap-3 mb-6">
        <a href="{{ route('pegawai.inventaris.ruangan.show', $ruangan) }}" class="p-2 rounded-lg bg-white/5 border border-white/10 hover:bg-white/10 transition">
            <svg class="w-5 h-5 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/></svg>
        </a>
        <div>
            <h1 class="text-xl font-bold text-white">Tambah Barang</h1>
            <p class="text-xs text-slate-400 mt-0.5">Ruangan: {{ $ruangan->nama }}</p>
        </div>
    </div>

    <form action="{{ route('pegawai.inventaris.barang.store', $ruangan) }}" method="POST" enctype="multipart/form-data"
          class="bg-white/5 backdrop-blur border border-white/10 rounded-xl p-6 space-y-5">
        @csrf

        {{-- Global Error Display --}}
        @if ($errors->any())
            <div class="bg-red-500/10 border border-red-400/30 rounded-lg p-4">
                <div class="flex items-center gap-2 mb-2">
                    <svg class="w-5 h-5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                    <span class="text-red-400 font-semibold text-sm">Gagal menyimpan!</span>
                </div>
                <ul class="list-disc list-inside space-y-1">
                    @foreach ($errors->all() as $error)
                        <li class="text-red-300 text-xs">{{ $error }}</li>
                    @endforeach
                </ul>
            </div>
        @endif

        <div>
            <label class="block text-sm font-medium text-slate-300 mb-1">Nama Barang <span class="text-red-400">*</span></label>
            <input type="text" name="nama" value="{{ old('nama') }}" required
                   class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none"
                   placeholder="e.g. Tensimeter Digital">
            @error('nama')<p class="text-red-400 text-xs mt-1">{{ $message }}</p>@enderror
        </div>

        <div class="grid grid-cols-2 gap-4">
            <div>
                <label class="block text-sm font-medium text-slate-300 mb-1">Kode Inventaris</label>
                <input type="text" name="kode_inventaris" value="{{ old('kode_inventaris') }}"
                       class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none"
                       placeholder="e.g. INV-2024-001">
            </div>
            <div>
                <label class="block text-sm font-medium text-slate-300 mb-1">Merek / Tipe</label>
                <input type="text" name="merek" value="{{ old('merek') }}"
                       class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none"
                       placeholder="e.g. Omron HEM-7156">
            </div>
        </div>

        <div class="grid grid-cols-3 gap-4">
            <div>
                <label class="block text-sm font-medium text-slate-300 mb-1">Jumlah <span class="text-red-400">*</span></label>
                <input type="number" name="jumlah" value="{{ old('jumlah', 1) }}" min="1" required
                       class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none">
            </div>
            <div>
                <label class="block text-sm font-medium text-slate-300 mb-1">Kondisi <span class="text-red-400">*</span></label>
                <select name="kondisi" required
                        class="w-full bg-slate-800 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none">
                    <option value="baik" {{ old('kondisi') == 'baik' ? 'selected' : '' }}>Baik</option>
                    <option value="rusak_ringan" {{ old('kondisi') == 'rusak_ringan' ? 'selected' : '' }}>Rusak Ringan</option>
                    <option value="rusak_berat" {{ old('kondisi') == 'rusak_berat' ? 'selected' : '' }}>Rusak Berat</option>
                    <option value="tidak_aktif" {{ old('kondisi') == 'tidak_aktif' ? 'selected' : '' }}>Tidak Aktif</option>
                </select>
            </div>
            <div>
                <label class="block text-sm font-medium text-slate-300 mb-1">Tahun Perolehan</label>
                <input type="number" name="tahun_perolehan" value="{{ old('tahun_perolehan') }}" min="1900" max="{{ date('Y') + 1 }}"
                       class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none"
                       placeholder="{{ date('Y') }}">
            </div>
        </div>

        <div>
            <label class="block text-sm font-medium text-slate-300 mb-1">Sumber Dana</label>
            <input type="text" name="sumber_dana" value="{{ old('sumber_dana') }}"
                   class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none"
                   placeholder="e.g. DAK, BOK, APBD, Hibah">
        </div>

        <div>
            <label class="block text-sm font-medium text-slate-300 mb-1">Keterangan</label>
            <textarea name="keterangan" rows="2"
                      class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none"
                      placeholder="Catatan tambahan...">{{ old('keterangan') }}</textarea>
        </div>

        <div>
            <label class="block text-sm font-medium text-slate-300 mb-1">Foto Barang</label>
            <input type="file" name="foto" accept="image/*"
                   class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-slate-400 file:mr-3 file:py-1 file:px-3 file:rounded-lg file:border-0 file:bg-blue-600 file:text-white file:text-xs file:font-medium file:cursor-pointer">
            <p class="text-xs text-slate-500 mt-1">Maks 5MB</p>
        </div>

        <div class="flex gap-3 pt-2">
            <button type="submit"
                    class="px-6 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition shadow-lg shadow-blue-600/20">
                Simpan Barang
            </button>
            <a href="{{ route('pegawai.inventaris.ruangan.show', $ruangan) }}"
               class="px-6 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 text-slate-300 text-sm font-medium transition">
                Batal
            </a>
        </div>
    </form>
</div>
@endsection
