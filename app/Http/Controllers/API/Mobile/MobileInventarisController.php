<?php

namespace App\Http\Controllers\API\Mobile;

use App\Http\Controllers\Controller;
use App\Models\Ruangan;
use App\Models\InventarisBarang;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;

class MobileInventarisController extends Controller
{
    /**
     * List semua ruangan + stats
     */
    public function index()
    {
        $ruangan = Ruangan::withCount('barang')
            ->withSum('barang', 'jumlah')
            ->with('penanggungJawab:pegawai.id,pegawai.nama,pegawai.jabatan,pegawai.foto_path')
            ->orderBy('nama')
            ->get()
            ->map(fn($r) => [
                'id'             => $r->id,
                'uuid'           => $r->uuid,
                'nama'           => $r->nama,
                'kode'           => $r->kode,
                'lantai'         => $r->lantai,
                'deskripsi'      => $r->deskripsi,
                'foto_url'       => $r->foto_path ? url('storage/' . $r->foto_path) : null,
                'total_barang'   => $r->barang_count ?? 0,
                'total_unit'     => (int) ($r->barang_sum_jumlah ?? 0),
                'kondisi_summary' => $r->getKondisiSummary(),
                'ar_enabled'     => (bool) $r->ar_enabled,
                'penanggung_jawab' => $r->penanggungJawab->first()
                    ? $this->formatPegawai($r->penanggungJawab->first())
                    : null,
            ]);

        // Global stats
        $stats = [
            'total_ruangan' => Ruangan::count(),
            'total_jenis'   => InventarisBarang::count(),
            'total_unit'    => (int) InventarisBarang::sum('jumlah'),
            'kondisi'       => [
                'baik'         => (int) InventarisBarang::where('kondisi', 'baik')->sum('jumlah'),
                'rusak_ringan' => (int) InventarisBarang::where('kondisi', 'rusak_ringan')->sum('jumlah'),
                'rusak_berat'  => (int) InventarisBarang::where('kondisi', 'rusak_berat')->sum('jumlah'),
                'tidak_aktif'  => (int) InventarisBarang::where('kondisi', 'tidak_aktif')->sum('jumlah'),
            ],
        ];

        return response()->json(['data' => $ruangan, 'stats' => $stats]);
    }

    /**
     * Detail ruangan + list barang
     */
    public function show(Ruangan $ruangan)
    {
        $ruangan->load([
            'barang' => fn($q) => $q->orderBy('nama'),
            'penanggungJawab',
            'pegawaiAnggota',
        ]);

        $pj = $ruangan->penanggungJawab->first();

        $data = [
            'id'              => $ruangan->id,
            'uuid'            => $ruangan->uuid,
            'nama'            => $ruangan->nama,
            'kode'            => $ruangan->kode,
            'lantai'          => $ruangan->lantai,
            'deskripsi'       => $ruangan->deskripsi,
            'foto_url'        => $ruangan->foto_path ? url('storage/' . $ruangan->foto_path) : null,
            'total_unit'      => $ruangan->barang->sum('jumlah'),
            'kondisi_summary' => $ruangan->getKondisiSummary(),
            'penanggung_jawab' => $pj ? $this->formatPegawai($pj) : null,
            'pegawai_bertugas' => $ruangan->pegawaiAnggota->map(fn($p) => $this->formatPegawai($p))->values(),
            'barang'          => $ruangan->barang->map(fn($b) => [
                'id'              => $b->id,
                'ruangan_id'      => $b->ruangan_id,
                'nama'            => $b->nama,
                'kode_inventaris' => $b->kode_inventaris,
                'merek'           => $b->merek,
                'jumlah'          => $b->jumlah,
                'kondisi'         => $b->kondisi,
                'tahun_perolehan' => $b->tahun_perolehan,
                'sumber_dana'     => $b->sumber_dana,
                'foto_url'        => $b->foto_path ? url('storage/' . $b->foto_path) : null,
                'keterangan'      => $b->keterangan,
            ]),
        ];

        return response()->json(['data' => $data]);
    }

    /**
     * Get ruangan by UUID (untuk QR scan)
     */
    public function showByUuid(string $uuid)
    {
        $ruangan = Ruangan::where('uuid', $uuid)->firstOrFail();
        return $this->show($ruangan);
    }

    /**
     * Tambah ruangan baru
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

        return response()->json([
            'data'    => $this->formatRuangan($ruangan),
            'message' => 'Ruangan berhasil ditambahkan.',
        ], 201);
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
            if ($ruangan->foto_path) {
                Storage::disk('public')->delete($ruangan->foto_path);
            }
            $data['foto_path'] = $request->file('foto')->store('inventaris/ruangan', 'public');
        }

        $ruangan->update($data);

        return response()->json([
            'data'    => $this->formatRuangan($ruangan->fresh()),
            'message' => 'Ruangan berhasil diperbarui.',
        ]);
    }

    /**
     * Hapus ruangan
     */
    public function destroy(Ruangan $ruangan)
    {
        if ($ruangan->foto_path) {
            Storage::disk('public')->delete($ruangan->foto_path);
        }
        foreach ($ruangan->barang as $b) {
            if ($b->foto_path) Storage::disk('public')->delete($b->foto_path);
        }

        $ruangan->delete();

        return response()->json(['message' => 'Ruangan berhasil dihapus.']);
    }

