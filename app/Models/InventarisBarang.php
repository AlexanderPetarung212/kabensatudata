<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class InventarisBarang extends Model
{
    use HasFactory;

    protected $table = 'inventaris_barang';

    protected $fillable = [
        'ruangan_id',
        'nama',
        'kode_inventaris',
        'merek',
        'jumlah',
        'kondisi',
        'tahun_perolehan',
        'sumber_dana',
        'foto_path',
        'keterangan',
        'ar_pos_x',
        'ar_pos_y',
        'ar_pos_z',
        'ar_positioned',
    ];

    protected $casts = [
        'jumlah'           => 'integer',
        'tahun_perolehan'  => 'integer',
        'ar_pos_x'         => 'float',
        'ar_pos_y'         => 'float',
        'ar_pos_z'         => 'float',
        'ar_positioned'    => 'boolean',
    ];

    // ─── Relationships ───────────────────────────────────────────────

    public function ruangan(): BelongsTo
    {
        return $this->belongsTo(Ruangan::class);
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    public function getKondisiLabelAttribute(): string
    {
        return match ($this->kondisi) {
            'baik'         => 'Baik',
            'rusak_ringan' => 'Rusak Ringan',
            'rusak_berat'  => 'Rusak Berat',
            'tidak_aktif'  => 'Tidak Aktif',
            default        => $this->kondisi,
        };
    }

    public function getKondisiColorAttribute(): string
    {
        return match ($this->kondisi) {
            'baik'         => 'emerald',
            'rusak_ringan' => 'amber',
            'rusak_berat'  => 'red',
            'tidak_aktif'  => 'slate',
            default        => 'slate',
        };
    }
}
