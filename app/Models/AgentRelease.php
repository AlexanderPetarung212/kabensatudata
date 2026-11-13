<?php

namespace App\Models;

use App\Models\Concerns\HasPublicUuid;
use Illuminate\Database\Eloquent\Model;

class AgentRelease extends Model
{
    use HasPublicUuid;

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'is_published' => 'boolean',
            'published_at' => 'datetime',
            'revoked_at' => 'datetime',
        ];
    }
}
