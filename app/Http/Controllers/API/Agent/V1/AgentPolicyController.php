<?php

namespace App\Http\Controllers\API\Agent\V1;

use App\Http\Controllers\Controller;
use App\Models\DevicePolicy;
use App\Models\ManagedDevice;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class AgentPolicyController extends Controller
{
    public function show(Request $request): JsonResponse
    {
        /** @var ManagedDevice $device */
        $device = $request->attributes->get('managed_device');

        $policy = DevicePolicy::query()
            ->where('is_active', true)
            ->where(function ($query) use ($device): void {
                $query->where('managed_device_id', $device->id)
                    ->orWhere(function ($siteQuery) use ($device): void {
                        $siteQuery->whereNull('managed_device_id')
                            ->where('managed_site_id', $device->managed_site_id);
                    });
            })
            ->orderByRaw('managed_device_id IS NULL')
            ->orderByDesc('version')
            ->first();

        return response()->json([
            'data' => $policy ? [
                'uuid' => $policy->uuid,
                'name' => $policy->name,
                'version' => $policy->version,
                'allowed_capabilities' => $policy->allowed_capabilities ?? [],
                'allowed_directories' => $policy->allowed_directories ?? [],
                'settings' => $policy->settings ?? [],
            ] : [
                'uuid' => null,
                'name' => 'safe-default',
                'version' => 1,
                'allowed_capabilities' => [
                    'heartbeat',
                    'inventory',
                    'metrics',
                    'screenshot_on_demand',
                ],
                'allowed_directories' => [],
                'settings' => [
                    'live_session_max_minutes' => config('command-center.live_session_max_minutes'),
                ],
            ],
        ]);
    }
}
