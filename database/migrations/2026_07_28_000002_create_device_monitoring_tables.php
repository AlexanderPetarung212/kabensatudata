<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('device_heartbeats', function (Blueprint $table) {
            $table->id();
            $table->foreignId('managed_device_id')->constrained()->cascadeOnDelete();
            $table->timestamp('recorded_at');
            $table->unsignedBigInteger('uptime_seconds')->nullable();
            $table->string('user_session')->nullable();
            $table->boolean('screen_locked')->nullable();
            $table->string('agent_version', 50)->nullable();
            $table->string('local_ip', 45)->nullable();
            $table->json('health')->nullable();
            $table->timestamps();

            $table->index(['managed_device_id', 'recorded_at']);
        });

        Schema::create('device_metrics', function (Blueprint $table) {
            $table->id();
            $table->foreignId('managed_device_id')->constrained()->cascadeOnDelete();
            $table->timestamp('recorded_at');
            $table->decimal('cpu_percent', 5, 2)->nullable();
            $table->decimal('memory_percent', 5, 2)->nullable();
            $table->unsignedBigInteger('memory_used_bytes')->nullable();
            $table->unsignedBigInteger('memory_total_bytes')->nullable();
            $table->unsignedBigInteger('disk_used_bytes')->nullable();
            $table->unsignedBigInteger('disk_total_bytes')->nullable();
            $table->unsignedBigInteger('network_rx_bytes')->nullable();
            $table->unsignedBigInteger('network_tx_bytes')->nullable();
            $table->json('details')->nullable();
            $table->timestamps();

            $table->index(['managed_device_id', 'recorded_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('device_metrics');
        Schema::dropIfExists('device_heartbeats');
    }
};
