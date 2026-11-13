<?php

namespace App\Services\CommandCenter;

use App\Models\CommandCenterAuditLog;
use App\Models\ManagedDevice;
use App\Models\ManagedSite;
use App\Models\User;
use Illuminate\Http\Request;

class CommandCenterAuditService
{
    public function record(
        string $action,
        string $result,
        ?User $user = null,
        ?ManagedDevice $device = null,
        ?ManagedSite $site = null,
        ?string $reason = null,
        array $metadata = [],
        ?Request $request = null,
    ): CommandCenterAuditLog {
        $request ??= request();

        return CommandCenterAuditLog::create([
            'user_id' => $user?->id,
            'managed_site_id' => $site?->id ?? $device?->managed_site_id,
            'managed_device_id' => $device?->id,
            'action' => $action,
            'result' => $result,
            'reason' => $reason ? mb_substr($reason, 0, 500) : null,
            'request_ip' => $request?->ip(),
            'user_agent' => $request?->userAgent() ? mb_substr($request->userAgent(), 0, 500) : null,
            'metadata' => $this->sanitizeMetadata($metadata),
            'occurred_at' => now(),
        ]);
    }

    private function sanitizeMetadata(array $metadata): array
    {
        $blocked = ['token', 'secret', 'password', 'authorization', 'credential'];

        foreach ($metadata as $key => $value) {
            if (in_array(strtolower((string) $key), $blocked, true)) {
                unset($metadata[$key]);
            }
        }

        return $metadata;
    }
}
