<?php

namespace App\Http\Controllers\Api\Mobile;

use App\Models\Pegawai;
use App\Models\PegawaiDokumen;
use App\Services\GoogleDriveService;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

class MobilePegawaiController extends Controller
{
    // ─── LIST PEGAWAI ────────────────────────────────────────────────────
    public function index(Request $request)
    {
        $query = Pegawai::with('user:id,email');

        if ($search = $request->input('search')) {
            $query->where(function ($q) use ($search) {
                $q->where('nama', 'like', "%{$search}%")
                  ->orWhere('nip', 'like', "%{$search}%")
                  ->orWhere('jabatan', 'like', "%{$search}%")
                  ->orWhere('pangkat_gol', 'like', "%{$search}%");
            });
        }

        $pegawai = $query->orderBy('nama')->get()->map(function ($p) {
            return [
                'id'                  => $p->id,
                'user_id'             => $p->user_id,
                'nama'                => $p->nama,
                'nip'                 => $p->nip,
                'nik'                 => $p->nik,
                'jenis_kelamin'       => $p->jenis_kelamin,
                'jabatan'             => $p->jabatan,
                'unit'                => $p->unit,
                'pangkat_gol'         => $p->pangkat_gol,
                'pendidikan_terakhir' => $p->pendidikan_terakhir,
                'profesi'             => $p->profesi,
                'tanggal_lahir'       => $p->tanggal_lahir?->format('Y-m-d'),
                'alamat'              => $p->alamat,
                'no_hp'               => $p->no_hp,
                'foto_url'            => $p->foto_path ? url('storage/' . $p->foto_path) : null,
                'email'               => $p->user?->email,
                'dokumen_count'       => $p->dokumen()->count(),
            ];
        });

        return response()->json(['data' => $pegawai]);
    }

    // ─── DETAIL PEGAWAI ──────────────────────────────────────────────────
    public function show(Pegawai $pegawai)
    {
        $pegawai->load(['user:id,email', 'dokumen']);

        return response()->json([
            'data' => [
                'id'                  => $pegawai->id,
                'user_id'             => $pegawai->user_id,
                'nama'                => $pegawai->nama,
                'nip'                 => $pegawai->nip,
                'nik'                 => $pegawai->nik,
                'jenis_kelamin'       => $pegawai->jenis_kelamin,
                'jabatan'             => $pegawai->jabatan,
                'unit'                => $pegawai->unit,
                'pangkat_gol'         => $pegawai->pangkat_gol,
                'pendidikan_terakhir' => $pegawai->pendidikan_terakhir,
                'profesi'             => $pegawai->profesi,
                'tanggal_lahir'       => $pegawai->tanggal_lahir?->format('Y-m-d'),
                'alamat'              => $pegawai->alamat,
                'no_hp'               => $pegawai->no_hp,
                'foto_url'            => $pegawai->foto_path ? url('storage/' . $pegawai->foto_path) : null,
                'ktp_url'             => $pegawai->ktp_path ? url('storage/' . $pegawai->ktp_path) : null,
                'email'               => $pegawai->user?->email,
                'dokumen'             => $pegawai->dokumen->map(function ($d) {
                    return [
                        'id'         => $d->id,
                        'jenis'      => $d->jenis,
                        'judul'      => $d->judul,
                        'issued_at'  => $d->issued_at?->format('Y-m-d'),
                        'keterangan' => $d->keterangan,
                        'file_name'  => $d->file_path ? basename($d->file_path) : null,
                        'file_extension' => $d->file_path ? strtolower(pathinfo($d->file_path, PATHINFO_EXTENSION)) : null,
                        'gdrive_url' => $d->gdrive_url,
                        'download_url' => $d->gdrive_url ?: route('api.mobile.pegawai.documents.download', [$d->pegawai_id, $d->id]),
                    ];
                }),
            ],
        ]);
    }

