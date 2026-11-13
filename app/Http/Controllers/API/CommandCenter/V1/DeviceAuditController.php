<?php

namespace App\Http\Controllers\API\CommandCenter\V1;

use App\Http\Controllers\Controller;
use App\Models\CommandCenterAuditLog;
use App\Models\ManagedSite;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class DeviceAuditController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        abort_unless($request->user()->can('audit.view'), 403);

        $siteIds = $request->user()->hasRole('super_admin')
            ? ManagedSite::query()->select('id')
            : $request->user()->managedSites()->select('managed_sites.id');

        $logs = CommandCenterAuditLog::query()
            ->whereIn('managed_site_id', $siteIds)
            ->with([
                'user:id,name,email',
            ])
            ->latest('occurred_at')
            ->limit(min(max($request->integer('limit', 100), 1), 500))
            ->get();

        return response()->json(['data' => $logs]);
    }
}
