<?php

namespace App\Http\Controllers\API\Agent\V1;

use App\Http\Controllers\Controller;
use App\Models\AgentRelease;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Symfony\Component\HttpFoundation\StreamedResponse;

class AgentReleaseController extends Controller
{
    public function latest(Request $request): JsonResponse
    {
        $data = $request->validate([
            'platform' => ['nullable', 'in:windows'],
            'architecture' => ['nullable', 'in:x64,arm64'],
            'channel' => ['nullable', 'in:stable,beta'],
        ]);

        $release = AgentRelease::query()
            ->where('is_published', true)
            ->whereNull('revoked_at')
            ->where('platform', $data['platform'] ?? 'windows')
            ->where('architecture', $data['architecture'] ?? 'x64')
            ->where('channel', $data['channel'] ?? 'stable')
            ->orderByDesc('published_at')
            ->orderByDesc('id')
            ->first();

        return response()->json([
            'data' => $release ? [
                'uuid' => $release->uuid,
                'version' => $release->version,
                'checksum_sha256' => $release->checksum_sha256,
                'signature' => $release->signature,
                'minimum_supported_version' => $release->minimum_supported_version,
                'download_url' => route('agent.v1.releases.download', $release),
            ] : null,
        ]);
    }

    public function download(AgentRelease $release): StreamedResponse
    {
        abort_if(!$release->is_published || $release->revoked_at, 404);

        return Storage::disk($release->disk)->download(
            $release->path,
            basename($release->path),
            [
                'Cache-Control' => 'private, no-store',
                'X-Content-Type-Options' => 'nosniff',
            ],
        );
    }
}
