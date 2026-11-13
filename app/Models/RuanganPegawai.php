<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Relations\Pivot;

class RuanganPegawai extends Pivot
{
    protected $table = 'ruangan_pegawai';

    protected $fillable = [
        'ruangan_id',
        'pegawai_id',
        'role',
    ];
}
