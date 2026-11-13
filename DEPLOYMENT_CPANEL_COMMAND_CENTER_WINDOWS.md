# Deployment cPanel — KabenSatuData Command Center Windows

Dokumen ini adalah manifest aktual implementasi pilot `0.1.0`, bukan daftar
rencana. Website produksi tidak diubah langsung oleh proses pengembangan ini;
file berikut perlu diunggah dan perintah deployment perlu dijalankan di cPanel.

## 1. Status fitur

Siap untuk pilot terbatas:

- enrollment perangkat dengan kode sekali pakai;
- Windows Service berjalan otomatis di latar belakang;
- credential Agent dilindungi DPAPI dan request ditandatangani HMAC;
- heartbeat, status online/idle/offline, CPU, RAM, disk, dan network;
- alert otomatis offline, CPU tinggi, RAM tinggi, dan disk menipis;
- device info, daftar proses, daftar aplikasi, dan diagnostic ZIP;
- screenshot on-demand, termasuk pilihan monitor;
- lock, restart, dan shutdown dengan permission serta alasan;
- command state machine, retry sebelum ACK, idempotency, dan expiry;
- artifact privat dengan checksum, retention, dan authorization;
- dashboard akun admin/kepala puskesmas dan audit log;
- feature switch untuk mematikan seluruh Command Center dengan cepat.

Belum diaktifkan pada pilot:

- live screen;
- camera snapshot/live;
- audio;
- remote mouse/keyboard;
- file manager Windows;
- auto-update Agent.

Tombol untuk capability yang belum didukung otomatis nonaktif. Fitur live
memerlukan gateway realtime/WebRTC/TURN di luar shared hosting cPanel, indikator
sesi media pada komputer, pembatasan durasi, dan pengujian perangkat Windows.

## 2. File backend baru yang harus diunggah

Pertahankan struktur folder persis seperti repository.

```text
app/Console/Commands/MaintainCommandCenter.php

app/Enums/DeviceCommandStatus.php
app/Enums/ManagedDevicePlatform.php
app/Enums/ManagedDeviceStatus.php

app/Http/Controllers/API/Agent/V1/AgentArtifactController.php
app/Http/Controllers/API/Agent/V1/AgentCommandController.php
app/Http/Controllers/API/Agent/V1/AgentEnrollmentController.php
app/Http/Controllers/API/Agent/V1/AgentHeartbeatController.php
app/Http/Controllers/API/Agent/V1/AgentPolicyController.php
app/Http/Controllers/API/Agent/V1/AgentReleaseController.php
app/Http/Controllers/API/Agent/V1/AgentTelemetryController.php

app/Http/Controllers/API/CommandCenter/V1/DeviceArtifactController.php
app/Http/Controllers/API/CommandCenter/V1/DeviceAuditController.php
app/Http/Controllers/API/CommandCenter/V1/DeviceCommandController.php
app/Http/Controllers/API/CommandCenter/V1/EnrollmentController.php
app/Http/Controllers/API/CommandCenter/V1/ManagedDeviceController.php
app/Http/Controllers/CommandCenter/CommandCenterDashboardController.php

app/Http/Middleware/AuthenticateManagedDevice.php
app/Http/Middleware/NoStoreSensitiveResponse.php

app/Models/AgentRelease.php
app/Models/CommandCenterAuditLog.php
app/Models/Concerns/HasPublicUuid.php
app/Models/DeviceAlert.php
app/Models/DeviceArtifact.php
app/Models/DeviceCommand.php
app/Models/DeviceCommandEvent.php
app/Models/DeviceCredential.php
app/Models/DeviceEnrollmentToken.php
app/Models/DeviceHeartbeat.php
app/Models/DeviceMetric.php
app/Models/DevicePolicy.php
app/Models/ManagedDevice.php
app/Models/ManagedSite.php

app/Policies/DeviceArtifactPolicy.php
app/Policies/ManagedDevicePolicy.php

app/Services/CommandCenter/CommandCenterAuditService.php
app/Services/CommandCenter/DeviceAlertService.php
app/Services/CommandCenter/DeviceArtifactService.php
app/Services/CommandCenter/DeviceAuthenticationService.php
app/Services/CommandCenter/DeviceCommandService.php
app/Services/CommandCenter/DeviceEnrollmentService.php

config/command-center.php

database/migrations/2026_07_28_000001_create_managed_device_core_tables.php
database/migrations/2026_07_28_000002_create_device_monitoring_tables.php
database/migrations/2026_07_28_000003_create_device_command_tables.php
database/migrations/2026_07_28_000004_create_device_policy_audit_release_tables.php
database/seeders/CommandCenterPermissionSeeder.php

resources/views/command-center/managed.blade.php
tests/Feature/CommandCenter/Concerns/BuildsCommandCenterDatabase.php
tests/Feature/CommandCenter/DeviceSignatureTest.php
tests/Feature/CommandCenter/ManagedDeviceFlowTest.php
```

