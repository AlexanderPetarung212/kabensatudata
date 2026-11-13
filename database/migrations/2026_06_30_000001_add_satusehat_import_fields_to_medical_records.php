<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tambah field untuk tracking import SATUSEHAT ke medical_records.
     * Tidak mengubah data existing — hanya menambahkan kolom baru (nullable).
     */
    public function up(): void
    {
        Schema::table('medical_records', function (Blueprint $table) {
            $table->string('satusehat_encounter_id')->nullable()->unique()->after('uuid')
                ->comment('SATUSEHAT Encounter ID — untuk deduplication');
            $table->string('data_source')->default('manual')->after('satusehat_encounter_id')
                ->comment('Asal data: manual (input petugas) atau satusehat (import)');
            $table->timestamp('satusehat_imported_at')->nullable()->after('synced_at')
                ->comment('Waktu import dari SATUSEHAT');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('medical_records', function (Blueprint $table) {
            $table->dropColumn(['satusehat_encounter_id', 'data_source', 'satusehat_imported_at']);
        });
    }
};
