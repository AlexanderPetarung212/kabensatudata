<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('inventaris_barang', function (Blueprint $table) {
            $table->float('ar_pos_x')->nullable()->after('keterangan');
            $table->float('ar_pos_y')->nullable()->after('ar_pos_x');
            $table->float('ar_pos_z')->nullable()->after('ar_pos_y');
            $table->boolean('ar_positioned')->default(false)->after('ar_pos_z');
        });
    }

    public function down(): void
    {
        Schema::table('inventaris_barang', function (Blueprint $table) {
            $table->dropColumn(['ar_pos_x', 'ar_pos_y', 'ar_pos_z', 'ar_positioned']);
        });
    }
};
