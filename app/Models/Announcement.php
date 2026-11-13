<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class Announcement extends Model
{
    use HasFactory;

    protected $fillable = [
        'title',
        'message',
        'type',
        'is_active',
    ];

    protected $casts = [
        'is_active' => 'boolean',
    ];

    /**
     * Scope: only the active announcement.
     */
    public function scopeActive($query)
    {
        return $query->where('is_active', true);
    }

    /**
     * Deactivate all other announcements and activate this one.
     */
    public function activate(): void
    {
        static::where('id', '!=', $this->id)->update(['is_active' => false]);
        $this->update(['is_active' => true]);
    }

    /**
     * Deactivate this announcement.
     */
    public function deactivate(): void
    {
        $this->update(['is_active' => false]);
    }
}
