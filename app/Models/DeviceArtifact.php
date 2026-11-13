<?php

namespace App\Models;

use App\Models\Concerns\HasPublicUuid;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class DeviceArtifact extends Model
{
    use HasPublicUuid;

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'metadata' => 'array',
            'expires_at' => 'datetime',
            'deleted_at' => 'datetime',
        ];
    }

    public function device(): BelongsTo
    {
        return $this->belongsTo(ManagedDevice::class, 'managed_device_id');
    }

    public function command(): BelongsTo
    {
        return $this->belongsTo(DeviceCommand::class, 'device_command_id');
    }
}
