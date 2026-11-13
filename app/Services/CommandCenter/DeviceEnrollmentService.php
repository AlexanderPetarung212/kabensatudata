<?php

namespace App\Services\CommandCenter;

use App\Enums\ManagedDevicePlatform;
use App\Enums\ManagedDeviceStatus;
use App\Models\DeviceEnrollmentToken;
use App\Models\ManagedDevice;
use App\Models\ManagedSite;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class DeviceEnrollmentService
{
    public function __construct(
        private readonly DeviceAuthenticationService $authentication,
    ) {
    }

    /**
     * @return array{record: DeviceEnrollmentToken, plain_token: string}
     */
    public function createToken(ManagedSite $site, User $creator, ?string $label = null, int $maxUses = 1): array
    {
        $plainToken = 'ksenr_'.$this->base64UrlEncode(random_bytes(32));

        $record = DeviceEnrollmentToken::create([
            'managed_site_id' => $site->id,
            'token_hash' => hash('sha256', $plainToken),
            'label' => $label ? mb_substr($label, 0, 255) : null,
            'max_uses' => max(1, min($maxUses, 100)),
            'used_count' => 0,
            'created_by' => $creator->id,
            'expires_at' => now()->addMinutes(config('command-center.agent.enrollment_ttl_minutes', 10)),
        ]);

        return [
            'record' => $record,
            'plain_token' => $plainToken,
        ];
    }

    /**
     * @return array{device: ManagedDevice, bearer_token: string}
     */
    public function enroll(array $data, string $remoteIp): array
    {
        return DB::transaction(function () use ($data, $remoteIp): array {
            $enrollment = DeviceEnrollmentToken::query()
                ->where('token_hash', hash('sha256', $data['enrollment_token']))
                ->lockForUpdate()
                ->first();

            if (!$enrollment || !$enrollment->isUsable() || !$enrollment->site?->is_active) {
                throw ValidationException::withMessages([
                    'enrollment_token' => 'Kode enrollment tidak valid, kedaluwarsa, atau sudah digunakan.',
                ]);
            }

            $fingerprintHash = filled($data['hardware_fingerprint'] ?? null)
                ? hash('sha256', $data['hardware_fingerprint'])
                : null;

            $device = $fingerprintHash
                ? ManagedDevice::query()
                    ->where('managed_site_id', $enrollment->managed_site_id)
                    ->where('hardware_fingerprint_hash', $fingerprintHash)
                    ->whereNull('revoked_at')
                    ->first()
                : null;

            $attributes = [
                'managed_site_id' => $enrollment->managed_site_id,
                'platform' => ManagedDevicePlatform::from($data['platform']),
                'device_name' => $data['device_name'],
                'hostname' => $data['hostname'] ?? null,
                'os_name' => $data['os_name'] ?? null,
                'os_version' => $data['os_version'] ?? null,
                'architecture' => $data['architecture'] ?? null,
                'agent_version' => $data['agent_version'] ?? null,
                'hardware_fingerprint_hash' => $fingerprintHash,
                'status' => ManagedDeviceStatus::Online,
                'capabilities' => $data['capabilities'] ?? [],
                'permissions_state' => $data['permissions_state'] ?? [],
                'metadata' => [
                    'installer_version' => $data['installer_version'] ?? null,
                ],
                'last_seen_at' => now(),
                'last_ip' => $remoteIp,
                'enrolled_at' => $device?->enrolled_at ?? now(),
            ];

            if ($device) {
                $device->update($attributes);
            } else {
                $device = ManagedDevice::create($attributes);
            }

            $enrollment->increment('used_count');
            $enrollment->forceFill(['last_used_at' => now()])->save();

            return [
                'device' => $device->fresh('site'),
                'bearer_token' => $this->authentication->issueCredential($device),
            ];
        });
    }

    /**
     * Auto-enroll using pre-shared key (no enrollment token needed).
     * Supports multiple PCs — each identified by hardware_fingerprint.
     *
     * @return array{device: ManagedDevice, bearer_token: string}
     */
    public function autoEnroll(array $data, string $remoteIp): array
    {
        $psk = config('command-center.agent.pre_shared_key');

        if (!$psk || !hash_equals($psk, $data['pre_shared_key'])) {
            throw ValidationException::withMessages([
                'pre_shared_key' => 'Kunci akses tidak valid.',
            ]);
        }

        return DB::transaction(function () use ($data, $remoteIp): array {
            // Get or create default site
            $siteCode = config('command-center.default_site_code', 'PUSKESMAS-UTAMA');
            $site = ManagedSite::firstOrCreate(
                ['code' => $siteCode],
                ['name' => 'Puskesmas Utama', 'is_active' => true],
            );

            $fingerprintHash = filled($data['hardware_fingerprint'] ?? null)
                ? hash('sha256', $data['hardware_fingerprint'])
                : null;

            // Find existing device by fingerprint (re-enroll) or hostname
            $device = null;
            if ($fingerprintHash) {
                $device = ManagedDevice::query()
                    ->where('managed_site_id', $site->id)
                    ->where('hardware_fingerprint_hash', $fingerprintHash)
                    ->whereNull('revoked_at')
                    ->first();
            }

            if (!$device && filled($data['hostname'] ?? null)) {
                $device = ManagedDevice::query()
                    ->where('managed_site_id', $site->id)
                    ->where('hostname', $data['hostname'])
                    ->where('platform', ManagedDevicePlatform::from($data['platform']))
                    ->whereNull('revoked_at')
                    ->first();
            }

            $attributes = [
                'managed_site_id' => $site->id,
                'platform' => ManagedDevicePlatform::from($data['platform']),
                'device_name' => $data['device_name'],
                'hostname' => $data['hostname'] ?? null,
                'os_name' => $data['os_name'] ?? null,
                'os_version' => $data['os_version'] ?? null,
                'architecture' => $data['architecture'] ?? null,
                'agent_version' => $data['agent_version'] ?? null,
                'hardware_fingerprint_hash' => $fingerprintHash,
                'status' => ManagedDeviceStatus::Online,
                'capabilities' => $data['capabilities'] ?? [],
                'permissions_state' => $data['permissions_state'] ?? [],
                'metadata' => [
                    'installer_version' => $data['installer_version'] ?? null,
                    'auto_enrolled' => true,
                ],
                'last_seen_at' => now(),
                'last_ip' => $remoteIp,
                'enrolled_at' => $device?->enrolled_at ?? now(),
            ];

            if ($device) {
                $device->update($attributes);
            } else {
                $device = ManagedDevice::create($attributes);
            }

            return [
                'device' => $device->fresh('site'),
                'bearer_token' => $this->authentication->issueCredential($device),
            ];
        });
    }

    private function base64UrlEncode(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }
}
