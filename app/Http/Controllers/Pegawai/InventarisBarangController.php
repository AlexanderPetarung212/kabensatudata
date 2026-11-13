<?php

namespace App\Http\Controllers\Pegawai;

use App\Http\Controllers\Controller;
use App\Models\Ruangan;
use App\Models\InventarisBarang;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use PhpOffice\PhpSpreadsheet\Spreadsheet;
use PhpOffice\PhpSpreadsheet\Writer\Xlsx;
use PhpOffice\PhpSpreadsheet\Style\Alignment;
use PhpOffice\PhpSpreadsheet\Style\Border;
use PhpOffice\PhpSpreadsheet\Style\Fill;

class InventarisBarangController extends Controller
{
    /**
     * Form tambah barang di ruangan tertentu
     */
    public function create(Ruangan $ruangan)
    {
        return view('pegawai.inventaris.barang.create', compact('ruangan'));
    }

    /**
     * Simpan barang baru
     */
    public function store(Request $request, Ruangan $ruangan)
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
            'foto'             => 'nullable|file|mimes:jpg,jpeg,png,gif,webp|max:5120',
        ]);

        $data = collect($validated)->except('foto')->toArray();
        $data['ruangan_id'] = $ruangan->id;

        if ($request->hasFile('foto')) {
            $data['foto_path'] = $request->file('foto')->store('inventaris/barang', 'public');
        }

        InventarisBarang::create($data);

        return redirect()
            ->route('pegawai.inventaris.ruangan.show', $ruangan)
            ->with('success', "Barang \"{$validated['nama']}\" berhasil ditambahkan.");
    }

    /**
     * Form edit barang
     */
    public function edit(Ruangan $ruangan, InventarisBarang $barang)
    {
        return view('pegawai.inventaris.barang.edit', compact('ruangan', 'barang'));
    }

    /**
     * Update barang
     */
    public function update(Request $request, Ruangan $ruangan, InventarisBarang $barang)
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
            'foto'             => 'nullable|file|mimes:jpg,jpeg,png,gif,webp|max:5120',
        ]);

        $data = collect($validated)->except('foto')->toArray();

        if ($request->hasFile('foto')) {
            if ($barang->foto_path) {
                Storage::disk('public')->delete($barang->foto_path);
            }
            $data['foto_path'] = $request->file('foto')->store('inventaris/barang', 'public');
        }

        $barang->update($data);

        return redirect()
            ->route('pegawai.inventaris.ruangan.show', $ruangan)
            ->with('success', 'Barang berhasil diperbarui.');
    }

    /**
     * Hapus barang
     */
    public function destroy(Ruangan $ruangan, InventarisBarang $barang)
    {
        if ($barang->foto_path) {
            Storage::disk('public')->delete($barang->foto_path);
        }

        $nama = $barang->nama;
        $barang->delete();

        return redirect()
            ->route('pegawai.inventaris.ruangan.show', $ruangan)
            ->with('success', "Barang \"{$nama}\" berhasil dihapus.");
    }

    /**
     * Export inventaris ruangan ke Excel
     */
    public function exportExcel(Ruangan $ruangan)
    {
        $barang = $ruangan->barang()->orderBy('nama')->get();

        $spreadsheet = new Spreadsheet();
        $sheet = $spreadsheet->getActiveSheet();
        $sheet->setTitle('Inventaris');

        // ── Header Info ──
        $sheet->setCellValue('A1', 'INVENTARIS BARANG');
        $sheet->setCellValue('A2', 'Ruangan: ' . $ruangan->nama);
        $sheet->setCellValue('A3', 'Kode: ' . ($ruangan->kode ?? '-'));
        $sheet->setCellValue('A4', 'Diekspor: ' . now()->format('d/m/Y H:i'));
        $sheet->mergeCells('A1:H1');
        $sheet->mergeCells('A2:H2');
        $sheet->getStyle('A1')->getFont()->setBold(true)->setSize(14);
        $sheet->getStyle('A2')->getFont()->setBold(true)->setSize(11);

        // ── Table Header ──
        $headers = ['No', 'Nama Barang', 'Kode Inventaris', 'Merek/Tipe', 'Jumlah', 'Kondisi', 'Tahun Perolehan', 'Sumber Dana', 'Keterangan'];
        $col = 'A';
        foreach ($headers as $header) {
            $sheet->setCellValue($col . '6', $header);
            $col++;
        }

        $headerStyle = [
            'font' => ['bold' => true, 'color' => ['rgb' => 'FFFFFF']],
            'fill' => ['fillType' => Fill::FILL_SOLID, 'startColor' => ['rgb' => '1E40AF']],
            'alignment' => ['horizontal' => Alignment::HORIZONTAL_CENTER],
            'borders' => ['allBorders' => ['borderStyle' => Border::BORDER_THIN]],
        ];
        $sheet->getStyle('A6:I6')->applyFromArray($headerStyle);

        // ── Data Rows ──
        $row = 7;
        $kondisiMap = [
            'baik' => 'Baik',
            'rusak_ringan' => 'Rusak Ringan',
            'rusak_berat' => 'Rusak Berat',
            'tidak_aktif' => 'Tidak Aktif',
        ];

        foreach ($barang as $i => $item) {
            $sheet->setCellValue("A{$row}", $i + 1);
            $sheet->setCellValue("B{$row}", $item->nama);
            $sheet->setCellValue("C{$row}", $item->kode_inventaris ?? '-');
            $sheet->setCellValue("D{$row}", $item->merek ?? '-');
            $sheet->setCellValue("E{$row}", $item->jumlah);
            $sheet->setCellValue("F{$row}", $kondisiMap[$item->kondisi] ?? $item->kondisi);
            $sheet->setCellValue("G{$row}", $item->tahun_perolehan ?? '-');
            $sheet->setCellValue("H{$row}", $item->sumber_dana ?? '-');
            $sheet->setCellValue("I{$row}", $item->keterangan ?? '-');

            $sheet->getStyle("A{$row}:I{$row}")->getBorders()->getAllBorders()->setBorderStyle(Border::BORDER_THIN);
            $row++;
        }

        // ── Summary Row ──
        $sheet->setCellValue("A{$row}", '');
        $sheet->setCellValue("D{$row}", 'TOTAL:');
        $sheet->setCellValue("E{$row}", $barang->sum('jumlah'));
        $sheet->getStyle("D{$row}:E{$row}")->getFont()->setBold(true);

        // Auto-size columns
        foreach (range('A', 'I') as $c) {
            $sheet->getColumnDimension($c)->setAutoSize(true);
        }

        // Output
        $filename = 'Inventaris_' . Str::slug($ruangan->nama) . '_' . now()->format('Ymd') . '.xlsx';
        $tempPath = storage_path('app/temp/' . $filename);
        @mkdir(dirname($tempPath), 0755, true);

        $writer = new Xlsx($spreadsheet);
        $writer->save($tempPath);

        return response()->download($tempPath, $filename)->deleteFileAfterSend(true);
    }
}
