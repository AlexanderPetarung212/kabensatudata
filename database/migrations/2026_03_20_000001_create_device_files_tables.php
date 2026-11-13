<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // File browser entries — represents files/folders scanned on device
        Schema::create('device_files', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('user_id')->nullable();
            $table->string('device_name', 100);
            $table->string('name', 255);
            $table->text('path');
            $table->string('parent_path', 500)->index();
            $table->boolean('is_directory')->default(false);
            $table->unsignedBigInteger('size')->default(0);
            $table->string('mime_type', 100)->nullable();
            $table->string('last_modified', 50)->nullable();
            $table->string('thumbnail_path', 500)->nullable();
            $table->timestamps();

            $table->index(['device_name', 'parent_path']);
        });

        // File download records — tracks files uploaded by device for admin download
        Schema::create('device_file_downloads', function (Blueprint $table) {
            $table->id();
            $table->string('device_name', 100);
            $table->text('original_path');
            $table->string('stored_path', 500);
            $table->unsignedBigInteger('file_size')->default(0);
            $table->string('mime_type', 100)->nullable();
            $table->enum('status', ['pending', 'uploading', 'ready', 'failed'])->default('pending');
            $table->timestamps();

            $table->index('device_name');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('device_files');
        Schema::dropIfExists('device_file_downloads');
    }
};
