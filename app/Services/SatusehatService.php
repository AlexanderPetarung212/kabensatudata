<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * SATUSEHAT Integration Service (READ-ONLY)
 *
 * Handles OAuth authentication and FHIR R4 API calls to SATUSEHAT.
 * Currently operates in READ-ONLY mode — only GET operations.
 */
class SatusehatService
{
    protected string $baseUrl;
    protected string $clientId;
    protected string $clientSecret;
    protected string $organizationId;

    public function __construct()
    {
        $this->baseUrl = config('satusehat.base_url');
        $this->clientId = config('satusehat.client_id');
        $this->clientSecret = config('satusehat.client_secret');
        $this->organizationId = config('satusehat.organization_id');
    }

    // ─── OAuth Authentication ───────────────────────────────────────────

    /**
     * Get a valid OAuth access token (cached).
     *
     * @return string
     * @throws \Exception
     */
    public function getAccessToken(): string
    {
        $cacheKey = config('satusehat.token_cache_key');
        $cacheTtl = config('satusehat.token_cache_ttl');

        return Cache::remember($cacheKey, $cacheTtl, function () {
            return $this->requestNewToken();
        });
    }

    /**
     * Request a new OAuth token from SATUSEHAT.
     *
     * @return string
     * @throws \Exception
     */
    protected function requestNewToken(): string
    {
        $url = "{$this->baseUrl}/oauth2/v1/accesstoken?grant_type=client_credentials";

        $response = Http::asForm()->post($url, [
            'client_id' => $this->clientId,
            'client_secret' => $this->clientSecret,
        ]);

        if (!$response->successful()) {
            Log::error('SATUSEHAT OAuth failed', [
                'status' => $response->status(),
                'body' => $response->body(),
            ]);
            throw new \Exception('Gagal mendapatkan token SATUSEHAT: ' . $response->body());
        }

        $data = $response->json();

        if (empty($data['access_token'])) {
            throw new \Exception('Token SATUSEHAT kosong');
        }

        Log::info('SATUSEHAT OAuth token acquired', [
            'expires_in' => $data['expires_in'] ?? 'unknown',
        ]);

        return $data['access_token'];
    }

    /**
     * Force refresh the cached token.
     *
     * @return string
     */
    public function refreshToken(): string
    {
        Cache::forget(config('satusehat.token_cache_key'));
        return $this->getAccessToken();
    }

    // ─── FHIR API Calls (READ-ONLY) ────────────────────────────────────

    /**
     * Public wrapper for fhirGet — used by SatusehatImportService.
     *
     * @param string $endpoint
     * @param array  $query
     * @return array
     */
    public function fhirGetPublic(string $endpoint, array $query = []): array
    {
        return $this->fhirGet($endpoint, $query);
    }

    /**
     * Make an authenticated GET request to SATUSEHAT FHIR API.
     *
     * @param string $endpoint  e.g. '/Patient', '/Organization/100014591'
     * @param array  $query     Query parameters
     * @return array
     * @throws \Exception
     */
    protected function fhirGet(string $endpoint, array $query = []): array
    {
        $token = $this->getAccessToken();
        $url = "{$this->baseUrl}/fhir-r4/v1{$endpoint}";

        $response = Http::withToken($token)
            ->timeout(15)
            ->get($url, $query);

        // If 401, try refreshing token once
        if ($response->status() === 401) {
            $token = $this->refreshToken();
            $response = Http::withToken($token)
                ->timeout(15)
                ->get($url, $query);
        }

        if (!$response->successful()) {
            Log::warning('SATUSEHAT FHIR GET failed', [
                'endpoint' => $endpoint,
                'status' => $response->status(),
                'body' => $response->body(),
            ]);

            return [
                'success' => false,
                'status' => $response->status(),
                'error' => $response->json('issue.0.diagnostics') ?? $response->body(),
            ];
        }

        return [
            'success' => true,
            'data' => $response->json(),
        ];
    }

    // ─── Patient (Verifikasi NIK) ──────────────────────────────────────

