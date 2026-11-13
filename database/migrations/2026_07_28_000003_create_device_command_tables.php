<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('device_commands', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->foreignId('managed_device_id')->constrained()->cascadeOnDelete();
            $table->foreignId('requested_by')->nullable()->constrained('users')->nullOnDelete();
            $table->string('name', 80);
            $table->unsignedSmallInteger('version')->default(1);
            $table->string('executor', 20)->default('service');
            $table->json('payload')->nullable();
            $table->unsignedTinyInteger('priority')->default(5);
            $table->string('status', 20)->default('requested');
            $table->string('reason', 500)->nullable();
            $table->boolean('sensitive')->default(false);
            $table->string('idempotency_key', 100);
            $table->timestamp('expires_at');
            $table->timestamp('authorized_at')->nullable();
            $table->timestamp('queued_at')->nullable();
            $table->timestamp('sent_at')->nullable();
            $table->timestamp('delivered_at')->nullable();
            $table->timestamp('started_at')->nullable();
            $table->timestamp('completed_at')->nullable();
            $table->timestamp('failed_at')->nullable();
            $table->timestamp('cancelled_at')->nullable();
            $table->json('result_summary')->nullable();
            $table->string('error_code', 80)->nullable();
            $table->text('error_message')->nullable();
            $table->timestamps();

            $table->unique(['managed_device_id', 'idempotency_key'], 'device_command_idempotency_unique');
            $table->index(['managed_device_id', 'executor', 'status', 'priority'], 'device_command_dispatch_index');
            $table->index(['status', 'expires_at']);
        });

        Schema::create('device_command_events', function (Blueprint $table) {
            $table->id();
            $table->foreignId('device_command_id')->constrained()->cascadeOnDelete();
            $table->foreignId('actor_user_id')->nullable()->constrained('users')->nullOnDelete();
            $table->string('event', 30);
            $table->string('source', 20);
            $table->json('payload')->nullable();
            $table->timestamp('occurred_at');
            $table->timestamps();

            $table->index(['device_command_id', 'occurred_at']);
        });

        Schema::create('device_artifacts', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->foreignId('managed_device_id')->constrained()->cascadeOnDelete();
            $table->foreignId('device_command_id')->nullable()->constrained()->nullOnDelete();
            $table->string('type', 40);
            $table->string('disk', 40)->default('command_center_private');
            $table->string('path', 1024);
            $table->string('original_name')->nullable();
            $table->string('mime_type', 120)->nullable();
            $table->unsignedBigInteger('size_bytes')->default(0);
            $table->char('checksum_sha256', 64);
            $table->json('metadata')->nullable();
            $table->timestamp('expires_at')->nullable();
            $table->timestamp('deleted_at')->nullable();
            $table->timestamps();

            $table->index(['managed_device_id', 'type', 'created_at']);
            $table->index('expires_at');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('device_artifacts');
        Schema::dropIfExists('device_command_events');
        Schema::dropIfExists('device_commands');
    }
};