    // ─── CREATE PEGAWAI ──────────────────────────────────────────────────
    public function store(Request $request)
    {
        $user = $request->user();
        if (! $user->hasAnyRole(['super_admin'])) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        $validated = $request->validate([
            'nama'                => 'required|string|max:255',
            'nip'                 => 'nullable|string|max:50',
            'nik'                 => 'nullable|string|max:20',
            'jenis_kelamin'       => 'nullable|string|in:L,P,Laki-laki,Perempuan',
            'jabatan'             => 'nullable|string|max:255',
            'unit'                => 'nullable|string|max:255',
            'pangkat_gol'         => 'nullable|string|max:20',
            'pendidikan_terakhir' => 'nullable|string|max:50',
            'profesi'             => 'nullable|string|max:255',
            'tanggal_lahir'       => 'nullable|date',
            'alamat'              => 'nullable|string',
            'no_hp'               => 'nullable|string|max:20',
            'user_id'             => 'nullable|exists:users,id',
        ]);

        if ($request->hasFile('foto')) {
            $validated['foto_path'] = $request->file('foto')->store('pegawai/foto', 'public');
        }
        if ($request->hasFile('ktp')) {
            $validated['ktp_path'] = $request->file('ktp')->store('pegawai/ktp', 'public');
        }

        $pegawai = Pegawai::create($validated);

        return response()->json(['data' => $pegawai, 'message' => 'Pegawai berhasil ditambahkan'], 201);
    }

    // ─── UPDATE PEGAWAI ──────────────────────────────────────────────────
    public function update(Request $request, Pegawai $pegawai)
    {
        $user = $request->user();
        $isAdmin = $user->hasAnyRole(['super_admin']);
        $isOwner = $pegawai->user_id === $user->id;

        if (! $isAdmin && ! $isOwner) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        $validated = $request->validate([
            'nama'                => 'sometimes|string|max:255',
            'nip'                 => 'nullable|string|max:50',
            'nik'                 => 'nullable|string|max:20',
            'jenis_kelamin'       => 'nullable|string|in:L,P,Laki-laki,Perempuan',
            'jabatan'             => 'nullable|string|max:255',
            'unit'                => 'nullable|string|max:255',
            'pangkat_gol'         => 'nullable|string|max:20',
            'pendidikan_terakhir' => 'nullable|string|max:50',
            'profesi'             => 'nullable|string|max:255',
            'tanggal_lahir'       => 'nullable|date',
            'alamat'              => 'nullable|string',
            'no_hp'               => 'nullable|string|max:20',
        ]);

        if ($request->hasFile('foto')) {
            if ($pegawai->foto_path) Storage::disk('public')->delete($pegawai->foto_path);
            $validated['foto_path'] = $request->file('foto')->store('pegawai/foto', 'public');
        }
        if ($request->hasFile('ktp')) {
            if ($pegawai->ktp_path) Storage::disk('public')->delete($pegawai->ktp_path);
            $validated['ktp_path'] = $request->file('ktp')->store('pegawai/ktp', 'public');
        }

        $pegawai->update($validated);

        return response()->json(['data' => $pegawai, 'message' => 'Pegawai berhasil diperbarui']);
    }

    // ─── DELETE PEGAWAI ──────────────────────────────────────────────────
    public function destroy(Request $request, Pegawai $pegawai)
    {
        $user = $request->user();
        if (! $user->hasAnyRole(['super_admin'])) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        // Delete associated files
        if ($pegawai->foto_path) Storage::disk('public')->delete($pegawai->foto_path);
        if ($pegawai->ktp_path) Storage::disk('public')->delete($pegawai->ktp_path);
        foreach ($pegawai->dokumen as $doc) {
            if ($doc->file_path) Storage::disk('public')->delete($doc->file_path);
        }
        $pegawai->dokumen()->delete();
        $pegawai->delete();

        return response()->json(['message' => 'Pegawai berhasil dihapus']);
    }

    // ─── DOCUMENTS ───────────────────────────────────────────────────────

    public function documentIndex(Pegawai $pegawai)
    {
        return response()->json([
            'data' => $pegawai->dokumen->map(function ($d) {
                return [
                    'id'         => $d->id,
                    'jenis'      => $d->jenis,
                    'judul'      => $d->judul,
                    'issued_at'  => $d->issued_at?->format('Y-m-d'),
                    'keterangan' => $d->keterangan,
                    'file_name'  => $d->file_path ? basename($d->file_path) : null,
                    'file_extension' => $d->file_path ? strtolower(pathinfo($d->file_path, PATHINFO_EXTENSION)) : null,
                    'gdrive_url' => $d->gdrive_url,
                    'download_url' => $d->gdrive_url ?: route('api.mobile.pegawai.documents.download', [$d->pegawai_id, $d->id]),
                ];
            }),
        ]);
    }

