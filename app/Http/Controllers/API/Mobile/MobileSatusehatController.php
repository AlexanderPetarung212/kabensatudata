<?php

namespace App\Http\Controllers\API\Mobile;

use App\Http\Controllers\Controller;
use App\Services\SatusehatService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;

/**
 * Controller untuk integrasi SATUSEHAT (READ-ONLY).
 *
 * Menyediakan endpoint untuk:
 * - Verifikasi NIK pasien
 * - Lookup tenaga kesehatan
 * - Cek koneksi SATUSEHAT
 */
class MobileSatusehatController extends Controller
{
    protected SatusehatService $satusehat;

    public function __construct(SatusehatService $satusehat)
    {
        $this->satusehat = $satusehat;
    }

    /**
     * Verifikasi NIK dan ambil data pasien dari SATUSEHAT.
     *
     * GET /api/mobile/satusehat/verify-nik?nik=8107XXXXXXXXXXXX
     *
     * @param Request $request
     * @return JsonResponse
     */
    public function verifyNik(Request $request): JsonResponse
    {
        $request->validate([
            'nik' => 'required|string|digits:16',
        ]);

        try {
            $result = $this->satusehat->verifyNik($request->query('nik'));

            if (!$result['success']) {
                return response()->json([
                    'success' => false,
                    'message' => $result['error'] ?? 'Gagal verifikasi NIK',
                ], $result['status'] ?? 500);
            }

            if (!($result['found'] ?? false)) {
                return response()->json([
                    'success' => true,
                    'found' => false,
                    'message' => $result['message'] ?? 'NIK tidak ditemukan',
                ]);
            }

            // Return parsed patient data (tanpa raw FHIR data untuk keamanan)
            return response()->json([
                'success' => true,
                'found' => true,
                'patient' => $result['patient'],
            ]);

        } catch (\Exception $e) {
            Log::error('SATUSEHAT verifyNik error', [
                'error' => $e->getMessage(),
            ]);

            return response()->json([
                'success' => false,
                'message' => 'Terjadi kesalahan saat menghubungi SATUSEHAT',
            ], 500);
        }
    }

    /**
     * Cari data tenaga kesehatan berdasarkan NIK.
     *
     * GET /api/mobile/satusehat/practitioner?nik=8107XXXXXXXXXXXX
     *
     * @param Request $request
     * @return JsonResponse
     */
    public function findPractitioner(Request $request): JsonResponse
    {
        $request->validate([
            'nik' => 'required|string|digits:16',
        ]);

        try {
            $result = $this->satusehat->findPractitioner($request->query('nik'));

            if (!$result['success']) {
                return response()->json([
                    'success' => false,
                    'message' => $result['error'] ?? 'Gagal mencari data nakes',
                ], $result['status'] ?? 500);
            }

            return response()->json($result);

        } catch (\Exception $e) {
            Log::error('SATUSEHAT findPractitioner error', [
                'error' => $e->getMessage(),
            ]);

            return response()->json([
                'success' => false,
                'message' => 'Terjadi kesalahan saat menghubungi SATUSEHAT',
            ], 500);
        }
    }

    /**
     * Health check — cek apakah koneksi SATUSEHAT berfungsi.
     *
     * GET /api/mobile/satusehat/health
     *
     * @return JsonResponse
     */
    public function health(): JsonResponse
    {
        try {
            $result = $this->satusehat->healthCheck();

            return response()->json($result);
        } catch (\Exception $e) {
            return response()->json([
                'status' => 'error',
                'message' => $e->getMessage(),
            ], 500);
        }
    }

    /**
     * Get daftar lokasi layanan Puskesmas dari SATUSEHAT.
     *
     * GET /api/mobile/satusehat/locations
     *
     * @return JsonResponse
     */
    public function locations(): JsonResponse
    {
        try {
            $result = $this->satusehat->getLocations();

            if (!$result['success']) {
                return response()->json([
                    'success' => false,
                    'message' => $result['error'] ?? 'Gagal mengambil data lokasi',
                ], $result['status'] ?? 500);
            }

            // Parse locations into simplified format
            $locations = [];
            foreach ($result['data']['entry'] ?? [] as $entry) {
                $resource = $entry['resource'] ?? [];
                $locations[] = [
                    'id' => $resource['id'] ?? null,
                    'name' => $resource['name'] ?? null,
                    'description' => $resource['description'] ?? null,
                    'status' => $resource['status'] ?? null,
                    'address' => implode(', ', $resource['address']['line'] ?? []),
                ];
            }

            return response()->json([
                'success' => true,
                'locations' => $locations,
            ]);

        } catch (\Exception $e) {
            Log::error('SATUSEHAT locations error', [
                'error' => $e->getMessage(),
            ]);

            return response()->json([
                'success' => false,
                'message' => 'Terjadi kesalahan saat menghubungi SATUSEHAT',
            ], 500);
        }
    }
}
