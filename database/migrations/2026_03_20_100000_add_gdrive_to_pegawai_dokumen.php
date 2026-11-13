<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('pegawai_dokumen', function (Blueprint $table) {
            $table->string('gdrive_file_id')->nullable()->after('file_path');
            $table->string('gdrive_url', 500)->nullable()->after('gdrive_file_id');
        });
    }

    public function down(): void
    {
        Schema::table('pegawai_dokumen', function (Blueprint $table) {
            $table->dropColumn(['gdrive_file_id', 'gdrive_url']);
        });
    }
};
