<div class="space-y-4">
    <div>
        <label class="block text-sm font-medium text-slate-300 mb-1">Judul</label>
        <input type="text" name="title" value="{{ old('title', $announcement->title ?? '') }}" required maxlength="255"
            class="w-full bg-white/10 border border-white/10 rounded-lg px-3 py-2 text-slate-200 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-cyan-400/40"
            placeholder="Contoh: Pembaruan Aplikasi v2.0">
    </div>

    <div>
        <label class="block text-sm font-medium text-slate-300 mb-1">Tipe Pemberitahuan</label>
        <select name="type" required
            class="w-full bg-white/10 border border-white/10 rounded-lg px-3 py-2 text-slate-200 focus:outline-none focus:ring-2 focus:ring-cyan-400/40">
            @php
                $types = ['info' => 'ℹ️ Info — Pengumuman umum', 'warning' => '⚠️ Warning — Peringatan', 'update' => '🆕 Update — Versi baru', 'critical' => '🚨 Critical — Wajib perhatian'];
                $currentType = old('type', $announcement->type ?? 'info');
            @endphp
            @foreach($types as $value => $label)
                <option value="{{ $value }}" {{ $currentType === $value ? 'selected' : '' }} class="bg-slate-800">{{ $label }}</option>
            @endforeach
        </select>
    </div>

    <div>
        <label class="block text-sm font-medium text-slate-300 mb-1">Pesan</label>
        <textarea name="message" required maxlength="2000" rows="5"
            class="w-full bg-white/10 border border-white/10 rounded-lg px-3 py-2 text-slate-200 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-cyan-400/40"
            placeholder="Tulis pesan pemberitahuan yang akan ditampilkan di APK...">{{ old('message', $announcement->message ?? '') }}</textarea>
        <p class="text-xs text-slate-400 mt-1">Maks 2000 karakter</p>
    </div>

    <div class="flex gap-3">
        <button type="submit" class="px-4 py-2 rounded-lg text-white bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 transition font-medium">
            {{ isset($announcement) ? 'Simpan Perubahan' : 'Buat Pemberitahuan' }}
        </button>
        <a href="{{ route('panel.announcements.index') }}" class="px-4 py-2 rounded-lg text-slate-300 bg-white/10 border border-white/10 hover:bg-white/15 transition">Batal</a>
    </div>
</div>
