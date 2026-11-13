<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('ruangan_pegawai', function (Blueprint $table) {
            $table->engine = 'InnoDB';
            $table->id();
            $table->foreignId('ruangan_id')->constrained('ruangan')->cascadeOnDelete();
            $table->foreignId('pegawai_id')->constrained('pegawai')->cascadeOnDelete();
            $table->enum('role', ['pj', 'anggota'])->default('anggota');
            $table->timestamps();

            $table->unique(['ruangan_id', 'pegawai_id']);
            $table->index('role');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('ruangan_pegawai');
    }
};
