<?php

namespace App\Http\Controllers\CommandCenter;

use App\Http\Controllers\Controller;
use App\Models\ManagedSite;
use Illuminate\Contracts\View\View;
use Illuminate\Http\Request;

class CommandCenterDashboardController extends Controller
{
    public function index(Request $request): View
    {
        $this->authorize('viewAny', \App\Models\ManagedDevice::class);

        $sites = $request->user()->hasRole('super_admin')
            ? ManagedSite::query()->where('is_active', true)->orderBy('name')->get()
            : $request->user()->managedSites()->where('is_active', true)->orderBy('name')->get();

        return view('command-center.managed', [
            'sites' => $sites,
            'commandDefinitions' => collect(config('command-center.commands'))
                ->map(fn (array $definition, string $name): array => [
                    'name' => $name,
                    'permission' => $definition['permission'],
                    'capability' => $definition['capability'] ?? null,
                    'sensitive' => (bool) ($definition['sensitive'] ?? false),
                    'allowed' => $request->user()->can($definition['permission']),
                ])
                ->values(),
        ]);
    }
}
