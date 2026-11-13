<?php

namespace Database\Seeders;

use App\Models\ManagedSite;
use App\Models\User;
use Illuminate\Database\Seeder;
use Spatie\Permission\Models\Permission;
use Spatie\Permission\Models\Role;
use Spatie\Permission\PermissionRegistrar;

class CommandCenterPermissionSeeder extends Seeder
{
    public function run(): void
    {
        app(PermissionRegistrar::class)->forgetCachedPermissions();

        $permissions = [
            'command_center.view',
            'device.view',
            'device.enroll',
            'device.revoke',
            'device.view_metrics',
            'device.view_processes',
            'device.capture_screenshot',
            'device.view_screen',
            'device.view_camera',
            'device.capture_audio',
            'device.browse_files',
            'device.download_files',
            'device.remote_input',
            'device.lock',
            'device.restart',
            'device.shutdown',
            'device.run_maintenance',
            'device.manage_policy',
            'artifact.download',
            'artifact.delete',
            'audit.view',
            'agent_release.manage',
        ];

        foreach ($permissions as $permission) {
            Permission::findOrCreate($permission, 'web');
        }

        $viewer = Role::findOrCreate('command_center_viewer', 'web');
        $viewer->syncPermissions([
            'command_center.view',
            'device.view',
            'device.view_metrics',
        ]);

        $operator = Role::findOrCreate('command_center_operator', 'web');
        $operator->syncPermissions([
            'command_center.view',
            'device.view',
            'device.view_metrics',
            'device.view_processes',
            'device.capture_screenshot',
            'device.view_screen',
            'device.lock',
            'device.restart',
            'device.run_maintenance',
            'artifact.download',
        ]);

        $securityAdmin = Role::findOrCreate('command_center_security_admin', 'web');
        $securityAdmin->syncPermissions($permissions);

        $auditor = Role::findOrCreate('command_center_auditor', 'web');
        $auditor->syncPermissions([
            'command_center.view',
            'device.view',
            'device.view_metrics',
            'audit.view',
            'artifact.download',
        ]);

        foreach (['admin', 'super_admin'] as $roleName) {
            Role::findOrCreate($roleName, 'web')->givePermissionTo($permissions);
        }

        $kepala = Role::findOrCreate('kepala_puskesmas', 'web');
        $kepala->givePermissionTo([
            'command_center.view',
            'device.view',
            'device.view_metrics',
            'device.view_processes',
            'device.capture_screenshot',
            'device.view_screen',
            'device.view_camera',
            'artifact.download',
            'audit.view',
        ]);

        $siteCode = config('command-center.default_site_code', 'PUSKESMAS-UTAMA');
        $site = ManagedSite::firstOrCreate(
            ['code' => $siteCode],
            ['name' => 'Puskesmas Utama', 'is_active' => true],
        );

        User::role(['admin', 'super_admin', 'kepala_puskesmas'])
            ->each(function (User $user) use ($site): void {
                $user->managedSites()->syncWithoutDetaching([
                    $site->id => ['assigned_by' => null],
                ]);
            });

        app(PermissionRegistrar::class)->forgetCachedPermissions();
    }
}
