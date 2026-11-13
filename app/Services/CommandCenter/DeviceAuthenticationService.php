<?php

namespace App\Services\CommandCenter;

use App\Models\DeviceCredential;
use App\Models\ManagedDevice;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Str;
use RuntimeException;

class DeviceAuthenticationService
{
    public function issueCredential(ManagedDevice $device, string $name = 'primary'): string
    {
        $plainSecret = $this->base64UrlEncode(random_bytes(48));

        $device->credentials()
            ->where('name', $name)
            ->whereNull('revoked_at')
            ->update([
                'revoked_at' => now(),
                'rotated_at' => now(),
            ]);

        DeviceCredential::updateOrCreate(
            [
                'managed_device_id' => $device->id,
                'name' => $name,
            ],
            [
                'secret_hash' => hash('sha256', $plainSecret),
                'secret_encrypted' => Crypt::encryptString($plainSecret),
                'last_used_at' => null,
                'expires_at' => null,
                'revoked_at' => null,
                'rotated_at' => null,
            ],
        );

        return "{$device->uuid}.{$plainSecret}";
    }

    /**
     * @return array{device: ManagedDevice, credential: DeviceCredential, secret: string}
     */
    public function authenticateBearer(?string $bearerToken): array
    {
        if (!$bearerToken || !str_contains($bearerToken, '.')) {
            throw new RuntimeException('missing_device_token');
        }

        [$deviceUuid, $plainSecret] = explode('.', $bearerToken, 2);

        if (!Str::isUuid($deviceUuid) || strlen($plainSecret) < 32) {
            throw new RuntimeException('invalid_device_token');
        }

        $device = ManagedDevice::query()
            ->where('uuid', $deviceUuid)
            ->with('activeCredential')
            ->first();

        if (!$device || $device->revoked_at || !$device->activeCredential) {
            throw new RuntimeException('revoked_or_unknown_device');
        }

        $credential = $device->activeCredential;

        if ($credential->expires_at?->isPast() || !hash_equals($credential->secret_hash, hash('sha256', $plainSecret))) {
            throw new RuntimeException('invalid_device_token');
        }

        return compact('device', 'credential', 'plainSecret') + ['secret' => $plainSecret];
    }

    public function expectedSignature(Request $request, string $plainSecret, string $timestamp, string $nonce): string
    {
        $canonical = implode("\n", [
            strtoupper($request->method()),
            $request->getPathInfo(),
            $timestamp,
            $nonce,
            hash('sha256', $request->getContent()),
        ]);

        return hash_hmac('sha256', $canonical, $plainSecret);
    }

    private function base64UrlEncode(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }
}
