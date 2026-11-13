<?php

namespace App\Services;

use App\Models\FamilyMember;
use App\Models\MedicalRecord;
use App\Models\User;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Str;

/**
 * Service untuk import data RME dari SATUSEHAT ke KabenSatuData.
 *
 * Operasi READ-ONLY terhadap SATUSEHAT:
 * - GET Encounter (kunjungan)
 * - GET Condition (diagnosis)
 * - GET Observation (vital signs)
 * - GET MedicationRequest (obat/resep)
 * - GET Composition (anamnesis, keluhan)
 *
 * Keputusan desain:
 * - Patient matching: by IHS Number → family_members.ihs_number
 * - Jika patient tidak match: import dengan family_member_id = null (Opsi B)
 * - Diagnosis ganda: rank 1 = utama, sekunder dicatat di therapy (Opsi A+C)
 * - Deduplication: by satusehat_encounter_id (skip jika sudah ada)
 * - Duplikasi tanggal+nama: skip encounter jika sudah ada record manual (Opsi A)
 */
class SatusehatImportService
{
    protected SatusehatService $satusehat;

    /**
     * LOINC Code → medical_records field mapping.
     */
    protected array $loincMapping = [
        '8480-6'  => 'systolic',           // Systolic blood pressure
        '8462-4'  => 'diastolic',          // Diastolic blood pressure
        '8867-4'  => 'heart_rate',         // Heart rate
        '8310-5'  => 'body_temperature',   // Body temperature
        '9279-1'  => 'respiratory_rate',   // Respiratory rate
        '29463-7' => 'weight',             // Body weight
        '8302-2'  => 'height',             // Body height
    ];

    /**
     * Encounter status → workflow_status mapping.
     */
    protected array $statusMapping = [
        'finished'    => 'completed',
        'arrived'     => 'pending_registration',
        'in-progress' => 'pending_doctor',
        'triaged'     => 'pending_nurse',
    ];

    public function __construct(SatusehatService $satusehat)
    {
        $this->satusehat = $satusehat;
    }

    /**
     * Import encounters dari SATUSEHAT ke medical_records.
     *
     * @param array $options  Options: limit, since, dryRun, onProgress callback
     * @return array  Summary hasil import
     */
    public function importEncounters(array $options = []): array
    {
        $limit = $options['limit'] ?? 0;
        $since = $options['since'] ?? null;
        $dryRun = $options['dryRun'] ?? false;
        $onProgress = $options['onProgress'] ?? null;

        $summary = [
            'total_fetched' => 0,
            'imported' => 0,
            'skipped_duplicate' => 0,
            'skipped_existing' => 0,
            'skipped_cancelled' => 0,
            'unmatched_patient' => 0,
            'errors' => 0,
            'records' => [],
        ];

        // Fetch all encounters from SATUSEHAT
        $encounters = $this->fetchAllEncounters($limit, $since);
        $summary['total_fetched'] = count($encounters);

        foreach ($encounters as $i => $encounter) {
            $result = $this->processEncounter($encounter, $dryRun);

            // Update summary
            $summary[$result['action']] = ($summary[$result['action']] ?? 0) + 1;
            $summary['records'][] = $result;

            // Callback for progress
            if ($onProgress) {
                $onProgress($i + 1, $summary['total_fetched'], $result);
            }

            // Rate limit between Condition/Observation calls
            if ($result['action'] === 'imported') {
                usleep(300000); // 300ms between imports
            }
        }

        return $summary;
    }

