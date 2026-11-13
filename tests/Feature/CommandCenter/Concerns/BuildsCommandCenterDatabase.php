<?php

namespace Tests\Feature\CommandCenter\Concerns;

use Illuminate\Support\Facades\Schema;

trait BuildsCommandCenterDatabase
{
    protected function setUpCommandCenterDatabase(): void
    {
        Schema::dropAllTables();

        foreach ([
            '0001_01_01_000000_create_users_table.php',
            '2025_03_15_124840_create_permission_tables.php',
            '2026_07_28_000001_create_managed_device_core_tables.php',
            '2026_07_28_000002_create_device_monitoring_tables.php',
            '2026_07_28_000003_create_device_command_tables.php',
            '2026_07_28_000004_create_device_policy_audit_release_tables.php',
        ] as $filename) {
            $migration = require database_path('migrations/'.$filename);
            $migration->up();
        }
    }
}
