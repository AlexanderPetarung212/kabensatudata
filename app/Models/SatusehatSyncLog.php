<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class SatusehatSyncLog extends Model
{
    protected $fillable = [
        'type',
        'status',
        'total_fetched',
        'imported',
        'skipped_duplicate',
        'skipped_existing',
        'unmatched_patient',
        'errors',
        'error_message',
        'triggered_by',
        'started_at',
        'completed_at',
    ];

    protected $casts = [
        'started_at' => 'datetime',
        'completed_at' => 'datetime',
    ];

    public function triggeredByUser(): BelongsTo
    {
        return $this->belongsTo(User::class, 'triggered_by');
    }

    /**
     * Duration in human-readable format.
     */
    public function getDurationAttribute(): ?string
    {
        if (!$this->started_at || !$this->completed_at) {
            return null;
        }

        $seconds = $this->started_at->diffInSeconds($this->completed_at);

        if ($seconds < 60) {
            return "{$seconds} detik";
        }

        $minutes = floor($seconds / 60);
        $secs = $seconds % 60;
        return "{$minutes}m {$secs}s";
    }
}
