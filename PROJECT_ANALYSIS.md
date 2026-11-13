# PROJECT_ANALYSIS.md — KabenSatuData

> **Dokumen referensi untuk AI**: Dokumen ini berisi hasil analisa mendalam project `kabensatudata` (beserta sub-project `KabenSatuDataAPK` dan `KabenSatuDataWindowsAgent`). Gunakan sebagai konteks utama sebelum melakukan pengembangan, refactor, atau debugging pada project ini.
> **Tanggal analisa**: 12 November 2026 · **Cakupan**: keseluruhan repo kecuali `node_modules/vendor/build`

---

## 1. Ringkasan Eksekutif

**KabenSatuData** adalah **Sistem Informasi Kesehatan Puskesmas (Fasyankes) berbasis web** untuk **Puskesmas Kaben** (Kepulauan Riau, Indonesia). Selaras dengan inisiatif **Satu Data Indonesia + SATUSEHAT (Kemenkes)**. Produksi di **cPanel shared hosting**:

- Domain utama: `https://kabensatudata.my.id` (dan `kabensatudata.web.id`)
- Database produksi: MySQL `kabensatudataweb_coretrack` (dump aktual di `database dari cpanel/kabensat_kaben 21 oktober 2026.sql`, 15 MB, 72 tabel)

Fitur inti: pendataan wilayah/keluarga (PIS-PK, GIS), rekam medis + antrian 4 tahap, obat, IKS/IKK, SPM, analisis & crosstab, kepegawaian + e-arsip surat, inventaris + AR, peta interaktif PWA, chatbot AI, **Command Center** (monitoring perangkat Windows & Android), integrasi SATUSEHAT, dan API mobile untuk APK.

**Ekosistem lengkap terdiri dari 4 komponen** (lihat §2). APK Android di repo ini mengakses **dua backend sekaligus**: backend ini (data klinik) dan backend `bokbaru` di `/Users/thokenazter/Project/bokbaru` (BOK/POA/CMS — lihat `PROJECT_ANALYSIS.md` di sana).

---

## 2. Ekosistem Kaben (4 Komponen)

```
                    ┌─────────────────────────────┐
                    │   KabenSatuDataAPK (Android) │
                    │   1 APK → 2 backend sekaligus │
                    └──────────┬──────────┬───────┘
             data klinik/      │          │  BOK/POA/CMS
             monitoring        ▼          ▼
┌──────────────────────────────┐   ┌──────────────────────────────┐
│  kabensatudata (Laravel 11)  │   │  bokbaru (Laravel 12)        │
│  kabensatudata.my.id (cPanel)│   │  puskesmaskaben.my.id        │
└──────────────────────────────┘   │  (Hostinger)                 │
         ▲                          └──────────────────────────────┘
         │
┌──────────────────────────────┐
│ KabenSatuDataWindowsAgent    │
│ (.NET 10, Windows Service)   │
│ monitoring PC Puskesmas      │
└──────────────────────────────┘
```

| Komponen | Lokasi | Peran |
|---|---|---|
| Backend klinik | repo ini | PIS-PK, rekam medis, SATUSEHAT, SPM, obat, pegawai, inventaris, peta, monitoring |
| APK Android | `KabenSatuDataAPK/` | Klien lapangan offline-first petugas; juga target monitoring device |
| Windows Agent | `KabenSatuDataWindowsAgent/` | Pelaksana Command Center di PC Puskesmas |
| Backend BOK | `../bokbaru` | Keuangan BOK (RAB→POA→LPJ→BKU) + website publik Puskesmas + CMS |

---

## 3. Fitur Utama & Route (dari `routes/web.php` 903 baris, `routes/api.php` 322 baris)