    /**
     * Fetch all encounters from SATUSEHAT (paginated).
     */
    protected function fetchAllEncounters(int $limit = 0, ?string $since = null): array
    {
        $allEncounters = [];
        $pageSize = 50; // Larger page size to reduce requests
        $page = 1;
        $orgId = config('satusehat.organization_id');
        $maxPages = 20; // Safety limit

        do {
            $query = [
                'service-provider' => "Organization/{$orgId}",
                '_count' => $pageSize,
                '_sort' => '-date',
            ];

            if ($since) {
                $query['date'] = "ge{$since}";
            }

            // Offset pagination
            if ($page > 1) {
                $query['_page'] = $page;
            }

            $result = $this->satusehat->fhirGetPublic('/Encounter', $query);

            if (!$result['success']) {
                Log::error('SATUSEHAT fetchAllEncounters failed', ['page' => $page, 'error' => $result]);
                break;
            }

            $entries = $result['data']['entry'] ?? [];
            if (empty($entries)) {
                break;
            }

            foreach ($entries as $entry) {
                $allEncounters[] = $entry['resource'];

                if ($limit > 0 && count($allEncounters) >= $limit) {
                    break 2;
                }
            }

            // Check for next page using FHIR Bundle links
            $links = $result['data']['link'] ?? [];
            $hasNextLink = false;
            foreach ($links as $link) {
                if (($link['relation'] ?? '') === 'next') {
                    $hasNextLink = true;
                    break;
                }
            }

            // Continue if: has next link OR got full page of results (might have more)
            $hasNextPage = $hasNextLink || count($entries) >= $pageSize;
            $page++;

        } while ($hasNextPage && $page <= $maxPages);

        return $allEncounters;
    }

    /**
     * Process satu encounter: match patient, fetch details, insert record.
     */
    protected function processEncounter(array $encounter, bool $dryRun): array
    {
        $encounterId = $encounter['id'] ?? null;
        $status = $encounter['status'] ?? 'unknown';
        $patientRef = $encounter['subject']['reference'] ?? '';
        $patientName = $encounter['subject']['display'] ?? 'Unknown';
        $patientIhs = str_replace('Patient/', '', $patientRef);
        $visitDate = $this->parseDate($encounter['period']['start'] ?? null);

        $result = [
            'encounter_id' => $encounterId,
            'patient_name' => $patientName,
            'patient_ihs' => $patientIhs,
            'visit_date' => $visitDate,
            'status' => $status,
            'action' => null,
            'message' => '',
        ];

        // Skip cancelled encounters
        if ($status === 'cancelled') {
            $result['action'] = 'skipped_cancelled';
            $result['message'] = 'Encounter cancelled';
            return $result;
        }

        // Deduplication: skip if already imported
        if (MedicalRecord::where('satusehat_encounter_id', $encounterId)->exists()) {
            $result['action'] = 'skipped_duplicate';
            $result['message'] = 'Already imported';
            return $result;
        }

        // Duplikasi check: skip jika sudah ada record manual dengan tanggal + nama yang sama
        if ($visitDate) {
            $existingManual = MedicalRecord::where('data_source', 'manual')
                ->whereDate('visit_date', $visitDate)
                ->where(function ($q) use ($patientName) {
                    $q->where('patient_name', $patientName)
                      ->orWhere('patient_name', 'LIKE', '%' . explode(' ', $patientName)[0] . '%');
                })
                ->exists();

            if ($existingManual) {
                $result['action'] = 'skipped_existing';
                $result['message'] = 'Manual record exists for same date+patient';
                return $result;
            }
        }

        // Match patient to family_member
        $familyMember = null;
        if ($patientIhs) {
            $familyMember = FamilyMember::where('ihs_number', $patientIhs)->first();

            // Fallback: try matching by name (case-insensitive)
            if (!$familyMember) {
                $familyMember = FamilyMember::whereRaw(
                    'LOWER(name) = ?',
                    [strtolower($patientName)]
                )->first();
            }
        }

        if (!$familyMember) {
            $result['unmatched'] = true;
        }

        // Fetch Conditions (diagnosis)
        $diagnosis = $this->fetchDiagnosis($encounterId);

        // Fetch Observations (vital signs)
        $vitalSigns = $this->fetchVitalSigns($encounterId);

        // Fetch MedicationRequest (obat/resep)
        $medication = $this->fetchMedications($encounterId);

        // Fetch Composition (anamnesis, keluhan)
        $composition = $this->fetchComposition($encounterId);

        // Extract chief_complaint from Encounter.reasonCode
        $chiefComplaint = $this->extractReasonCode($encounter);

        // Match practitioner to user
        $createdBy = $this->matchPractitioner($encounter['participant'] ?? []);

        // Build medical record data
        $recordData = $this->buildRecordData(
            encounter: $encounter,
            familyMember: $familyMember,
            diagnosis: $diagnosis,
            vitalSigns: $vitalSigns,
            medication: $medication,
            composition: $composition,
            chiefComplaint: $chiefComplaint,
            createdBy: $createdBy,
        );

        if (!$dryRun) {
            try {
                MedicalRecord::create($recordData);
                $result['action'] = 'imported';
                $result['message'] = $diagnosis['primary_code']
                    ? "{$diagnosis['primary_code']} {$diagnosis['primary_name']}"
                    : 'No diagnosis';
            } catch (\Exception $e) {
                $result['action'] = 'errors';
                $result['message'] = $e->getMessage();
                Log::error('SATUSEHAT import error', [
                    'encounter_id' => $encounterId,
                    'error' => $e->getMessage(),
                ]);
            }
        } else {
            $result['action'] = 'imported';
            $result['message'] = '[DRY RUN] ' . ($diagnosis['primary_code'] ?? 'No diagnosis');
        }

        if (!$familyMember) {
            $result['unmatched_patient'] = true;
        }

        return $result;
    }