Folder `tests/` boleh tidak diunggah ke produksi apabila deployment package
memang tidak menyertakan test suite.

## 3. File backend yang harus ditimpa

Backup versi produksi sebelum menimpa file berikut:

```text
.env.example
app/Filament/Widgets/HealthIssuesChart.php
app/Http/Controllers/CommandCenterController.php
app/Models/User.php
app/Providers/AppServiceProvider.php
app/Providers/AuthServiceProvider.php
bootstrap/app.php
config/app.php
config/filesystems.php
resources/views/filament/pages/crosstab-analysis.blade.php
resources/views/layouts/admin.blade.php
routes/api.php
routes/console.php
routes/web.php
```

Keterangan:

- `CommandCenterController.php` sekarang hanya melayani URL secret lama bila
  feature legacy diaktifkan dan token benar-benar dikonfigurasi.
- `HealthIssuesChart.php` memperbaiki nama class agar sesuai PSR-4.
- `crosstab-analysis.blade.php` mengganti komponen label Filament yang tidak
  tersedia; tanpa perbaikan ini `php artisan view:cache` gagal.
- route debug/migrate/cache yang sebelumnya terbuka kini hanya didaftarkan pada
  environment `local`.
- endpoint monitoring APK lama yang membaca data atau mengirim command kini
  membutuhkan role `admin` atau `super_admin`.

Jangan menimpa seluruh folder aplikasi secara buta karena worktree juga memuat
pengembangan lain. Unggah file satu per satu atau gunakan deployment Git yang
terkontrol.

## 4. File yang tidak perlu diunggah ke document root website

```text
KabenSatuDataWindowsAgent/src/
KabenSatuDataWindowsAgent/tests/
KabenSatuDataWindowsAgent/scripts/build.ps1
KabenSatuDataWindowsAgent/KabenSatuData.Agent.slnx
PLANNING_KABEN_COMMAND_CENTER_WINDOWS.md
```

Source Windows Agent disimpan di repository pengembangan. Paket pilot yang
didistribusikan ke komputer adalah:

```text
KabenSatuDataWindowsAgent/artifacts/KabenSatuDataAgent-win-x64.zip
KabenSatuDataWindowsAgent/artifacts/KabenSatuDataAgent-win-x64.zip.sha256
```

SHA-256 pilot:

```text
214e9d041a06363765fa715ef25c71e646ae1f05fcc13f49f14c4199f6a4156e
```

Distribusikan ZIP melalui media internal/terproteksi. Jangan meletakkannya pada
folder publik tanpa autentikasi. Tabel `agent_releases` sudah tersedia untuk
pengembangan auto-update, tetapi auto-update belum diaktifkan pada Agent pilot.

## 5. Environment variable produksi

Edit `.env` produksi. Jangan mengunggah `.env` lokal dan jangan mengisi secret
ke `.env.example`.

```dotenv
APP_ENV=production
APP_DEBUG=false
APP_URL=https://DOMAIN-PRODUKSI

COMMAND_CENTER_ENABLED=false
COMMAND_CENTER_LEGACY_SECRET_URL=false
CC_TOKEN=
COMMAND_CENTER_DEFAULT_SITE_CODE=KODE-PUSKESMAS

COMMAND_CENTER_ENROLLMENT_TTL_MINUTES=10
COMMAND_CENTER_COMMAND_POLL_SECONDS=5
COMMAND_CENTER_HEARTBEAT_SECONDS=60
COMMAND_CENTER_OFFLINE_AFTER_SECONDS=180

COMMAND_CENTER_CPU_ALERT_PERCENT=95
COMMAND_CENTER_MEMORY_ALERT_PERCENT=90
COMMAND_CENTER_DISK_ALERT_PERCENT=90

COMMAND_CENTER_ARTIFACT_DISK=command_center_private
COMMAND_CENTER_MAX_ARTIFACT_KB=20480
COMMAND_CENTER_SCREENSHOT_RETENTION_DAYS=7
COMMAND_CENTER_MEDIA_RETENTION_DAYS=3
COMMAND_CENTER_METRIC_RETENTION_DAYS=30
COMMAND_CENTER_AUDIT_RETENTION_DAYS=365

DEVICE_REQUEST_SIGNATURE_REQUIRED=true
DEVICE_REQUEST_CLOCK_SKEW_SECONDS=120
DEVICE_REQUEST_NONCE_TTL_SECONDS=300
```

