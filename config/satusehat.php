<?php

return [

    /*
    |--------------------------------------------------------------------------
    | SATUSEHAT Environment
    |--------------------------------------------------------------------------
    |
    | Determines which SATUSEHAT environment to use.
    | Options: 'sandbox', 'production'
    |
    */
    'env' => env('SATUSEHAT_ENV', 'production'),

    /*
    |--------------------------------------------------------------------------
    | Base URLs
    |--------------------------------------------------------------------------
    */
    'base_url' => env('SATUSEHAT_BASE_URL', 'https://api-satusehat.kemkes.go.id'),

    /*
    |--------------------------------------------------------------------------
    | OAuth Credentials
    |--------------------------------------------------------------------------
    */
    'client_id' => env('SATUSEHAT_CLIENT_ID'),
    'client_secret' => env('SATUSEHAT_CLIENT_SECRET'),

    /*
    |--------------------------------------------------------------------------
    | Organization
    |--------------------------------------------------------------------------
    */
    'organization_id' => env('SATUSEHAT_ORGANIZATION_ID'),

    /*
    |--------------------------------------------------------------------------
    | Token Cache
    |--------------------------------------------------------------------------
    |
    | Cache key and TTL for OAuth access token.
    | Default TTL is 3.5 hours (token expires in 4 hours).
    |
    */
    'token_cache_key' => 'satusehat_access_token',
    'token_cache_ttl' => env('SATUSEHAT_TOKEN_TTL', 12600), // 3.5 hours in seconds

    /*
    |--------------------------------------------------------------------------
    | Read-Only Mode
    |--------------------------------------------------------------------------
    |
    | When true, only GET operations are allowed.
    | POST/PUT/DELETE to SATUSEHAT will be blocked.
    |
    */
    'readonly' => env('SATUSEHAT_READONLY', true),

];
