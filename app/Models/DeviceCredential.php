<?php

namespace App\Models;

use App\Models\Concerns\HasPublicUuid;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class DeviceCredential extends Model
{
    use HasPublicUuid;

    protected $guarded = [];

    protected $hidden = [
        'secret_hash',
        'secret_encrypted',
    ];

    protected function casts(): array
    {
        return [
            'last_used_at' => 'datetime',
            'expires_at' => 'datetime',
            'revoked_at' => 'datetime',
            'rotated_at' => 'datetime',
        ];
    }

    public function device(): BelongsTo
    {
        return $this->belongsTo(ManagedDevice::class, 'managed_device_id');
    }
}
