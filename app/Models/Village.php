<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Str;

class Village extends Model
{
    protected $fillable = [
        'uuid',
        'name',
        'code',
        'sequence_number',
        'district',
        'regency',
        'province',
        'synced_at',
        'synced_by',
        'device_id',
    ];

    protected static function booted(): void
    {
        static::creating(function ($model) {
            if (empty($model->uuid)) {
                $model->uuid = Str::uuid()->toString();
            }
        });
    }

    public function buildings(): HasMany
    {
        return $this->hasMany(Building::class);
    }

    // Relasi ke model Family
    public function families()
    {
        return $this->hasManyThrough(Family::class, Building::class);
    }
}
