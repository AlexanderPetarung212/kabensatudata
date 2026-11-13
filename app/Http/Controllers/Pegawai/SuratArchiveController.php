<?php

namespace App\Http\Controllers\Pegawai;

use App\Http\Controllers\Controller;
use App\Models\Pegawai;
use App\Models\SuratArchive;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\Log;
use Carbon\Carbon;
use Illuminate\Support\Str;
use App\Services\GoogleDriveService;

class SuratArchiveController extends Controller
{
    public function index(Request $request)
    {
        $user = Auth::user();
        $jenis = $request->query('jenis', 'SURAT_TUGAS');
        $q = trim((string)$request->query('q', ''));

        $query = SuratArchive::query()->with(['pegawai', 'creator'])->where('jenis', $jenis);

        if (!$user->hasRole('super_admin')) {
            // Batasi ke arsip yang dibuat oleh user ini atau milik pegawai yang terhubung dengan user ini
            $pegawai = Pegawai::firstWhere('user_id', $user->id);
            $query->where(function ($w) use ($user, $pegawai) {
                $w->where('created_by', $user->id);
                if ($pegawai) {
                    $w->orWhere('pegawai_id', $pegawai->id);
                }
            });
        }

        if ($q !== '') {
            $query->where(function ($w) use ($q) {
                $w->where('nomor_surat', 'like', "%{$q}%")
                  ->orWhere('perihal', 'like', "%{$q}%");
            });
        }

        $arsip = $query->latest()->paginate(15)->withQueryString();

        return view('pegawai.arsip.index', compact('arsip', 'jenis', 'q'));
    }

    // PDF generation removed per request

    public function create()
    {
        // Form upload arsip manual
        $pegawaiList = Pegawai::orderBy('nama')->limit(500)->get(['id','nama','nip']);
        return view('pegawai.arsip.upload', compact('pegawaiList'));
    }

    public function store(Request $request)
    {
        $data = $request->validate([
            'jenis' => ['required', 'string', 'max:50'],
            'nomor_surat' => ['nullable', 'string', 'max:150'],
            'pegawai_id' => ['nullable', 'exists:pegawai,id'],
            'perihal' => ['required', 'string', 'max:2000'],
            'issued_at' => ['required', 'date'],
            'file' => ['required', 'file', 'mimes:pdf,jpg,jpeg,png,doc,docx,xlsx', 'max:51200'],
        ]);

        $path = $request->file('file')->store('arsip', 'public');

        $gdriveFileId = null;
        $gdriveUrl = null;

        try {
            $gdrive = new GoogleDriveService();
            $result = $gdrive->upload($request->file('file'), 'E-Arsip_Surat');
            $gdriveFileId = $result['file_id'];
            $gdriveUrl = $result['direct_link'];
        } catch (\Exception $e) {
            Log::warning('GDrive arsip upload failed: ' . $e->getMessage());
        }

        SuratArchive::create([
            'jenis' => strtoupper($data['jenis']),
            'nomor_surat' => $data['nomor_surat'] ?? null,
            'pegawai_id' => $data['pegawai_id'] ?? null,
            'perihal' => $data['perihal'],
            'issued_at' => $data['issued_at'],
            'file_path' => $path,
            'gdrive_file_id' => $gdriveFileId,
            'gdrive_url' => $gdriveUrl,
            'created_by' => Auth::id(),
            'meta' => null,
        ]);

        return redirect()->route('pegawai.surat-arsip.index')->with('status', 'Arsip berhasil diunggah');
    }

    protected function authorizeView(SuratArchive $archive): void
    {
        $user = Auth::user();
        if ($user->hasRole('super_admin')) return;
        $pegawai = $archive->pegawai;
        if ($archive->created_by === $user->id) return;
        if ($pegawai && $pegawai->user_id === $user->id) return;
        abort(403);
    }

    protected function formatTanggalIndo(Carbon $date): string
    {
        $bulan = [1=>'Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember'];
        return $date->format('j') . ' ' . $bulan[(int)$date->format('n')] . ' ' . $date->format('Y');
    }

