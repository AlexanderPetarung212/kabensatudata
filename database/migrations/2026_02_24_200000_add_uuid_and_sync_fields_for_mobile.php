<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

return new class extends Migration
{
    /**
     * Run the migrations.
     * Adds UUID and sync tracking fields to tables that need mobile offline sync.
     */
    public function up(): void
    {
        $tables = ['villages', 'buildings', 'families', 'family_members', 'medical_records'];

        foreach ($tables as $table) {
            Schema::table($table, function (Blueprint $blueprint) use ($table) {
                $blueprint->uuid('uuid')->nullable()->unique()->after('id');
                $blueprint->timestamp('synced_at')->nullable()->after('updated_at');
                $blueprint->unsignedBigInteger('synced_by')->nullable()->after('synced_at');
                $blueprint->string('device_id', 100)->nullable()->after('synced_by');

                $blueprint->foreign('synced_by')
                    ->references('id')
                    ->on('users')
                    ->nullOnDelete();

                $blueprint->index('synced_at');
            });
        }

        // Backfill existing records with UUIDs
        foreach ($tables as $table) {
            $records = \DB::table($table)->whereNull('uuid')->get();
            foreach ($records as $record) {
                \DB::table($table)->where('id', $record->id)->update([
                    'uuid' => Str::uuid()->toString(),
                ]);
            }
        }

        // Make uuid NOT NULL after backfill
        foreach ($tables as $table) {
            Schema::table($table, function (Blueprint $blueprint) {
                $blueprint->uuid('uuid')->nullable(false)->change();
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        $tables = ['villages', 'buildings', 'families', 'family_members', 'medical_records'];

        foreach ($tables as $table) {
            Schema::table($table, function (Blueprint $blueprint) use ($table) {
                $blueprint->dropForeign([$table . '_synced_by_foreign'] ?? []);
                $blueprint->dropIndex([$table . '_synced_at_index'] ?? []);
                $blueprint->dropColumn(['uuid', 'synced_at', 'synced_by', 'device_id']);
            });
        }
    }
};
