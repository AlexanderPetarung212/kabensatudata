<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

/**
 * Force all /api/* requests to expect JSON responses.
 * This prevents Sanctum from redirecting to login page
 * when auth fails on API routes (especially on shared hosting
 * where Accept headers may get stripped).
 */
class ForceJsonResponse
{
    public function handle(Request $request, Closure $next)
    {
        // Force Accept: application/json for all API routes
        if ($request->is('api/*')) {
            $request->headers->set('Accept', 'application/json');
        }

        return $next($request);
    }
}