    /**
     * Fetch diagnosis (Condition) untuk satu encounter.
     * Implements Opsi A+C: rank 1 = utama, sekunder di therapy.
     */
    protected function fetchDiagnosis(string $encounterId): array
    {
        $result = $this->satusehat->fhirGetPublic('/Condition', [
            'encounter' => "Encounter/{$encounterId}",
        ]);

        $primary = ['code' => null, 'name' => null];
        $secondary = [];

        if (!$result['success']) {
            return [
                'primary_code' => null,
                'primary_name' => null,
                'secondary_text' => null,
            ];
        }

        $entries = $result['data']['entry'] ?? [];

        foreach ($entries as $index => $entry) {
            $resource = $entry['resource'] ?? [];
            $coding = $resource['code']['coding'][0] ?? [];
            $code = $coding['code'] ?? null;
            $display = $coding['display'] ?? null;

            if ($index === 0) {
                // First/primary diagnosis (rank 1)
                $primary = ['code' => $code, 'name' => $display];
            } else {
                // Secondary diagnoses
                $secondary[] = "{$code} - {$display}";
            }
        }

        return [
            'primary_code' => $primary['code'],
            'primary_name' => $primary['name'],
            'secondary_text' => !empty($secondary)
                ? 'Diagnosis sekunder: ' . implode('; ', $secondary)
                : null,
        ];
    }

    /**
     * Fetch vital signs (Observation) untuk satu encounter.
     */
    protected function fetchVitalSigns(string $encounterId): array
    {
        $result = $this->satusehat->fhirGetPublic('/Observation', [
            'encounter' => "Encounter/{$encounterId}",
            '_count' => 20,
        ]);

        $vitalSigns = [];

        if (!$result['success']) {
            return $vitalSigns;
        }

        foreach ($result['data']['entry'] ?? [] as $entry) {
            $resource = $entry['resource'] ?? [];
            $loincCode = $resource['code']['coding'][0]['code'] ?? null;

            if (!$loincCode || !isset($this->loincMapping[$loincCode])) {
                continue;
            }

            $field = $this->loincMapping[$loincCode];
            $value = $resource['valueQuantity']['value']
                ?? $resource['valueString']
                ?? null;

            if ($value !== null && $value !== '') {
                $vitalSigns[$field] = is_numeric($value) ? (float) $value : $value;
            }
        }

        return $vitalSigns;
    }

    /**
     * Match SATUSEHAT Practitioner ke User lokal.
     */
    protected function matchPractitioner(array $participants): ?int
    {
        foreach ($participants as $participant) {
            $type = $participant['type'][0]['coding'][0]['code'] ?? '';
            // ATND = attender (dokter utama)
            if ($type !== 'ATND') {
                continue;
            }

            $practitionerRef = $participant['individual']['reference'] ?? '';
            $ihsNumber = str_replace('Practitioner/', '', $practitionerRef);

            if ($ihsNumber) {
                $user = User::where('ihs_number', $ihsNumber)->first();
                if ($user) {
                    return $user->id;
                }
            }

            // Fallback: match by name
            $name = $participant['individual']['display'] ?? '';
            if ($name) {
                $user = User::whereRaw('LOWER(name) LIKE ?', ['%' . strtolower(explode(' ', $name)[0]) . '%'])->first();
                if ($user) {
                    return $user->id;
                }
            }
        }

        return null;
    }