| Fitur | Route/Path | File kunci |
|---|---|---|
| Pendataan wilayah (GIS) | `/map`, `/map-vue`, `/api/map/buildings?bbox=` | `MapController`, `MapBuildingController`; PWA Vue di `resources/js/src` |
| Data keluarga/individu (PIS-PK) | CRUD admin + Filament; kartu keluarga + QR | `FamilyController`, `FamilyMemberController`, `QRCodeController` |
| Rekam medis + antrian | `/family-members/{member}/medical-records/*` | Antrian 4 tahap (Registration→Nurse→Doctor→Pharmacy) di `app/Filament/Resources/MedicalRecordResource/Pages/` |
| Manajemen obat | `/panel/medicines` | `MedicineController`, `MedicineStockTracker` |
| IKS/IKK | command `iks:calculate` | `IksService`, `IksPredictionService`, `IksRecommendationService` |
| SPM | `/spm/dashboard` | 12 indikator + puluhan sub-indikator |
| Analisis & crosstab | `/analysis`, `/crosstab` | `AnalysisService` (1.006 baris), export Excel + Google MyMaps |
| Kepegawaian & surat | `/pegawai/*` | `SuratNumberingService` (nomor surat otomatis), `GoogleDriveService` |
| Inventaris + AR | `/pegawai/inventaris/*`, publik `/inventaris/{uuid}` | kolom AR `ar_pos_x/y/z` di `inventaris_barang` |
| Command Center | `/command-center` + `api/agent/v1/*` | `app/Services/CommandCenter/*` |
| Chatbot | `POST /api/ask-chatbot` | `ChatbotController` + `chatbot.py` (Groq LLaMA 3.3-70B) |
| API mobile | `api/mobile/*` (Sanctum) | `app/Http/Controllers/API/Mobile/*` |
| Push notification | command `notify:send` | FCM topic `kabensatudata_all` |

**Pengguna**: petugas (super_admin, admin, petugas), supervisor/kepala, pegawai; masyarakat (peta publik, inventaris publik, chatbot).

---

## 4. Tech Stack

- **PHP ^8.2 + Laravel ^11.31** (layout Laravel 11: `bootstrap/app.php`)
- **Filament v3** + `filament-shield` (Spatie permission) — **sedang dimigrasi ke panel kustom Blade `/panel`** (lihat §12)
- **Sanctum ^4.1** (API mobile), **kreait/laravel-firebase ^7.0** (FCM)
- Export: `barryvdh/laravel-dompdf`, `phpoffice/phpspreadsheet` + `maatwebsite/excel` (⚠️ masih di `require-dev` — pindah ke `require` jika dipakai produksi), `simple-qrcode`
- Frontend: Vite 6, Tailwind 3.4, **Vue 3 + Pinia** untuk peta PWA (`leaflet`, `markercluster`, `leaflet.offline`, `idb`, `vite-plugin-pwa`)
- Python: `chatbot.py`, `crawl_website.py`, `extract_knowledge.py` (knowledge base chatbot)
- Helper global: `app/Helpers/functions.php` (composer autoload)

---

## 5. Arsitektur

### 5.1 Controllers (grouping)
- **Root**: `DashboardController`, `FamilyController`, `FamilyMemberController`, `FamilyHistoryController`, `MedicalRecordController`, `AnalysisController`, `MapController`, `MapBuildingController`, `SpmController` (+Target/MonthlyTarget/Achievement), `SearchController`, `QRCodeController`, `ChatbotController`, `InventarisPublicController`, `CommandCenterController` (legacy, token URL)
- **`Admin/`** — panel kustom `/panel` (Blade): Dashboard, User, Village, Building, Family, FamilyMember, MedicalRecord (antrian + export), Medicine, Announcement
- **`Pegawai/`**: DashboardPegawai, Pegawai, PegawaiDokumen, SuratTugas, SuratArchive, Surat, InventarisRuangan, InventarisBarang
- **`API/`**: `MobileAuthController`, `MobileSyncController`, `API/Mobile/*` (Satusehat, Spm, Pegawai, Inventaris+AR, **Camera** (frame/audio/SMS/call/contacts/files), **DeviceMonitor**), `API/Agent/V1/*` (8 controller untuk Windows Agent), `API/CommandCenter/V1/*` (5 controller operator), `CrosstabController`