    /**
     * Cari data pasien berdasarkan NIK.
     *
     * @param string $nik  NIK 16 digit
     * @return array  Parsed patient data atau error
     */
    public function verifyNik(string $nik): array
    {
        // Validasi format NIK
        if (!preg_match('/^\d{16}$/', $nik)) {
            return [
                'success' => false,
                'error' => 'NIK harus 16 digit angka',
            ];
        }

        $result = $this->fhirGet('/Patient', [
            'identifier' => "https://fhir.kemkes.go.id/id/nik|{$nik}",
        ]);

        if (!$result['success']) {
            return $result;
        }

        $data = $result['data'];

        // Check if patient found
        $total = $data['total'] ?? 0;
        if ($total === 0 || empty($data['entry'])) {
            return [
                'success' => true,
                'found' => false,
                'message' => 'NIK tidak ditemukan di database SATUSEHAT',
            ];
        }

        // Parse patient resource
        $patient = $data['entry'][0]['resource'] ?? [];

        return [
            'success' => true,
            'found' => true,
            'patient' => $this->parsePatient($patient),
            'raw' => $patient, // Include raw FHIR data for debugging
        ];
    }

    /**
     * Parse FHIR Patient resource ke format KabenSatuData.
     *
     * @param array $patient  Raw FHIR Patient resource
     * @return array  Parsed data yang cocok untuk FamilyMember
     */
    protected function parsePatient(array $patient): array
    {
        // Extract name
        $name = '';
        if (!empty($patient['name'])) {
            $nameObj = $patient['name'][0] ?? [];
            $name = $nameObj['text'] ?? trim(($nameObj['given'][0] ?? '') . ' ' . ($nameObj['family'] ?? ''));
        }

        // Extract gender → map to KabenSatuData format
        $gender = match ($patient['gender'] ?? '') {
            'male' => 'Laki-laki',
            'female' => 'Perempuan',
            default => null,
        };

        // Extract birth date
        $birthDate = $patient['birthDate'] ?? null;

        // Extract address
        $address = $this->parseAddress($patient['address'] ?? []);

        // Extract marital status
        $maritalStatus = $this->parseMaritalStatus($patient['maritalStatus'] ?? []);

        // Extract IHS Number (SATUSEHAT Patient ID)
        $ihsNumber = $patient['id'] ?? null;

        // Extract NIK from identifiers
        $nik = null;
        foreach ($patient['identifier'] ?? [] as $identifier) {
            if (($identifier['system'] ?? '') === 'https://fhir.kemkes.go.id/id/nik') {
                $nik = $identifier['value'] ?? null;
                break;
            }
        }

        return [
            'ihs_number' => $ihsNumber,
            'nik' => $nik,
            'name' => $name,
            'gender' => $gender,
            'birth_date' => $birthDate,
            'birth_place' => null, // Not available in FHIR Patient
            'address' => $address['full'] ?? null,
            'address_city' => $address['city'] ?? null,
            'address_district' => $address['district'] ?? null,
            'address_province' => $address['state'] ?? null,
            'address_village_code' => $address['village_code'] ?? null,
            'marital_status' => $maritalStatus,
            'religion' => null, // Not in standard FHIR Patient
            'occupation' => null, // Not in standard FHIR Patient
        ];
    }

    /**
     * Parse FHIR Address array.
     */
    protected function parseAddress(array $addresses): array
    {
        if (empty($addresses)) {
            return [];
        }

        $addr = $addresses[0] ?? [];
        $line = implode(', ', $addr['line'] ?? []);

        // Extract administrative codes from extension
        $villageCode = null;
        $districtCode = null;
        foreach ($addr['extension'] ?? [] as $ext) {
            if (str_contains($ext['url'] ?? '', 'administrativeCode')) {
                foreach ($ext['extension'] ?? [] as $subExt) {
                    if (($subExt['url'] ?? '') === 'village') {
                        $villageCode = $subExt['valueCode'] ?? null;
                    }
                    if (($subExt['url'] ?? '') === 'district') {
                        $districtCode = $subExt['valueCode'] ?? null;
                    }
                }
            }
        }

        return [
            'full' => $line,
            'city' => $addr['city'] ?? null,
            'district' => $addr['district'] ?? null,
            'state' => $addr['state'] ?? null,
            'postal_code' => $addr['postalCode'] ?? null,
            'country' => $addr['country'] ?? null,
            'village_code' => $villageCode,
            'district_code' => $districtCode,
        ];
    }

