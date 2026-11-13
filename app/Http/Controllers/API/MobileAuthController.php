<?php

namespace App\Http\Controllers\API;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

class MobileAuthController extends Controller
{
    /**
     * Login and issue a Sanctum token for mobile app.
     * Token is long-lived (30 days) since field workers may be offline for extended periods.
     */
    public function login(Request $request): JsonResponse
    {
        $request->validate([
            'email' => 'required|email',
            'password' => 'required',
            'device_name' => 'required|string|max:255',
            'device_id' => 'required|string|max:100',
        ]);

        $user = User::where('email', $request->email)->first();

        if (! $user || ! Hash::check($request->password, $user->password)) {
            throw ValidationException::withMessages([
                'email' => ['Email atau password salah.'],
            ]);
        }

        // Only allow petugas and above to use mobile app
        if (! $user->hasAnyRole(['super_admin', 'admin', 'petugas', 'supervisor'])) {
            return response()->json([
                'message' => 'Akun Anda tidak memiliki akses ke aplikasi mobile.',
            ], 403);
        }

        // Revoke existing tokens for this device (single device = single session)
        $user->tokens()->where('name', 'mobile-' . $request->device_id)->delete();

        // Create token that expires in 30 days
        $token = $user->createToken(
            'mobile-' . $request->device_id,
            ['mobile:sync', 'mobile:read', 'mobile:write'],
            now()->addDays(30)
        );

        return response()->json([
            'token' => $token->plainTextToken,
            'user' => [
                'id' => $user->id,
                'name' => $user->name,
                'email' => $user->email,
                'roles' => $user->getRoleNames(),
            ],
            'expires_at' => $token->accessToken->expires_at->toISOString(),
        ]);
    }

    /**
     * Logout and revoke the current token.
     */
    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()->delete();

        return response()->json([
            'message' => 'Logout berhasil.',
        ]);
    }

    /**
     * Get the authenticated user's profile.
     */
    public function profile(Request $request): JsonResponse
    {
        $user = $request->user();

        return response()->json([
            'user' => [
                'id' => $user->id,
                'name' => $user->name,
                'email' => $user->email,
                'roles' => $user->getRoleNames(),
            ],
        ]);
    }

    /**
     * Refresh token (extend expiry by another 30 days).
     */
    public function refreshToken(Request $request): JsonResponse
    {
        $user = $request->user();
        $currentToken = $user->currentAccessToken();
        $deviceId = $request->input('device_id', 'unknown');

        // Delete old token
        $currentToken->delete();

        // Create new token
        $token = $user->createToken(
            'mobile-' . $deviceId,
            ['mobile:sync', 'mobile:read', 'mobile:write'],
            now()->addDays(30)
        );

        return response()->json([
            'token' => $token->plainTextToken,
            'expires_at' => $token->accessToken->expires_at->toISOString(),
        ]);
    }
}
