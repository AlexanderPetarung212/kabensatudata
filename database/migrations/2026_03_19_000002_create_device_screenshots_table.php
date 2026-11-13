<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('device_screenshots', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('user_id')->nullable();
            $table->string('device_id', 100);
            $table->string('device_name', 100);
            $table->string('file_path');
            $table->string('thumbnail_path')->nullable();
            $table->unsignedBigInteger('file_size')->default(0);
            $table->timestamp('captured_at');
            $table->timestamps();

            $table->index(['device_name', 'captured_at']);
            $table->index('device_id');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('device_screenshots');
    }
};
