<?php

namespace Tests\Feature\CommandCenter;

use App\Models\ManagedSite;
use App\Models\User;
use App\Services\CommandCenter\DeviceAuthenticationService;
use App\Services\CommandCenter\DeviceAlertService;
use Database\Seeders\CommandCenterPermissionSeeder;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Tests\Feature\CommandCenter\Concerns\BuildsCommandCenterDatabase;
use Tests\TestCase;

class ManagedDeviceFlowTest extends TestCase
{
    use BuildsCommandCenterDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->setUpCommandCenterDatabase();
    }

    public function test_admin_can_enroll_agent_and_complete_audited_command_lifecycle(): void
    {
        config()->set('command-center.agent.require_request_signature', false);
        $this->seed(CommandCenterPermissionSeeder::class);

        $admin = User::factory()->create();
        $admin->assignRole('command_center_security_admin');
        $site = ManagedSite::query()->firstOrFail();
        $admin->managedSites()->attach($site);

        $enrollment = $this->actingAs($admin)
            ->postJson('/command-center/api/v1/enrollments', [
                'site_uuid' => $site->uuid,
                'label' => 'Pilot Farmasi',
                'max_uses' => 1,
            ])
            ->assertCreated()
            ->json('data.enrollment_token');

        $agentToken = $this->postJson('/api/agent/v1/enroll', [
            'enrollment_token' => $enrollment,
            'platform' => 'windows',
            'device_name' => 'Komputer Farmasi',
            'hostname' => 'FARMASI-01',
            'os_name' => 'Windows',
            'os_version' => '11',
            'architecture' => 'x64',
            'agent_version' => '0.1.0',
            'hardware_fingerprint' => 'pilot-fingerprint-1234567890',
            'capabilities' => ['heartbeat', 'metrics', 'screenshot_on_demand'],
        ])
            ->assertCreated()
            ->json('data.bearer_token');

        $deviceUuid = $this->withToken($agentToken)
            ->postJson('/api/agent/v1/heartbeat', [
                'uptime_seconds' => 120,
                'user_session' => 'PUSKESMAS\\farmasi',
                'screen_locked' => false,
                'agent_version' => '0.1.0',
                'metrics' => [
                    'cpu_percent' => 12.5,
                    'memory_percent' => 45.3,
                ],
            ])
            ->assertOk()
            ->json('data.status');

        $this->assertSame('online', $deviceUuid);

        $device = \App\Models\ManagedDevice::query()->firstOrFail();

        $commandUuid = $this->actingAs($admin)
            ->postJson("/command-center/api/v1/devices/{$device->uuid}/commands", [
                'name' => 'CAPTURE_SCREENSHOT',
                'payload' => ['monitor' => 0],
                'reason' => 'Verifikasi layar komputer pilot',
                'idempotency_key' => 'pilot-command-1',
            ])
            ->assertCreated()
            ->assertJsonPath('data.status', 'queued')
            ->json('data.uuid');

        $this->withToken($agentToken)
            ->getJson('/api/agent/v1/commands/next?executor=companion')
            ->assertOk()
            ->assertJsonPath('data.uuid', $commandUuid)
            ->assertJsonPath('data.name', 'CAPTURE_SCREENSHOT');

        // A response that is lost before the delivered ACK must be safely redeliverable.
        $this->withToken($agentToken)
            ->getJson('/api/agent/v1/commands/next?executor=companion')
            ->assertOk()
            ->assertJsonPath('data.uuid', $commandUuid);

        $this->withToken($agentToken)
            ->postJson("/api/agent/v1/commands/{$commandUuid}/delivered")
            ->assertOk()
            ->assertJsonPath('data.status', 'delivered');

        $this->withToken($agentToken)
            ->postJson("/api/agent/v1/commands/{$commandUuid}/started")
            ->assertOk()
            ->assertJsonPath('data.status', 'executing');

        $this->withToken($agentToken)
            ->postJson("/api/agent/v1/commands/{$commandUuid}/completed", [
                'result' => ['captured' => true],
            ])
            ->assertOk()
            ->assertJsonPath('data.status', 'completed');

        $this->assertDatabaseHas('device_commands', [
            'uuid' => $commandUuid,
            'status' => 'completed',
        ]);
        $this->assertDatabaseHas('command_center_audit_logs', [
            'action' => 'device.command.requested',
            'result' => 'queued',
        ]);

        $this->actingAs($admin)
            ->postJson("/command-center/api/v1/devices/{$device->uuid}/commands", [
                'name' => 'CAMERA_START',
                'reason' => 'Capability check',
            ])
            ->assertUnprocessable()
            ->assertJsonValidationErrors('name');
    }

    public function test_enrollment_token_cannot_be_reused(): void
    {
        config()->set('command-center.agent.require_request_signature', false);
        $this->seed(CommandCenterPermissionSeeder::class);

        $admin = User::factory()->create();
        $admin->assignRole('command_center_security_admin');
        $site = ManagedSite::query()->firstOrFail();
        $admin->managedSites()->attach($site);

        $token = $this->actingAs($admin)
            ->postJson('/command-center/api/v1/enrollments', [
                'site_uuid' => $site->uuid,
                'max_uses' => 1,
            ])
            ->assertCreated()
            ->json('data.enrollment_token');

        $payload = [
            'enrollment_token' => $token,
            'platform' => 'windows',
            'device_name' => 'Pilot 01',
            'hardware_fingerprint' => 'fingerprint-111111111111',
        ];

        $this->postJson('/api/agent/v1/enroll', $payload)->assertCreated();
        $this->postJson('/api/agent/v1/enroll', [
            ...$payload,
            'device_name' => 'Pilot 02',
            'hardware_fingerprint' => 'fingerprint-222222222222',
        ])->assertUnprocessable();
    }

    public function test_legacy_secret_route_is_disabled_by_default(): void
    {
        config()->set('command-center.legacy_secret_url.enabled', false);

        $this->get('/cc/anything')->assertNotFound();
    }

    public function test_authenticated_dashboard_is_no_store_and_scoped_by_permission(): void
    {
        $this->seed(CommandCenterPermissionSeeder::class);
        $site = ManagedSite::query()->firstOrFail();
        $viewer = User::factory()->create();
        $viewer->assignRole('command_center_viewer');
        $viewer->managedSites()->attach($site);

        $dashboardResponse = $this->actingAs($viewer)
            ->get('/command-center')
            ->assertOk()
            ->assertSee('KabenSatuData Command Center');
        $this->assertStringContainsString('no-store', $dashboardResponse->headers->get('Cache-Control'));

        $unauthorized = User::factory()->create();
        $this->actingAs($unauthorized)
            ->get('/command-center')
            ->assertForbidden();
    }

    public function test_agent_artifact_is_private_and_download_requires_permission(): void
    {
        config()->set('command-center.agent.require_request_signature', false);
        Storage::fake('command_center_private');
        $this->seed(CommandCenterPermissionSeeder::class);

        $admin = User::factory()->create();
        $admin->assignRole('command_center_security_admin');
        $site = ManagedSite::query()->firstOrFail();
        $admin->managedSites()->attach($site);
        $device = \App\Models\ManagedDevice::create([
            'managed_site_id' => $site->id,
            'platform' => 'windows',
            'device_name' => 'Artifact Pilot',
            'status' => 'online',
            'enrolled_at' => now(),
        ]);
        $bearer = app(DeviceAuthenticationService::class)->issueCredential($device);

        $artifactUuid = $this->withToken($bearer)
            ->post('/api/agent/v1/artifacts', [
                'type' => 'screenshot',
                'file' => UploadedFile::fake()->create('screen.jpg', 24, 'image/jpeg'),
            ], ['Accept' => 'application/json'])
            ->assertCreated()
            ->json('data.uuid');

        $downloadUrl = $this->actingAs($admin)
            ->getJson("/command-center/api/v1/devices/{$device->uuid}/artifacts")
            ->assertOk()
            ->assertJsonPath('data.0.uuid', $artifactUuid)
            ->json('data.0.download_url');

        $downloadResponse = $this->actingAs($admin)
            ->get($downloadUrl)
            ->assertOk();
        $this->assertStringContainsString('no-store', $downloadResponse->headers->get('Cache-Control'));
        $this->assertDatabaseHas('command_center_audit_logs', [
            'action' => 'device.artifact.downloaded',
            'result' => 'success',
            'managed_device_id' => $device->id,
        ]);
    }

    public function test_metric_alert_is_opened_and_resolved_automatically(): void
    {
        $this->seed(CommandCenterPermissionSeeder::class);
        $site = ManagedSite::query()->firstOrFail();
        $device = \App\Models\ManagedDevice::create([
            'managed_site_id' => $site->id,
            'platform' => 'windows',
            'device_name' => 'Alert Pilot',
            'status' => 'online',
            'enrolled_at' => now(),
        ]);
        $alerts = app(DeviceAlertService::class);

        $alerts->evaluateMetrics($device, ['cpu_percent' => 98]);
        $this->assertDatabaseHas('device_alerts', [
            'managed_device_id' => $device->id,
            'type' => 'cpu_high',
            'status' => 'open',
            'severity' => 'critical',
        ]);

        $alerts->evaluateMetrics($device, ['cpu_percent' => 12]);
        $this->assertDatabaseHas('device_alerts', [
            'managed_device_id' => $device->id,
            'type' => 'cpu_high',
            'status' => 'resolved',
        ]);
    }
}
