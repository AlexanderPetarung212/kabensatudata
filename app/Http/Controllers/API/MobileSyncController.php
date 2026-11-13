<?php

namespace App\Http\Controllers\API;

use App\Http\Controllers\Controller;
use App\Models\Building;
use App\Models\Family;
use App\Models\FamilyMember;
use App\Models\MedicalRecord;
use App\Models\Medicine;
use App\Models\MedicineUsage;
use App\Models\Village;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Str;

class MobileSyncController extends Controller
{
    /**
     * Model map for dynamic table operations.
     */
    private array $modelMap = [
        'villages' => Village::class,
        'buildings' => Building::class,
        'families' => Family::class,
        'family_members' => FamilyMember::class,
        'medical_records' => MedicalRecord::class,
    ];

    /**
     * Pull data from server to mobile device.
     * Supports incremental sync via ?since=ISO8601_TIMESTAMP
     *
     * GET /api/mobile/sync/pull?since=2026-02-24T00:00:00Z&village_ids[]=1&village_ids[]=2
     */
    public function pull(Request $request): JsonResponse
    {
        $request->validate([
            'since' => 'nullable|date',
            'village_ids' => 'nullable|array',
            'village_ids.*' => 'integer|exists:villages,id',
        ]);

        $since = $request->input('since');
        $villageIds = $request->input('village_ids', []);

        $data = [];

        // Villages
        $villagesQuery = Village::query();
        if (!empty($villageIds)) {
            $villagesQuery->whereIn('id', $villageIds);
        }
        if ($since) {
            $villagesQuery->where('updated_at', '>', $since);
        }
        $data['villages'] = $villagesQuery->get()->map(fn($v) => $this->formatForSync($v));

        // Buildings (scoped to villages)
        $buildingsQuery = Building::query();
        if (!empty($villageIds)) {
            $buildingsQuery->whereIn('village_id', $villageIds);
        }
        if ($since) {
            $buildingsQuery->where('updated_at', '>', $since);
        }
        $data['buildings'] = $buildingsQuery->get()->map(fn($b) => $this->formatForSync($b));

        // Families (scoped to buildings in villages)
        $familiesQuery = Family::query();
        if (!empty($villageIds)) {
            $familiesQuery->whereHas('building', fn($q) => $q->whereIn('village_id', $villageIds));
        }
        if ($since) {
            $familiesQuery->where('updated_at', '>', $since);
        }
        $data['families'] = $familiesQuery->get()->map(fn($f) => $this->formatForSync($f));

        // Family Members (scoped)
        $membersQuery = FamilyMember::without('family'); // Skip eager load for efficiency
        if (!empty($villageIds)) {
            $membersQuery->whereHas('family.building', fn($q) => $q->whereIn('village_id', $villageIds));
        }
        if ($since) {
            $membersQuery->where('updated_at', '>', $since);
        }
        $data['family_members'] = $membersQuery->get()->map(fn($m) => $this->formatForSync($m));

        // Medical Records (scoped) — exclude large satusehat_raw_data at query level
        $recordsQuery = MedicalRecord::query()
            ->select(array_diff(
                \Schema::getColumnListing('medical_records'),
                ['satusehat_raw_data']  // exclude: JSON blob too large for mobile sync
            ));
        if (!empty($villageIds)) {
            $recordsQuery->whereHas('familyMember.family.building', fn($q) => $q->whereIn('village_id', $villageIds));
        }
        if ($since) {
            $recordsQuery->where('updated_at', '>', $since);
        }
        $data['medical_records'] = $recordsQuery->get()->map(fn($r) => $this->formatForSync($r));

        return response()->json([
            'data' => $data,
            'server_time' => now()->toISOString(),
            'counts' => [
                'villages' => count($data['villages']),
                'buildings' => count($data['buildings']),
                'families' => count($data['families']),
                'family_members' => count($data['family_members']),
                'medical_records' => count($data['medical_records']),
            ],
        ]);
    }

