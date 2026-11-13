<?php

namespace App\Http\Controllers\Api\Mobile;

use App\Models\FamilyMember;
use App\Models\SpmTarget;
use App\Models\SpmIndicator;
use App\Models\SpmAchievementOverride;
use App\Models\SpmMonthlyTarget;
use App\Services\SpmDenominatorService;
use Carbon\Carbon;
use Illuminate\Http\Request;
use Illuminate\Routing\Controller;

class MobileSpmController extends Controller
{
    public function __construct()
    {
        $this->middleware('auth:sanctum');
    }

    /**
     * SPM Dashboard — JSON version of SpmController::dashboard()
     */
    public function dashboard(Request $request)
    {
        $year = (int) ($request->input('year', now()->year));
        $villageId = $request->input('village_id');
        $month = $request->input('month');
        $indicatorId = $request->input('indicator_id');

        if ($month && (int)$month >= 1 && (int)$month <= 12) {
            $periodStart = Carbon::create($year, (int)$month, 1)->startOfDay();
            $periodEnd = (clone $periodStart)->endOfMonth();
        } else {
            $periodStart = Carbon::create($year, 1, 1)->startOfDay();
            $periodEnd = Carbon::create($year, 12, 31)->endOfDay();
            $month = null;
        }

        $base = FamilyMember::query()
            ->when($villageId, function ($q) use ($villageId) {
                $q->whereHas('family.building', fn($b) => $b->where('village_id', $villageId));
            });

        $denominator = app(SpmDenominatorService::class);
        $indicatorsQuery = SpmIndicator::with('subIndicators')->orderBy('code');
        if ($indicatorId) $indicatorsQuery->where('id', $indicatorId);
        $indicators = $indicatorsQuery->get();

        $results = [];
        $overviewLabels = [];
        $overviewActuals = [];
        $overviewDenoms = [];
        $overviewPotentials = [];

        foreach ($indicators as $indicator) {
            $subs = [];
            $sumNumerator = 0;
            $sumDenomDinkes = 0;
            $sumDenomRiil = 0;

            foreach ($indicator->subIndicators as $sub) {
                $numerator = \App\Models\MedicalRecord::query()
                    ->when($villageId, fn($q) => $q->whereHas('familyMember.family.building', fn($b) => $b->where('village_id', $villageId)))
                    ->whereBetween('visit_date', [$periodStart, $periodEnd])
                    ->where(function($q) use ($sub) {
                        $q->whereHas('spmSubIndicators', fn($w) => $w->where('spm_sub_indicators.id', $sub->id))
                          ->orWhere('spm_service_type', $sub->code);
                    })
                    ->count();

                // Override check
                $isOverridden = false;
                if ($month) {
                    $override = SpmAchievementOverride::where('spm_sub_indicator_id', $sub->id)
                        ->where('year', $year)->where('month', (int)$month)
                        ->when($villageId, fn($q) => $q->where('village_id', $villageId))
                        ->first();
                    if ($override) { $numerator = (int) $override->value; $isOverridden = true; }
                } else {
                    $overrideY = SpmAchievementOverride::where('spm_sub_indicator_id', $sub->id)
                        ->where('year', $year)->whereNull('month')
                        ->when($villageId, fn($q) => $q->where('village_id', $villageId))
                        ->first();
                    if ($overrideY) { $numerator = (int) $overrideY->value; $isOverridden = true; }
                    else {
                        $mq = SpmAchievementOverride::where('spm_sub_indicator_id', $sub->id)
                            ->where('year', $year)->whereNotNull('month')
                            ->when($villageId, fn($q) => $q->where('village_id', $villageId));
                        if ((clone $mq)->count() > 0) {
                            $numerator = (int)(clone $mq)->sum('value');
                            $isOverridden = true;
                        }
                    }
                }

                $denomCount = $denominator->countForSubIndicator($sub->code, $base, $periodStart, $periodEnd);

                if ($sub->code === 'SPM_02_KB_AKTIF' && !$isOverridden) {
                    $kbQ = $denominator->queryForSubIndicator($sub->code, $base, $periodStart, $periodEnd);
                    $numerator = (clone $kbQ)->where('uses_contraception', true)->count();
                }

                if (!$isOverridden && in_array($sub->code, ['SPM_04_ASI_EKS','SPM_04_IDL'])) {
                    $denomIds = $denominator->queryForSubIndicator($sub->code, $base, $periodStart, $periodEnd)->pluck('id');
                    $numerator = \App\Models\MedicalRecord::query()
                        ->when($villageId, fn($q) => $q->whereHas('familyMember.family.building', fn($b) => $b->where('village_id', $villageId)))
                        ->whereBetween('visit_date', [$periodStart, $periodEnd])
                        ->where(function($q) use ($sub) {
                            $q->whereHas('spmSubIndicators', fn($w) => $w->where('spm_sub_indicators.id', $sub->id))
                              ->orWhere('spm_service_type', $sub->code);
                        })
                        ->whereIn('family_member_id', $denomIds)
                        ->count();
                }

                $percentage = $denomCount > 0 ? round(($numerator / $denomCount) * 100, 2) : 0.0;

                $target = SpmTarget::where('year', $year)->where('spm_sub_indicator_id', $sub->id)
                    ->when($villageId, fn($q) => $q->where('village_id', $villageId))->first()
                    ?: SpmTarget::where('year', $year)->whereNull('village_id')->where('spm_sub_indicator_id', $sub->id)->first();

                $denominatorDinkes = $target?->denominator_dinkes;
                $targetPercentage = $target?->target_percentage;
                $targetAbsolute = ($denominatorDinkes && $targetPercentage !== null) ? round($denominatorDinkes * ($targetPercentage / 100)) : null;

                if ($month) {
                    $mt = SpmMonthlyTarget::where('year', $year)->where('month', (int)$month)->where('spm_sub_indicator_id', $sub->id)
                        ->when($villageId, fn($q) => $q->where('village_id', $villageId))->first()
                        ?: SpmMonthlyTarget::where('year', $year)->where('month', (int)$month)->where('spm_sub_indicator_id', $sub->id)->whereNull('village_id')->first();
                    if ($mt) $targetAbsolute = (int)$mt->target_absolute;
                }

                $gap = ($targetAbsolute !== null) ? ($numerator - $targetAbsolute) : null;

                $subs[] = [
                    'id' => $sub->id, 'code' => $sub->code, 'name' => $sub->name,
                    'numerator_riil' => $numerator, 'denominator_riil' => $denomCount,
                    'percentage_riil' => $percentage,
                    'denominator_dinkes' => $denominatorDinkes,
                    'target_percentage' => $targetPercentage,
                    'target_absolute' => $targetAbsolute, 'gap' => $gap,
                    'is_overridden' => $isOverridden,
                ];

                $sumNumerator += $numerator;
                if ($denominatorDinkes !== null) $sumDenomDinkes += $denominatorDinkes;
                $sumDenomRiil += $denomCount;
            }

            $results[] = ['code' => $indicator->code, 'name' => $indicator->name, 'subs' => $subs];
            $overviewLabels[] = $indicator->name;
            $overviewActuals[] = $sumNumerator;
            $overviewDenoms[] = $sumDenomDinkes;
            $overviewPotentials[] = $sumDenomRiil;
        }

        $villages = \App\Models\Village::orderBy('name')->get()->map(fn($v) => ['id' => $v->id, 'name' => $v->name]);

        return response()->json([
            'data' => [
                'year' => $year, 'month' => $month, 'village_id' => $villageId,
                'overview' => [
                    'labels' => $overviewLabels,
                    'actuals' => $overviewActuals,
                    'denoms' => $overviewDenoms,
                    'potentials' => $overviewPotentials,
                ],
                'indicators' => $results,
                'villages' => $villages,
            ],
        ]);
    }

