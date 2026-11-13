<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Tabel untuk mencatat riwayat setiap sync SATUSEHAT.
     */
    public function up(): void
    {
        Schema::create('satusehat_sync_logs', function (Blueprint $table) {
            $table->id();
            $table->string('type')->default('manual')->comment('cron atau manual');
            $table->string('status')->default('running')->comment('running, completed, failed');
            $table->integer('total_fetched')->default(0);
            $table->integer('imported')->default(0);
            $table->integer('skipped_duplicate')->default(0);
            $table->integer('skipped_existing')->default(0);
            $table->integer('unmatched_patient')->default(0);
            $table->integer('errors')->default(0);
            $table->text('error_message')->nullable();
            $table->foreignId('triggered_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamp('started_at')->useCurrent();
            $table->timestamp('completed_at')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('satusehat_sync_logs');
    }
};