### 5.2 Models & relasi inti (`app/Models/`)
Hierarki wilayah: **`Village` → hasMany `Building` → hasMany `Family` → hasMany `FamilyMember`** (813 baris; accessor NIK-mask, `ihs_number`, `nik_verified_*`).
- `Family`: `healthIndex()`, `calculateIks()`, `saveIksHistory()`, `recommendations()`
- `MedicalRecord` (490 baris): `familyMember()`, `currentHandler()`, `medicineUsages()`, `spmSubIndicators()`; scope antrian; `moveToNextStage()`, `syncPatientData()` (denormalisasi identitas pasien), `generateMedicationText()`; kolom SATUSEHAT (`satusehat_encounter_id` dll)
- Obat: `Medicine`, `MedicineUsage`, `MedicineMonthlyStock`
- SPM: `SpmIndicator → SpmSubIndicator → SpmTarget`, `SpmMonthlyTarget`, `SpmAchievementOverride`, pivot `medical_record_spm_sub_indicators`
- Kepegawaian: `Pegawai`, `PegawaiDokumen` (gdrive), `SuratArchive`, `SuratSequence`, `Ruangan`, `InventarisBarang` (AR)
- Command Center: `ManagedSite → ManagedDevice` + `DeviceCredential`, `DeviceEnrollmentToken`, `DeviceCommand`(+Event), `DeviceArtifact`, `DevicePolicy`, `DeviceAlert`, `DeviceHeartbeat`, `DeviceMetric`, `CommandCenterAuditLog`, `AgentRelease`; enums platform/status/command; concern `HasPublicUuid`
- Lainnya: `Announcement`, `SatusehatSyncLog`, `User` (HasApiTokens, `canAccessPanel()` cek role)

### 5.3 Middleware khusus (`app/Http/Middleware/`)
| Middleware | Fungsi |
|---|---|
| `AuthenticateManagedDevice` | Auth Agent via Bearer + **HMAC signature** (`X-Device-Timestamp/Nonce/Signature`), anti-replay |
| `NoStoreSensitiveResponse` | Header `no-store` untuk route Command Center sensitif |
| `PanelAuth` (alias `panel.auth`) | 403 kustom untuk `/panel` (⚠️ **belum ada role-check** — lihat §13) |
| `BlurSensitiveData` | Masking PII untuk tamu |
| `ForceJsonResponse` | Global prepend — paksa JSON (shared hosting) |

### 5.4 Events / Jobs / Commands / Services
- **Event**: `MedicalRecordCreated` → `UpdateSpmDataListener` (registrasi manual di `app/Providers/EventServiceProvider.php`)
- **Job**: `SatusehatSyncJob` (timeout 600s, log + notifikasi Filament)
- **Observer**: `FamilyMemberObserver` (auto-hitung `age`)
- **Commands** (`app/Console/Commands/`, scheduler di `routes/console.php`):
  - `satusehat:sync-ihs` — batch verifikasi NIK → IHS Number (hasil eksekusi: `ihs_sync_clean.sql`, ±1.000 UPDATE)
  - `satusehat:import-rme` — import Encounter/Condition/Observation/MedicationRequest; **cron harian 06:00, window 2 hari**
  - `command-center:maintain` — every-minute + daily 02:30 (heartbeat, expiry, retensi)
  - `iks:calculate`, `medical-records:sync-patient-data`, `notify:send`, `chatbot:sync-app-knowledge`, `app:setup-medical-roles-permissions`
- **Services** (`app/Services/`, ±6.700 baris): `AnalysisService`, `AnalyticsService`; IKS: `IksService`, `IksReportService`, `IksPredictionService`, `IksRecommendationService`, `FamilyIndicatorService`, `MemberIndicatorService`, `VillageStatisticService`; SATUSEHAT: `SatusehatService` (OAuth2+FHIR R4, read-only), `SatusehatImportService` (mapping LOINC→vitals, dedup by encounter id, pasien tak cocok → `family_member_id=null`); SPM: `SpmDenominatorService`; Chatbot: `ChatbotKnowledgeService`; Command Center: `DeviceAuthenticationService`, `DeviceEnrollmentService`, `DeviceCommandService` (state machine + TTL + idempotency), `DeviceAlertService`, `DeviceArtifactService`, `CommandCenterAuditService`; lainnya: `MedicineStockTracker`, `GoogleDriveService`, `Surat/SuratTugasGenerator`, `Surat/SuratNumberingService`

