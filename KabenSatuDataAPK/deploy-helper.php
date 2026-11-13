<?php
/**
 * TEMPORARY DEPLOY HELPER for BOK — Hapus setelah selesai!
 * Akses via: https://puskesmaskaben.my.id/deploy-helper.php?token=kaben2026deploy
 */

$SECRET = 'kaben2026deploy';
if (($_GET['token'] ?? '') !== $SECRET) {
    die('❌ Akses ditolak.');
}

// Find Laravel root
$laravelRoot = null;
$searchPaths = [
    realpath(__DIR__ . '/..'),
    __DIR__,
    realpath(__DIR__ . '/../../bokbaru'),
    realpath(__DIR__ . '/../bokbaru'),
    '/home/' . get_current_user() . '/bokbaru',
    '/home/' . get_current_user() . '/webpkm',
    '/home/' . get_current_user() . '/www',
];

foreach ($searchPaths as $path) {
    if ($path && file_exists($path . '/artisan')) {
        $laravelRoot = $path;
        break;
    }
}

if (!$laravelRoot) {
    echo "<pre>❌ Laravel root tidak ditemukan.\n";
    echo "Script dir: " . __DIR__ . "\n";
    echo "Parent: " . realpath(__DIR__ . '/..') . "\n";
    echo "User: " . get_current_user() . "\n\n";
    $parent = realpath(__DIR__ . '/..');
    if ($parent) {
        echo "Isi parent:\n";
        foreach (scandir($parent) as $f) {
            if ($f === '.' || $f === '..') continue;
            echo "  " . (is_dir($parent . '/' . $f) ? '📁' : '📄') . " $f\n";
        }
    }
    echo "</pre>";
    die();
}

echo "<pre style='font-family:monospace;background:#111;color:#0f0;padding:20px;'>\n";
echo "🔧 BOK Deploy Helper\n";
echo "================================\n";
echo "Laravel root: $laravelRoot\n";
echo "Public root:  " . __DIR__ . "\n\n";

function runArtisan($laravelRoot, $command) {
    echo "▶ php artisan $command\n";
    $output = [];
    $code = 0;
    exec("cd " . escapeshellarg($laravelRoot) . " && php artisan $command 2>&1", $output, $code);
    echo implode("\n", $output) . "\n";
    echo ($code === 0 ? "  ✅ OK" : "  ❌ Failed (code: $code)") . "\n\n";
    return $code === 0;
}

$step = $_GET['step'] ?? 'info';

