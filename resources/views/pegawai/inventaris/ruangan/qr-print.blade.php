<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <title>QR — {{ $ruangan->nama }}</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        @page { size: A4; margin: 0; }
        body { font-family: 'Segoe UI', Tahoma, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #fff; }
        .card { text-align: center; padding: 40px; }
        .logo { font-size: 16px; color: #475569; margin-bottom: 8px; letter-spacing: 1px; text-transform: uppercase; }
        .room-name { font-size: 32px; font-weight: 700; color: #0f172a; margin-bottom: 4px; }
        .room-code { font-size: 14px; color: #64748b; margin-bottom: 30px; }
        .qr-container { display: inline-block; padding: 20px; background: #fff; border: 3px solid #e2e8f0; border-radius: 16px; margin-bottom: 20px; }
        .url { font-size: 11px; color: #94a3b8; word-break: break-all; max-width: 320px; margin: 0 auto 24px; }
        .instruction { font-size: 13px; color: #64748b; background: #f1f5f9; padding: 12px 20px; border-radius: 8px; display: inline-block; }
        .footer { margin-top: 30px; font-size: 11px; color: #94a3b8; }
        @media print {
            body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            .no-print { display: none; }
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="logo">Puskesmas Kabensari</div>
        <div class="room-name">{{ $ruangan->nama }}</div>
        <div class="room-code">
            @if($ruangan->kode){{ $ruangan->kode }} · @endif
            @if($ruangan->lantai)Lantai {{ $ruangan->lantai }}@endif
        </div>

        <div class="qr-container">
            {!! \SimpleSoftwareIO\QrCode\Facades\QrCode::size(250)->margin(1)->generate($ruangan->getPublicUrl()) !!}
        </div>

        <div class="url">{{ $ruangan->getPublicUrl() }}</div>

        <div class="instruction">
            📱 Scan QR code untuk melihat daftar inventaris ruangan ini
        </div>

        <div class="footer">
            Sistem Inventaris Digital — KabenSatuData
        </div>

        <button onclick="window.print()" class="no-print" style="margin-top:20px; padding:10px 24px; background:#3b82f6; color:#fff; border:none; border-radius:8px; cursor:pointer; font-size:14px;">
            🖨️ Cetak
        </button>
    </div>
</body>
</html>