    /**
     * SPM Sub-Indicator Detail — patient lists
     */
    public function subDetail(Request $request, $subId)
    {
        $sub = \App\Models\SpmSubIndicator::findOrFail($subId);
        $year = (int) ($request->input('year', now()->year));
        $villageId = $request->input('village_id');
        $month = $request->input('month');

        if ($month && (int)$month >= 1 && (int)$month <= 12) {
            $periodStart = Carbon::create($year, (int)$month, 1)->startOfDay();
            $periodEnd = (clone $periodStart)->endOfMonth();
        } else {
            $periodStart = Carbon::create($year, 1, 1)->startOfDay();
            $periodEnd = Carbon::create($year, 12, 31)->endOfDay();
            $month = null;
        }

        // Achieved patients
        $mrQuery = \App\Models\MedicalRecord::query()
            ->when($villageId, fn($q) => $q->whereHas('familyMember.family.building', fn($b) => $b->where('village_id', $villageId)))
            ->whereBetween('visit_date', [$periodStart, $periodEnd])
            ->where(function($q) use ($sub) {
                $q->whereHas('spmSubIndicators', fn($w) => $w->where('spm_sub_indicators.id', $sub->id))
                  ->orWhere('spm_service_type', $sub->code);
            });

        if ($sub->code === 'SPM_02_KB_AKTIF') {
            $baseMembers = FamilyMember::query()
                ->when($villageId, fn($q) => $q->whereHas('family.building', fn($b) => $b->where('village_id', $villageId)));
            $denominator = app(SpmDenominatorService::class);
            $kbQ = $denominator->queryForSubIndicator($sub->code, $baseMembers, $periodStart, $periodEnd);
            $memberIds = (clone $kbQ)->where('uses_contraception', true)->pluck('id')->unique()->values();
        } elseif (in_array($sub->code, ['SPM_04_ASI_EKS','SPM_04_IDL'])) {
            $baseMembers = FamilyMember::query()
                ->when($villageId, fn($q) => $q->whereHas('family.building', fn($b) => $b->where('village_id', $villageId)));
            $denominator = app(SpmDenominatorService::class);
            $denomIds = $denominator->queryForSubIndicator($sub->code, $baseMembers, $periodStart, $periodEnd)->pluck('id');
            $memberIds = (clone $mrQuery)->whereIn('family_member_id', $denomIds)->pluck('family_member_id')->unique()->values();
        } else {
            $memberIds = (clone $mrQuery)->pluck('family_member_id')->unique()->values();
        }

        $members = FamilyMember::with(['family.building.village'])->whereIn('id', $memberIds)->get()->keyBy('id');
        $lastByMember = ($sub->code === 'SPM_02_KB_AKTIF')
            ? collect()
            : (clone $mrQuery)->selectRaw('family_member_id, MAX(visit_date) as last_visit')->groupBy('family_member_id')->pluck('last_visit', 'family_member_id');

        $rows = $memberIds->map(function($id) use ($members, $lastByMember) {
            $m = $members->get($id);
            if (!$m) return null;
            return [
                'id' => $id, 'name' => $m->name, 'gender' => $m->gender,
                'age' => $m->age, 'nik' => $m->nik,
                'village' => $m->family?->building?->village?->name,
                'last_visit' => $lastByMember->get($id) ? Carbon::parse($lastByMember->get($id))->format('Y-m-d') : null,
            ];
        })->filter()->values();

        // Potential patients
        $baseMembers = FamilyMember::query()
            ->when($villageId, fn($q) => $q->whereHas('family.building', fn($b) => $b->where('village_id', $villageId)));
        $denominator = app(SpmDenominatorService::class);
        $denomQ = $denominator->queryForSubIndicator($sub->code, $baseMembers, $periodStart, $periodEnd);
        $potentialMembers = $denomQ->with(['family.building.village'])->get();
        $achievedSet = collect($memberIds)->flip();

        $potential = $potentialMembers->map(function($m) use ($achievedSet) {
            return [
                'id' => $m->id, 'name' => $m->name, 'gender' => $m->gender,
                'age' => $m->age, 'nik' => $m->nik,
                'village' => optional($m->family->building->village)->name,
                'status' => $achievedSet->has($m->id) ? 'Tercatat' : 'Belum tercatat',
            ];
        })->values();

        return response()->json([
            'data' => [
                'sub_indicator' => ['id' => $sub->id, 'code' => $sub->code, 'name' => $sub->name],
                'year' => $year, 'month' => $month,
                'achieved_count' => count($rows),
                'potential_count' => count($potential),
                'rows' => $rows,
                'potential' => $potential,
            ],
        ]);
    }