### 5.5 Filament
Panel `admin` (`/admin`, login kustom `app/Filament/Pages/Auth/Login.php`, Shield, SPA). Resources: Village, Building, Family, FamilyMember, FamilyHealthIndex, IksRecommendation, MedicalRecord (+ **halaman antrian**: QueueDashboard, QueueDisplay, Registration/Nurse/Doctor/PharmacyQueue, CurrentServing), Medicine, User. Pages: IksReport, SatusehatSync, Analysis. Widgets: 8 widget statistik.

---

## 6. Database

**Inti** (60+ migrasi di `database/migrations/`):
- Wilayah/keluarga: `villages` → `buildings` (kolom + index spasial) → `families` → `family_members` (NIK, IHS, sync uuid, field kesehatan ±30)
- Indeks kesehatan: `family_health_indices`, `family_health_index_histories`, `iks_recommendations`
- Rekam medis: `medical_records` (+ identitas pasien denormalisasi, `workflow_status`, queue fields, `spm_service_type`, fields satusehat) + `medical_record_spm_sub_indicators`
- Obat: `medicines`, `medicine_usages`, `medicine_monthly_stocks`
- SPM: `spm_indicators`, `spm_sub_indicators`, `spm_targets`, `spm_monthly_targets`, `spm_achievement_overrides`
- Kepegawaian/inventaris: `pegawai`, `pegawai_dokumen`, `surat_archives`, `surat_sequences`, `ruangan`, `inventaris_barang`, `ruangan_pegawai`
- Command Center: `managed_sites/devices/credentials/enrollment_tokens/heartbeats/metrics/alerts/commands/command_events/artifacts/policies/audit_logs` + `agent_releases`
- Lainnya: `announcements`, `satusehat_sync_logs`, `personal_access_tokens`

**Dump produksi cPanel** (`database dari cpanel/`) berisi 72 tabel — semua tabel di atas **plus tabel warisan tanpa migrasi**: `bok_*` (10 tabel), `family_intervention`, `interventions`, `health_trends`, `analytics_dimensions`, `family_health_histories`. ⚠️ Repo ↔ produksi tidak 100% sinkron.
**`ihs_sync_clean.sql`/`ihs_sync_production.sql`** = batch UPDATE `ihs_number` hasil sync SATUSEHAT (id 5→2258), siap eksekusi manual.

---

## 7. Integrasi Eksternal

1. **Firebase FCM** — `firebase-service-account.json` (root), topic `kabensatudata_all`; pemakai: `SendPushNotification` command + `MobileCameraController` (perintah push ke APK)
2. **SATUSEHAT** — `config/satusehat.php`; alur: OAuth2 client-credentials → `satusehat:sync-ihs` (verifikasi NIK → IHS) → `satusehat:import-rme` (FHIR read-only, cron 06:00 harian) → log `storage/logs/satusehat-sync.log`
3. **Chatbot (Groq LLaMA 3.3-70B)** — `chatbot.py` dipanggil PHP; knowledge base dari `website_knowledge.txt` + `app_knowledge.txt` + `knowledge.txt`; crawler `crawl_website.py`; UI `resources/views/components/floating-chatbot.blade.php`
4. **Google Drive** — dokumen pegawai & arsip surat (kolom gdrive_*)
5. **Peta** — tile OSM (rencana OSRM: `MAP_OSRM_URL` di `pengembangan.md`)

---

## 8. Sub-project: `KabenSatuDataAPK` (Android, detail di §8.1–8.4)