    public function documentStore(Request $request, Pegawai $pegawai)
    {
        $user = $request->user();
        $isAdmin = $user->hasAnyRole(['super_admin']);
        $isOwner = $pegawai->user_id === $user->id;

        if (! $isAdmin && ! $isOwner) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        $validated = $request->validate([
            'jenis'      => 'required|string|max:50',
            'judul'      => 'required|string|max:255',
            'file'       => 'required|file|max:10240', // 10MB
            'issued_at'  => 'nullable|date',
            'keterangan' => 'nullable|string',
        ]);

        $validated['pegawai_id'] = $pegawai->id;
        unset($validated['file']);

        // Try upload to Google Drive first
        try {
            $gdrive = new GoogleDriveService();
            $folderName = 'Pegawai_' . ($pegawai->nip ?: $pegawai->id) . '_' . Str::slug($pegawai->nama);
            $result = $gdrive->upload($request->file('file'), $folderName);

            $validated['gdrive_file_id'] = $result['file_id'];
            $validated['gdrive_url'] = $result['direct_link'];
        } catch (\Exception $e) {
            // Fallback to local storage if GDrive fails
            $validated['file_path'] = $request->file('file')->store('pegawai/dokumen', 'public');
        }

        $doc = PegawaiDokumen::create($validated);

        return response()->json(['data' => $doc, 'message' => 'Dokumen berhasil diupload'], 201);
    }

    public function documentDestroy(Request $request, Pegawai $pegawai, PegawaiDokumen $document)
    {
        $user = $request->user();
        $isAdmin = $user->hasAnyRole(['super_admin']);
        $isOwner = $pegawai->user_id === $user->id;

        if (! $isAdmin && ! $isOwner) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        // Delete from Google Drive
        if ($document->gdrive_file_id) {
            try {
                $gdrive = new GoogleDriveService();
                $gdrive->delete($document->gdrive_file_id);
            } catch (\Exception $e) {
                // Log but continue with DB deletion
            }
        }

        // Delete local file if exists
        if ($document->file_path) Storage::disk('public')->delete($document->file_path);
        $document->delete();

        return response()->json(['message' => 'Dokumen berhasil dihapus']);
    }

    public function documentDownload(Pegawai $pegawai, PegawaiDokumen $document)
    {
        // If has Google Drive URL, redirect to it
        if ($document->gdrive_url) {
            return redirect($document->gdrive_url);
        }

        // Fallback to local file
        if (! $document->file_path || ! Storage::disk('public')->exists($document->file_path)) {
            return response()->json(['message' => 'File tidak ditemukan'], 404);
        }
        return Storage::disk('public')->download($document->file_path, $document->judul . '.' . pathinfo($document->file_path, PATHINFO_EXTENSION));
    }

    // ─── SURAT ARSIP ─────────────────────────────────────────────────────

    public function suratArsipIndex(Request $request)
    {
        $query = \App\Models\SuratArchive::with('pegawai:id,nama')->orderByDesc('created_at');

        if ($jenis = $request->input('jenis')) {
            $query->where('jenis', $jenis);
        }
        if ($search = $request->input('search')) {
            $query->where(function ($q) use ($search) {
                $q->where('nomor_surat', 'like', "%{$search}%")
                  ->orWhere('perihal', 'like', "%{$search}%");
            });
        }

        $archives = $query->get()->map(function ($a) {
            return [
                'id'       => $a->id,
                'tanggal'  => $a->created_at->format('Y-m-d'),
                'nomor'    => $a->nomor_surat,
                'perihal'  => $a->perihal,
                'jenis'    => $a->jenis,
                'pegawai_nama' => $a->pegawai?->nama,
                'has_file' => !empty($a->file_path) || !empty($a->gdrive_url),
                'file_name' => $a->file_path ? basename($a->file_path) : null,
                'file_extension' => $a->file_path ? strtolower(pathinfo($a->file_path, PATHINFO_EXTENSION)) : null,
                'gdrive_url' => $a->gdrive_url,
                'download_url' => $a->gdrive_url ?: ($a->file_path ? route('api.mobile.surat-arsip.download', $a->id) : null),
            ];
        });

        return response()->json(['data' => $archives]);
    }

