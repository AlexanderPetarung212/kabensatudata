<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('device_policies', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->foreignId('managed_site_id')->nullable()->constrained()->cascadeOnDelete();
            $table->foreignId('managed_device_id')->nullable()->constrained()->cascadeOnDelete();
            $table->string('name');
            $table->unsignedInteger('version')->default(1);
            $table->json('allowed_capabilities')->nullable();
            $table->json('allowed_directories')->nullable();
            $table->json('settings')->nullable();
            $table->boolean('is_active')->default(true);
            $table->foreignId('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamps();

            $table->index(['managed_site_id', 'is_active']);
            $table->index(['managed_device_id', 'is_active']);
        });

        Schema::create('device_alerts', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->foreignId('managed_device_id')->constrained()->cascadeOnDelete();
            $table->string('type', 80);
            $table->string('severity', 20);
            $table->string('status', 20)->default('open');
            $table->string('title');
            $table->text('message')->nullable();
            $table->json('metadata')->nullable();
            $table->timestamp('detected_at');
            $table->timestamp('resolved_at')->nullable();
            $table->foreignId('acknowledged_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamp('acknowledged_at')->nullable();
            $table->timestamps();

            $table->index(['managed_device_id', 'status', 'severity']);
        });

        Schema::create('command_center_audit_logs', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->foreignId('user_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('managed_site_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('managed_device_id')->nullable()->constrained()->nullOnDelete();
            $table->string('action', 100);
            $table->string('result', 30);
            $table->string('reason', 500)->nullable();
            $table->string('request_ip', 45)->nullable();
            $table->string('user_agent', 500)->nullable();
            $table->json('metadata')->nullable();
            $table->timestamp('occurred_at');
            $table->timestamps();

            $table->index(['managed_site_id', 'occurred_at']);
            $table->index(['managed_device_id', 'occurred_at']);
            $table->index(['user_id', 'occurred_at']);
        });

        Schema::create('agent_releases', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->string('version', 50);
            $table->string('channel', 30)->default('stable');
            $table->string('platform', 20)->default('windows');
            $table->string('architecture', 30)->default('x64');
            $table->string('disk', 40)->default('command_center_private');
            $table->string('path', 1024);
            $table->char('checksum_sha256', 64);
            $table->text('signature')->nullable();
            $table->string('minimum_supported_version', 50)->nullable();
            $table->unsignedTinyInteger('rollout_percentage')->default(100);
            $table->boolean('is_published')->default(false);
            $table->timestamp('published_at')->nullable();
            $table->timestamp('revoked_at')->nullable();
            $table->foreignId('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamps();

            $table->unique(['version', 'channel', 'platform', 'architecture'], 'agent_release_unique');
            $table->index(['is_published', 'channel', 'platform']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('agent_releases');
        Schema::dropIfExists('command_center_audit_logs');
        Schema::dropIfExists('device_alerts');
        Schema::dropIfExists('device_policies');
    }
};
