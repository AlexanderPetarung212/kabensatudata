<?php

namespace Database\Seeders;

use App\Models\Building;
use App\Models\Family;
use App\Models\Village;
use Illuminate\Database\Seeder;

/**
 * Creates placeholder Village, Building, and Family records to support
 * Quick Patient Registration for patients outside the coverage area.
 *
 * Safe to run multiple times (uses firstOrCreate).
 * Run via: php artisan db:seed --class=GeneralPatientSeeder
 */
class GeneralPatientSeeder extends Seeder
{
    public function run(): void
    {
        // 1. Village placeholder: "Pasien Umum"
        $village = Village::firstOrCreate(
            ['code' => '0000000000'],
            [
                'name'            => 'Pasien Umum',
                'sequence_number' => 99,
                'district'        => 'Luar Wilayah',
                'regency'         => 'Luar Wilayah',
                'province'        => 'Luar Wilayah',
            ]
        );

        $this->command->info("✅ Village: {$village->name} (ID: {$village->id})");

        // 2. Building placeholder: "Registrasi Umum"
        $building = Building::firstOrCreate(
            ['village_id' => $village->id, 'building_number' => 'REG-001'],
            [
                'description' => 'Registrasi Pasien Umum',
            ]
        );

        $this->command->info("✅ Building: {$building->building_number} (ID: {$building->id})");

        // 3. Family placeholder: "Keluarga Umum"
        $family = Family::firstOrCreate(
            ['building_id' => $building->id, 'family_number' => 'UMUM-001'],
            [
                'head_name' => 'Registrasi Umum',
            ]
        );

        $this->command->info("✅ Family: {$family->family_number} (ID: {$family->id})");

        $this->command->info('');
        $this->command->info('📋 Quick Patient Registration structure ready:');
        $this->command->info("   Village ID: {$village->id} (code: 0000000000)");
        $this->command->info("   Building ID: {$building->id} (REG-001)");
        $this->command->info("   Family ID: {$family->id} (UMUM-001)");
        $this->command->info('');
        $this->command->info('💡 Pasien baru dari APK akan masuk ke Family ID: ' . $family->id);
    }
}
