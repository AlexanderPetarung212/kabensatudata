<?php

namespace App\Http\Controllers;

use App\Models\Ruangan;
use Illuminate\Http\Request;

class InventarisPublicController extends Controller
{
    /**
     * Halaman publik inventaris ruangan (tanpa login).
     * Diakses via QR code: /inventaris/{uuid}
     */
    public function show(string $uuid)
    {
        $ruangan = Ruangan::where('uuid', $uuid)->firstOrFail();
        $ruangan->load([
            'barang' => fn($q) => $q->orderBy('nama'),
        ]);

        // Defensive: load PJ/anggota — gracefully handle if pivot table doesn't exist yet
        $pj = null;
        $anggota = collect();
        try {
            $ruangan->load(['penanggungJawab', 'pegawaiAnggota']);
            $pj = $ruangan->penanggungJawab->first();
            $anggota = $ruangan->pegawaiAnggota ?? collect();
        } catch (\Exception $e) {
            // ruangan_pegawai table might not exist yet — skip silently
        }

        // Defensive: kondisi summary
        try {
            $kondisiSummary = $ruangan->getKondisiSummary();
        } catch (\Exception $e) {
            $kondisiSummary = [
                'baik' => $ruangan->barang->where('kondisi', 'baik')->sum('jumlah'),
                'rusak_ringan' => $ruangan->barang->where('kondisi', 'rusak_ringan')->sum('jumlah'),
                'rusak_berat' => $ruangan->barang->where('kondisi', 'rusak_berat')->sum('jumlah'),
                'tidak_aktif' => $ruangan->barang->where('kondisi', 'tidak_aktif')->sum('jumlah'),
            ];
        }

        $totalUnit = $ruangan->barang->sum('jumlah');

        return view('inventaris.public', compact('ruangan', 'kondisiSummary', 'totalUnit', 'pj', 'anggota'));
    }
}
