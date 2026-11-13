<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class DeviceHeartbeat extends Model
{
    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'recorded_at' => 'datetime',
            'screen_locked' => 'boolean',
            'health' => 'array',
        ];
    }

    public function device(): BelongsTo
    {
        return $this->belongsTo(ManagedDevice::class, 'managed_device_id');
    }
}