    /**
     * Build data array untuk MedicalRecord::create().
     */
    protected function buildRecordData(
        array $encounter,
        ?FamilyMember $familyMember,
        array $diagnosis,
        array $vitalSigns,
        string $medication,
        array $composition,
        ?string $chiefComplaint,
        ?int $createdBy,
    ): array {
        $patientName = $encounter['subject']['display'] ?? 'Unknown';
        $visitDate = $this->parseDate($encounter['period']['start'] ?? null);
        $status = $this->statusMapping[$encounter['status'] ?? ''] ?? 'completed';

        // Extract location
        $location = $encounter['location'][0]['location']['display'] ?? null;

        // Period times
        $startTime = $encounter['period']['start'] ?? null;
        $endTime = $encounter['period']['end'] ?? null;

        // Therapy: combine diagnosis sekunder + medication info if no dedicated medication
        $therapyParts = [];
        if ($diagnosis['secondary_text']) {
            $therapyParts[] = $diagnosis['secondary_text'];
        }
        $therapy = implode("\n", $therapyParts) ?: null;

        $data = [
            'uuid' => (string) Str::uuid(),
            'satusehat_encounter_id' => $encounter['id'],
            'data_source' => 'satusehat',
            'satusehat_imported_at' => now(),

            // Patient info
            'family_member_id' => $familyMember?->id,
            'patient_name' => $familyMember?->name ?? $patientName,
            'patient_gender' => $familyMember?->gender,
            'patient_nik' => $familyMember?->nik,
            'patient_rm_number' => $familyMember?->rm_number,
            'patient_birth_date' => $familyMember?->birth_date,
            'patient_address' => $familyMember?->address,

            // Visit info
            'visit_date' => $visitDate,
            'workflow_status' => $status,

            // Timestamps from encounter period
            'doctor_start_time' => $startTime ? date('Y-m-d H:i:s', strtotime($startTime)) : null,
            'doctor_end_time' => $endTime ? date('Y-m-d H:i:s', strtotime($endTime)) : null,
            'completed_at' => $endTime ? date('Y-m-d H:i:s', strtotime($endTime)) : null,

            // Diagnosis (Opsi A+C)
            'diagnosis_code' => $diagnosis['primary_code'],
            'diagnosis_name' => $diagnosis['primary_name'],
            'therapy' => $therapy,

            // Medication (dari MedicationRequest)
            'medication' => $medication ?: null,

            // Keluhan & Anamnesis (dari Composition + Encounter.reasonCode)
            'chief_complaint' => $chiefComplaint ?: ($composition['chief_complaint'] ?? null),
            'anamnesis' => $composition['anamnesis'] ?? null,

            // Procedure (dari Encounter.serviceType jika ada)
            'procedure' => $location ? "Rawat Jalan - {$location}" : null,

            // Created by (matched practitioner)
            'created_by' => $createdBy,
        ];

        // Merge vital signs
        foreach ($vitalSigns as $field => $value) {
            $data[$field] = $value;
        }

        // Calculate patient age if birth_date available
        if ($familyMember?->birth_date && $visitDate) {
            try {
                $birthDate = new \DateTime($familyMember->birth_date);
                $visit = new \DateTime($visitDate);
                $data['patient_age'] = $birthDate->diff($visit)->y;
            } catch (\Exception $e) {
                // Ignore
            }
        }

        return $data;
    }

