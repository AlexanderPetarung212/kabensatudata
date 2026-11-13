<?php

namespace App\Providers;

use App\Models\User;
use App\Models\DeviceArtifact;
use App\Models\ManagedDevice;
use App\Policies\DeviceArtifactPolicy;
use App\Policies\ManagedDevicePolicy;
use App\Policies\UserPolicy;
use Illuminate\Foundation\Support\Providers\AuthServiceProvider as ServiceProvider;
use Illuminate\Support\Facades\Gate;

class AuthServiceProvider extends ServiceProvider
{
    /**
     * The model to policy mappings for the application.
     *
     * @var array<class-string, class-string>
     */
    protected $policies = [
        User::class => UserPolicy::class,
        ManagedDevice::class => ManagedDevicePolicy::class,
        DeviceArtifact::class => DeviceArtifactPolicy::class,
    ];

    /**
     * Register any authentication / authorization services.
     */
    public function boot(): void
    {
        // Super Admin bypass all policies
        Gate::before(function (User $user, string $ability) {
            return $user->hasRole('super_admin') ? true : null;
        });
    }
}