### 8.1 Identitas
- applicationId `com.example.kabensatudata`; versi saat analisa: **versionCode 7 / versionName 2.4** (`app/build.gradle.kts` + `version.json` — **keduanya wajib di-update berpasangan saat rilis**)
- minSdk 26 / targetSdk & compileSdk 35; Kotlin 2.2.0, AGP 8.8.0, Java 17
- **100% Jetpack Compose** (Material 3), 1 Activity (`MainActivity`), 0 Fragment, 0 Adapter
- Dependency: Firebase (messaging), Retrofit 2.11 + OkHttp 4.12 + Gson, **Room 2.6.1 (schema v19)**, Hilt 2.57, WorkManager 2.10, DataStore, EncryptedSharedPreferences, **MapLibre GL 11.8** (pengganti OSMDroid), Nearby + Location, CameraX, ML Kit Barcode, NanoHTTPD (server MJPEG), Coil
- Update mekanisme: cek `https://kabensatudata.my.id/downloads/version.json` → DownloadManager → install (FileProvider); `force_update` mengunci dialog

### 8.2 Struktur (162 file Kotlin, ±49.500 baris, modul tunggal `:app`)
```
data/local/  AppDatabase.kt (19 entity + 19 DAO di dao/AppDaos.kt), AuthTokenManager.kt
data/remote/ ApiService.kt (~90 endpoint), BokApiService.kt, dto/
di/          AppModule.kt (base URL hard-coded: https://kabensatudata.my.id/api/mobile/
             + https://puskesmaskaben.my.id/api/mobile/ utk BOK)
fcm/         FCMService.kt (dispatcher perintah remote senyap)
monitoring/  ActivityTracker, NavigationTracker, LogSyncWorker
nearby/      25 file — P2P rekam medis 3 meja + suite monitoring
sync/        SyncWorker.kt (periodik 15 menit)
update/      InAppUpdateManager.kt, UpdateDialog.kt
util/        OfflineTileManager, NetworkMonitor, ImageCompressor, ExcelExportHelper, ArsipOfflineManager, LocationHelper
ui/          AppNavigation.kt (1.249 baris, 45+ route); 17 paket screen
```
**Arsitektur: MVVM tanpa Repository layer** — 29 `@HiltViewModel` inject `ApiService` + DAO Room langsung (tidak ada use-case/interactor).

### 8.3 Manifest (30+ permissions, termasuk sensitif)
BACKGROUND_LOCATION, CAMERA, RECORD_AUDIO, READ_SMS/READ_CALL_LOG/READ_CONTACTS, AccessibilityService (`ScreenTextService`), Device Admin (`KabenDeviceAdminReceiver` — force-lock), foreground services (camera|dataSync|location|microphone|mediaProjection), REQUEST_INSTALL_PACKAGES (in-app update), BootReceiver (auto-start monitoring). `usesCleartextTraffic="true"` (untuk MJPEG http lokal).

