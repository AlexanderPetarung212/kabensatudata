<?php

return [
    'enabled' => (bool) env('COMMAND_CENTER_ENABLED', true),

    'legacy_secret_url' => [
        'enabled' => (bool) env('COMMAND_CENTER_LEGACY_SECRET_URL', false),
        'token' => env('CC_TOKEN'),
    ],

    'default_site_code' => env('COMMAND_CENTER_DEFAULT_SITE_CODE', 'PUSKESMAS-UTAMA'),

    'agent' => [
        'pre_shared_key' => env('COMMAND_CENTER_PSK', null),
        'enrollment_ttl_minutes' => (int) env('COMMAND_CENTER_ENROLLMENT_TTL_MINUTES', 10),
        'command_poll_seconds' => (int) env('COMMAND_CENTER_COMMAND_POLL_SECONDS', 5),
        'heartbeat_seconds' => (int) env('COMMAND_CENTER_HEARTBEAT_SECONDS', 60),
        'offline_after_seconds' => (int) env('COMMAND_CENTER_OFFLINE_AFTER_SECONDS', 180),
        'clock_skew_seconds' => (int) env('DEVICE_REQUEST_CLOCK_SKEW_SECONDS', 120),
        'require_request_signature' => (bool) env('DEVICE_REQUEST_SIGNATURE_REQUIRED', true),
        'nonce_ttl_seconds' => (int) env('DEVICE_REQUEST_NONCE_TTL_SECONDS', 300),
    ],

    'commands' => [
        'GET_DEVICE_INFO' => [
            'permission' => 'device.view',
            'capability' => 'device_info',
            'executor' => 'service',
            'sensitive' => false,
            'ttl_seconds' => 120,
            'payload_rules' => [],
        ],
        'GET_PROCESS_LIST' => [
            'permission' => 'device.view_processes',
            'capability' => 'process_list',
            'executor' => 'service',
            'sensitive' => false,
            'ttl_seconds' => 120,
            'payload_rules' => [],
        ],
        'GET_APP_LIST' => [
            'permission' => 'device.view_processes',
            'capability' => 'application_list',
            'executor' => 'service',
            'sensitive' => false,
            'ttl_seconds' => 120,
            'payload_rules' => [],
        ],
        'CAPTURE_SCREENSHOT' => [
            'permission' => 'device.capture_screenshot',
            'capability' => 'screenshot_on_demand',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [
                'monitor' => ['nullable', 'integer', 'min:0', 'max:16'],
            ],
        ],
        'SCREEN_START' => [
            'permission' => 'device.view_screen',
            'capability' => 'live_screen',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [
                'quality' => ['nullable', 'in:low,medium,high'],
            ],
        ],
        'SCREEN_STOP' => [
            'permission' => 'device.view_screen',
            'capability' => 'live_screen',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [],
        ],
        'CAMERA_SNAPSHOT' => [
            'permission' => 'device.view_camera',
            'capability' => 'camera',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [
                'camera' => ['nullable', 'string', 'max:120'],
            ],
        ],
        'CAMERA_START' => [
            'permission' => 'device.view_camera',
            'capability' => 'camera',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [
                'quality' => ['nullable', 'in:low,medium,high'],
                'camera' => ['nullable', 'string', 'max:120'],
            ],
        ],
        'CAMERA_STOP' => [
            'permission' => 'device.view_camera',
            'capability' => 'camera',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [],
        ],
        'AUDIO_START' => [
            'permission' => 'device.capture_audio',
            'capability' => 'audio_capture',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [
                'duration_seconds' => ['required', 'integer', 'min:1', 'max:300'],
            ],
        ],
        'AUDIO_STOP' => [
            'permission' => 'device.capture_audio',
            'capability' => 'audio_capture',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [],
        ],
        'LOCK' => [
            'permission' => 'device.lock',
            'capability' => 'lock',
            'executor' => 'companion',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [],
        ],
        'RESTART' => [
            'permission' => 'device.restart',
            'capability' => 'restart',
            'executor' => 'service',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [
                'delay_seconds' => ['nullable', 'integer', 'min:0', 'max:300'],
            ],
        ],
        'SHUTDOWN' => [
            'permission' => 'device.shutdown',
            'capability' => 'shutdown',
            'executor' => 'service',
            'sensitive' => true,
            'ttl_seconds' => 60,
            'payload_rules' => [
                'delay_seconds' => ['nullable', 'integer', 'min:0', 'max:300'],
            ],
        ],
        'DIAGNOSTIC_BUNDLE' => [
            'permission' => 'device.run_maintenance',
            'capability' => 'diagnostic_bundle',
            'executor' => 'service',
            'sensitive' => false,
            'ttl_seconds' => 180,
            'payload_rules' => [],
        ],
        'SCAN_FOLDER' => [
            'permission' => 'device.browse_files',
            'capability' => 'file_browser',
            'executor' => 'service',
            'sensitive' => true,
            'ttl_seconds' => 120,
            'payload_rules' => [
                'path' => ['required', 'string', 'max:1024'],
            ],
        ],
        'UPLOAD_FILE' => [
            'permission' => 'device.browse_files',
            'capability' => 'file_browser',
            'executor' => 'service',
            'sensitive' => true,
            'ttl_seconds' => 180,
            'payload_rules' => [
                'file_path' => ['required', 'string', 'max:1024'],
            ],
        ],
    ],

    'live_session_max_minutes' => (int) env('COMMAND_CENTER_MAX_LIVE_SESSION_MINUTES', 15),

    'alerts' => [
        'cpu_percent' => (float) env('COMMAND_CENTER_CPU_ALERT_PERCENT', 95),
        'memory_percent' => (float) env('COMMAND_CENTER_MEMORY_ALERT_PERCENT', 90),
        'disk_percent' => (float) env('COMMAND_CENTER_DISK_ALERT_PERCENT', 90),
    ],

    'artifacts' => [
        'disk' => env('COMMAND_CENTER_ARTIFACT_DISK', 'command_center_private'),
        'max_size_kb' => (int) env('COMMAND_CENTER_MAX_ARTIFACT_KB', 102400),
        'screenshot_retention_days' => (int) env('COMMAND_CENTER_SCREENSHOT_RETENTION_DAYS', 7),
        'media_retention_days' => (int) env('COMMAND_CENTER_MEDIA_RETENTION_DAYS', 3),
        'allowed_mime_types' => [
            'image/jpeg',
            'image/png',
            'application/json',
            'application/zip',
            'text/plain',
            'audio/aac',
            'audio/mpeg',
            'video/mp4',
        ],
    ],

    'retention' => [
        'heartbeat_days' => (int) env('COMMAND_CENTER_HEARTBEAT_RETENTION_DAYS', 7),
        'metric_days' => (int) env('COMMAND_CENTER_METRIC_RETENTION_DAYS', 30),
        'audit_days' => (int) env('COMMAND_CENTER_AUDIT_RETENTION_DAYS', 365),
    ],
];