    /**
     * Push offline changes from mobile device to server.
     * Uses last-write-wins strategy with conflict detection.
     *
     * POST /api/mobile/sync/push
     * Body: { changes: [ { table, action, uuid, data, client_updated_at } ] }
     */
    public function push(Request $request): JsonResponse
    {
        $request->validate([
            'changes' => 'required|array',
            'changes.*.table' => 'required|string|in:' . implode(',', array_keys($this->modelMap)),
            'changes.*.action' => 'required|string|in:create,update,delete',
            'changes.*.uuid' => 'required|uuid',
            'changes.*.data' => 'required|array',
            'changes.*.client_updated_at' => 'required|date',
            'device_id' => 'required|string|max:100',
        ]);

        $user = $request->user();
        $deviceId = $request->input('device_id');
        $changes = $request->input('changes');

        $accepted = [];
        $conflicts = [];
        $errors = [];
        $uuidMap = []; // Maps client UUIDs to server IDs

        DB::beginTransaction();

        try {
            foreach ($changes as $index => $change) {
                $table = $change['table'];
                $action = $change['action'];
                $uuid = $change['uuid'];
                $data = $change['data'];
                $clientUpdatedAt = $change['client_updated_at'];

                $modelClass = $this->modelMap[$table];

                try {
                    switch ($action) {
                        case 'create':
                            // Extract medicine_usages before creating the record
                            $medicineUsages = $data['medicine_usages'] ?? null;
                            unset($data['medicine_usages']);

                            $result = $this->handleCreate($modelClass, $uuid, $data, $user, $deviceId);

                            // Handle medicine_usages for medical records
                            if ($table === 'medical_records' && $medicineUsages && is_array($medicineUsages)) {
                                $this->syncMedicineUsages($result, $medicineUsages);
                            }

                            $accepted[] = [
                                'index' => $index,
                                'uuid' => $uuid,
                                'server_id' => $result->id,
                                'action' => 'created',
                            ];
                            $uuidMap[$uuid] = $result->id;
                            break;

                        case 'update':
                            // Extract medicine_usages before updating
                            $medicineUsages = $data['medicine_usages'] ?? null;
                            unset($data['medicine_usages']);

                            $result = $this->handleUpdate($modelClass, $uuid, $data, $clientUpdatedAt, $user, $deviceId);

                            // Handle medicine_usages for medical records
                            if ($table === 'medical_records' && $medicineUsages && is_array($medicineUsages)) {
                                $model = $result['model'] ?? $result;
                                $this->syncMedicineUsages($model, $medicineUsages);
                            }

                            if ($result['status'] === 'accepted') {
                                $accepted[] = [
                                    'index' => $index,
                                    'uuid' => $uuid,
                                    'server_id' => $result['model']->id,
                                    'action' => 'updated',
                                ];
                            } else {
                                $conflicts[] = [
                                    'index' => $index,
                                    'uuid' => $uuid,
                                    'action' => 'update_conflict',
                                    'server_data' => $this->formatForSync($result['model']),
                                    'server_updated_at' => $result['model']->updated_at->toISOString(),
                                    'message' => 'Data sudah diperbarui oleh pengguna lain. Data server yang lebih baru telah diterapkan.',
                                ];
                                $accepted[] = [
                                    'index' => $index,
                                    'uuid' => $uuid,
                                    'server_id' => $result['model']->id,
                                    'action' => 'updated_with_conflict',
                                ];
                            }
                            break;

                        case 'delete':
                            $this->handleDelete($modelClass, $uuid);
                            $accepted[] = [
                                'index' => $index,
                                'uuid' => $uuid,
                                'action' => 'deleted',
                            ];
                            break;
                    }
                } catch (\Exception $e) {
                    $errors[] = [
                        'index' => $index,
                        'uuid' => $uuid,
                        'action' => $action,
                        'error' => $e->getMessage(),
                    ];

                    Log::error("Mobile sync error for {$table}:{$uuid}", [
                        'action' => $action,
                        'error' => $e->getMessage(),
                        'user_id' => $user->id,
                        'device_id' => $deviceId,
                    ]);
                }
            }

            // Only commit if there are no critical errors
            if (empty($errors)) {
                DB::commit();
            } else {
                // Partial commit: we still commit accepted changes
                DB::commit();
            }
        } catch (\Exception $e) {
            DB::rollBack();

            Log::error('Mobile sync batch error', [
                'error' => $e->getMessage(),
                'user_id' => $user->id,
                'device_id' => $deviceId,
            ]);

            return response()->json([
                'message' => 'Terjadi kesalahan saat sinkronisasi.',
                'error' => $e->getMessage(),
            ], 500);
        }

        return response()->json([
            'accepted' => $accepted,
            'conflicts' => $conflicts,
            'errors' => $errors,
            'uuid_map' => $uuidMap,
            'server_time' => now()->toISOString(),
            'summary' => [
                'total' => count($changes),
                'accepted' => count($accepted),
                'conflicts' => count($conflicts),
                'errors' => count($errors),
            ],
        ]);
    }

