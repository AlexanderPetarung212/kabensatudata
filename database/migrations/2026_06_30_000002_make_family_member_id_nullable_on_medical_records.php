<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Jadikan family_member_id nullable agar rekam medis dari SATUSEHAT
     * yang pasiennya belum ter-match tetap bisa disimpan.
     *
     * Data existing TIDAK terpengaruh — semua record manual sudah punya
     * family_member_id yang valid.
     */
    public function up(): void
    {
        Schema::table('medical_records', function (Blueprint $table) {
            $table->unsignedBigInteger('family_member_id')->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::table('medical_records', function (Blueprint $table) {
            $table->unsignedBigInteger('family_member_id')->nullable(false)->change();
        });
    }
};
