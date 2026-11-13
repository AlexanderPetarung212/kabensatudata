# AGENTS.md — KabenSatuData

**Sistem Informasi Kesehatan Puskesmas Kaben** (Laravel 11 + Filament, cPanel `kabensatudata.my.id`). Bagian dari ekosistem Kaben 4 komponen (backend ini + APK Android + Windows Agent + backend BOK terpisah).

## WAJIB SEBELUM BEKERJA

Baca **`PROJECT_ANALYSIS.md`** di root repo ini (analisa mendalam: arsitektur, skema DB, integrasi, temuan keamanan, roadmap). Untuk konteks APK: bagian §8 dokumen tersebut. Untuk backend BOK: `../bokbaru/PROJECT_ANALYSIS.md`.

## Guardrails Inti

1. **JANGAN cetak/menyalin kredensial** (`.env`, `firebase-service-account.json`, password di `deploy.sh`, keystore APK) ke output atau commit — rujuk path saja.
2. **Kontrak API dua sisi**: perubahan `routes/api.php` / `app/Http/Controllers/API/Mobile/*` wajib dicek dampaknya ke `KabenSatuDataAPK/app/src/main/java/com/example/kabensatudata/data/remote/ApiService.kt` — dan sebaliknya.
3. **Room (APK)**: JANGAN gunakan `fallbackToDestructiveMigration()` untuk skema baru; buat migration eksplisit (saat ini schema v19).
4. **Arah panel admin**: ikuti `updateapp.md` — fitur baru ke panel kustom `/panel` (Blade), bukan Filament. Grup `/panel` belum ada role-check (gap terdokumentasi §13 PROJECT_ANALYSIS.md).
5. **Monitoring/Command Center**: perubahan endpoint `api/mobile/camera/*`, `device-monitor/*`, `api/agent/v1/*` wajib menjaga otorisasi role (`super_admin|admin`), audit, dan tidak menurunkan keamanan.
6. **Rilis APK**: update `KabenSatuDataAPK/version.json` DAN `app/build.gradle.kts` (versionCode/versionName) berpasangan; URL base hard-coded di ≥6 lokasi (lihat `rebuild.sh`).
7. **Migration Laravel**: jangan ubah migrasi lama; buat migrasi baru. Ada tabel produksi tanpa migrasi (`bok_*`, `interventions`, `health_trends` — lihat §6 PROJECT_ANALYSIS.md).
8. Scheduler penting: `satusehat:import-rme` cron harian 06:00 (`routes/console.php`) — jangan ganggu tanpa sengaja.