    /**
     * Get list of villages for mobile app.
     */
    public function villages(Request $request): JsonResponse
    {
        $villages = Village::withCount(['buildings'])
            ->get()
            ->map(fn($v) => [
                'id' => $v->id,
                'uuid' => $v->uuid,
                'name' => $v->name,
                'code' => $v->code,
                'district' => $v->district,
                'regency' => $v->regency,
                'province' => $v->province,
                'buildings_count' => $v->buildings_count,
            ]);

        return response()->json(['data' => $villages]);
    }

    /**
     * Get buildings for a specific village.
     */
    public function buildings(Request $request): JsonResponse
    {
        $request->validate([
            'village_id' => 'required|integer|exists:villages,id',
        ]);

        $buildings = Building::where('village_id', $request->village_id)
            ->withCount('families')
            ->get()
            ->map(fn($b) => [
                'id' => $b->id,
                'uuid' => $b->uuid,
                'village_id' => $b->village_id,
                'building_number' => $b->building_number,
                'latitude' => $b->latitude,
                'longitude' => $b->longitude,
                'description' => $b->description,
                'families_count' => $b->families_count,
                'updated_at' => $b->updated_at?->toISOString(),
            ]);

        return response()->json(['data' => $buildings]);
    }

    /**
     * Get families for a specific building.
     */
    public function families(Request $request): JsonResponse
    {
        $request->validate([
            'building_id' => 'required|integer|exists:buildings,id',
        ]);

        $families = Family::where('building_id', $request->building_id)
            ->withCount('members')
            ->get()
            ->map(fn($f) => array_merge(
                $f->only([
                    'id', 'uuid', 'building_id', 'family_number', 'head_name',
                    'sequence_number_in_building',
                    'has_clean_water', 'is_water_protected',
                    'has_toilet', 'is_toilet_sanitary',
                    'has_mental_illness', 'takes_medication_regularly',
                    'has_restrained_member',
                ]),
                [
                    'members_count' => $f->members_count,
                    'updated_at' => $f->updated_at?->toISOString(),
                ]
            ));

        return response()->json(['data' => $families]);
    }

    /**
     * Get family members for a specific family.
     */
    public function familyMembers(Request $request): JsonResponse
    {
        $request->validate([
            'family_id' => 'required|integer|exists:families,id',
        ]);

        $members = FamilyMember::without('family')
            ->where('family_id', $request->family_id)
            ->get()
            ->map(fn($m) => array_merge(
                $m->only([
                    'id', 'uuid', 'family_id', 'name', 'nik', 'rm_number',
                    'ihs_number', 'nik_verified_status', 'nik_verified_source',
                    'sequence_number_in_family', 'relationship',
                    'birth_place', 'gender', 'address',
                    'is_pregnant', 'religion', 'education', 'marital_status', 'occupation',
                    'has_jkn', 'is_smoker', 'use_water', 'use_toilet',
                    'has_tuberculosis', 'takes_tb_medication_regularly', 'has_chronic_cough',
                    'has_hypertension', 'takes_hypertension_medication_regularly',
                    'uses_contraception', 'gave_birth_in_health_facility',
                    'exclusive_breastfeeding', 'complete_immunization', 'growth_monitoring',
                ]),
                [
                    'birth_date' => $m->birth_date?->format('Y-m-d'),
                    'age' => $m->age,
                    'nik_verified_at' => $m->nik_verified_at?->toISOString(),
                    'updated_at' => $m->updated_at?->toISOString(),
                ]
            ));

        return response()->json(['data' => $members]);
    }