Mulai dengan `COMMAND_CENTER_ENABLED=false`. Ubah menjadi `true` hanya setelah
migration dan seeder berhasil. Nilai artifact pilot disarankan 20 MiB agar
lebih realistis untuk shared hosting. Jika diagnostic ZIP perlu lebih besar,
samakan `COMMAND_CENTER_MAX_ARTIFACT_KB`, `upload_max_filesize`,
`post_max_size`, dan limit reverse proxy.

Pastikan waktu server cPanel dan komputer Windows tersinkron NTP. Selisih lebih
dari dua menit akan ditolak oleh anti-replay signature.

## 6. Urutan deployment cPanel

Ganti `/home/USER/path-aplikasi` dengan path sebenarnya.

1. Backup database dan file yang akan ditimpa.
2. Set `COMMAND_CENTER_ENABLED=false` pada `.env`.
3. Unggah semua file pada bagian 2 dan 3.
4. Dari cPanel Terminal, masuk ke root Laravel:

```bash
cd /home/USER/path-aplikasi
php artisan config:clear
php artisan route:clear
php artisan view:clear
php artisan migrate --force
php artisan db:seed --class=CommandCenterPermissionSeeder --force
```

5. Pastikan folder private dapat ditulis oleh user PHP:

```bash
mkdir -p storage/app/private/command-center
chmod 750 storage/app/private/command-center
```

6. Ubah `.env` menjadi:

```dotenv
COMMAND_CENTER_ENABLED=true
```

7. Bangun cache produksi:

```bash
php artisan optimize
```

8. Verifikasi route:

```bash
php artisan route:list --path=command-center
php artisan route:list --path=api/agent/v1
```

Hasil yang diharapkan adalah 14 route dashboard/admin dan 12 route Agent.
`/cc/{token}` tidak boleh muncul selama legacy mode `false`.

## 7. Cron cPanel

Laravel scheduler wajib berjalan setiap menit:

```cron
* * * * * /usr/local/bin/php /home/USER/path-aplikasi/artisan schedule:run >/dev/null 2>&1
```

Sesuaikan lokasi binary PHP dari menu cPanel. Scheduler menangani:

- penandaan perangkat offline;
- alert offline;
- command kedaluwarsa;
- pembersihan heartbeat, metric, audit, dan artifact berdasarkan retention.

Tanpa cron, heartbeat dan command tetap berfungsi tetapi status offline,
expiry, dan retention tidak konsisten.

## 8. Smoke test produksi

1. Login dengan akun `admin`, `super_admin`, atau role Command Center.
2. Buka `/command-center`; response harus `no-store` dan tidak dapat dibuka
   tanpa login.
3. Pastikan `/cc/sembarang-token` menghasilkan 404.
4. Pastikan route `/run-migrate`, `/clear-cache`, `/debug-*`, dan
   `/api/mobile/debug-auth` tidak ada pada `APP_ENV=production`.
5. Buat enrollment satu kali dari dashboard.
6. Ekstrak paket Agent pada satu komputer pilot Windows 10/11 x64.
7. Jalankan PowerShell sebagai Administrator:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\install.ps1 -ServerUrl "https://DOMAIN-PRODUKSI"
```

8. Masukkan kode enrollment.
9. Pastikan perangkat online maksimal tiga menit.
10. Uji metrics, device info, proses, aplikasi, screenshot, dan diagnostic.
11. Uji lock/restart/shutdown paling akhir dengan alasan yang jelas.
12. Verifikasi command dan download artifact tercatat di audit log.

## 9. Rollback aman

Jika ada masalah produksi:

1. Set `COMMAND_CENTER_ENABLED=false`.
2. Jalankan `php artisan config:clear` lalu `php artisan config:cache`.
3. Stop service Agent pada komputer pilot bila diperlukan.
4. Pulihkan file backend dari backup.

Jangan langsung menjalankan `migrate:rollback` saat insiden. Migration bersifat
additif dan menyimpan audit/telemetry; menonaktifkan feature switch lebih aman
serta tidak menghapus bukti operasional.

## 10. Catatan database existing

Test Command Center menggunakan schema terisolasi karena repository lama
memiliki urutan historical migration yang tidak cocok untuk instalasi database
kosong (`villages` diubah oleh migration yang timestamp-nya lebih awal daripada
migration pembuat tabel). Deployment pada database produksi existing aman
selama seluruh migration lama sudah tercatat pada tabel `migrations`. Untuk
fresh install baru, historical migration perlu dirapikan pada pekerjaan
terpisah sebelum menjalankan `migrate:fresh`.
