<?php

namespace App\Http\Controllers\API\CommandCenter\V1;

use App\Enums\ManagedDeviceStatus;
use App\Http\Controllers\Controller;
use App\Models\ManagedDevice;
use App\Services\CommandCenter\CommandCenterAuditService;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class ManagedDeviceController extends Controller
{
    public function __construct(
        private readonly CommandCenterAuditService $audit,
    ) {
    }

    public function index(Request $request): JsonResponse
    {
        $this->authorize('viewAny', ManagedDevice::class);

        $query = $this->visibleQuery($request)
            ->with('site')
            ->withCount([
                'alerts as open_alerts_count' => fn (Builder $query) => $query->where('status', 'open'),
            ]);

        if ($request->filled('site_uuid')) {
            $query->whereHas('site', fn (Builder $site) => $site->where('uuid', $request->string('site_uuid')));
        }

        if ($request->filled('platform')) {
            $query->where('platform', $request->string('platform'));
        }

        if ($request->filled('search')) {
            $search = '%'.str_replace(['%', '_'], ['\\%', '\\_'], $request->string('search')).'%';
            $query->where(function (Builder $device) use ($search): void {
                $device->where('device_name', 'like', $search)
                    ->orWhere('hostname', 'like', $search);
            });
        }

        $devices = $query->orderByDesc('last_seen_at')->limit(500)->get();

        return response()->json([
            'data' => $devices->map(fn (ManagedDevice $device) => $this->serialize($device)),
            'server_time' => now()->toIso8601String(),
        ]);
    }

    public function show(Request $request, ManagedDevice $device): JsonResponse
    {
        $this->authorize('view', $device);

        $device->load([
            'site',
            'heartbeats' => fn ($query) => $query->latest('recorded_at')->limit(1),
            'metrics' => fn ($query) => $query->latest('recorded_at')->limit(1),
            'alerts' => fn ($query) => $query->where('status', 'open')->latest('detected_at')->limit(20),
        ]);
        $device->loadCount([
            'alerts as open_alerts_count' => fn (Builder $query) => $query->where('status', 'open'),
        ]);

        return response()->json([
            'data' => [
                ...$this->serialize($device),
                'latest_heartbeat' => $device->heartbeats->first(),
                'latest_metric' => $device->metrics->first(),
                'alerts' => $device->alerts,
                'metadata' => $device->metadata ?? (object) [],
            ],
        ]);
    }

    public function metrics(Request $request, ManagedDevice $device): JsonResponse
    {
        $this->authorize('view', $device);
        abort_unless($request->user()->can('device.view_metrics'), 403);

        $limit = min(max((int) $request->integer('limit', 100), 1), 500);

        return response()->json([
            'data' => $device->metrics()
                ->latest('recorded_at')
                ->limit($limit)
                ->get()
                ->reverse()
                ->values(),
        ]);
    }

    public function revoke(Request $request, ManagedDevice $device): JsonResponse
    {
        $this->authorize('revoke', $device);

        $device->credentials()->whereNull('revoked_at')->update(['revoked_at' => now()]);
        $device->update([
            'status' => ManagedDeviceStatus::Revoked,
            'revoked_at' => now(),
        ]);

        $this->audit->record(
            'device.revoked',
            'success',
            $request->user(),
            $device,
            metadata: ['device_uuid' => $device->uuid],
            request: $request,
        );

        return response()->json([
            'data' => [
                'uuid' => $device->uuid,
                'status' => ManagedDeviceStatus::Revoked->value,
            ],
        ]);
    }

    private function visibleQuery(Request $request): Builder
    {
        $query = ManagedDevice::query()->whereNull('revoked_at');

        if (!$request->user()->hasRole('super_admin')) {
            $query->whereIn('managed_site_id', $request->user()->managedSites()->select('managed_sites.id'));
        }

        return $query;
    }

    private function serialize(ManagedDevice $device): array
    {
        $offlineAt = now()->subSeconds(config('command-center.agent.offline_after_seconds', 180));
        $status = !$device->last_seen_at || $device->last_seen_at->lt($offlineAt)
            ? ManagedDeviceStatus::Offline->value
            : $device->status->value;

        return [
            'uuid' => $device->uuid,
            'site' => [
                'uuid' => $device->site->uuid,
                'name' => $device->site->name,
            ],
            'platform' => $device->platform->value,
            'device_name' => $device->device_name,
            'hostname' => $device->hostname,
            'os_name' => $device->os_name,
            'os_version' => $device->os_version,
            'architecture' => $device->architecture,
            'agent_version' => $device->agent_version,
            'status' => $status,
            'last_seen_at' => $device->last_seen_at?->toIso8601String(),
            'last_ip' => $device->last_ip,
            'capabilities' => $device->capabilities ?? [],
            'permissions_state' => $device->permissions_state ?? [],
            'open_alerts_count' => (int) ($device->open_alerts_count ?? 0),
        ];
    }
}
