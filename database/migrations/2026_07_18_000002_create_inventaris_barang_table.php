<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        // Pastikan tabel ruangan pakai InnoDB (required untuk FK)
        DB::statement('ALTER TABLE ruangan ENGINE = InnoDB');

        Schema::create('inventaris_barang', function (Blueprint $table) {
            $table->engine = 'InnoDB';
            $table->id();
            $table->foreignId('ruangan_id')->constrained('ruangan')->cascadeOnDelete();
            $table->string('nama');
            $table->string('kode_inventaris')->nullable();
            $table->string('merek')->nullable();
            $table->unsignedInteger('jumlah')->default(1);
            $table->enum('kondisi', ['baik', 'rusak_ringan', 'rusak_berat', 'tidak_aktif'])->default('baik');
            $table->year('tahun_perolehan')->nullable();
            $table->string('sumber_dana')->nullable();
            $table->string('foto_path')->nullable();
            $table->text('keterangan')->nullable();
            $table->timestamps();

            $table->index('ruangan_id');
            $table->index('kondisi');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('inventaris_barang');
    }
};