    /**
     * Get medical records for a specific family member.
     */
    public function medicalRecords(Request $request): JsonResponse
    {
        $request->validate([
            'family_member_id' => 'required|integer|exists:family_members,id',
        ]);

        $records = MedicalRecord::where('family_member_id', $request->family_member_id)
            ->orderBy('visit_date', 'desc')
            ->get()
            ->map(fn($r) => array_merge(
                $r->only([
                    'id', 'uuid', 'family_member_id', 'chief_complaint', 'anamnesis',
                    'systolic', 'diastolic', 'weight', 'height',
                    'heart_rate', 'body_temperature', 'respiratory_rate',
                    'diagnosis_code', 'diagnosis_name',
                    'therapy', 'medication', 'procedure',
                    'workflow_status', 'created_by',
                ]),
                [
                    'visit_date' => $r->visit_date?->format('Y-m-d'),
                    'bmi' => $r->bmi,
                    'bmi_category' => $r->bmi_category,
                    'blood_pressure_category' => $r->blood_pressure_category,
                    'updated_at' => $r->updated_at?->toISOString(),
                ]
            ));

        return response()->json(['data' => $records]);
    }

    /**
     * Get all active medicines for mobile app.
     */
    public function medicines(Request $request): JsonResponse
    {
        $medicines = Medicine::active()->orderBy('name')->get()->map(function ($m) {
            return [
                'id' => $m->id,
                'name' => $m->name,
                'generic_name' => $m->generic_name,
                'strength' => $m->strength,
                'stock_quantity' => (int) $m->stock_quantity,
                'unit' => $m->unit,
                'minimum_stock' => (int) $m->minimum_stock,
                'is_active' => (bool) $m->is_active,
            ];
        });

        return response()->json(['data' => $medicines]);
    }

    /**
     * Medical Dashboard: top diseases, top medicines used, daily visits (30 days).
     */
    public function medicalDashboard(Request $request): JsonResponse
    {
        $request->validate([
            'date_from' => 'nullable|date',
            'date_to' => 'nullable|date|after_or_equal:date_from',
        ]);

        $dateFrom = $request->input('date_from')
            ? \Carbon\Carbon::parse($request->input('date_from'))->startOfDay()
            : now()->subDays(30)->startOfDay();
        $dateTo = $request->input('date_to')
            ? \Carbon\Carbon::parse($request->input('date_to'))->endOfDay()
            : now()->endOfDay();

        // Top 10 Diseases
        $topDiseases = MedicalRecord::whereBetween('visit_date', [$dateFrom, $dateTo])
            ->whereNotNull('diagnosis_name')
            ->where('diagnosis_name', '!=', '')
            ->select('diagnosis_name', DB::raw('COUNT(*) as count'))
            ->groupBy('diagnosis_name')
            ->orderByDesc('count')
            ->limit(10)
            ->get()
            ->map(fn($r) => ['name' => $r->diagnosis_name, 'count' => (int) $r->count]);

        // Top 10 Medicines Used
        $topMedicines = MedicineUsage::whereBetween('medicine_usages.created_at', [$dateFrom, $dateTo])
            ->join('medicines', 'medicines.id', '=', 'medicine_usages.medicine_id')
            ->select('medicines.name', 'medicines.unit', DB::raw('SUM(medicine_usages.quantity_used) as total_used'))
            ->groupBy('medicines.id', 'medicines.name', 'medicines.unit')
            ->orderByDesc('total_used')
            ->limit(10)
            ->get()
            ->map(fn($r) => [
                'name' => $r->name,
                'unit' => $r->unit,
                'total_used' => (int) $r->total_used,
            ]);

        // Daily Visits
        $dailyVisits = MedicalRecord::whereBetween('visit_date', [$dateFrom, $dateTo])
            ->select(DB::raw('DATE(visit_date) as date'), DB::raw('COUNT(*) as count'))
            ->groupBy(DB::raw('DATE(visit_date)'))
            ->orderBy('date')
            ->get()
            ->map(fn($r) => ['date' => $r->date, 'count' => (int) $r->count]);

        // Summary stats
        $totalRecords = MedicalRecord::whereBetween('visit_date', [$dateFrom, $dateTo])->count();
        $totalPatients = MedicalRecord::whereBetween('visit_date', [$dateFrom, $dateTo])
            ->distinct('family_member_id')->count('family_member_id');

        $periodDays = $dateFrom->diffInDays($dateTo) + 1;

        return response()->json([
            'top_diseases' => $topDiseases,
            'top_medicines' => $topMedicines,
            'daily_visits' => $dailyVisits,
            'summary' => [
                'total_visits' => $totalRecords,
                'total_patients' => $totalPatients,
                'period_days' => $periodDays,
                'date_from' => $dateFrom->toDateString(),
                'date_to' => $dateTo->toDateString(),
            ],
        ]);
    }

