<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tambah field SATUSEHAT ke tabel family_members dan users.
     * Tidak mengubah data existing — hanya menambahkan kolom baru (nullable).
     */
    public function up(): void
    {
        // Tambah IHS Number ke family_members
        Schema::table('family_members', function (Blueprint $table) {
            $table->string('ihs_number')->nullable()->after('uuid')
                ->comment('SATUSEHAT Patient IHS Number');
            $table->timestamp('satusehat_synced_at')->nullable()->after('synced_at')
                ->comment('Terakhir kali data di-sync dari SATUSEHAT');
        });

        // Tambah NIK nakes dan IHS Number ke users
        Schema::table('users', function (Blueprint $table) {
            $table->string('nik_nakes', 16)->nullable()->after('email')
                ->comment('NIK Tenaga Kesehatan');
            $table->string('ihs_number')->nullable()->after('nik_nakes')
                ->comment('SATUSEHAT Practitioner IHS Number');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('family_members', function (Blueprint $table) {
            $table->dropColumn(['ihs_number', 'satusehat_synced_at']);
        });

        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['nik_nakes', 'ihs_number']);
        });
    }
};
