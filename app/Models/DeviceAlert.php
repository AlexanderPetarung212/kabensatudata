<?php

namespace App\Models;

use App\Models\Concerns\HasPublicUuid;
use Illuminate\Database\Eloquent\Model;

class DeviceAlert extends Model
{
    use HasPublicUuid;

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'metadata' => 'array',
            'detected_at' => 'datetime',
            'resolved_at' => 'datetime',
            'acknowledged_at' => 'datetime',
        ];
    }
}