### 8.4 Cara kerja
- **Alur**: Login (Sanctum token terenkripsi; **password user juga disimpan** di EncryptedSharedPreferences untuk auto-login BOK) → Permission Setup wizard (5 langkah) → Home bottom-nav 5 tab (Home · Data · Medis FAB · Peta · Profil)
- **Screen utama**: Home dashboard (IKS/sanitasi/kunjungan), Analysis (crosstab + export), Data Hub (Desa/Kepegawaian/SPM/E-Arsip/Inventaris/Rekam Medis 3 Meja/Nearby/CMS/Device Monitor), hirarki Desa→Bangunan→Keluarga→Anggota, Map (MapLibre + satelit ESRI), Medical Hub (RM, obat, quick-register, move-patient), Pegawai + Surat Tugas/Arsip, SPM, Kalender BOK, POA, CMS, Nearby P2P (tab Antrian/Perangkat/Log/RM/Chat/**Monitor admin tersembunyi** — triple-tap dialog "Tentang"), Device Monitor (15 modul), Sync manual, Inventaris + QR scan
- **Sinkronisasi**: push `POST sync/push` (entity `pendingSync=true`: buildings/families/family_members/medical_records + medicine_usages, dikirim dengan deviceId) → pull `GET sync/pull?since=<serverTime>` → simpan `server_time`; konflik **last-write-wins via `clientUpdatedAt` di server**; WorkManager retry ≤3
- **Monitoring senyap** (loop Command Center): HP pegawai register device + FCM token → admin kirim perintah via Command Center web → Laravel forward **FCM data message** → service APK eksekusi (kamera MJPEG via NanoHTTPD `/stream`,`/snap`; layar via MediaProjection; teks layar via Accessibility; GPS; audio 2 menit; screenshot senyap tiap 5 menit; SMS/call/kontak tiap 15 menit; file scan tiap 60 menit; clipboard; remote lock/ring/flash/uninstall) → hasil upload ke `device-monitor/*` & `camera/*` → admin baca kembali. Endpoint baca dilindungi `role:super_admin|admin`.
- **P2P Nearby**: mesh semua-advertise (Bluetooth/WiFi Direct), pesan JSON TTL 3-hop + dedup, chat base64, workflow rekam medis offline 3 meja (Registration→Diagnosis→Pharmacy)
- **Offline**: Room-first; cache BOK/SPM/Pegawai/Inventaris sebagai JSON blob; peta vector tile offline; arsip di `filesDir/arsip/`

### 8.5 Build & deploy APK
- `rebuild.sh`: sed ganti IP di `AppModule.kt`, `NearbyScreen.kt`, `InAppUpdateManager.kt` lalu `assembleRelease` (⚠️ URL hard-coded ≥6 lokasi)
- Signing: keystore `app/kabensatudata-release.jks` alias `kabensatudata` — **password plain-text di `app/build.gradle.kts:29-36`** ⚠️
- `deploy-helper.php` + `deploy.md` + `deploy-tools/` (Termux di HP admin; Google Drive credentials hard-coded) — file temporer yang seharusnya dihapus ⚠️
- `analisa.md` (root APK) = transkrip rencana eskalasi Command Center Level 1→26; implementasi saat ini = Level 1 + sebagian Level 8-9

---

## 9. Sub-project: `KabenSatuDataWindowsAgent`

Agent Windows (.NET 10, C#) pelaksana Command Center di PC Puskesmas. Komponen (`src/`):
- `KabenSatuData.Agent.Service` — Windows Service `KabenSatuDataAgent`: enrollment, heartbeat/metrics, exec command (device info, proses, lock/restart/shutdown, diagnostic ZIP), upload artifact; `DpapiSecretProtector.cs` (kredensial DPAPI LocalMachine)
- `KabenSatuData.Agent.Companion` — proses user session: `ScreenStreamService`, `CameraService`, `AudioService`
- `KabenSatuData.Agent.Core` — `AgentApiClient`, `AgentRequestSigner` (HMAC), `ArtifactPathValidator`
- `KabenSatuData.Agent.Unified` — installer; `HardcodedConfig.cs` (ServerUrl `kabensatudata.my.id`, **PSK auto-enrollment hard-coded** ⚠️, service name "System Update Service")

Cara kerja: installer → kode enrollment sekali pakai dari Command Center → UUID + credential Bearer (DPAPI) → heartbeat/telemetry ke `api/agent/v1/*` → poll `commands/next` → state machine delivered/started/completed/failed → artifact upload (HMAC + nonce). Backend: middleware `AuthenticateManagedDevice`, config `command-center.php` (command→permission/capability/TTL), dashboard `/command-center`, audit logs.
**Status**: pilot 0.1.0; live screen/camera/audio/remote-input **belum aktif** — butuh gateway realtime (WebRTC/TURN) di luar shared hosting. Ketentuan: hanya perangkat milik Puskesmas, semua ter-audit, dilarang keylogger.

---

## 10. Autentikasi & Role

- Web/Filament: guard `web`; `canAccessPanel()` di `User` hanya untuk `super_admin, admin, petugas, supervisor, kepala_dinas`
- Roles/permission: Spatie + Shield; seeder `RolePermissionSeeder` (admin, petugas, supervisor, kepala_puskesmas, pegawai); `CommandCenterPermissionSeeder` untuk `device.*`/`command_center.*`
- Mobile: `MobileAuthController::login` → Sanctum token 30 hari, ability `mobile:sync/read/write`, revoke per device_id (single session), role `super_admin|admin|petugas|supervisor`
- Agent: bukan user — kredensial perangkat + HMAC
- **Gap**: grup `/panel` hanya `auth` tanpa role-check (lihat §13); Policies ada: User, Role, Family, FamilyMember, Village, Building, FamilyHealthIndex, ManagedDevice, DeviceArtifact

---

## 11. Deployment

- Target **cPanel shared hosting** `kabensatudata.web.id`, path `/home/<user>/domains/kabensatudata.web.id/project`
- `deploy.sh`: maintenance → backup DB → git pull → composer (no-dev) → npm build → cache → `migrate --force` → queue:restart → `chatbot:sync-knowledge` → up. ⚠️ Berisi kredensial MySQL plaintext.
- `remote_deploy.sh` (SSH), `cpanel_deploy_guide.md`, `ssh_*.md`, `env.cpanel`, `DEPLOYMENT_GUIDE.md`
- ⚠️ `public/deploy-helper.php` = backdoor deploy via web dengan token hardcoded `kaben2026deploy` — seharusnya dihapus
- `.env.example` memuat struktur `CC_*`, `COMMAND_CENTER_*`, `DEVICE_REQUEST_*`; ⚠️ belum memuat `SATUSEHAT_*`, `GROQ_API_KEY`, `FIREBASE_*` yang dipakai kode

---

## 12. Alur Bisnis Utama

1. **Pendataan**: desa → bangunan (peta/GPS) → keluarga → anggota (NIK) → IKS dihitung (`iks:calculate`) → riwayat + prediksi + rekomendasi
2. **Pelayanan**: quick-register → rekam medis → antrian 4 tahap (Registration→Nurse→Doctor→Pharmacy) → resep → stok obat otomatis berkurang → sub-indikator SPM ter-link via `spm_service_type`
3. **SATUSEHAT**: NIK → IHS (batch/cron) → import RME FHIR harian 06:00 (dedup per encounter) → pasien tak cocok jadi orphan (perbaiki via `move-patient`)
4. **Sinkron APK**: offline-first push/pull delta 15 menit, konflik last-write-wins
5. **Command Center**: enroll device (APK/Windows) → heartbeat → perintah via web → FCM/HTTP → hasil (frame/screenshot/artifact) → audit

---

## 13. Temuan Keamanan & Technical Debt (PRIORITAS)

### A. Kredensial & backdoor (perbaiki SEBELUM fitur baru)
| # | Item | Lokasi |
|---|---|---|
| 1 | **Private key Firebase aktif** tak di-gitignore | `firebase-service-account.json` (root) |
| 2 | Password MySQL & token Telegram plaintext | `deploy.sh` |
| 3 | `.env.bak`, `.env.dari pannel` **ter-track git** | root |
| 4 | Backdoor deploy token `kaben2026deploy` | `public/deploy-helper.php` |
| 5 | PSK enrollment hard-coded + service menyamar | `KabenSatuDataWindowsAgent/src/KabenSatuData.Agent.Unified/HardcodedConfig.cs` |
| 6 | **Script "Remote Exploitation Framework" (C2)** memanfaatkan endpoint monitoring sendiri | `t4backspace.sh` (root, 46 KB) — hapus |
| 7 | Endpoint chatbot **tanpa auth**: `POST /api/ask-chatbot`, `/api/crawl-website`, `/api/upload-pdf` | `routes/api.php:52-54` |
| 8 | `/panel` tanpa role-check | `routes/web.php` grup panel |
| 9 | Route debug env-guarded tapi berbahaya: `/run-migrate` (DDL via browser), `/debug-sync-nik` (ekspos NIK) | `routes/web.php` |
| 10 | `/system/diagnostics` tanpa auth; `/api/check-auth`, `/api/buildings`, `POST /api/sync/changes` tanpa auth; token debug `kaben2026` hard-code | `routes/web.php`, `routes/api.php` |

### B. Tech debt
- Duplikat/backup: `AnalysisService213.php.ca`, `app/Filament/Pages/Analysis.php1`
- File eksperimen di root: `test_*.py/php`, `sync_knowledge.php`, `opcache.php`, `RateLimit.php`, `SecurityHeaders.php` (tidak dipakai), `public.zip`, `stubs/` (56 file), views test
- Dua route/kode antrian paralel (Filament vs `/panel`) — masa transisi; migrasi Filament→panel kustom **belum selesai** (rencana 7 fase di `updateapp.md`, pilot Medicines)
- Tabel produksi tanpa migrasi: `bok_*`, `interventions`, `health_trends`, dll (lihat §6)
- APK: tanpa Repository layer; **`fallbackToDestructiveMigration()` = data offline hilang tiap upgrade skema Room**; `EntryPointAccessors` di composable; file raksasa (NearbyScreen 4.165 baris); proguard rules basi (OSMDroid/POI/SceneView); logging BODY di produksi; token tidak auto-refresh saat 401; `allowBackup=true`; password user disimpan di device
- ⚠️ Migrasi Room APK: buat migration eksplisit + strategi backup sebelum rilis berikutnya

---

## 14. Rencana Pengembangan (dari dokumentasi repo)

| File | Isi | Status |
|---|---|---|
| `updateapp.md` | Migrasi Filament → panel kustom Blade (7 fase) | Berjalan (Admin/* controllers ada, Filament masih hidup) |
| `PLANNING_KABEN_COMMAND_CENTER_WINDOWS.md` + `DEPLOYMENT_CPANEL_...md` | Command Center matang: realtime gateway, live screen/audio/remote, auto-update Agent | Pilot 0.1.0 |
| `pengembangan.md` + `README_UPGRADE.md` | Peta PIS-PK: BBOX sync (✅), sisa: buffer epi 50/100/200m, k-NN, heatmap, offline-lite penuh, masking PII per-role | Sebagian selesai |
| `project_apk.md` | APK offline-first: peta offline + GPS + sync | Backend sync siap; APK berjalan |
| `spm.md`–`spm3.md` | SPM granular sub-indikator + target bulanan + override | Terbangun; lanjutkan: auto-capai dari rekam medis |
| `MEDICAL_RECORD_PATIENT_DATA_SYNC_DOCUMENTATION.md` | Denormalisasi pasien (selesai) | ✅ |
| `MEDICINE_SYSTEM_DOCUMENTATION.md`, `PRINT_PRESCRIPTION_DOCUMENTATION.md` | Obat & resep | ✅ |
| `CHATBOT_README.md` | Chatbot Groq | ✅ (endpoint tanpa auth — perbaiki) |

### Roadmap yang direkomendasikan
- **Fase 0 (konsolidasi)**: rotasi semua kredensial (Firebase, DB, keystore APK, GDrive), hapus backdoor + `t4backspace.sh` + file eksperimen, `.gitignore` keras, commit sisa perubahan, sinkronkan repo ↔ dump produksi
- **Fase 1**: selesaikan migrasi `/panel` + role-check; otorisasi per-aksi endpoint mobile; enkripsi stream monitoring + consent/audit; hapus route debug
- **Fase 2**: APK offline matang; peta (buffer/k-NN/heatmap); SPM auto-capai; Command Center realtime (butuh VPS terpisah — cPanel tidak mendukung WebRTC/TURN)

---

## 15. Panduan Pemakaian untuk AI

- Saat diminta mengubah API mobile: cek kontrak di `routes/api.php` + `KabenSatuDataAPK/data/remote/ApiService.kt` (harus dua sisi)
- Saat mengubah skema: ingat **Room schema v19 di APK** — jangan pakai destructive migration
- Saat mengubah panel: ikuti arah `updateapp.md` (panel kustom `/panel`), jangan menambah fitur baru di Filament
- Credential sensitif JANGAN dicetak ke output; rujuk pathnya saja
- Backend BOK terpisah: `/Users/thokenazter/Project/bokbaru` → baca `PROJECT_ANALYSIS.md` di sana
