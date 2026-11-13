<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('managed_sites', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->string('name');
            $table->string('code', 50)->unique();
            $table->boolean('is_active')->default(true);
            $table->timestamps();
        });

        Schema::create('managed_site_user', function (Blueprint $table) {
            $table->foreignId('managed_site_id')->constrained()->cascadeOnDelete();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('assigned_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamps();

            $table->primary(['managed_site_id', 'user_id']);
        });

        Schema::create('managed_devices', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->foreignId('managed_site_id')->constrained()->restrictOnDelete();
            $table->string('platform', 20);
            $table->string('device_name');
            $table->string('hostname')->nullable();
            $table->string('os_name')->nullable();
            $table->string('os_version')->nullable();
            $table->string('architecture', 30)->nullable();
            $table->string('agent_version', 50)->nullable();
            $table->char('hardware_fingerprint_hash', 64)->nullable();
            $table->string('status', 20)->default('offline');
            $table->json('capabilities')->nullable();
            $table->json('permissions_state')->nullable();
            $table->json('metadata')->nullable();
            $table->timestamp('last_seen_at')->nullable();
            $table->string('last_ip', 45)->nullable();
            $table->timestamp('enrolled_at')->nullable();
            $table->timestamp('revoked_at')->nullable();
            $table->timestamps();

            $table->index(['managed_site_id', 'status']);
            $table->index(['managed_site_id', 'platform']);
            $table->index('hardware_fingerprint_hash');
            $table->index('last_seen_at');
        });

        Schema::create('device_enrollment_tokens', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->foreignId('managed_site_id')->constrained()->cascadeOnDelete();
            $table->char('token_hash', 64)->unique();
            $table->string('label')->nullable();
            $table->unsignedSmallInteger('max_uses')->default(1);
            $table->unsignedSmallInteger('used_count')->default(0);
            $table->foreignId('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamp('expires_at');
            $table->timestamp('last_used_at')->nullable();
            $table->json('metadata')->nullable();
            $table->timestamps();

            $table->index(['managed_site_id', 'expires_at']);
        });

        Schema::create('device_credentials', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->foreignId('managed_device_id')->constrained()->cascadeOnDelete();
            $table->string('name')->default('primary');
            $table->char('secret_hash', 64);
            $table->text('secret_encrypted');
            $table->timestamp('last_used_at')->nullable();
            $table->timestamp('expires_at')->nullable();
            $table->timestamp('revoked_at')->nullable();
            $table->timestamp('rotated_at')->nullable();
            $table->timestamps();

            $table->unique(['managed_device_id', 'name']);
            $table->index(['managed_device_id', 'revoked_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('device_credentials');
        Schema::dropIfExists('device_enrollment_tokens');
        Schema::dropIfExists('managed_devices');
        Schema::dropIfExists('managed_site_user');
        Schema::dropIfExists('managed_sites');
    }
};