    /**
     * Tambah barang ke ruangan
     */
    public function storeBarang(Request $request, Ruangan $ruangan)
    {
        $validated = $request->validate([
            'nama'             => 'required|string|max:255',
            'kode_inventaris'  => 'nullable|string|max:100',
            'merek'            => 'nullable|string|max:255',
            'jumlah'           => 'required|integer|min:1',
            'kondisi'          => 'required|in:baik,rusak_ringan,rusak_berat,tidak_aktif',
            'tahun_perolehan'  => 'nullable|integer|min:1900|max:' . (date('Y') + 1),
            'sumber_dana'      => 'nullable|string|max:100',
            'keterangan'       => 'nullable|string',
            'foto'             => 'nullable|image|max:5120',
        ]);

        $data = collect($validated)->except('foto')->toArray();
        $data['ruangan_id'] = $ruangan->id;

        if ($request->hasFile('foto')) {
            $data['foto_path'] = $request->file('foto')->store('inventaris/barang', 'public');
        }

        $barang = InventarisBarang::create($data);

        return response()->json([
            'data'    => $this->formatBarang($barang),
            'message' => 'Barang berhasil ditambahkan.',
        ], 201);
    }

    /**
     * Update barang
     */
    public function updateBarang(Request $request, Ruangan $ruangan, InventarisBarang $barang)
    {
        $validated = $request->validate([
            'nama'             => 'required|string|max:255',
            'kode_inventaris'  => 'nullable|string|max:100',
            'merek'            => 'nullable|string|max:255',
            'jumlah'           => 'required|integer|min:1',
            'kondisi'          => 'required|in:baik,rusak_ringan,rusak_berat,tidak_aktif',
            'tahun_perolehan'  => 'nullable|integer|min:1900|max:' . (date('Y') + 1),
            'sumber_dana'      => 'nullable|string|max:100',
            'keterangan'       => 'nullable|string',
            'foto'             => 'nullable|image|max:5120',
        ]);

        $data = collect($validated)->except('foto')->toArray();

        if ($request->hasFile('foto')) {
            if ($barang->foto_path) Storage::disk('public')->delete($barang->foto_path);
            $data['foto_path'] = $request->file('foto')->store('inventaris/barang', 'public');
        }

        $barang->update($data);

        return response()->json([
            'data'    => $this->formatBarang($barang->fresh()),
            'message' => 'Barang berhasil diperbarui.',
        ]);
    }

    /**
     * Hapus barang
     */
    public function destroyBarang(Ruangan $ruangan, InventarisBarang $barang)
    {
        if ($barang->foto_path) Storage::disk('public')->delete($barang->foto_path);
        $barang->delete();

        return response()->json(['message' => 'Barang berhasil dihapus.']);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private function formatPegawai(\App\Models\Pegawai $p): array
    {
        return [
            'id'       => $p->id,
            'nama'     => $p->nama,
            'jabatan'  => $p->jabatan,
            'foto_url' => $p->foto_path ? url('storage/' . $p->foto_path) : null,
        ];
    }

    private function formatRuangan(Ruangan $r): array
    {
        return [
            'id'        => $r->id,
            'uuid'      => $r->uuid,
            'nama'      => $r->nama,
            'kode'      => $r->kode,
            'lantai'    => $r->lantai,
            'deskripsi' => $r->deskripsi,
            'foto_url'  => $r->foto_path ? url('storage/' . $r->foto_path) : null,
        ];
    }

    private function formatBarang(InventarisBarang $b): array
    {
        return [
            'id'              => $b->id,
            'ruangan_id'      => $b->ruangan_id,
            'nama'            => $b->nama,
            'kode_inventaris' => $b->kode_inventaris,
            'merek'           => $b->merek,
            'jumlah'          => $b->jumlah,
            'kondisi'         => $b->kondisi,
            'tahun_perolehan' => $b->tahun_perolehan,
            'sumber_dana'     => $b->sumber_dana,
            'foto_url'        => $b->foto_path ? url('storage/' . $b->foto_path) : null,
            'keterangan'      => $b->keterangan,
            'ar_pos_x'        => $b->ar_pos_x,
            'ar_pos_y'        => $b->ar_pos_y,
            'ar_pos_z'        => $b->ar_pos_z,
            'ar_positioned'   => (bool) $b->ar_positioned,
        ];
    }

    // ─── AR Endpoints ─────────────────────────────────────────────────

    /**
     * Save AR positions for barang in a ruangan (batch)
     */
    public function saveArPositions(Request $request, Ruangan $ruangan)
    {
        $validated = $request->validate([
            'positions'            => 'required|array|min:1',
            'positions.*.barang_id' => 'required|exists:inventaris_barang,id',
            'positions.*.x'        => 'required|numeric',
            'positions.*.y'        => 'required|numeric',
            'positions.*.z'        => 'required|numeric',
        ]);

        foreach ($validated['positions'] as $pos) {
            InventarisBarang::where('id', $pos['barang_id'])
                ->where('ruangan_id', $ruangan->id)
                ->update([
                    'ar_pos_x'      => $pos['x'],
                    'ar_pos_y'      => $pos['y'],
                    'ar_pos_z'      => $pos['z'],
                    'ar_positioned' => true,
                ]);
        }

        // Mark ruangan as AR-enabled
        $ruangan->update(['ar_enabled' => true]);

        return response()->json([
            'message' => 'Posisi AR berhasil disimpan untuk ' . count($validated['positions']) . ' barang',
        ]);
    }

    /**
     * Get AR data for a ruangan (positioned barang only)
     */
    public function getArData(Ruangan $ruangan)
    {
        $barang = $ruangan->barang()
            ->where('ar_positioned', true)
            ->get()
            ->map(fn($b) => $this->formatBarang($b));

        return response()->json([
            'ar_enabled' => (bool) $ruangan->ar_enabled,
            'ruangan'    => [
                'id'   => $ruangan->id,
                'nama' => $ruangan->nama,
                'kode' => $ruangan->kode,
            ],
            'barang'     => $barang,
        ]);
    }
}