if ($step === 'info') {
    echo "📋 Info:\n";
    $checks = [
        'vendor/autoload.php' => $laravelRoot . '/vendor/autoload.php',
        'bootstrap/app.php' => $laravelRoot . '/bootstrap/app.php',
        '.env' => $laravelRoot . '/.env',
        'ForceJsonResponse.php' => $laravelRoot . '/app/Http/Middleware/ForceJsonResponse.php',
    ];
    foreach ($checks as $label => $path) {
        $exists = file_exists($path);
        echo "  " . ($exists ? "✅" : "❌") . " $label\n";
    }
    
    if (file_exists($laravelRoot . '/.env')) {
        $env = file_get_contents($laravelRoot . '/.env');
        preg_match('/APP_URL=(.*)/', $env, $m);
        echo "\n📋 APP_URL: " . ($m[1] ?? 'N/A') . "\n";
    }
    
    echo "\n🔗 Aksi:\n";
    echo "<a href='?token=$SECRET&step=run' style='color:#0ff;font-size:16px;'>▶ JALANKAN SEMUA FIX (cache clear + migrate)</a>\n";
    echo "<a href='?token=$SECRET&step=test-api' style='color:#ff0;font-size:14px;'>▶ Test API (butuh email & pass)</a>\n";

} elseif ($step === 'run') {
    echo "🚀 Menjalankan perbaikan...\n\n";
    runArtisan($laravelRoot, 'config:clear');
    runArtisan($laravelRoot, 'route:clear');
    runArtisan($laravelRoot, 'view:clear');
    runArtisan($laravelRoot, 'cache:clear');
    runArtisan($laravelRoot, 'migrate --force');

    // ── Storage Symlink ──────────────────────────────────────────────────
    $publicDir = __DIR__; // e.g. /home/puskes17/public_html
    $storageLinkTarget = $laravelRoot . '/storage/app/public';
    $storageLinkPath = $publicDir . '/storage';

    echo "▶ Storage Symlink\n";
    echo "  Target: $storageLinkTarget\n";
    echo "  Link:   $storageLinkPath\n";

    if (is_link($storageLinkPath)) {
        $currentTarget = readlink($storageLinkPath);
        echo "  ✅ Symlink sudah ada → $currentTarget\n\n";
    } elseif (is_dir($storageLinkPath)) {
        echo "  ⚠️ /storage adalah folder (bukan symlink). Gambar mungkin tidak tampil.\n";
        echo "  Solusi: hapus folder lalu buat symlink.\n\n";
    } elseif (file_exists($storageLinkTarget)) {
        // Create symlink
        if (@symlink($storageLinkTarget, $storageLinkPath)) {
            echo "  ✅ Symlink berhasil dibuat!\n\n";
        } else {
            echo "  ❌ Gagal buat symlink (PHP mungkin tidak memiliki izin).\n";
            echo "  Buat manual via File Manager cPanel:\n";
            echo "    ln -s $storageLinkTarget $storageLinkPath\n\n";
        }
    } else {
        echo "  ❌ Target storage not found: $storageLinkTarget\n";
        echo "  Pastikan folder storage/app/public ada di server.\n\n";
    }

    // ── Check APP_URL ────────────────────────────────────────────────────
    if (file_exists($laravelRoot . '/.env')) {
        $env = file_get_contents($laravelRoot . '/.env');
        preg_match('/APP_URL=(.*)/', $env, $m);
        $appUrl = trim($m[1] ?? '');
        echo "▶ APP_URL: $appUrl\n";
        if (!str_contains($appUrl, 'puskesmaskaben.my.id')) {
            echo "  ⚠️ APP_URL mungkin belum benar. Seharusnya: https://puskesmaskaben.my.id\n";
        } else {
            echo "  ✅ OK\n";
        }
    }

    echo "\n✅ SELESAI!\n";
    echo "\n⚠️ HAPUS FILE INI SETELAH SELESAI!\n";
    echo "<a href='?token=$SECRET&step=info' style='color:#0ff;'>← Kembali</a>\n";

} elseif ($step === 'fix-storage') {
    echo "🔗 FIX STORAGE SYMLINK + APP_URL\n";
    echo "================================\n\n";

    $publicDir = __DIR__;
    $storageLinkTarget = $laravelRoot . '/storage/app/public';
    $storageLinkPath = $publicDir . '/storage';

    // Remove broken symlink or folder if exists
    if (is_link($storageLinkPath)) {
        $old = readlink($storageLinkPath);
        echo "▶ Menghapus symlink lama → $old\n";
        @unlink($storageLinkPath);
    } elseif (is_dir($storageLinkPath)) {
        echo "▶ /storage adalah folder, menghapus...\n";
        @rmdir($storageLinkPath);
    }

    // Create new symlink
    echo "▶ Membuat symlink baru:\n";
    echo "  $storageLinkPath → $storageLinkTarget\n";

    if (!file_exists($storageLinkTarget)) {
        // Create target dir if missing
        @mkdir($storageLinkTarget, 0755, true);
        echo "  📂 Target folder dibuat.\n";
    }

    if (@symlink($storageLinkTarget, $storageLinkPath)) {
        echo "  ✅ Symlink berhasil dibuat!\n\n";
    } else {
        echo "  ❌ PHP symlink() gagal. Coba via Terminal:\n";
        echo "  ln -s $storageLinkTarget $storageLinkPath\n\n";
    }

    // Verify
    if (is_link($storageLinkPath)) {
        $target = readlink($storageLinkPath);
        echo "▶ Verifikasi: $storageLinkPath → $target\n";
        echo "  " . (file_exists($storageLinkTarget) ? "✅ Target ada" : "❌ Target tidak ditemukan") . "\n\n";
    }

    // Fix APP_URL to HTTPS
    $envPath = $laravelRoot . '/.env';
    if (file_exists($envPath)) {
        $env = file_get_contents($envPath);
        if (str_contains($env, 'APP_URL=http://puskesmaskaben')) {
            $env = preg_replace('/APP_URL=http:\/\/puskesmaskaben\.my\.id\/?/', 'APP_URL=https://puskesmaskaben.my.id', $env);
            file_put_contents($envPath, $env);
            echo "▶ APP_URL diubah ke HTTPS ✅\n";
        } else {
            preg_match('/APP_URL=(.*)/', $env, $m);
            echo "▶ APP_URL: " . trim($m[1] ?? 'N/A') . " (tidak diubah)\n";
        }
    }

    // Clear config cache
    runArtisan($laravelRoot, 'config:clear');

    echo "✅ SELESAI!\n";
    echo "<a href='?token=$SECRET&step=info' style='color:#0ff;'>← Kembali</a>\n";

} elseif ($step === 'test-api') {
    echo "🧪 TEST API AUTH (simulasi APK)\n";
    echo "================================\n\n";

    $email = $_GET['email'] ?? '';
    $pass = $_GET['pass'] ?? '';

    if (!$email || !$pass) {
        echo "Masukkan email dan password:\n";
        echo "  ?token=$SECRET&step=test-api&email=EMAIL&pass=PASSWORD\n";
    } else {
        $baseUrl = 'https://puskesmaskaben.my.id';

        // Step 1: Login
        echo "▶ Step 1: POST /api/mobile/login\n";
        $ch = curl_init("$baseUrl/api/mobile/login");
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => json_encode(['email' => $email, 'password' => $pass]),
            CURLOPT_HTTPHEADER => ['Content-Type: application/json', 'Accept: application/json'],
            CURLOPT_SSL_VERIFYPEER => false,
        ]);
        $loginResponse = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        echo "  HTTP: $httpCode\n";
        echo "  Response: $loginResponse\n\n";

        $loginData = json_decode($loginResponse, true);
        $token = $loginData['token'] ?? $loginData['data']['token'] ?? null;

        if (!$token) {
            echo "❌ Login gagal\n";
        } else {
            echo "  ✅ Token: " . substr($token, 0, 20) . "...\n\n";

            // Step 2: Calendar
            echo "▶ Step 2: GET /api/mobile/calendar?year=2026 (dengan token)\n";
            $ch = curl_init("$baseUrl/api/mobile/calendar?year=2026");
            curl_setopt_array($ch, [
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_HTTPHEADER => ['Accept: application/json', 'Authorization: Bearer ' . $token],
                CURLOPT_SSL_VERIFYPEER => false,
            ]);
            $calResponse = curl_exec($ch);
            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            curl_close($ch);

            echo "  HTTP: $httpCode\n";
            if ($httpCode === 200) {
                echo "  ✅ Calendar OK\n";
            } else {
                echo "  ❌ " . substr($calResponse, 0, 300) . "\n";
            }

            // Step 3: Claim prep (first POA)
            echo "\n▶ Step 3: GET /api/mobile/calendar/1/claim-prep/3 (dengan token)\n";
            $ch = curl_init("$baseUrl/api/mobile/calendar/1/claim-prep/3");
            curl_setopt_array($ch, [
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_HTTPHEADER => ['Accept: application/json', 'Authorization: Bearer ' . $token],
                CURLOPT_SSL_VERIFYPEER => false,
            ]);
            $claimResponse = curl_exec($ch);
            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            curl_close($ch);

            echo "  HTTP: $httpCode\n";
            if ($httpCode === 200) {
                echo "  ✅ Claim prep OK\n";
            } else {
                echo "  ❌ " . substr($claimResponse, 0, 500) . "\n";
            }

            // Step 4: Error Log
            echo "\n▶ Step 4: Laravel Error Log\n";
            $logFile = $laravelRoot . '/storage/logs/laravel.log';
            if (file_exists($logFile)) {
                $log = file_get_contents($logFile);
                $entries = preg_split('/(?=\[\d{4}-\d{2}-\d{2})/', $log);
                $lastErrors = array_filter($entries, fn($e) => str_contains($e, 'ERROR'));
                $lastErrors = array_slice($lastErrors, -3);
                foreach ($lastErrors as $err) {
                    echo htmlspecialchars(substr(trim($err), 0, 800)) . "\n---\n";
                }
            } else {
                echo "  ⚠️ Log tidak ditemukan\n";
            }
        }
    }
    echo "\n<a href='?token=$SECRET&step=info' style='color:#0ff;'>← Kembali</a>\n";
}

echo "</pre>";
