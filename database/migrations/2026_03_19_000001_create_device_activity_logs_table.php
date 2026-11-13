<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('device_activity_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->nullable()->constrained()->onDelete('set null');
            $table->string('device_id', 100)->index();
            $table->string('device_name', 100);
            $table->string('event_type', 50)->index(); // login, logout, screen_on, screen_off, navigate, app_usage, etc.
            $table->json('event_data')->nullable();     // JSON: {"screen":"MedicalHub"}, {"package":"com.whatsapp","duration_ms":120000}
            $table->bigInteger('client_timestamp');      // epoch millis from client
            $table->timestamp('server_timestamp')->useCurrent();
            $table->index(['device_id', 'client_timestamp']);
            $table->index(['user_id', 'server_timestamp']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('device_activity_logs');
    }
};