    // ─── SPM Relevance Check ────────────────────────────────────────────

    /**
     * Check which SPM sub-indicators are relevant for a given family member.
     * Returns only sub-indicators where the member qualifies as denominator
     * AND has not yet been recorded in the current year.
     */
    public function checkRelevance(Request $request)
    {
        $request->validate(['family_member_id' => 'required|integer|exists:family_members,id']);

        $memberId = (int) $request->input('family_member_id');
        $member = FamilyMember::findOrFail($memberId);
        $year = (int) ($request->input('year', now()->year));

        $periodStart = Carbon::create($year, 1, 1)->startOfDay();
        $periodEnd   = Carbon::create($year, 12, 31)->endOfDay();

        // Build a base query that only includes THIS member
        $baseQuery = FamilyMember::query()->where('id', $memberId);

        $denominator  = app(SpmDenominatorService::class);
        $indicators   = SpmIndicator::with('subIndicators')->orderBy('code')->get();

        $relevant = [];

        foreach ($indicators as $indicator) {
            foreach ($indicator->subIndicators as $sub) {
                // Check if this member is in the denominator for this sub-indicator
                $isEligible = $denominator->countForSubIndicator(
                    $sub->code,
                    clone $baseQuery,
                    $periodStart,
                    $periodEnd
                ) > 0;

                if (!$isEligible) continue;

                // Check if already recorded in pivot this year
                $alreadyRecorded = \App\Models\MedicalRecord::query()
                    ->where('family_member_id', $memberId)
                    ->whereBetween('visit_date', [$periodStart, $periodEnd])
                    ->where(function ($q) use ($sub) {
                        $q->whereHas('spmSubIndicators', fn($w) => $w->where('spm_sub_indicators.id', $sub->id))
                          ->orWhere('spm_service_type', $sub->code);
                    })
                    ->exists();

                $relevant[] = [
                    'id'             => $sub->id,
                    'code'           => $sub->code,
                    'name'           => $sub->name,
                    'indicator_name' => $indicator->name,
                    'already_recorded' => $alreadyRecorded,
                ];
            }
        }

        return response()->json([
            'data' => [
                'family_member_id' => $memberId,
                'member_name'      => $member->name,
                'relevant'         => count($relevant) > 0,
                'sub_indicators'   => $relevant,
            ],
        ]);
    }

    // ─── Record–SPM Link ────────────────────────────────────────────────

    /**
     * Link a medical record to one or more SPM sub-indicators (pivot).
     */
    public function recordLink(Request $request)
    {
        $request->validate([
            'medical_record_id'      => 'required|integer|exists:medical_records,id',
            'spm_sub_indicator_ids'  => 'required|array',
            'spm_sub_indicator_ids.*' => 'integer|exists:spm_sub_indicators,id',
        ]);

        $record = \App\Models\MedicalRecord::findOrFail($request->input('medical_record_id'));
        $ids    = $request->input('spm_sub_indicator_ids');

        // syncWithoutDetaching preserves existing links and adds new ones
        $record->spmSubIndicators()->syncWithoutDetaching($ids);

        return response()->json([
            'message' => 'SPM sub-indicators linked successfully.',
            'linked'  => count($ids),
        ]);
    }
}
