<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <meta name="robots" content="noindex,nofollow,noarchive">
    <title>KabenSatuData Command Center</title>
    <style>
        :root {
            color-scheme: dark;
            --bg: #07111f;
            --panel: #0d1b2d;
            --panel-2: #12243a;
            --line: #203851;
            --text: #e7f0fb;
            --muted: #8fa8c2;
            --blue: #3da9fc;
            --cyan: #42d9c8;
            --green: #46d17d;
            --yellow: #f5c84b;
            --red: #ff6375;
        }
        * { box-sizing: border-box; }
        body {
            margin: 0;
            min-height: 100vh;
            background: radial-gradient(circle at 90% 0, #123255 0, var(--bg) 36%);
            color: var(--text);
            font: 14px/1.45 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        }
        button, input, select, textarea { font: inherit; }
        button { cursor: pointer; }
        .topbar {
            min-height: 70px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 18px;
            padding: 14px 22px;
            border-bottom: 1px solid var(--line);
            background: rgba(7, 17, 31, .9);
            position: sticky;
            top: 0;
            z-index: 10;
            backdrop-filter: blur(18px);
        }
        .brand { display: flex; align-items: center; gap: 12px; }
        .brand-mark {
            width: 42px; height: 42px; border-radius: 13px;
            display: grid; place-items: center;
            background: linear-gradient(135deg, var(--blue), var(--cyan));
            color: #04121e; font-size: 21px; font-weight: 900;
        }
        .brand h1 { margin: 0; font-size: 17px; }
        .brand p { margin: 2px 0 0; color: var(--muted); font-size: 12px; }
        .top-actions { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
        .btn {
            border: 1px solid var(--line);
            background: var(--panel-2);
            color: var(--text);
            border-radius: 9px;
            padding: 9px 12px;
            transition: .16s ease;
        }
        .btn:hover:not(:disabled) { border-color: var(--blue); transform: translateY(-1px); }
        .btn:disabled { opacity: .4; cursor: not-allowed; }
        .btn.primary { background: var(--blue); color: #04101c; border-color: var(--blue); font-weight: 700; }
        .btn.danger { color: #ffdce1; border-color: #743144; background: #351624; }
        .btn.small { padding: 6px 9px; font-size: 12px; }
        .shell { max-width: 1640px; margin: 0 auto; padding: 18px; }
        .stats {
            display: grid;
            grid-template-columns: repeat(4, minmax(130px, 1fr));
            gap: 12px;
            margin-bottom: 14px;
        }
        .stat, .panel {
            border: 1px solid var(--line);
            background: linear-gradient(150deg, rgba(18, 36, 58, .94), rgba(10, 25, 42, .95));
            border-radius: 14px;
            box-shadow: 0 18px 50px rgba(0,0,0,.18);
        }
        .stat { padding: 14px 16px; }
        .stat label { display: block; color: var(--muted); font-size: 11px; text-transform: uppercase; letter-spacing: .08em; }
        .stat strong { display: block; font-size: 26px; margin-top: 5px; }
        .workspace {
            display: grid;
            grid-template-columns: minmax(290px, 360px) minmax(0, 1fr);
            gap: 14px;
            align-items: start;
        }
        .panel-head {
            display: flex; align-items: center; justify-content: space-between; gap: 10px;
            padding: 14px 16px; border-bottom: 1px solid var(--line);
        }
        .panel-head h2 { margin: 0; font-size: 14px; }
        .filters { padding: 12px; display: grid; gap: 8px; border-bottom: 1px solid var(--line); }
        .input {
            width: 100%; border: 1px solid var(--line); border-radius: 8px;
            color: var(--text); background: #081727; padding: 9px 10px; outline: none;
        }
        .input:focus { border-color: var(--blue); }
        .device-list { max-height: calc(100vh - 285px); overflow: auto; padding: 8px; }
        .device {
            width: 100%; text-align: left; color: var(--text);
            border: 1px solid transparent; border-radius: 10px;
            background: transparent; padding: 11px; margin-bottom: 5px;
        }
        .device:hover, .device.active { background: #152c47; border-color: #2e557a; }
        .device-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
        .device-name { font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .device-meta { color: var(--muted); font-size: 11px; margin-top: 5px; }
        .dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 5px; }
        .dot.online { background: var(--green); box-shadow: 0 0 12px var(--green); }
        .dot.idle { background: var(--yellow); }
        .dot.offline, .dot.revoked { background: var(--red); }
        .detail-empty { min-height: 520px; display: grid; place-items: center; color: var(--muted); text-align: center; padding: 30px; }
        .detail { display: none; }
        .detail.visible { display: block; }
        .detail-title { padding: 18px; border-bottom: 1px solid var(--line); }
        .detail-title h2 { margin: 0; font-size: 21px; }
        .detail-title p { margin: 5px 0 0; color: var(--muted); }
        .tabs { display: flex; gap: 4px; overflow-x: auto; padding: 10px 12px; border-bottom: 1px solid var(--line); }
        .tab { white-space: nowrap; border: 0; color: var(--muted); background: transparent; border-radius: 8px; padding: 8px 11px; }
        .tab.active { color: var(--text); background: #17304d; }
        .tab-panel { display: none; padding: 16px; }
        .tab-panel.active { display: block; }
        .info-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
        .info-card { padding: 12px; border: 1px solid var(--line); border-radius: 10px; background: #0a192a; min-height: 76px; }
        .info-card label { color: var(--muted); font-size: 10px; text-transform: uppercase; letter-spacing: .07em; }
        .info-card div { margin-top: 7px; font-weight: 650; overflow-wrap: anywhere; }
        .alert-list { display: grid; gap: 8px; margin-top: 12px; }
        .alert-row { padding: 10px 12px; border: 1px solid #71424b; border-radius: 9px; background: #2d1720; }
        .alert-row.warning { border-color: #735b23; background: #2c2512; }
        .alert-row small { display: block; color: var(--muted); margin-top: 4px; }
        .command-groups { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
        .command-card { border: 1px solid var(--line); background: #0a192a; border-radius: 11px; padding: 12px; }
        .command-card h3 { margin: 0 0 9px; font-size: 12px; color: var(--muted); }
        .command-actions { display: flex; gap: 7px; flex-wrap: wrap; }
        .command-log { margin-top: 14px; display: grid; gap: 7px; }
        .log-row { border: 1px solid var(--line); background: #081522; border-radius: 8px; padding: 9px 10px; font-size: 12px; }
        .log-row strong { color: var(--cyan); }
        .artifact-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
        .artifact { display: block; color: var(--text); text-decoration: none; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; background: #091726; }
        .artifact img { width: 100%; aspect-ratio: 16/10; object-fit: cover; background: #030b13; display: block; }
        .artifact-body { padding: 9px; font-size: 11px; }
        .artifact-body div:last-child { color: var(--muted); margin-top: 3px; }
        .notice { padding: 11px 12px; border: 1px solid #735b23; background: #2c2512; color: #ffe39a; border-radius: 9px; margin-bottom: 12px; }
        .modal {
            position: fixed; inset: 0; z-index: 30; display: none; place-items: center;
            background: rgba(0, 7, 14, .78); padding: 20px;
        }
        .modal.open { display: grid; }
        .modal-card { width: min(520px, 100%); border: 1px solid var(--line); background: var(--panel); border-radius: 15px; padding: 18px; }
        .modal-card h2 { margin: 0 0 14px; }
        .field { display: grid; gap: 5px; margin-bottom: 11px; }
        .field label { color: var(--muted); font-size: 12px; }
        .token-box { padding: 12px; border: 1px dashed var(--cyan); background: #061a1d; color: #a8fff2; border-radius: 9px; overflow-wrap: anywhere; margin-top: 12px; }
        .toast { position: fixed; right: 18px; bottom: 18px; z-index: 50; max-width: 420px; padding: 11px 14px; background: #12304b; border: 1px solid #2f628c; border-radius: 10px; display: none; }
        .toast.show { display: block; }
        .toast.error { background: #3b1820; border-color: #8d3949; }
        .muted { color: var(--muted); }
        @media (max-width: 980px) {
            .workspace { grid-template-columns: 1fr; }
            .device-list { max-height: 320px; }
            .info-grid, .command-groups { grid-template-columns: repeat(2, minmax(0, 1fr)); }
        }
        @media (max-width: 620px) {
            .topbar, .shell { padding: 12px; }
            .topbar { align-items: flex-start; }
            .stats { grid-template-columns: repeat(2, 1fr); }
            .info-grid, .command-groups, .artifact-grid { grid-template-columns: 1fr; }
            .brand p { display: none; }
        }
    </style>
</head>
<body>
<header class="topbar">
    <div class="brand">
        <div class="brand-mark">K</div>
        <div>
            <h1>KabenSatuData Command Center</h1>
            <p>Managed devices · authenticated control plane</p>
        </div>
    </div>
    <div class="top-actions">
        <span class="muted">{{ auth()->user()->name }}</span>
        @can('device.enroll')
            <button class="btn primary" id="openEnrollment">+ Enrollment</button>
        @endcan
        <a class="btn" href="{{ url('/') }}" style="text-decoration:none">Kembali</a>
    </div>
</header>

<main class="shell">
    @if($sites->isEmpty())
        <div class="notice">Akun ini belum ditugaskan ke site/Puskesmas. Super admin perlu menjalankan seeder dan menetapkan site.</div>
    @endif

    <section class="stats">
        <div class="stat"><label>Total perangkat</label><strong id="totalCount">0</strong></div>
        <div class="stat"><label>Online</label><strong id="onlineCount" style="color:var(--green)">0</strong></div>
        <div class="stat"><label>Idle</label><strong id="idleCount" style="color:var(--yellow)">0</strong></div>
        <div class="stat"><label>Offline</label><strong id="offlineCount" style="color:var(--red)">0</strong></div>
    </section>

    <section class="workspace">
        <aside class="panel">
            <div class="panel-head">
                <h2>Perangkat terkelola</h2>
                <button class="btn small" id="refreshDevices">Refresh</button>
            </div>
            <div class="filters">
                <input class="input" id="searchDevice" placeholder="Cari nama perangkat...">
                <select class="input" id="siteFilter">
                    <option value="">Semua site</option>
                    @foreach($sites as $site)
                        <option value="{{ $site->uuid }}">{{ $site->name }}</option>
                    @endforeach
                </select>
            </div>
            <div class="device-list" id="deviceList">
                <div class="muted" style="padding:16px">Memuat perangkat...</div>
            </div>
        </aside>

        <section class="panel">
            <div class="detail-empty" id="detailEmpty">
                <div>
                    <div style="font-size:42px;margin-bottom:10px">🖥️</div>
                    <strong>Pilih perangkat</strong>
                    <div>Pilih komputer atau perangkat Android untuk membuka kontrol dan riwayat.</div>
                </div>
            </div>

            <div class="detail" id="detail">
                <div class="detail-title">
                    <h2 id="detailName">—</h2>
                    <p id="detailSubtitle">—</p>
                </div>
                <nav class="tabs">
                    <button class="tab active" data-tab="overview">Overview</button>
                    <button class="tab" data-tab="commands">Command</button>
                    <button class="tab" data-tab="live">Live View</button>
                    <button class="tab" data-tab="artifacts">Capture & artifact</button>
                    <button class="tab" data-tab="metrics">Metrics</button>
                </nav>

                <div class="tab-panel active" id="tab-overview">
                    <div class="info-grid" id="infoGrid"></div>
                    <div class="alert-list" id="alertList"></div>
                </div>

                <div class="tab-panel" id="tab-commands">
                    <div class="notice">Tindakan sensitif membutuhkan alasan dan selalu masuk audit log. Live media memerlukan Agent yang mendukung capability tersebut.</div>
                    <div class="command-groups">
                        <div class="command-card">
                            <h3>INFORMASI</h3>
                            <div class="command-actions">
                                <button class="btn small" data-command="GET_DEVICE_INFO">Device info</button>
                                <button class="btn small" data-command="GET_PROCESS_LIST">Proses</button>
                                <button class="btn small" data-command="GET_APP_LIST">Aplikasi</button>
                                <button class="btn small" data-command="DIAGNOSTIC_BUNDLE">Diagnostik</button>
                            </div>
                        </div>
                        <div class="command-card">
                            <h3>SCREEN & CAMERA</h3>
                            <div class="command-actions">
                                <button class="btn small" data-command="CAPTURE_SCREENSHOT">Screenshot</button>
                                <button class="btn small" data-command="SCREEN_START">Screen start</button>
                                <button class="btn small" data-command="SCREEN_STOP">Screen stop</button>
                                <button class="btn small" data-command="CAMERA_SNAPSHOT">Camera snapshot</button>
                                <button class="btn small" data-command="CAMERA_START">Camera start</button>
                                <button class="btn small" data-command="CAMERA_STOP">Camera stop</button>
                            </div>
                        </div>
                        <div class="command-card">
                            <h3>PERANGKAT</h3>
                            <div class="command-actions">
                                <button class="btn small" data-command="LOCK">Lock</button>
                                <button class="btn small danger" data-command="RESTART">Restart</button>
                                <button class="btn small danger" data-command="SHUTDOWN">Shutdown</button>
                            </div>
                        </div>
                    </div>
                    <div class="command-log" id="commandLog"></div>
                </div>

                <div class="tab-panel" id="tab-live">
                    <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
                        <select id="liveFrameType" class="input" style="width:auto;min-width:140px">
                            <option value="screen">Layar PC</option>
                            <option value="camera">Webcam</option>
                        </select>
                        <span id="liveStatus" class="muted">Belum streaming</span>
                    </div>
                    <div id="liveViewContainer" style="background:#111;border-radius:8px;overflow:hidden;min-height:200px;display:grid;place-items:center">
                        <img id="liveFrame" src="" alt="Live frame" style="max-width:100%;display:none" />
                        <span id="liveFramePlaceholder" class="muted" style="padding:40px">Klik Screen start atau Camera start untuk streaming.<br>Lalu buka tab ini untuk melihat.</span>
                    </div>
                </div>

                <div class="tab-panel" id="tab-artifacts">
                    <div class="artifact-grid" id="artifactGrid"><span class="muted">Belum dimuat.</span></div>
                </div>

                <div class="tab-panel" id="tab-metrics">
                    <div class="info-grid" id="metricGrid"><span class="muted">Belum dimuat.</span></div>
                </div>
            </div>
        </section>
    </section>
</main>

@can('device.enroll')
<div class="modal" id="enrollmentModal" role="dialog" aria-modal="true">
    <div class="modal-card">
        <h2>Buat kode enrollment</h2>
        <form id="enrollmentForm">
            <div class="field">
                <label>Puskesmas/site</label>
                <select class="input" name="site_uuid" required>
                    @foreach($sites as $site)
                        <option value="{{ $site->uuid }}">{{ $site->name }}</option>
                    @endforeach
                </select>
            </div>
            <div class="field">
                <label>Label perangkat/instalasi</label>
                <input class="input" name="label" maxlength="255" placeholder="Contoh: Komputer Farmasi">
            </div>
            <div class="field">
                <label>Jumlah pemakaian</label>
                <input class="input" type="number" name="max_uses" min="1" max="100" value="1">
            </div>
            <div style="display:flex;justify-content:flex-end;gap:8px">
                <button class="btn" type="button" id="closeEnrollment">Tutup</button>
                <button class="btn primary" type="submit">Buat kode</button>
            </div>
        </form>
        <div id="enrollmentResult"></div>
    </div>
</div>
@endcan

<div class="toast" id="toast"></div>

<script>
const API = @json(url('/command-center/api/v1'));
const CSRF = document.querySelector('meta[name="csrf-token"]').content;
const COMMANDS = @json($commandDefinitions);
const commandAccess = Object.fromEntries(COMMANDS.map(item => [item.name, item]));
let devices = [];
let selected = null;
let refreshTimer = null;

const el = id => document.getElementById(id);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({
    '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#039;'
}[char]));
const formatBytes = value => {
    const bytes = Number(value || 0);
    if (!bytes) return '0 B';
    const units = ['B','KB','MB','GB','TB'];
    const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / Math.pow(1024, index)).toFixed(index ? 1 : 0)} ${units[index]}`;
};
const formatDate = value => value ? new Date(value).toLocaleString('id-ID') : '—';

function toast(message, error = false) {
    const node = el('toast');
    node.textContent = message;
    node.className = `toast show${error ? ' error' : ''}`;
    setTimeout(() => node.className = 'toast', 3500);
}

async function api(path, options = {}) {
    const headers = {
        'Accept': 'application/json',
        'X-CSRF-TOKEN': CSRF,
        ...(options.body ? {'Content-Type':'application/json'} : {}),
        ...(options.headers || {})
    };
    const response = await fetch(`${API}${path}`, {...options, headers});
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
        const firstError = body.errors ? Object.values(body.errors).flat()[0] : null;
        throw new Error(firstError || body.message || `HTTP ${response.status}`);
    }
    return body;
}

async function loadDevices() {
    const params = new URLSearchParams();
    if (el('siteFilter').value) params.set('site_uuid', el('siteFilter').value);
    if (el('searchDevice').value.trim()) params.set('search', el('searchDevice').value.trim());
    try {
        const body = await api(`/devices?${params}`);
        devices = body.data || [];
        if (selected) selected = devices.find(device => device.uuid === selected.uuid) || selected;
        renderDevices();
        renderStats();
        if (selected) renderSelected();
    } catch (error) {
        el('deviceList').innerHTML = `<div class="notice">${escapeHtml(error.message)}</div>`;
    }
}

function renderStats() {
    el('totalCount').textContent = devices.length;
    el('onlineCount').textContent = devices.filter(device => device.status === 'online').length;
    el('idleCount').textContent = devices.filter(device => device.status === 'idle').length;
    el('offlineCount').textContent = devices.filter(device => device.status === 'offline').length;
}

function renderDevices() {
    if (!devices.length) {
        el('deviceList').innerHTML = '<div class="muted" style="padding:16px">Belum ada perangkat terdaftar.</div>';
        return;
    }
    el('deviceList').innerHTML = devices.map(device => `
        <button class="device ${selected?.uuid === device.uuid ? 'active' : ''}" data-device="${escapeHtml(device.uuid)}">
            <div class="device-top">
                <span class="device-name">${escapeHtml(device.device_name)}</span>
                <span><span class="dot ${escapeHtml(device.status)}"></span>${escapeHtml(device.status)}</span>
            </div>
            <div class="device-meta">${escapeHtml(device.platform)} · ${escapeHtml(device.site.name)} · ${escapeHtml(device.last_ip || 'tanpa IP')}</div>
        </button>
    `).join('');
    document.querySelectorAll('[data-device]').forEach(button => {
        button.addEventListener('click', () => selectDevice(button.dataset.device));
    });
}

async function selectDevice(uuid) {
    selected = devices.find(device => device.uuid === uuid);
    renderDevices();
    renderSelected();
    try {
        const body = await api(`/devices/${uuid}`);
        selected = body.data;
        renderSelected();
    } catch (error) {
        toast(error.message, true);
    }
}

function renderSelected() {
    if (!selected) return;
    el('detailEmpty').style.display = 'none';
    el('detail').classList.add('visible');
    el('detailName').textContent = selected.device_name;
    el('detailSubtitle').textContent = `${selected.site.name} · ${selected.platform} · ${selected.status} · terakhir ${formatDate(selected.last_seen_at)}`;
    const values = [
        ['Status', selected.status],
        ['Hostname', selected.hostname],
        ['Sistem', `${selected.os_name || '—'} ${selected.os_version || ''}`],
        ['Arsitektur', selected.architecture],
        ['Agent', selected.agent_version],
        ['IP terakhir', selected.last_ip],
        ['Capability', (selected.capabilities || []).join(', ') || '—'],
        ['Permission Agent', JSON.stringify(selected.permissions_state || {})],
        ['Alert terbuka', selected.open_alerts_count || 0],
    ];
    el('infoGrid').innerHTML = values.map(([label, value]) => `
        <div class="info-card"><label>${escapeHtml(label)}</label><div>${escapeHtml(value ?? '—')}</div></div>
    `).join('');
    const alerts = selected.alerts || [];
    el('alertList').innerHTML = alerts.map(alert => `
        <div class="alert-row ${escapeHtml(alert.severity)}">
            <strong>${escapeHtml(alert.title)}</strong>
            <small>${escapeHtml(alert.message || alert.type)} · ${formatDate(alert.detected_at)}</small>
        </div>
    `).join('');
    document.querySelectorAll('[data-command]').forEach(button => {
        const access = commandAccess[button.dataset.command];
        const capabilityAvailable = !access?.capability || (selected.capabilities || []).includes(access.capability);
        button.disabled = !access?.allowed || !capabilityAvailable;
        button.title = !access?.allowed
            ? 'Permission akun tidak tersedia'
            : (capabilityAvailable ? '' : `Agent belum mendukung ${access.capability}`);
    });
}

async function sendCommand(name) {
    if (!selected) return;
    const definition = commandAccess[name];
    if (!definition?.allowed) return toast('Permission command tidak tersedia.', true);
    if (definition.capability && !(selected.capabilities || []).includes(definition.capability)) {
        return toast(`Agent belum mendukung ${definition.capability}.`, true);
    }
    let reason = null;
    if (definition.sensitive) {
        reason = prompt(`Alasan menjalankan ${name} pada ${selected.device_name}:`);
        if (!reason?.trim()) return;
    }
    try {
        const body = await api(`/devices/${selected.uuid}/commands`, {
            method: 'POST',
            body: JSON.stringify({
                name,
                payload: name === 'AUDIO_START' ? {duration_seconds: 60} : {},
                reason,
                idempotency_key: crypto.randomUUID()
            })
        });
        addCommandLog(body.data);
        toast(`${name} masuk antrean.`);
        pollCommand(body.data.uuid);
        // Auto-switch to Live View for streaming commands
        if (['SCREEN_START','CAMERA_START'].includes(name)) {
            el('liveFrameType').value = name === 'SCREEN_START' ? 'screen' : 'camera';
            document.querySelector('[data-tab="live"]')?.click();
        }
    } catch (error) {
        toast(error.message, true);
    }
}

function addCommandLog(command) {
    const row = document.createElement('div');
    row.className = 'log-row';
    row.id = `command-${command.uuid}`;
    row.innerHTML = `<strong>${escapeHtml(command.name)}</strong> · <span>${escapeHtml(command.status)}</span><div class="muted">${escapeHtml(command.uuid)}</div>`;
    el('commandLog').prepend(row);
}

async function pollCommand(uuid) {
    for (let attempt = 0; attempt < 40; attempt++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const body = await api(`/commands/${uuid}`);
            const command = body.data;
            const row = el(`command-${uuid}`);
            if (row) row.innerHTML = `<strong>${escapeHtml(command.name)}</strong> · <span>${escapeHtml(command.status)}</span><div class="muted">${escapeHtml(command.error_message || command.uuid)}</div>`;
            if (['completed','failed','expired','cancelled','rejected'].includes(command.status)) {
                if (command.status === 'completed') loadArtifacts();
                return;
            }
        } catch (error) {
            return;
        }
    }
}

async function loadArtifacts() {
    if (!selected) return;
    try {
        const body = await api(`/devices/${selected.uuid}/artifacts`);
        const artifacts = body.data || [];
        el('artifactGrid').innerHTML = artifacts.length ? artifacts.map(artifact => `
            <a class="artifact" href="${escapeHtml(artifact.download_url)}" target="_blank" rel="noopener">
                ${artifact.mime_type?.startsWith('image/') ? `<img src="${escapeHtml(artifact.download_url)}" alt="Capture">` : '<div style="height:130px;display:grid;place-items:center;font-size:36px">📄</div>'}
                <div class="artifact-body">
                    <strong>${escapeHtml(artifact.type)}</strong>
                    <div>${formatBytes(artifact.size_bytes)} · ${formatDate(artifact.created_at)}</div>
                </div>
            </a>
        `).join('') : '<span class="muted">Belum ada artifact.</span>';
    } catch (error) {
        el('artifactGrid').innerHTML = `<div class="notice">${escapeHtml(error.message)}</div>`;
    }
}

async function loadMetrics() {
    if (!selected) return;
    try {
        const body = await api(`/devices/${selected.uuid}/metrics?limit=1`);
        const metric = (body.data || []).at(-1);
        if (!metric) {
            el('metricGrid').innerHTML = '<span class="muted">Belum ada telemetry.</span>';
            return;
        }
        const values = [
            ['CPU', metric.cpu_percent != null ? `${metric.cpu_percent}%` : '—'],
            ['RAM', metric.memory_percent != null ? `${metric.memory_percent}%` : '—'],
            ['RAM terpakai', `${formatBytes(metric.memory_used_bytes)} / ${formatBytes(metric.memory_total_bytes)}`],
            ['Disk', `${formatBytes(metric.disk_used_bytes)} / ${formatBytes(metric.disk_total_bytes)}`],
            ['Network RX', formatBytes(metric.network_rx_bytes)],
            ['Network TX', formatBytes(metric.network_tx_bytes)],
            ['Dicatat', formatDate(metric.recorded_at)],
        ];
        el('metricGrid').innerHTML = values.map(([label, value]) => `<div class="info-card"><label>${escapeHtml(label)}</label><div>${escapeHtml(value)}</div></div>`).join('');
    } catch (error) {
        el('metricGrid').innerHTML = `<div class="notice">${escapeHtml(error.message)}</div>`;
    }
}

// ─── Live View ──────────────────────────────────────────────────────
let liveTimer = null;
function startLivePolling() {
    stopLivePolling();
    fetchLiveFrame();
    liveTimer = setInterval(fetchLiveFrame, 1000);
}
function stopLivePolling() {
    if (liveTimer) { clearInterval(liveTimer); liveTimer = null; }
}
async function fetchLiveFrame() {
    if (!selected) return;
    const type = el('liveFrameType').value;
    const suffix = type === 'screen' ? 'screen' : '';
    try {
        const url = `${API}/devices/${selected.uuid}/live-frame?type=${type}&_=${Date.now()}`;
        const response = await fetch(url, {headers: {'Accept':'image/jpeg','X-CSRF-TOKEN':CSRF}});
        if (!response.ok) {
            el('liveStatus').textContent = 'Tidak ada frame (belum streaming)';
            el('liveFrame').style.display = 'none';
            el('liveFramePlaceholder').style.display = '';
            return;
        }
        const age = response.headers.get('X-Frame-Age') || '?';
        el('liveStatus').textContent = `Streaming aktif · frame ${age}s lalu`;
        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        el('liveFrame').onload = function() { URL.revokeObjectURL(objectUrl); };
        el('liveFrame').src = objectUrl;
        el('liveFrame').style.display = '';
        el('liveFramePlaceholder').style.display = 'none';
    } catch (e) {
        el('liveStatus').textContent = 'Error: ' + e.message;
    }
}
el('liveFrameType').addEventListener('change', fetchLiveFrame);

document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(item => item.classList.toggle('active', item === tab));
        document.querySelectorAll('.tab-panel').forEach(panel => panel.classList.toggle('active', panel.id === `tab-${tab.dataset.tab}`));
        if (tab.dataset.tab === 'artifacts') loadArtifacts();
        if (tab.dataset.tab === 'metrics') loadMetrics();
        if (tab.dataset.tab === 'live') startLivePolling(); else stopLivePolling();
    });
});
document.querySelectorAll('[data-command]').forEach(button => button.addEventListener('click', () => sendCommand(button.dataset.command)));
el('refreshDevices').addEventListener('click', loadDevices);
el('siteFilter').addEventListener('change', loadDevices);
let searchDelay;
el('searchDevice').addEventListener('input', () => {
    clearTimeout(searchDelay);
    searchDelay = setTimeout(loadDevices, 300);
});

@can('device.enroll')
el('openEnrollment').addEventListener('click', () => el('enrollmentModal').classList.add('open'));
el('closeEnrollment').addEventListener('click', () => el('enrollmentModal').classList.remove('open'));
el('enrollmentForm').addEventListener('submit', async event => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.currentTarget));
    data.max_uses = Number(data.max_uses || 1);
    try {
        const body = await api('/enrollments', {method:'POST', body:JSON.stringify(data)});
        const token = body.data.enrollment_token;
        el('enrollmentResult').innerHTML = `
            <div class="token-box">
                <strong>Kode hanya ditampilkan sekali</strong>
                <div id="plainEnrollmentToken" style="margin-top:7px">${escapeHtml(token)}</div>
                <div class="muted" style="margin-top:7px">Kedaluwarsa: ${formatDate(body.data.expires_at)}</div>
                <button class="btn small" style="margin-top:9px" type="button" id="copyEnrollmentToken">Salin kode</button>
            </div>`;
        el('copyEnrollmentToken').addEventListener('click', () => navigator.clipboard.writeText(token).then(() => toast('Kode disalin.')));
    } catch (error) {
        toast(error.message, true);
    }
});
@endcan

loadDevices();
refreshTimer = setInterval(loadDevices, 10000);
</script>
</body>
</html>
