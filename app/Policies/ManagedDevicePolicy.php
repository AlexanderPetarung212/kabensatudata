<?php

namespace App\Policies;

use App\Models\ManagedDevice;
use App\Models\User;

class ManagedDevicePolicy
{
    public function viewAny(User $user): bool
    {
        return $user->can('command_center.view') && $user->can('device.view');
    }

    public function view(User $user, ManagedDevice $device): bool
    {
        return $user->can('device.view') && $this->belongsToUserSite($user, $device);
    }

    public function update(User $user, ManagedDevice $device): bool
    {
        return $user->can('device.manage_policy') && $this->belongsToUserSite($user, $device);
    }

    public function revoke(User $user, ManagedDevice $device): bool
    {
        return $user->can('device.revoke') && $this->belongsToUserSite($user, $device);
    }

    private function belongsToUserSite(User $user, ManagedDevice $device): bool
    {
        return $user->managedSites()
            ->where('managed_sites.id', $device->managed_site_id)
            ->exists();
    }
}
