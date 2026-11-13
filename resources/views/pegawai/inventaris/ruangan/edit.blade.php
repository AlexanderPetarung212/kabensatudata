@extends('layouts.pegawai')

@section('title', 'Edit Ruangan')

@section('pegawai-content')
<div class="max-w-2xl mx-auto px-4 py-6">
    <div class="flex items-center gap-3 mb-6">
        <a href="{{ route('pegawai.inventaris.ruangan.show', $ruangan) }}" class="p-2 rounded-lg bg-white/5 border border-white/10 hover:bg-white/10 transition">
            <svg class="w-5 h-5 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/></svg>
        </a>
        <h1 class="text-xl font-bold text-white">Edit Ruangan: {{ $ruangan->nama }}</h1>
    </div>

    <form action="{{ route('pegawai.inventaris.ruangan.update', $ruangan) }}" method="POST" enctype="multipart/form-data"
          class="bg-white/5 backdrop-blur border border-white/10 rounded-xl p-6 space-y-5">
        @csrf
        @method('PUT')

        <div>
            <label class="block text-sm font-medium text-slate-300 mb-1">Nama Ruangan <span class="text-red-400">*</span></label>
            <input type="text" name="nama" value="{{ old('nama', $ruangan->nama) }}" required
                   class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none">
            @error('nama')<p class="text-red-400 text-xs mt-1">{{ $message }}</p>@enderror
        </div>

        <div class="grid grid-cols-2 gap-4">
            <div>
                <label class="block text-sm font-medium text-slate-300 mb-1">Kode Ruangan</label>
                <input type="text" name="kode" value="{{ old('kode', $ruangan->kode) }}"
                       class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none">
            </div>
            <div>
                <label class="block text-sm font-medium text-slate-300 mb-1">Lantai</label>
                <input type="text" name="lantai" value="{{ old('lantai', $ruangan->lantai) }}"
                       class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none">
            </div>
        </div>

        <div>
            <label class="block text-sm font-medium text-slate-300 mb-1">Deskripsi</label>
            <textarea name="deskripsi" rows="3"
                      class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none">{{ old('deskripsi', $ruangan->deskripsi) }}</textarea>
        </div>

        <div>
            <label class="block text-sm font-medium text-slate-300 mb-1">Foto Ruangan</label>
            @if($ruangan->foto_path)
                <div class="mb-2">
                    <img src="{{ url('storage/' . $ruangan->foto_path) }}" class="h-24 rounded-lg object-cover" alt="Foto ruangan">
                    <p class="text-xs text-slate-500 mt-1">Upload baru untuk mengganti foto</p>
                </div>
            @endif
            <input type="file" name="foto" accept="image/*"
                   class="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-slate-400 file:mr-3 file:py-1 file:px-3 file:rounded-lg file:border-0 file:bg-blue-600 file:text-white file:text-xs file:font-medium file:cursor-pointer">
        </div>

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