    public function download(SuratArchive $archive)
    {
        $this->authorizeView($archive);

        // Redirect to Google Drive if available
        if ($archive->gdrive_url) {
            return redirect($archive->gdrive_url);
        }

        $full = storage_path('app/public/' . $archive->file_path);
        if (!is_file($full)) {
            abort(404, 'File tidak ditemukan');
        }
        $ext = pathinfo($full, PATHINFO_EXTENSION);
        $nomor = $archive->nomor_surat ?: 'nonomor';
        $nomorSan = preg_replace('#[^A-Za-z0-9]+#', '-', $nomor);
        $judul = $archive->perihal ?: 'dokumen';
        $judulSlug = Str::limit(Str::slug($judul, '-'), 60, '');
        $filename = trim($nomorSan . '-' . $judulSlug, '-');
        if ($filename === '') {
            $filename = 'arsip-' . $archive->id;
        }
        $filename .= '.' . $ext;

        return response()->download($full, $filename);
    }

    public function edit(SuratArchive $archive)
    {
        $this->authorizeManage($archive);
        $pegawaiList = Pegawai::orderBy('nama')->limit(500)->get(['id','nama','nip']);
        return view('pegawai.arsip.edit', ['archive' => $archive, 'pegawaiList' => $pegawaiList]);
    }

    public function update(Request $request, SuratArchive $archive)
    {
        $this->authorizeManage($archive);
        $data = $request->validate([
            'jenis' => ['required', 'string', 'max:50'],
            'nomor_surat' => ['nullable', 'string', 'max:150'],
            'pegawai_id' => ['nullable', 'exists:pegawai,id'],
            'perihal' => ['required', 'string', 'max:2000'],
            'issued_at' => ['required', 'date'],
            'file' => ['nullable', 'file', 'mimes:pdf,jpg,jpeg,png,doc,docx,xlsx', 'max:51200'],
        ]);

        if ($request->hasFile('file')) {
            // Delete old file (GDrive + local)
            if ($archive->gdrive_file_id) {
                try { (new GoogleDriveService())->delete($archive->gdrive_file_id); } catch (\Exception $e) {}
            }
            if ($archive->file_path) {
                Storage::disk('public')->delete($archive->file_path);
            }

            $archive->file_path = $request->file('file')->store('arsip', 'public');

            try {
                $gdrive = new GoogleDriveService();
                $result = $gdrive->upload($request->file('file'), 'E-Arsip_Surat');
                $archive->gdrive_file_id = $result['file_id'];
                $archive->gdrive_url = $result['direct_link'];
            } catch (\Exception $e) {
                Log::warning('GDrive arsip update failed: ' . $e->getMessage());
                $archive->gdrive_file_id = null;
                $archive->gdrive_url = null;
            }
        }

        $archive->jenis = strtoupper($data['jenis']);
        $archive->nomor_surat = $data['nomor_surat'] ?? null;
        $archive->pegawai_id = $data['pegawai_id'] ?? null;
        $archive->perihal = $data['perihal'];
        $archive->issued_at = $data['issued_at'];
        $archive->save();

        return redirect()->route('pegawai.surat-arsip.index')->with('status', 'Arsip diperbarui');
    }

    public function destroy(SuratArchive $archive)
    {
        $this->authorizeManage($archive);
        // Delete from GDrive
        if ($archive->gdrive_file_id) {
            try { (new GoogleDriveService())->delete($archive->gdrive_file_id); } catch (\Exception $e) {}
        }
        if ($archive->file_path) {
            Storage::disk('public')->delete($archive->file_path);
        }
        $archive->delete();
        return redirect()->route('pegawai.surat-arsip.index')->with('status', 'Arsip dihapus');
    }

    protected function authorizeManage(SuratArchive $archive): void
    {
        $user = Auth::user();
        if ($user->hasRole('super_admin')) return;
        if ($archive->created_by === $user->id) return;
        abort(403);
    }
}
