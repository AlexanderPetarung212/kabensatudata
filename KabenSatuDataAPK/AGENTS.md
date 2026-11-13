# AGENTS.md — KabenSatuDataAPK (Android)

Aplikasi Android Jetpack Compose (Kotlin, MVVM tanpa Repository, Room schema v19, Hilt, MapLibre) — klien offline-first Puskesmas Kaben. Memanggil **dua backend**: `kabensatudata.my.id` (data klinik/monitoring) dan `puskesmaskaben.my.id` (BOK/POA/CMS). Saat ini versionCode 7 / versionName 2.4.

## WAJIB SEBELUM BEKERJA

Baca **`../PROJECT_ANALYSIS.md`** (folder induk) — bagian **§8** berisi analisa lengkap APK (struktur, manifest, sync, monitoring, build). Konteks backend: `../PROJECT_ANALYSIS.md` (klinik) dan `../../bokbaru/PROJECT_ANALYSIS.md` (BOK).

## Guardrails Inti

1. **JANGAN cetak/menyalin kredensial**: password keystore di `app/build.gradle.kts`, kredensial di `deploy-helper.php`, `google-services.json`.
2. **Room**: saat ini `fallbackToDestructiveMigration()` di `AppDatabase.kt` (schema v19) — untuk skema baru WAJIB buat migration eksplisit, jangan menambah versi destructive (data offline pasien bisa hilang).
3. **Kontrak API dua sisi**: perubahan `data/remote/ApiService.kt` / `BokApiService.kt` wajib disinkronkan dengan `../routes/api.php` (backend Laravel) — dan sebaliknya.
4. **URL base hard-coded** di `di/AppModule.kt`, `MonitorScreen.kt`, `NearbyScreen.kt`, `InAppUpdateManager.kt` — gunakan `rebuild.sh` untuk ganti server, jangan ganti manual sebagian.
5. **Rilis**: update `version.json` DAN `app/build.gradle.kts` berpasangan; APK didistribusikan via `https://kabensatudata.my.id/downloads/`.
6. **Monitoring/FCM**: `fcm/FCMService.kt` adalah dispatcher perintah Command Center — perubahan format/aksi harus sinkron dengan `../app/Http/Controllers/API/Mobile/MobileCameraController.php`.
7. Struktur satu modul `:app`; ikuti pola MVVM existing (ViewModel inject ApiService + DAO langsung) sampai ada keputusan refactor Repository layer.
