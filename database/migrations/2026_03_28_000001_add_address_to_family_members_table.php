<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Add free-text address field to family_members table.
     * Used primarily for Quick Patient Registration where the patient
     * doesn't belong to the regular Village→Building→Family hierarchy.
     */
    public function up(): void
    {
        Schema::table('family_members', function (Blueprint $table) {
            $table->text('address')->nullable()->after('occupation');
        });
    }

    public function down(): void
    {
        Schema::table('family_members', function (Blueprint $table) {
            $table->dropColumn('address');
        });
    }
};
