<?php

use Illuminate\Http\Request;

define('LARAVEL_START', microtime(true));

// ─── Shared Hosting Fix: Recover Authorization header ───────────────
// Apache on shared hosting often strips the Authorization header.
// This code recovers it from multiple fallback sources before Laravel boots.
if (!isset($_SERVER['HTTP_AUTHORIZATION'])) {
    // Try REDIRECT_HTTP_AUTHORIZATION (set by RewriteRule)
    if (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
        $_SERVER['HTTP_AUTHORIZATION'] = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
    }
    // Try getallheaders() (available on Apache with mod_php)
    elseif (function_exists('getallheaders')) {
        $headers = getallheaders();
        foreach ($headers as $key => $value) {
            if (strtolower($key) === 'authorization') {
                $_SERVER['HTTP_AUTHORIZATION'] = $value;
                break;
            }
        }
    }
    // Try apache_request_headers()
    elseif (function_exists('apache_request_headers')) {
        $headers = apache_request_headers();
        foreach ($headers as $key => $value) {
            if (strtolower($key) === 'authorization') {
                $_SERVER['HTTP_AUTHORIZATION'] = $value;
                break;
            }
        }
    }
}

// Determine if the application is in maintenance mode...
if (file_exists($maintenance = __DIR__.'/../storage/framework/maintenance.php')) {
    require $maintenance;
}

// Register the Composer autoloader...
require __DIR__.'/../vendor/autoload.php';

// Bootstrap Laravel and handle the request...
(require_once __DIR__.'/../bootstrap/app.php')
    ->handleRequest(Request::capture());
