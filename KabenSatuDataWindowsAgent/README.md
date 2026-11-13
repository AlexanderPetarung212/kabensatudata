# KabenSatuData Windows Agent

Windows Agent resmi untuk komputer/laptop milik Puskesmas. Agent terdiri dari:

- `KabenSatuData.Agent.Service`: Windows Service untuk enrollment, heartbeat,
  telemetry, command, inventory, maintenance, upload artifact, dan update.
- `KabenSatuData.Agent.Companion`: proses pada user session untuk screenshot dan
  tindakan interaktif.
- `KabenSatuData.Agent.Core`: signing, API client, dan penyimpanan konfigurasi.
- `KabenSatuData.Agent.Contracts`: kontrak command antara Service dan Companion.

Versi pilot saat ini: `0.1.0` untuk Windows 10/11 x64. Service terdaftar
sebagai `KabenSatuDataAgent`, berjalan otomatis sebagai Windows Service, dan
tetap aktif tanpa dashboard browser dibuka.

## Capability MVP

Sudah diimplementasikan:

- Enrollment sekali pakai.
- Bearer credential yang dilindungi DPAPI LocalMachine.
- Request HMAC, timestamp, dan nonce.
- Heartbeat dan metrics.
- Device info.
- Daftar proses.
- Daftar aplikasi.
- Screenshot on-demand melalui Companion.
- Lock workstation.
- Restart/shutdown.
- Diagnostic bundle.
- Command ACK: delivered, started, completed, failed.
- Upload artifact privat.
- Validasi artifact agar proses user tidak dapat meminta Service mengunggah
  file di luar direktori kerja Agent.

Belum diaktifkan:

- Live screen.
- Kamera snapshot/live.
- Audio.
- Remote mouse/keyboard.
- Auto-update release.

Fitur live membutuhkan proof of concept Windows capture serta realtime
gateway/WebRTC/TURN dan tidak boleh direlay terus-menerus melalui PHP/cPanel.
Implementasi lanjutan wajib menampilkan indikator sesi media pada perangkat,
membatasi durasi, dan mencatat alasan/operator pada audit log.

## Build

Prasyarat:

- .NET SDK 10.
- PowerShell 7 untuk build lintas platform.
- Windows SDK/targeting pack yang dipulihkan oleh `dotnet restore`.

```powershell
./scripts/build.ps1 -Runtime win-x64
```

Output:

```text
artifacts/KabenSatuDataAgent-win-x64.zip
```

## Instalasi pada Windows pilot

1. Ekstrak package.
2. Buka PowerShell sebagai Administrator.
3. Jalankan:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
./install.ps1 -ServerUrl "https://kabensatudata.web.id"
```

4. Masukkan kode enrollment yang dibuat dari Command Center.
5. Verifikasi service `KabenSatuDataAgent` dan perangkat pada dashboard.

Lokasi operasional:

- Program: `C:\Program Files\KabenSatuData\Agent`
- Konfigurasi/secret terenkripsi DPAPI: `C:\ProgramData\KabenSatuData\Agent`
- Event log: Windows Event Viewer, source `KabenSatuData Agent`

Checklist pilot:

1. Perangkat muncul online maksimal tiga menit setelah instalasi.
2. CPU, RAM, disk, dan network tampil di tab Metrics.
3. Command Device info, Proses, Aplikasi, Screenshot, dan Diagnostik selesai.
4. Screenshot hanya dapat diunduh akun yang memiliki permission artifact.
5. Lock/restart/shutdown diuji terakhir pada komputer pilot, dengan alasan
   yang jelas dan tanpa pekerjaan pengguna yang belum disimpan.

Untuk uninstall:

```powershell
./uninstall.ps1
```

Gunakan `-RemoveDeviceData` hanya bila perangkat akan dihapus permanen dan
kredensial sudah dicabut dari Command Center.
