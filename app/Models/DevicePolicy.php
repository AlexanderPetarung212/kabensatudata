<?php

namespace App\Models;

use App\Models\Concerns\HasPublicUuid;
use Illuminate\Database\Eloquent\Model;

class DevicePolicy extends Model
{
    use HasPublicUuid;

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'allowed_capabilities' => 'array',
            'allowed_directories' => 'array',
            'settings' => 'array',
            'is_active' => 'boolean',
        ];
    }
}
