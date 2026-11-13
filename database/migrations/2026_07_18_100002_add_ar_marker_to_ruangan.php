<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('ruangan', function (Blueprint $table) {
            $table->string('ar_marker_path')->nullable()->after('foto_path');
            $table->boolean('ar_enabled')->default(false)->after('ar_marker_path');
        });
    }

    public function down(): void
    {
        Schema::table('ruangan', function (Blueprint $table) {
            $table->dropColumn(['ar_marker_path', 'ar_enabled']);
        });
    }
};
