<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class DeviceMetric extends Model
{
    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'recorded_at' => 'datetime',
            'cpu_percent' => 'float',
            'memory_percent' => 'float',
            'details' => 'array',
        ];
    }

    public function device(): BelongsTo
    {
        return $this->belongsTo(ManagedDevice::class, 'managed_device_id');
    }
}
