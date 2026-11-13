<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('ruangan', function (Blueprint $table) {
            $table->engine = 'InnoDB';
            $table->id();
            $table->uuid('uuid')->unique();
            $table->string('nama');
            $table->string('kode')->nullable();
            $table->string('lantai')->nullable();
            $table->text('deskripsi')->nullable();
            $table->string('foto_path')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('ruangan');
    }
};