    /**
     * Store a new medicine.
     */
    public function storeMedicine(Request $request): JsonResponse
    {
        $request->validate([
            'name' => 'required|string|max:255',
            'generic_name' => 'nullable|string|max:255',
            'strength' => 'nullable|string|max:255',
            'unit' => 'required|string|in:tablet,kapsul,botol,tube,ampul,vial,sachet,strip',
            'stock_quantity' => 'required|integer|min:0',
            'minimum_stock' => 'required|integer|min:0',
            'description' => 'nullable|string',
            'is_active' => 'boolean',
        ]);

        $medicine = Medicine::create($request->only([
            'name', 'generic_name', 'strength', 'unit',
            'stock_quantity', 'minimum_stock', 'description', 'is_active',
        ]));

        return response()->json([
            'message' => 'Obat berhasil ditambahkan',
            'data' => $this->formatMedicine($medicine),
        ], 201);
    }

    /**
     * Update an existing medicine.
     */
    public function updateMedicine(Request $request, int $id): JsonResponse
    {
        $medicine = Medicine::findOrFail($id);

        $request->validate([
            'name' => 'required|string|max:255',
            'generic_name' => 'nullable|string|max:255',
            'strength' => 'nullable|string|max:255',
            'unit' => 'required|string|in:tablet,kapsul,botol,tube,ampul,vial,sachet,strip',
            'stock_quantity' => 'required|integer|min:0',
            'minimum_stock' => 'required|integer|min:0',
            'description' => 'nullable|string',
            'is_active' => 'boolean',
        ]);

        $medicine->update($request->only([
            'name', 'generic_name', 'strength', 'unit',
            'stock_quantity', 'minimum_stock', 'description', 'is_active',
        ]));

        return response()->json([
            'message' => 'Obat berhasil diperbarui',
            'data' => $this->formatMedicine($medicine->fresh()),
        ]);
    }

    /**
     * Delete a medicine.
     */
    public function destroyMedicine(int $id): JsonResponse
    {
        $medicine = Medicine::findOrFail($id);
        $medicine->delete();

        return response()->json(['message' => 'Obat berhasil dihapus']);
    }

    /**
     * Adjust stock of a medicine.
     */
    public function adjustStock(Request $request, int $id): JsonResponse
    {
        $medicine = Medicine::findOrFail($id);

        $request->validate([
            'adjustment' => 'required|integer',
            'reason' => 'required|string|max:500',
        ]);

        $oldStock = $medicine->stock_quantity;
        $newStock = max(0, $oldStock + $request->adjustment);
        $medicine->update(['stock_quantity' => $newStock]);

        return response()->json([
            'message' => "Stok {$medicine->name} diubah dari {$oldStock} menjadi {$newStock}",
            'data' => $this->formatMedicine($medicine->fresh()),
        ]);
    }

    /**
     * Format a medicine for API response.
     */
    private function formatMedicine(Medicine $m): array
    {
        return [
            'id' => $m->id,
            'name' => $m->name,
            'generic_name' => $m->generic_name,
            'strength' => $m->strength,
            'stock_quantity' => (int) $m->stock_quantity,
            'stock_initial' => (int) ($m->stock_initial ?? 0),
            'unit' => $m->unit,
            'minimum_stock' => (int) $m->minimum_stock,
            'description' => $m->description,
            'is_active' => (bool) $m->is_active,
        ];
    }

    /**
     * Sync medicine usages for a medical record.
     * Deletes existing usages and creates new ones from APK data.
     */
    private function syncMedicineUsages(MedicalRecord $record, array $usages): void
    {
        // Delete existing usages (they will restore stock via model events)
        $record->medicineUsages()->delete();

        $medicationLines = [];

        foreach ($usages as $usage) {
            $medicineId = $usage['medicine_id'] ?? null;
            if (!$medicineId) continue;

            $medicine = Medicine::find($medicineId);
            if (!$medicine) continue;

            // Create usage (auto-reduces stock via MedicineUsage boot events)
            MedicineUsage::create([
                'medical_record_id' => $record->id,
                'medicine_id' => $medicineId,
                'quantity_used' => $usage['quantity_used'] ?? 0,
                'instruction_text' => $usage['instruction_text'] ?? null,
                'frequency' => $usage['frequency'] ?? null,
                'dosage' => $usage['dosage'] ?? null,
                'notes' => $usage['notes'] ?? null,
            ]);

            // Build medication text
            $line = $medicine->full_name;
            if (!empty($usage['quantity_used'])) {
                $line .= ' - ' . $usage['quantity_used'] . ' ' . $medicine->unit;
            }
            $freq = $usage['frequency'] ?? null;
            if ($freq) {
                $line .= " ({$freq})";
            } elseif (!empty($usage['instruction_text'])) {
                $short = MedicalRecord::instructionToShortFrequency($usage['instruction_text']);
                $line .= ' (' . ($short ?? $usage['instruction_text']) . ')';
            }
            if (!empty($usage['notes'])) {
                $line .= ' - ' . $usage['notes'];
            }
            $medicationLines[] = $line;
        }

        // Update medication text on the record
        if (!empty($medicationLines)) {
            $record->update(['medication' => implode("\n", $medicationLines)]);
        }
    }

