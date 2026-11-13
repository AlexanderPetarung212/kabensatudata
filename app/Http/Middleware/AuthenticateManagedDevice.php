<?php

namespace App\Http\Middleware;

use App\Services\CommandCenter\DeviceAuthenticationService;
use Closure;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Symfony\Component\HttpFoundation\Response;
use Throwable;

class AuthenticateManagedDevice
{
    public function __construct(
        private readonly DeviceAuthenticationService $authentication,
    ) {
    }

    public function handle(Request $request, Closure $next): Response
    {
        try {
            $authenticated = $this->authentication->authenticateBearer($request->bearerToken());
        } catch (Throwable) {
            return $this->error('DEVICE_UNAUTHENTICATED', 'Kredensial perangkat tidak valid.', 401);
        }

        $device = $authenticated['device'];
        $credential = $authenticated['credential'];
        $plainSecret = $authenticated['secret'];

        if (config('command-center.agent.require_request_signature', true)) {
            $failure = $this->verifyRequestSignature($request, $device->id, $plainSecret);
            if ($failure) {
                return $failure;
            }
        }

        $credential->forceFill(['last_used_at' => now()])->save();
        $request->attributes->set('managed_device', $device);

        return $next($request);
    }

    private function verifyRequestSignature(Request $request, int $deviceId, string $plainSecret): ?JsonResponse
    {
        $timestamp = (string) $request->header('X-Device-Timestamp', '');
        $nonce = (string) $request->header('X-Device-Nonce', '');
        $signature = (string) $request->header('X-Device-Signature', '');

        if (!ctype_digit($timestamp) || !preg_match('/^[A-Za-z0-9_-]{16,128}$/', $nonce) || !preg_match('/^[a-f0-9]{64}$/i', $signature)) {
            return $this->error('DEVICE_SIGNATURE_MISSING', 'Signature perangkat tidak lengkap.', 401);
        }

        $skew = abs(now()->timestamp - (int) $timestamp);
        if ($skew > config('command-center.agent.clock_skew_seconds', 120)) {
            return $this->error('DEVICE_CLOCK_SKEW', 'Waktu perangkat tidak sinkron.', 401);
        }

        $expected = $this->authentication->expectedSignature($request, $plainSecret, $timestamp, $nonce);
        if (!hash_equals($expected, strtolower($signature))) {
            return $this->error('DEVICE_SIGNATURE_INVALID', 'Signature perangkat tidak valid.', 401);
        }

        $nonceKey = 'managed-device-nonce:'.$deviceId.':'.hash('sha256', $nonce);
        if (!Cache::add($nonceKey, true, config('command-center.agent.nonce_ttl_seconds', 300))) {
            return $this->error('DEVICE_REQUEST_REPLAYED', 'Request perangkat sudah pernah digunakan.', 409);
        }

        return null;
    }

    private function error(string $code, string $message, int $status): JsonResponse
    {
        return response()->json([
            'message' => $message,
            'error_code' => $code,
        ], $status);
    }
}
