<?php

namespace App\Policies;

use App\Models\DeviceArtifact;
use App\Models\User;

class DeviceArtifactPolicy
{
    public function view(User $user, DeviceArtifact $artifact): bool
    {
        return $user->can('artifact.download')
            && $user->can('view', $artifact->device);
    }

    public function delete(User $user, DeviceArtifact $artifact): bool
    {
        return $user->can('artifact.delete')
            && $user->can('view', $artifact->device);
    }
}
