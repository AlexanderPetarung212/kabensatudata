<?php

namespace Tests\Feature\CommandCenter;

use App\Enums\ManagedDevicePlatform;
use App\Enums\ManagedDeviceStatus;
use App\Models\ManagedDevice;
use App\Models\ManagedSite;
use App\Services\CommandCenter\DeviceAuthenticationService;
use Illuminate\Support\Str;
use Tests\Feature\CommandCenter\Concerns\BuildsCommandCenterDatabase;
use Tests\TestCase;

class DeviceSignatureTest extends TestCase
{
    use BuildsCommandCenterDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->setUpCommandCenterDatabase();
    }

    public function test_signed_agent_request_is_accepted_and_replay_is_rejected(): void
    {
        config()->set('command-center.agent.require_request_signature', true);

        $site = ManagedSite::create([
            'name' => 'Pilot',
            'code' => 'PILOT',
            'is_active' => true,
        ]);
        $device = ManagedDevice::create([
            'managed_site_id' => $site->id,
            'platform' => ManagedDevicePlatform::Windows,
            'device_name' => 'Signed Agent',
            'status' => ManagedDeviceStatus::Online,
            'enrolled_at' => now(),
        ]);
        $bearer = app(DeviceAuthenticationService::class)->issueCredential($device);
        [, $secret] = explode('.', $bearer, 2);

        $path = '/api/agent/v1/heartbeat';
        $payload = ['uptime_seconds' => 321, 'screen_locked' => false];
        $content = json_encode($payload, JSON_UNESCAPED_SLASHES);
        $timestamp = (string) now()->timestamp;
        $nonce = Str::random(32);
        $canonical = implode("\n", [
            'POST',
            $path,
            $timestamp,
            $nonce,
            hash('sha256', $content),
        ]);
        $signature = hash_hmac('sha256', $canonical, $secret);

        $server = [
            'HTTP_AUTHORIZATION' => 'Bearer '.$bearer,
            'HTTP_ACCEPT' => 'application/json',
            'CONTENT_TYPE' => 'application/json',
            'HTTP_X_DEVICE_TIMESTAMP' => $timestamp,
            'HTTP_X_DEVICE_NONCE' => $nonce,
            'HTTP_X_DEVICE_SIGNATURE' => $signature,
        ];

        $this->call('POST', $path, [], [], [], $server, $content)
            ->assertOk()
            ->assertJsonPath('data.status', 'online');

        $this->call('POST', $path, [], [], [], $server, $content)
            ->assertStatus(409)
            ->assertJsonPath('error_code', 'DEVICE_REQUEST_REPLAYED');
    }
}
