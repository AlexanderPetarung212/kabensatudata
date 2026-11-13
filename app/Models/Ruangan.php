<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Str;

class Ruangan extends Model
{
    use HasFactory;

    protected $table = 'ruangan';

    protected $fillable = [
        'uuid',
        'nama',
        'kode',
        'lantai',
        'deskripsi',
        'foto_path',
        'ar_marker_path',
        'ar_enabled',
    ];

    protected static function boot()
    {
        parent::boot();

        static::creating(function ($model) {
            if (empty($model->uuid)) {
                $model->uuid = (string) Str::uuid();
            }
        });
    }

    // ─── Relationships ───────────────────────────────────────────────

    public function barang(): HasMany
    {
        return $this->hasMany(InventarisBarang::class);
    }

    /**
     * Semua pegawai yang bertugas di ruangan ini (PJ + anggota).
     */
    public function pegawai(): BelongsToMany
    {
        return $this->belongsToMany(Pegawai::class, 'ruangan_pegawai')
            ->withPivot('role')
            ->withTimestamps();
    }

    /**
     * Penanggung Jawab ruangan (hanya 1 orang).
     */
    public function penanggungJawab(): BelongsToMany
    {
        return $this->belongsToMany(Pegawai::class, 'ruangan_pegawai')
            ->withPivot('role')
            ->wherePivot('role', 'pj');
    }

    /**
     * Pegawai anggota (selain PJ).
     */
    public function pegawaiAnggota(): BelongsToMany
    {
        return $this->belongsToMany(Pegawai::class, 'ruangan_pegawai')
            ->withPivot('role')
            ->wherePivot('role', 'anggota');
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    public function getPublicUrl(): string
    {
        return url('/inventaris/' . $this->uuid);
    }

    public function getTotalBarang(): int
    {
        return $this->barang()->sum('jumlah');
    }

    public function getKondisiSummary(): array
    {
        $items = $this->barang()
            ->selectRaw("kondisi, SUM(jumlah) as total")
            ->groupBy('kondisi')
            ->pluck('total', 'kondisi')
            ->toArray();

        return [
            'baik'         => $items['baik'] ?? 0,
            'rusak_ringan' => $items['rusak_ringan'] ?? 0,
            'rusak_berat'  => $items['rusak_berat'] ?? 0,
            'tidak_aktif'  => $items['tidak_aktif'] ?? 0,
        ];
    }
}
