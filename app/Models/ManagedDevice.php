<?php

namespace App\Models;

use App\Enums\ManagedDevicePlatform;
use App\Enums\ManagedDeviceStatus;
use App\Models\Concerns\HasPublicUuid;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\HasOne;

class ManagedDevice extends Model
{
    use HasPublicUuid;

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'platform' => ManagedDevicePlatform::class,
            'status' => ManagedDeviceStatus::class,
            'capabilities' => 'array',
            'permissions_state' => 'array',
            'metadata' => 'array',
            'last_seen_at' => 'datetime',
            'enrolled_at' => 'datetime',
            'revoked_at' => 'datetime',
        ];
    }

    public function site(): BelongsTo
    {
        return $this->belongsTo(ManagedSite::class, 'managed_site_id');
    }

    public function credentials(): HasMany
    {
        return $this->hasMany(DeviceCredential::class);
    }

    public function activeCredential(): HasOne
    {
        return $this->hasOne(DeviceCredential::class)
            ->whereNull('revoked_at')
            ->latestOfMany();
    }

    public function heartbeats(): HasMany
    {
        return $this->hasMany(DeviceHeartbeat::class);
    }

    public function metrics(): HasMany
    {
        return $this->hasMany(DeviceMetric::class);
    }

    public function commands(): HasMany
    {
        return $this->hasMany(DeviceCommand::class);
    }

    public function artifacts(): HasMany
    {
        return $this->hasMany(DeviceArtifact::class);
    }

    public function policies(): HasMany
    {
        return $this->hasMany(DevicePolicy::class);
    }

    public function alerts(): HasMany
    {
        return $this->hasMany(DeviceAlert::class);
    }
}