    /**
     * Quick Patient Registration — register a new patient directly
     * without going through the full Village→Building→Family hierarchy.
     * The patient is stored in the "Pasien Umum" placeholder family.
     *
     * POST /api/mobile/quick-register
     */
    public function quickRegister(Request $request): JsonResponse
    {
        $request->validate([
            'name'        => 'required|string|max:255',
            'gender'      => 'required|string|in:Laki-laki,Perempuan',
            'birth_date'  => 'nullable|date',
            'nik'         => 'nullable|string|size:16',
            'birth_place' => 'nullable|string|max:255',
            'address'     => 'nullable|string|max:500',
            'occupation'  => 'nullable|string|max:255',
            'religion'    => 'nullable|string|max:50',
            'has_jkn'     => 'nullable|boolean',
        ]);

        // ── Duplicate Detection ──
        $existing = null;

        // 1) Exact match by NIK
        if ($request->nik) {
            $existing = FamilyMember::without('family')
                ->where('nik', $request->nik)
                ->first();
        }

        // 2) Fuzzy match by name + birth_date
        if (!$existing && $request->name && $request->birth_date) {
            $existing = FamilyMember::without('family')
                ->where('name', $request->name)
                ->whereDate('birth_date', $request->birth_date)
                ->first();
        }

        if ($existing) {
            return response()->json([
                'message'     => 'Pasien sudah terdaftar',
                'data'        => $this->formatQuickRegisterMember($existing),
                'is_existing' => true,
            ]);
        }

        // ── Find the General Patient family ──
        $generalFamily = Family::where('family_number', 'UMUM-001')
            ->whereHas('building', function ($q) {
                $q->whereHas('village', fn($vq) => $vq->where('code', '0000000000'));
            })
            ->first();

        if (!$generalFamily) {
            return response()->json([
                'message' => 'Struktur Pasien Umum belum dibuat. Jalankan GeneralPatientSeeder terlebih dahulu.',
            ], 500);
        }

        // ── Create new FamilyMember ──
        $member = FamilyMember::create([
            'family_id'    => $generalFamily->id,
            'name'         => $request->name,
            'gender'       => $request->gender,
            'birth_date'   => $request->birth_date,
            'nik'          => $request->nik,
            'birth_place'  => $request->birth_place,
            'address'      => $request->address,
            'occupation'   => $request->occupation,
            'religion'     => $request->religion,
            'has_jkn'      => $request->has_jkn ?? false,
            'relationship' => 'Lainnya',
        ]);

        // ── Override RM number with UM-XXX format ──
        $umCount = FamilyMember::where('family_id', $generalFamily->id)->count();
        $member->rm_number = 'UM-' . str_pad($umCount, 3, '0', STR_PAD_LEFT);
        $member->save();

        return response()->json([
            'message'     => 'Pasien berhasil didaftarkan',
            'data'        => $this->formatQuickRegisterMember($member),
            'is_existing' => false,
        ], 201);
    }

