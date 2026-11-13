<?php

namespace App\Http\Controllers\API\CommandCenter\V1;

use App\Http\Controllers\Controller;
use App\Models\DeviceEnrollmentToken;
use App\Models\ManagedSite;
use App\Services\CommandCenter\CommandCenterAuditService;
use App\Services\CommandCenter\DeviceEnrollmentService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class EnrollmentController extends Controller
{
    public function __construct(
        private readonly DeviceEnrollmentService $enrollment,
        private readonly CommandCenterAuditService $audit,
    ) {
    }

    public function index(Request $request): JsonResponse
    {
        abort_unless($request->user()->can('device.enroll'), 403);

        $siteIds = $request->user()->hasRole('super_admin')
            ? ManagedSite::query()->select('id')
            : $request->user()->managedSites()->select('managed_sites.id');

        $tokens = DeviceEnrollmentToken::query()
            ->whereIn('managed_site_id', $siteIds)
            ->with('site:id,uuid,name')
            ->latest()
            ->limit(100)
            ->get();

        return response()->json([
            'data' => $tokens->map(fn (DeviceEnrollmentToken $token) => [
                'uuid' => $token->uuid,
                'site' => $token->site,
                'label' => $token->label,
                'max_uses' => $token->max_uses,
                'used_count' => $token->used_count,
                'expires_at' => $token->expires_at->toIso8601String(),
                'usable' => $token->isUsable(),
                'created_at' => $token->created_at->toIso8601String(),
            ]),
        ]);
    }

    public function store(Request $request): JsonResponse
    {
        abort_unless($request->user()->can('device.enroll'), 403);

        $data = $request->validate([
            'site_uuid' => ['required', 'uuid'],
            'label' => ['nullable', 'string', 'max:255'],
            'max_uses' => ['nullable', 'integer', 'min:1', 'max:100'],
        ]);

        $site = ManagedSite::query()->where('uuid', $data['site_uuid'])->firstOrFail();
        if (!$request->user()->hasRole('super_admin')) {
            abort_unless($request->user()->managedSites()->where('managed_sites.id', $site->id)->exists(), 403);
        }

        $created = $this->enrollment->createToken(
            $site,
            $request->user(),
            $data['label'] ?? null,
            $data['max_uses'] ?? 1,
        );

        $this->audit->record(
            'device.enrollment.created',
            'success',
            $request->user(),
            site: $site,
            metadata: ['enrollment_uuid' => $created['record']->uuid],
            request: $request,
        );

        return response()->json([
            'data' => [
                'uuid' => $created['record']->uuid,
                'enrollment_token' => $created['plain_token'],
                'expires_at' => $created['record']->expires_at->toIso8601String(),
                'max_uses' => $created['record']->max_uses,
            ],
        ], 201);
    }
}
