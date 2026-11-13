<?php

namespace App\Models;

use App\Models\Concerns\HasPublicUuid;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;

class ManagedSite extends Model
{
    use HasPublicUuid;

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'is_active' => 'boolean',
        ];
    }

    public function devices(): HasMany
    {
        return $this->hasMany(ManagedDevice::class);
    }

    public function users(): BelongsToMany
    {
        return $this->belongsToMany(User::class, 'managed_site_user')
            ->withPivot('assigned_by')
            ->withTimestamps();
    }
}