    /**
     * Fetch MedicationRequest (obat/resep) untuk satu encounter.
     * Format output mengikuti format manual: "Nama Obat - Jumlah unit (Aturan)"
     */
    protected function fetchMedications(string $encounterId): string
    {
        $result = $this->satusehat->fhirGetPublic('/MedicationRequest', [
            'encounter' => $encounterId,
        ]);

        if (!$result['success'] || empty($result['data']['entry'])) {
            return '';
        }

        $medications = [];

        foreach ($result['data']['entry'] as $entry) {
            $med = $entry['resource'];

            // Get medication name
            $medName = $med['medicationReference']['display']
                ?? $med['medicationCodeableConcept']['coding'][0]['display']
                ?? $med['medicationCodeableConcept']['text']
                ?? 'Unknown';

            // Get quantity dispensed
            $qty = '';
            if (isset($med['dispenseRequest']['quantity'])) {
                $qtyVal = $med['dispenseRequest']['quantity']['value'] ?? '';
                $qtyUnit = $med['dispenseRequest']['quantity']['unit'] ?? 'unit';
                $qty = "{$qtyVal} {$qtyUnit}";
            }

            // Get dosage instruction
            $dosage = '';
            if (isset($med['dosageInstruction'][0])) {
                $di = $med['dosageInstruction'][0];

                // Try to get frequency (e.g., 3x1)
                if (isset($di['timing']['repeat'])) {
                    $freq = $di['timing']['repeat']['frequency'] ?? '';
                    $period = $di['timing']['repeat']['period'] ?? '';
                    if ($freq && $period) {
                        $dosage = "{$freq}x{$period}";
                    }
                }

                // Or use text instruction
                if (!$dosage && isset($di['text'])) {
                    $dosage = $di['text'];
                }

                // Or use patientInstruction
                if (!$dosage && isset($di['patientInstruction'])) {
                    $dosage = $di['patientInstruction'];
                }

                // Dose amount
                if (isset($di['doseAndRate'][0]['doseQuantity'])) {
                    $doseVal = $di['doseAndRate'][0]['doseQuantity']['value'] ?? '';
                    if ($doseVal && !$dosage) {
                        $dosage = $doseVal;
                    }
                }
            }

            // Build format: "Amoxicilin 500mg - 10 tablet (3x1)"
            $line = $medName;
            if ($qty) {
                $line .= " - {$qty}";
            }
            if ($dosage) {
                $line .= " ({$dosage})";
            }

            $medications[] = $line;
        }

        return implode("\n", $medications);
    }

    /**
     * Fetch Composition (anamnesis, keluhan) untuk satu encounter.
     * Mengembalikan array ['anamnesis' => ..., 'chief_complaint' => ...]
     */
    protected function fetchComposition(string $encounterId): array
    {
        $result = $this->satusehat->fhirGetPublic('/Composition', [
            'encounter' => $encounterId,
        ]);

        $data = [
            'anamnesis' => null,
            'chief_complaint' => null,
        ];

        if (!$result['success'] || empty($result['data']['entry'])) {
            return $data;
        }

        $comp = $result['data']['entry'][0]['resource'];
        $sections = $comp['section'] ?? [];

        foreach ($sections as $section) {
            $title = strtolower($section['title'] ?? '');
            $code = $section['code']['coding'][0]['code'] ?? '';
            $text = $section['text']['div'] ?? '';

            // Strip HTML tags from div content
            $cleanText = trim(strip_tags($text));

            if (empty($cleanText)) {
                continue;
            }

            // Match section by code or title
            // TK000003 = Anamnesis, TK000002 = Keluhan Utama
            if ($code === 'TK000003' || str_contains($title, 'anamnesis')) {
                $data['anamnesis'] = $cleanText;
            } elseif ($code === 'TK000002' || str_contains($title, 'keluhan')) {
                $data['chief_complaint'] = $cleanText;
            }
        }

        return $data;
    }

    /**
     * Extract reasonCode (keluhan utama) dari Encounter resource.
     */
    protected function extractReasonCode(array $encounter): ?string
    {
        $reasons = $encounter['reasonCode'] ?? [];
        $parts = [];

        foreach ($reasons as $reason) {
            $text = $reason['text']
                ?? $reason['coding'][0]['display']
                ?? null;
            if ($text) {
                $parts[] = $text;
            }
        }

        return $parts ? implode(', ', $parts) : null;
    }

    /**
     * Parse ISO date string ke Y-m-d format.
     */
    protected function parseDate(?string $dateString): ?string
    {
        if (!$dateString) {
            return null;
        }

        try {
            return date('Y-m-d', strtotime($dateString));
        } catch (\Exception $e) {
            return null;
        }
    }
}
