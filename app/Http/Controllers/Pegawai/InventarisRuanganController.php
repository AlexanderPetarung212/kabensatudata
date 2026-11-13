<?php

namespace App\Http\Controllers\Pegawai;

use App\Http\Controllers\Controller;
use App\Models\Ruangan;
use App\Models\InventarisBarang;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\Support\Facades\Storage;

class InventarisRuanganController extends Controller
{
    /**
     * Daftar semua ruangan + summary stats
     */
    public function index(Request $request)
    {
        $search = $request->input('search');

        $query = Ruangan::withCount('barang')
            ->withSum('barang', 'jumlah');

        if ($search) {
            $query->where(function ($q) use ($search) {
                $q->where('nama', 'like', "%{$search}%")
                  ->orWhere('kode', 'like', "%{$search}%")
                  ->orWhere('lantai', 'like', "%{$search}%");
            });
        }

        $ruangan = $query->orderBy('nama')->get();

        // Global stats
        $stats = [
            'total_ruangan' => Ruangan::count(),
            'total_jenis'   => InventarisBarang::count(),
            'total_unit'    => InventarisBarang::sum('jumlah'),
            'kondisi'       => [
                'baik'         => InventarisBarang::where('kondisi', 'baik')->sum('jumlah'),
                'rusak_ringan' => InventarisBarang::where('kondisi', 'rusak_ringan')->sum('jumlah'),
                'rusak_berat'  => InventarisBarang::where('kondisi', 'rusak_berat')->sum('jumlah'),
                'tidak_aktif'  => InventarisBarang::where('kondisi', 'tidak_aktif')->sum('jumlah'),
            ],
        ];

        return view('pegawai.inventaris.ruangan.index', compact('ruangan', 'stats', 'search'));
    }

    /**
     * Form tambah ruangan
     */
    public function create()
    {
        return view('pegawai.inventaris.ruangan.create');
    }

    /**
     * Simpan ruangan baru
     */
    public function store(Request $request)
    {
        $validated = $request->validate([
            'nama'      => 'required|string|max:255',
            'kode'      => 'nullable|string|max:50',
            'lantai'    => 'nullable|string|max:20',
            'deskripsi' => 'nullable|string',
            'foto'      => 'nullable|image|max:5120',
        ]);

        $data = collect($validated)->except('foto')->toArray();

        if ($request->hasFile('foto')) {
            $data['foto_path'] = $request->file('foto')->store('inventaris/ruangan', 'public');
        }

        $ruangan = Ruangan::create($data);

        return redirect()
            ->route('pegawai.inventaris.ruangan.show', $ruangan)
            ->with('success', "Ruangan \"{$ruangan->nama}\" berhasil ditambahkan.");
    }

    /**
     * Detail ruangan + list barang + pegawai
     */
    public function show(Ruangan $ruangan)
    {
        $ruangan->load([
            'barang' => fn($q) => $q->orderBy('nama'),
            'penanggungJawab',
            'pegawaiAnggota',
        ]);
        $kondisiSummary = $ruangan->getKondisiSummary();

        return view('pegawai.inventaris.ruangan.show', compact('ruangan', 'kondisiSummary'));
    }

    /**
     * Halaman kelola pegawai per ruangan
     */
    public function managePegawai(Ruangan $ruangan)
    {
        $ruangan->load('pegawai');
        $allPegawai = \App\Models\Pegawai::orderBy('nama')->get();
        $currentPjId = $ruangan->penanggungJawab()->first()?->id;
        $currentAnggotaIds = $ruangan->pegawaiAnggota()->pluck('pegawai.id')->toArray();

        return view('pegawai.inventaris.ruangan.manage-pegawai', compact(
            'ruangan', 'allPegawai', 'currentPjId', 'currentAnggotaIds'
        ));
    }

    /**
     * Simpan assignment pegawai ke ruangan
     */
    public function storePegawai(Request $request, Ruangan $ruangan)
    {
        $validated = $request->validate([
            'pj_id'       => 'nullable|exists:pegawai,id',
            'anggota_ids' => 'nullable|array',
            'anggota_ids.*' => 'exists:pegawai,id',
        ]);

        // Hapus semua assignment lama
        $ruangan->pegawai()->detach();

        // Assign PJ
        if (!empty($validated['pj_id'])) {
            $ruangan->pegawai()->attach($validated['pj_id'], ['role' => 'pj']);
        }

        // Assign anggota (kecuali yang sudah jadi PJ)
        $anggotaIds = collect($validated['anggota_ids'] ?? [])
            ->reject(fn($id) => $id == ($validated['pj_id'] ?? null))
            ->values();

        foreach ($anggotaIds as $pegawaiId) {
            $ruangan->pegawai()->attach($pegawaiId, ['role' => 'anggota']);
        }

        return redirect()
            ->route('pegawai.inventaris.ruangan.show', $ruangan)
            ->with('success', 'Pegawai berhasil diperbarui.');
    }


    /**
     * Form edit ruangan
     */
    public function edit(Ruangan $ruangan)
    {
        return view('pegawai.inventaris.ruangan.edit', compact('ruangan'));
    }

    /**
     * Update ruangan
     */
    public function update(Request $request, Ruangan $ruangan)
    {
        $validated = $request->validate([
            'nama'      => 'required|string|max:255',
            'kode'      => 'nullable|string|max:50',
            'lantai'    => 'nullable|string|max:20',
            'deskripsi' => 'nullable|string',
            'foto'      => 'nullable|image|max:5120',
        ]);

        $data = collect($validated)->except('foto')->toArray();

        if ($request->hasFile('foto')) {
            // Hapus foto lama
            if ($ruangan->foto_path) {
                Storage::disk('public')->delete($ruangan->foto_path);
            }
            $data['foto_path'] = $request->file('foto')->store('inventaris/ruangan', 'public');
        }

        $ruangan->update($data);

        return redirect()
            ->route('pegawai.inventaris.ruangan.show', $ruangan)
            ->with('success', 'Ruangan berhasil diperbarui.');
    }

    /**
     * Hapus ruangan + semua barang terkait
     */
    public function destroy(Ruangan $ruangan)
    {
        $nama = $ruangan->nama;

        // Hapus foto ruangan
        if ($ruangan->foto_path) {
            Storage::disk('public')->delete($ruangan->foto_path);
        }

        // Hapus foto barang
        foreach ($ruangan->barang as $barang) {
            if ($barang->foto_path) {
                Storage::disk('public')->delete($barang->foto_path);
            }
        }

        $ruangan->delete();

        return redirect()
            ->route('pegawai.inventaris.ruangan.index')
            ->with('success', "Ruangan \"{$nama}\" dan semua barang terkait berhasil dihapus.");
    }

    /**
     * Halaman cetak QR code (print-friendly)
     */
    public function printQr(Ruangan $ruangan)
    {
        return view('pegawai.inventaris.ruangan.qr-print', compact('ruangan'));
    }
}