    /**
     * Format a FamilyMember for quick-register response.
     */
    private function formatQuickRegisterMember(FamilyMember $m): array
    {
        return [
            'id'         => $m->id,
            'uuid'       => $m->uuid,
            'family_id'  => $m->family_id,
            'name'       => $m->name,
            'rm_number'  => $m->rm_number,
            'gender'     => $m->gender,
            'birth_date' => $m->birth_date?->format('Y-m-d'),
            'age'        => $m->age,
            'nik'        => $m->nik,
            'address'    => $m->address,
            'has_jkn'    => (bool) $m->has_jkn,
            'birth_place'=> $m->birth_place,
            'occupation' => $m->occupation,
            'religion'   => $m->religion,
            'relationship' => $m->relationship,
            'sequence_number_in_family' => $m->sequence_number_in_family,
            'ihs_number' => $m->ihs_number,
            'nik_verified_status' => $m->nik_verified_status,
            'nik_verified_at' => $m->nik_verified_at?->toISOString(),
            'nik_verified_source' => $m->nik_verified_source,
            'updated_at' => $m->updated_at?->toISOString(),
        ];
    }

    // ───────── Move Patient to Real Family ─────────

    /**
     * Move a patient from General Family (UMUM-001) to a real family.
     * Regenerates RM number and cascades update to all medical records.
     *
     * POST /api/mobile/move-patient/{id}
     * Body: { "family_id": 123 }
     */
    public function movePatient(Request $request, int $id): JsonResponse
    {
        $request->validate([
            'family_id' => 'required|integer|exists:families,id',
        ]);

        $member = FamilyMember::with(['family.building.village'])->find($id);

        if (!$member) {
            return response()->json([
                'message' => 'Pasien tidak ditemukan.',
            ], 404);
        }

        // Verify patient is from General Family (RM starts with UM-)
        if (!$member->rm_number || !str_starts_with($member->rm_number, 'UM-')) {
            return response()->json([
                'message' => 'Pasien ini bukan dari Keluarga Umum. Hanya pasien dengan RM format UM-XXX yang bisa dipindahkan.',
            ], 422);
        }

        // Verify target family is not the General Family itself
        $targetFamily = Family::with(['building.village'])->find($request->family_id);

        if ($targetFamily->family_number === 'UMUM-001') {
            return response()->json([
                'message' => 'Tidak bisa memindahkan ke Keluarga Umum.',
            ], 422);
        }

        $oldRmNumber = $member->rm_number;
        $oldFamilyId = $member->family_id;

        return DB::transaction(function () use ($member, $targetFamily, $oldRmNumber, $oldFamilyId, $request) {
            // 1. Update family_id
            $member->family_id = $request->family_id;

            // 2. Generate new sequence_number_in_family
            $maxSequence = FamilyMember::where('family_id', $request->family_id)
                ->max('sequence_number_in_family');
            $member->sequence_number_in_family = ($maxSequence ?? 0) + 1;

            // 3. Force-regenerate RM number
            // Need to refresh the family relationship to get the new hierarchy
            $member->load(['family.building.village']);
            $member->generateRmNumber(true);
            $member->save();

            // 4. Update all medical records for this patient via syncPatientData()
            $medicalRecords = MedicalRecord::where('family_member_id', $member->id)->get();
            $recordsUpdated = 0;
            foreach ($medicalRecords as $record) {
                $record->load(['familyMember.family.building.village']);
                $record->syncPatientData();
                $record->save();
                $recordsUpdated++;
            }

            return response()->json([
                'message' => 'Pasien berhasil dipindahkan ke keluarga baru.',
                'data' => [
                    'id'                     => $member->id,
                    'name'                   => $member->name,
                    'old_rm_number'          => $oldRmNumber,
                    'new_rm_number'          => $member->rm_number,
                    'old_family_id'          => $oldFamilyId,
                    'new_family_id'          => $member->family_id,
                    'new_family_name'        => $targetFamily->head_name,
                    'medical_records_updated' => $recordsUpdated,
                ],
            ]);
        });
    }

    // ───────── Private Helpers ─────────

    /**
     * Format a model for sync response.
     * Ensures numeric types are properly cast (Laravel returns decimals as strings).
     */
    private function formatForSync($model): array
    {
        $data = $model->toArray();
        $data['updated_at'] = $model->updated_at?->toISOString();
        $data['created_at'] = $model->created_at?->toISOString();

        // Cast decimal/float fields to proper numeric types
        $floatFields = ['latitude', 'longitude', 'weight', 'height', 'body_temperature', 'bmi'];
        $intFields = ['systolic', 'diastolic', 'heart_rate', 'respiratory_rate', 'age',
                      'sequence_number_in_building', 'sequence_number_in_family'];

        foreach ($floatFields as $field) {
            if (isset($data[$field]) && $data[$field] !== null) {
                $data[$field] = (float) $data[$field];
            }
        }
        foreach ($intFields as $field) {
            if (isset($data[$field]) && $data[$field] !== null) {
                $data[$field] = (int) $data[$field];
            }
        }

        // Remove large fields not needed by mobile APK
        unset(
            $data['satusehat_raw_data'],      // JSON blob — very large
            $data['satusehat_encounter_id'],   // not needed on mobile
            $data['satusehat_imported_at'],    // not needed on mobile
            $data['satusehat_synced_at'],      // not needed on mobile
        );

        return $data;
    }

