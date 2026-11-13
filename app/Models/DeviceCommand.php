<?php

namespace App\Models;

use App\Enums\DeviceCommandStatus;
use App\Models\Concerns\HasPublicUuid;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class DeviceCommand extends Model
{
    use HasPublicUuid;

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'payload' => 'array',
            'sensitive' => 'boolean',
            'status' => DeviceCommandStatus::class,
            'expires_at' => 'datetime',
            'authorized_at' => 'datetime',
            'queued_at' => 'datetime',
            'sent_at' => 'datetime',
            'delivered_at' => 'datetime',
            'started_at' => 'datetime',
            'completed_at' => 'datetime',
            'failed_at' => 'datetime',
            'cancelled_at' => 'datetime',
            'result_summary' => 'array',
        ];
    }

    public function device(): BelongsTo
    {
        return $this->belongsTo(ManagedDevice::class, 'managed_device_id');
    }

    public function requester(): BelongsTo
    {
        return $this->belongsTo(User::class, 'requested_by');
    }

    public function events(): HasMany
    {
        return $this->hasMany(DeviceCommandEvent::class);
    }

    public function artifacts(): HasMany
    {
        return $this->hasMany(DeviceArtifact::class);
    }
}
