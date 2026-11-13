<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tambah field verifikasi NIK ke family_members.
     * Backfill otomatis: member yang sudah punya ihs_number → status 'verified'.
     */
    public function up(): void
    {
        Schema::table('family_members', function (Blueprint $table) {
            $table->enum('nik_verified_status', ['verified', 'ihs_only', 'unverified'])
                  ->default('unverified')
                  ->after('ihs_number')
                  ->comment('Status verifikasi NIK: verified=cocok dengan SATUSEHAT, ihs_only=punya IHS tapi NIK belum dicocokkan, unverified=belum terverifikasi');

            $table->timestamp('nik_verified_at')
                  ->nullable()
                  ->after('nik_verified_status')
                  ->comment('Waktu NIK berhasil diverifikasi');

            $table->string('nik_verified_source', 50)
                  ->nullable()
                  ->after('nik_verified_at')
                  ->comment('Sumber verifikasi: satusehat | dukcapil | manual');
        });

        // Backfill otomatis: member yang sudah punya ihs_number = verified by satusehat
        DB::statement("
            UPDATE family_members
            SET
                nik_verified_status = 'verified',
                nik_verified_source = 'satusehat',
                nik_verified_at     = COALESCE(satusehat_synced_at, NOW())
            WHERE
                ihs_number IS NOT NULL
                AND ihs_number != ''
                AND nik IS NOT NULL
                AND nik != ''
        ");
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('family_members', function (Blueprint $table) {
            $table->dropColumn(['nik_verified_status', 'nik_verified_at', 'nik_verified_source']);
        });
    }
};