    /**
     * Handle create action from mobile.
     */
    private function handleCreate(string $modelClass, string $uuid, array $data, $user, string $deviceId)
    {
        // Check if UUID already exists (idempotent create)
        $existing = $modelClass::where('uuid', $uuid)->first();
        if ($existing) {
            return $existing;
        }

        // Clean data: remove server-side auto-generated fields
        unset($data['id'], $data['created_at'], $data['updated_at']);

        // Resolve foreign key UUIDs to IDs if needed
        $data = $this->resolveUuidReferences($data);

        $data['uuid'] = $uuid;
        $data['synced_at'] = now();
        $data['synced_by'] = $user->id;
        $data['device_id'] = $deviceId;

        // For medical records, set created_by
        if ($modelClass === MedicalRecord::class && !isset($data['created_by'])) {
            $data['created_by'] = $user->id;
        }

        return $modelClass::create($data);
    }

    /**
     * Handle update action from mobile.
     * Returns ['status' => 'accepted'|'conflict', 'model' => Model]
     */
    private function handleUpdate(string $modelClass, string $uuid, array $data, string $clientUpdatedAt, $user, string $deviceId): array
    {
        $model = $modelClass::where('uuid', $uuid)->firstOrFail();

        // Check for conflict: server version is newer than client's base version
        $isConflict = $model->updated_at && $model->updated_at->gt($clientUpdatedAt);

        // Apply the update (last-write-wins for both accepted and conflict)
        $this->forceUpdate($model, $data, $user, $deviceId);

        return [
            'status' => $isConflict ? 'conflict' : 'accepted',
            'model' => $model,
        ];
    }

    /**
     * Force update a model (used after conflict detection in last-write-wins).
     */
    private function forceUpdate($model, array $data, $user, string $deviceId): void
    {
        unset($data['id'], $data['uuid'], $data['created_at'], $data['updated_at']);

        $data = $this->resolveUuidReferences($data);
        $data['synced_at'] = now();
        $data['synced_by'] = $user->id;
        $data['device_id'] = $deviceId;

        // Filter to only fillable fields to prevent MassAssignmentException
        $fillable = $model->getFillable();
        $filteredData = array_intersect_key($data, array_flip($fillable));

        $model->fill($filteredData);
        $model->save();

        // Log if any fields were dropped (helps debug future sync issues)
        $dropped = array_diff_key($data, $filteredData);
        if (!empty($dropped)) {
            Log::warning('Sync: dropped non-fillable fields', [
                'model' => get_class($model),
                'uuid' => $model->uuid ?? null,
                'dropped_fields' => array_keys($dropped),
            ]);
        }
    }

    /**
     * Handle delete action from mobile.
     */
    private function handleDelete(string $modelClass, string $uuid): void
    {
        $model = $modelClass::where('uuid', $uuid)->first();
        if ($model) {
            $model->delete();
        }
    }

    /**
     * Resolve UUID references to database IDs for foreign keys.
     * Example: { "village_uuid": "abc-123" } → { "village_id": 5 }
     */
    private function resolveUuidReferences(array $data): array
    {
        $uuidRefMap = [
            'village_uuid' => ['field' => 'village_id', 'model' => Village::class],
            'building_uuid' => ['field' => 'building_id', 'model' => Building::class],
            'family_uuid' => ['field' => 'family_id', 'model' => Family::class],
            'family_member_uuid' => ['field' => 'family_member_id', 'model' => FamilyMember::class],
        ];

        foreach ($uuidRefMap as $uuidField => $ref) {
            if (isset($data[$uuidField])) {
                $related = $ref['model']::where('uuid', $data[$uuidField])->first();
                if ($related) {
                    $data[$ref['field']] = $related->id;
                }
                unset($data[$uuidField]);
            }
        }

        return $data;
    }
}