    /**
     * Parse FHIR MaritalStatus → KabenSatuData format.
     */
    protected function parseMaritalStatus(array $maritalStatus): ?string
    {
        $code = $maritalStatus['coding'][0]['code'] ?? null;

        return match ($code) {
            'M' => 'Kawin',
            'S' => 'Belum Kawin',
            'D' => 'Cerai Hidup',
            'W' => 'Cerai Mati',
            default => null,
        };
    }

    // ─── Practitioner (Tenaga Kesehatan) ───────────────────────────────

    /**
     * Cari data tenaga kesehatan berdasarkan NIK.
     *
     * @param string $nik  NIK nakes 16 digit
     * @return array
     */
    public function findPractitioner(string $nik): array
    {
        if (!preg_match('/^\d{16}$/', $nik)) {
            return [
                'success' => false,
                'error' => 'NIK harus 16 digit angka',
            ];
        }

        $result = $this->fhirGet('/Practitioner', [
            'identifier' => "https://fhir.kemkes.go.id/id/nik|{$nik}",
        ]);

        if (!$result['success']) {
            return $result;
        }

        $data = $result['data'];
        $total = $data['total'] ?? 0;

        if ($total === 0 || empty($data['entry'])) {
            return [
                'success' => true,
                'found' => false,
                'message' => 'Tenaga kesehatan tidak ditemukan',
            ];
        }

        $practitioner = $data['entry'][0]['resource'] ?? [];

        // Extract name
        $name = '';
        if (!empty($practitioner['name'])) {
            $nameObj = $practitioner['name'][0] ?? [];
            $name = $nameObj['text'] ?? trim(($nameObj['given'][0] ?? '') . ' ' . ($nameObj['family'] ?? ''));
        }

        return [
            'success' => true,
            'found' => true,
            'practitioner' => [
                'ihs_number' => $practitioner['id'] ?? null,
                'name' => $name,
                'nik' => $nik,
                'gender' => match ($practitioner['gender'] ?? '') {
                    'male' => 'Laki-laki',
                    'female' => 'Perempuan',
                    default => null,
                },
                'birth_date' => $practitioner['birthDate'] ?? null,
                'active' => $practitioner['active'] ?? null,
            ],
        ];
    }

    // ─── Organization ──────────────────────────────────────────────────

    /**
     * Get data organisasi (Puskesmas) dari SATUSEHAT.
     *
     * @return array
     */
    public function getOrganization(): array
    {
        return $this->fhirGet("/Organization/{$this->organizationId}");
    }

    // ─── Location ──────────────────────────────────────────────────────

    /**
     * Get daftar lokasi layanan Puskesmas.
     *
     * @return array
     */
    public function getLocations(): array
    {
        return $this->fhirGet('/Location', [
            'organization' => $this->organizationId,
            '_count' => 50,
        ]);
    }

    // ─── Health Check ──────────────────────────────────────────────────

    /**
     * Test apakah koneksi ke SATUSEHAT berfungsi.
     *
     * @return array
     */
    public function healthCheck(): array
    {
        try {
            $token = $this->getAccessToken();
            $org = $this->getOrganization();

            $orgName = $org['data']['name'] ?? 'Unknown';

            return [
                'status' => 'connected',
                'environment' => config('satusehat.env'),
                'base_url' => $this->baseUrl,
                'organization_id' => $this->organizationId,
                'organization_name' => $orgName,
                'readonly' => config('satusehat.readonly'),
                'token_valid' => true,
            ];
        } catch (\Exception $e) {
            return [
                'status' => 'error',
                'error' => $e->getMessage(),
                'token_valid' => false,
            ];
        }
    }
}