    public function suratArsipDownload($id)
    {
        $archive = \App\Models\SuratArchive::findOrFail($id);

        // Redirect to Google Drive if available
        if ($archive->gdrive_url) {
            return redirect($archive->gdrive_url);
        }

        if (! $archive->file_path || ! Storage::disk('public')->exists($archive->file_path)) {
            return response()->json(['message' => 'File tidak ditemukan'], 404);
        }
        $ext = pathinfo($archive->file_path, PATHINFO_EXTENSION);
        $filename = Str::slug($archive->perihal ?: 'surat') . '.' . $ext;
        return Storage::disk('public')->download($archive->file_path, $filename);
    }

    // ─── SURAT ARSIP CRUD (admin/nakes) ──────────────────────────────────

    public function suratArsipStore(Request $request)
    {
        $user = $request->user();
        if (! $user->hasAnyRole(['super_admin', 'pegawai'])) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        $validated = $request->validate([
            'jenis'       => 'required|string|max:100',
            'nomor_surat' => 'nullable|string|max:100',
            'perihal'     => 'required|string|max:500',
            'pegawai_id'  => 'nullable|exists:pegawai,id',
            'file'        => 'nullable|file|max:20480', // 20MB
        ]);

        if ($request->hasFile('file')) {
            try {
                $gdrive = new \App\Services\GoogleDriveService();
                $result = $gdrive->upload($request->file('file'), 'E-Arsip_Surat');
                $validated['gdrive_file_id'] = $result['file_id'];
                $validated['gdrive_url'] = $result['direct_link'];
            } catch (\Exception $e) {
                // Fallback to local storage
                $validated['file_path'] = $request->file('file')->store('surat-arsip', 'public');
                \Illuminate\Support\Facades\Log::warning('GDrive arsip upload failed: ' . $e->getMessage());
            }
        }
        $validated['created_by'] = $user->id;
        unset($validated['file']);

        $archive = \App\Models\SuratArchive::create($validated);

        return response()->json(['data' => $archive, 'message' => 'Arsip surat berhasil ditambahkan'], 201);
    }

    public function suratArsipUpdate(Request $request, $id)
    {
        $user = $request->user();
        if (! $user->hasAnyRole(['super_admin', 'pegawai'])) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        $archive = \App\Models\SuratArchive::findOrFail($id);

        $validated = $request->validate([
            'jenis'       => 'sometimes|string|max:100',
            'nomor_surat' => 'nullable|string|max:100',
            'perihal'     => 'sometimes|string|max:500',
            'pegawai_id'  => 'nullable|exists:pegawai,id',
        ]);

        if ($request->hasFile('file')) {
            // Delete old file (GDrive or local)
            if ($archive->gdrive_file_id) {
                try { (new \App\Services\GoogleDriveService())->delete($archive->gdrive_file_id); } catch (\Exception $e) {}
            }
            if ($archive->file_path) Storage::disk('public')->delete($archive->file_path);

            // Upload new file to GDrive
            try {
                $gdrive = new \App\Services\GoogleDriveService();
                $result = $gdrive->upload($request->file('file'), 'E-Arsip_Surat');
                $validated['gdrive_file_id'] = $result['file_id'];
                $validated['gdrive_url'] = $result['direct_link'];
                $validated['file_path'] = null;
            } catch (\Exception $e) {
                $validated['file_path'] = $request->file('file')->store('surat-arsip', 'public');
                $validated['gdrive_file_id'] = null;
                $validated['gdrive_url'] = null;
            }
        }

        $archive->update($validated);

        return response()->json(['data' => $archive, 'message' => 'Arsip surat berhasil diperbarui']);
    }

    public function suratArsipDestroy(Request $request, $id)
    {
        $user = $request->user();
        if (! $user->hasAnyRole(['super_admin', 'pegawai'])) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        $archive = \App\Models\SuratArchive::findOrFail($id);
        // Delete from GDrive
        if ($archive->gdrive_file_id) {
            try { (new \App\Services\GoogleDriveService())->delete($archive->gdrive_file_id); } catch (\Exception $e) {}
        }
        if ($archive->file_path) Storage::disk('public')->delete($archive->file_path);
        $archive->delete();

        return response()->json(['message' => 'Arsip surat berhasil dihapus']);
    }

    // ─── SURAT TUGAS CREATE ──────────────────────────────────────────────

    public function suratTugasStore(Request $request)
    {
        $user = $request->user();
        if (! $user->hasAnyRole(['super_admin', 'pegawai'])) {
            return response()->json(['message' => 'Unauthorized'], 403);
        }

        $validated = $request->validate([
            'pegawai_id'     => 'required|exists:pegawai,id',
            'nomor_surat'    => 'nullable|string|max:100',
            'dasar'          => 'nullable|string',
            'maksud_tugas'   => 'required|string',
            'tanggal_mulai'  => 'required|date',
            'tanggal_selesai'=> 'required|date|after_or_equal:tanggal_mulai',
            'kota_dibuat'    => 'nullable|string|max:100',
            'tempat_tugas'   => 'nullable|string|max:255',
        ]);

        $pegawai = Pegawai::findOrFail($validated['pegawai_id']);

        // Auto-increment nomor surat jika kosong
        if (empty($validated['nomor_surat'])) {
            $lastArchive = \App\Models\SuratArchive::orderByDesc('id')->first();
            $lastNumStr = $lastArchive ? preg_replace('/[^0-9]/', '', explode('/', $lastArchive->nomor_surat ?? '')[0]) : '0';
            $nextNum = ((int)$lastNumStr) + 1;
            $validated['nomor_surat'] = sprintf('800.1.11.1/%03d/%d', $nextNum, now()->year);
        }

        // Create archive record
        $archive = \App\Models\SuratArchive::create([
            'pegawai_id'  => $pegawai->id,
            'nomor_surat' => $validated['nomor_surat'],
            'perihal'     => $validated['maksud_tugas'],
            'jenis'       => 'Surat Tugas',
            'created_by'  => $user->id,
            'meta'        => [
                'dasar'           => $validated['dasar'] ?? null,
                'tanggal_mulai'   => $validated['tanggal_mulai'],
                'tanggal_selesai' => $validated['tanggal_selesai'],
            ],
        ]);

        return response()->json([
            'data'    => $archive,
            'message' => 'Surat tugas berhasil dibuat',
        ], 201);
    }

    // ─── STATS (for dashboard) ───────────────────────────────────────────

    public function stats()
    {
        $totalPegawai = Pegawai::count();
        $totalDokumen = PegawaiDokumen::count();
        $totalSuratTugas = \App\Models\SuratArchive::where('jenis', 'Surat Tugas')->count();

        // Chart breakdowns
        $genderBreakdown = Pegawai::selectRaw("COALESCE(jenis_kelamin, 'Tidak Diketahui') as label, COUNT(*) as value")
            ->groupBy('jenis_kelamin')->get();
        $educationBreakdown = Pegawai::selectRaw("COALESCE(pendidikan_terakhir, 'Tidak Diketahui') as label, COUNT(*) as value")
            ->groupBy('pendidikan_terakhir')->get();
        $professionBreakdown = Pegawai::selectRaw("COALESCE(profesi, 'Tidak Diketahui') as label, COUNT(*) as value")
            ->groupBy('profesi')->get();
        $golonganBreakdown = Pegawai::selectRaw("COALESCE(pangkat_gol, 'Tidak Diketahui') as label, COUNT(*) as value")
            ->groupBy('pangkat_gol')->get();

        return response()->json([
            'data' => [
                'total_pegawai'     => $totalPegawai,
                'total_dokumen'     => $totalDokumen,
                'total_surat_tugas' => $totalSuratTugas,
                'charts' => [
                    'gender'     => $genderBreakdown,
                    'education'  => $educationBreakdown,
                    'profession' => $professionBreakdown,
                    'golongan'   => $golonganBreakdown,
                ],
            ],
        ]);
    }
}

