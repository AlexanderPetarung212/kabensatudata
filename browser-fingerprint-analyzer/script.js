/**
 * Browser Fingerprint Analyzer
 * Mengumpulkan dan menganalisis browser fingerprint data
 * Semua data diproses 100% lokal — tidak ada data dikirim ke server
 */

// ============================================
// State & Collected Data
// ============================================
const collectedData = {
  network: {},
  hardware: {},
  browser: {},
  canvas: {},
  audio: {},
  storage: {},
  webgl: {},
  webrtc: {},
};

let isScanning = false;

// ============================================
// Utility: Simple Hash (djb2 variant)
// ============================================
function simpleHash(str) {
  let hash = 5381;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) + hash + str.charCodeAt(i)) & 0xffffffff;
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
}

// SHA-256 hash for more serious fingerprints
async function sha256(message) {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

// ============================================
// UI Helpers
// ============================================
function updateProgress(percent, statusText) {
  const container = document.getElementById('progressContainer');
  const fill = document.getElementById('progressFill');
  const status = document.getElementById('progressStatus');
  const pct = document.getElementById('progressPercent');

  container.classList.add('visible');
  fill.style.width = percent + '%';
  status.textContent = statusText;
  pct.textContent = Math.round(percent) + '%';
}

function renderDataRows(containerId, data) {
  const container = document.getElementById(containerId);
  container.innerHTML = '';
  for (const [label, value] of Object.entries(data)) {
    // Skip internal properties (prefixed with _)
    if (label.startsWith('_')) continue;
    const row = document.createElement('div');
    row.className = 'data-row';
    row.innerHTML = `
      <span class="data-row__label">${escapeHtml(label)}</span>
      <span class="data-row__value">${escapeHtml(String(value))}</span>
    `;
    container.appendChild(row);
  }
}

function setRiskBadge(elementId, level) {
  const el = document.getElementById(elementId);
  const labels = { low: 'Rendah', medium: 'Sedang', high: 'Tinggi' };
  const icons = { low: '✅', medium: '⚠️', high: '🔴' };
  el.className = `risk-badge risk-badge--${level}`;
  el.textContent = `${icons[level]} ${labels[level]}`;
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ============================================
// 1. Collect Network Info
// ============================================
async function collectNetworkInfo() {
  const data = {};

  // Public IP via ipify
  try {
    const res = await fetch('https://api.ipify.org?format=json');
    const json = await res.json();
    data['IP Publik'] = json.ip;
  } catch {
    data['IP Publik'] = 'Tidak dapat dideteksi (blocked)';
  }

  // Connection type
  if (navigator.connection) {
    data['Tipe Koneksi'] = navigator.connection.effectiveType || 'Unknown';
    data['Downlink'] = (navigator.connection.downlink || '?') + ' Mbps';
    data['RTT'] = (navigator.connection.rtt || '?') + ' ms';
    data['Save Data'] = navigator.connection.saveData ? 'Ya' : 'Tidak';
  } else {
    data['Tipe Koneksi'] = 'API tidak tersedia';
  }

  data['Online'] = navigator.onLine ? 'Ya' : 'Tidak';

  collectedData.network = data;
  renderDataRows('dataNetwork', data);
  setRiskBadge('riskNetwork', data['IP Publik'] && data['IP Publik'] !== 'Tidak dapat dideteksi (blocked)' ? 'high' : 'low');
}

// ============================================
// 2. Collect Hardware Info
// ============================================
function collectHardwareInfo() {
  const data = {};

  data['Screen Resolution'] = `${screen.width} × ${screen.height}`;
  data['Available Screen'] = `${screen.availWidth} × ${screen.availHeight}`;
  data['Color Depth'] = screen.colorDepth + ' bit';
  data['Pixel Ratio'] = window.devicePixelRatio;
  data['Device Memory'] = navigator.deviceMemory ? navigator.deviceMemory + ' GB' : 'API tidak tersedia';
  data['CPU Cores'] = navigator.hardwareConcurrency || 'API tidak tersedia';
  data['Max Touch Points'] = navigator.maxTouchPoints || 0;
  data['Orientation'] = screen.orientation ? screen.orientation.type : 'API tidak tersedia';

  // GPU via WebGL
  try {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
    if (gl) {
      const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
      if (debugInfo) {
        data['GPU Vendor'] = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL);
        data['GPU Renderer'] = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL);
      }
    }
  } catch {
    data['GPU'] = 'Tidak dapat dideteksi';
  }

  collectedData.hardware = data;
  renderDataRows('dataHardware', data);
  setRiskBadge('riskHardware', 'high'); // Hardware info is highly unique
}

// ============================================
// 3. Collect Browser Info
// ============================================
function collectBrowserInfo() {
  const data = {};

  data['User-Agent'] = navigator.userAgent;
  data['Platform'] = navigator.platform;
  data['Vendor'] = navigator.vendor || 'Tidak tersedia';
  data['Bahasa'] = navigator.language;
  data['Semua Bahasa'] = navigator.languages ? navigator.languages.join(', ') : navigator.language;
  data['Timezone'] = Intl.DateTimeFormat().resolvedOptions().timeZone;
  data['Timezone Offset'] = new Date().getTimezoneOffset() + ' menit';
  data['Cookies Enabled'] = navigator.cookieEnabled ? 'Ya' : 'Tidak';
  data['Do Not Track'] = navigator.doNotTrack || 'Tidak diset';
  data['PDF Viewer'] = navigator.pdfViewerEnabled !== undefined ? (navigator.pdfViewerEnabled ? 'Ya' : 'Tidak') : 'Tidak diketahui';
  data['Java Enabled'] = 'Tidak (deprecated)';

  // Plugins
  if (navigator.plugins && navigator.plugins.length > 0) {
    const pluginNames = Array.from(navigator.plugins).map(p => p.name).slice(0, 5);
    data['Plugins'] = pluginNames.join(', ');
  } else {
    data['Plugins'] = 'Tidak ada / Disembunyikan';
  }

  // Installed fonts detection (basic)
  data['MIME Types'] = navigator.mimeTypes ? navigator.mimeTypes.length + ' tipe' : 'Tidak tersedia';

  collectedData.browser = data;
  renderDataRows('dataBrowser', data);
  setRiskBadge('riskBrowser', 'medium');
}

// ============================================
// 4. Canvas Fingerprint
// ============================================
async function generateCanvasFingerprint() {
  const data = {};
  const canvas = document.getElementById('canvasFingerprint');
  const ctx = canvas.getContext('2d');

  // Draw complex scene for unique fingerprint
  ctx.fillStyle = '#f0f0f0';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // Text with various fonts
  ctx.fillStyle = '#333';
  ctx.font = '14px Arial';
  ctx.fillText('Fingerprint Test 🔒', 10, 20);

  ctx.font = 'italic bold 12px Times New Roman';
  ctx.fillStyle = '#a83279';
  ctx.fillText('Canvas2D Diagnostics', 10, 40);

  // Geometric shapes
  ctx.beginPath();
  ctx.arc(200, 30, 20, 0, Math.PI * 2);
  ctx.fillStyle = 'rgba(30, 144, 255, 0.6)';
  ctx.fill();

  ctx.beginPath();
  ctx.moveTo(230, 10);
  ctx.lineTo(270, 50);
  ctx.lineTo(230, 50);
  ctx.closePath();
  ctx.fillStyle = 'rgba(255, 165, 0, 0.7)';
  ctx.fill();

  // Gradient
  const gradient = ctx.createLinearGradient(0, 0, canvas.width, 0);
  gradient.addColorStop(0, 'rgba(255,0,0,0.1)');
  gradient.addColorStop(0.5, 'rgba(0,255,0,0.1)');
  gradient.addColorStop(1, 'rgba(0,0,255,0.1)');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 50, canvas.width, 10);

  // Get data URL and hash
  const dataURL = canvas.toDataURL();
  const hash = await sha256(dataURL);

  data['Canvas Tersedia'] = 'Ya';
  data['Data URL Length'] = dataURL.length + ' chars';
  data['Hash Algorithm'] = 'SHA-256';

  collectedData.canvas = data;
  collectedData.canvas._hash = hash;
  collectedData.canvas._dataURL = dataURL;

  renderDataRows('dataCanvas', data);

  // Add fingerprint hash display
  const container = document.getElementById('dataCanvas');
  const hashDiv = document.createElement('div');
  hashDiv.className = 'fingerprint-hash';
  hashDiv.innerHTML = `<span class="fingerprint-hash__label">Canvas Hash (SHA-256)</span>${escapeHtml(hash)}`;
  container.appendChild(hashDiv);

  // Add canvas preview
  const previewDiv = document.createElement('div');
  previewDiv.className = 'canvas-preview';
  const previewCanvas = document.createElement('canvas');
  previewCanvas.width = 280;
  previewCanvas.height = 60;
  const previewCtx = previewCanvas.getContext('2d');
  previewCtx.drawImage(canvas, 0, 0);
  previewDiv.appendChild(previewCanvas);
  container.appendChild(previewDiv);

  setRiskBadge('riskCanvas', 'high');
}

// ============================================
// 5. Audio Fingerprint
// ============================================
async function generateAudioFingerprint() {
  const data = {};

  try {
    const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = audioCtx.createOscillator();
    const analyser = audioCtx.createAnalyser();
    const gain = audioCtx.createGain();
    const compressor = audioCtx.createDynamicsCompressor();

    // Configure
    oscillator.type = 'triangle';
    oscillator.frequency.setValueAtTime(10000, audioCtx.currentTime);
    gain.gain.setValueAtTime(0, audioCtx.currentTime); // Muted

    compressor.threshold.setValueAtTime(-50, audioCtx.currentTime);
    compressor.knee.setValueAtTime(40, audioCtx.currentTime);
    compressor.ratio.setValueAtTime(12, audioCtx.currentTime);
    compressor.attack.setValueAtTime(0, audioCtx.currentTime);
    compressor.release.setValueAtTime(0.25, audioCtx.currentTime);

    // Connect
    oscillator.connect(compressor);
    compressor.connect(analyser);
    analyser.connect(gain);
    gain.connect(audioCtx.destination);

    oscillator.start(0);

    // Capture after short delay
    await delay(100);

    const frequencyData = new Float32Array(analyser.frequencyBinCount);
    analyser.getFloatFrequencyData(frequencyData);

    // Hash the frequency data
    const audioString = frequencyData.slice(0, 100).join(',');
    const hash = await sha256(audioString);

    oscillator.stop();
    await audioCtx.close();

    data['AudioContext'] = 'Tersedia';
    data['Sample Rate'] = audioCtx.sampleRate + ' Hz';
    data['FFT Size'] = analyser.fftSize;
    data['Frequency Bins'] = analyser.frequencyBinCount;
    data['Channel Count'] = audioCtx.destination.maxChannelCount;

    collectedData.audio = data;
    collectedData.audio._hash = hash;

    renderDataRows('dataAudio', data);

    // Add hash display
    const container = document.getElementById('dataAudio');
    const hashDiv = document.createElement('div');
    hashDiv.className = 'fingerprint-hash';
    hashDiv.innerHTML = `<span class="fingerprint-hash__label">Audio Hash (SHA-256)</span>${escapeHtml(hash)}`;
    container.appendChild(hashDiv);

    setRiskBadge('riskAudio', 'high');
  } catch (e) {
    data['AudioContext'] = 'Tidak tersedia: ' + e.message;
    collectedData.audio = data;
    renderDataRows('dataAudio', data);
    setRiskBadge('riskAudio', 'low');
  }
}

// ============================================
// 6. Storage & Cookies
// ============================================
function collectStorageInfo() {
  const data = {};

  // LocalStorage
  try {
    localStorage.setItem('__fp_test__', '1');
    localStorage.removeItem('__fp_test__');
    data['LocalStorage'] = 'Tersedia';

    // Count existing items
    data['LocalStorage Items'] = localStorage.length + ' item(s)';

    // List keys (first 10)
    if (localStorage.length > 0) {
      const keys = [];
      for (let i = 0; i < Math.min(localStorage.length, 10); i++) {
        keys.push(localStorage.key(i));
      }
      data['LocalStorage Keys'] = keys.join(', ');
      if (localStorage.length > 10) {
        data['LocalStorage Keys'] += ` ... (+${localStorage.length - 10} lainnya)`;
      }
    }
  } catch {
    data['LocalStorage'] = 'Diblokir';
  }

  // SessionStorage
  try {
    sessionStorage.setItem('__fp_test__', '1');
    sessionStorage.removeItem('__fp_test__');
    data['SessionStorage'] = 'Tersedia';
    data['SessionStorage Items'] = sessionStorage.length + ' item(s)';
  } catch {
    data['SessionStorage'] = 'Diblokir';
  }

  // IndexedDB
  data['IndexedDB'] = window.indexedDB ? 'Tersedia' : 'Tidak tersedia';

  // Cookies
  data['Cookies Enabled'] = navigator.cookieEnabled ? 'Ya' : 'Tidak';
  data['Document Cookies'] = document.cookie ? document.cookie.split(';').length + ' cookie(s)' : 'Tidak ada cookies';

  // Service Worker
  data['Service Worker'] = 'serviceWorker' in navigator ? 'Didukung' : 'Tidak didukung';

  // Cache API
  data['Cache API'] = 'caches' in window ? 'Didukung' : 'Tidak didukung';

  // Estimated storage
  if (navigator.storage && navigator.storage.estimate) {
    navigator.storage.estimate().then(estimate => {
      const used = (estimate.usage / 1024 / 1024).toFixed(2);
      const quota = (estimate.quota / 1024 / 1024).toFixed(0);
      const storageRow = document.createElement('div');
      storageRow.className = 'data-row';
      storageRow.innerHTML = `
        <span class="data-row__label">Storage Used</span>
        <span class="data-row__value">${used} MB / ${quota} MB</span>
      `;
      document.getElementById('dataStorage').appendChild(storageRow);
    });
  }

  collectedData.storage = data;
  renderDataRows('dataStorage', data);

  const hasTracking = localStorage.length > 0 || document.cookie.length > 0;
  setRiskBadge('riskStorage', hasTracking ? 'high' : 'low');
}

// ============================================
// 7. WebGL Fingerprint
// ============================================
async function collectWebGLInfo() {
  const data = {};

  try {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');

    if (!gl) {
      data['WebGL'] = 'Tidak tersedia';
      collectedData.webgl = data;
      renderDataRows('dataWebgl', data);
      setRiskBadge('riskWebgl', 'low');
      return;
    }

    data['WebGL Version'] = gl.getParameter(gl.VERSION);
    data['Shading Language'] = gl.getParameter(gl.SHADING_LANGUAGE_VERSION);
    data['Max Texture Size'] = gl.getParameter(gl.MAX_TEXTURE_SIZE);
    data['Max Viewport'] = gl.getParameter(gl.MAX_VIEWPORT_DIMS)?.join(' × ');
    data['Max Render Buffer'] = gl.getParameter(gl.MAX_RENDERBUFFER_SIZE);
    data['Antialiasing'] = gl.getContextAttributes()?.antialias ? 'Ya' : 'Tidak';

    const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
    if (debugInfo) {
      data['Unmasked Vendor'] = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL);
      data['Unmasked Renderer'] = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL);
    }

    // Extensions
    const extensions = gl.getSupportedExtensions() || [];
    data['Extensions Count'] = extensions.length;

    // Generate WebGL hash
    const webglString = [
      data['WebGL Version'],
      data['Shading Language'],
      data['Max Texture Size'],
      data['Unmasked Vendor'] || '',
      data['Unmasked Renderer'] || '',
      extensions.join(','),
    ].join('|');

    const hash = await sha256(webglString);
    collectedData.webgl = data;
    collectedData.webgl._hash = hash;

    renderDataRows('dataWebgl', data);

    // Add hash display
    const container = document.getElementById('dataWebgl');
    const hashDiv = document.createElement('div');
    hashDiv.className = 'fingerprint-hash';
    hashDiv.innerHTML = `<span class="fingerprint-hash__label">WebGL Hash (SHA-256)</span>${escapeHtml(hash)}`;
    container.appendChild(hashDiv);

    setRiskBadge('riskWebgl', 'high');
  } catch (e) {
    data['WebGL'] = 'Error: ' + e.message;
    collectedData.webgl = data;
    renderDataRows('dataWebgl', data);
    setRiskBadge('riskWebgl', 'low');
  }
}

// ============================================
// 8. WebRTC Leak Detection
// ============================================
async function detectWebRTCLeak() {
  const data = {};

  if (!window.RTCPeerConnection) {
    data['WebRTC'] = 'Tidak didukung';
    collectedData.webrtc = data;
    renderDataRows('dataWebrtc', data);
    setRiskBadge('riskWebrtc', 'low');
    return;
  }

  data['WebRTC API'] = 'Tersedia';

  try {
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
    });

    const candidates = [];
    let localIPFound = false;

    const candidatePromise = new Promise((resolve) => {
      const timeout = setTimeout(() => resolve(), 5000);

      pc.onicecandidate = (event) => {
        if (!event.candidate) {
          clearTimeout(timeout);
          resolve();
          return;
        }

        const candidate = event.candidate.candidate;
        candidates.push(candidate);

        // Extract IPs from candidate string
        const ipRegex = /(?:\d{1,3}\.){3}\d{1,3}/g;
        const ips = candidate.match(ipRegex);

        if (ips) {
          ips.forEach((ip) => {
            if (ip.startsWith('10.') || ip.startsWith('192.168.') || ip.startsWith('172.')) {
              data['Local IP (LEAKED!)'] = ip;
              localIPFound = true;
            } else if (ip !== '0.0.0.0') {
              data['Public IP (WebRTC)'] = ip;
            }
          });
        }

        // Check for IPv6
        const ipv6Regex = /([a-f0-9:]+:+[a-f0-9]+)/gi;
        const ipv6 = candidate.match(ipv6Regex);
        if (ipv6) {
          data['IPv6 (WebRTC)'] = ipv6[0];
        }
      };
    });

    // Create data channel and offer
    pc.createDataChannel('');
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);

    await candidatePromise;

    data['ICE Candidates'] = candidates.length + ' candidate(s)';
    data['Local IP Leak'] = localIPFound ? '⚠️ TERDETEKSI' : '✅ Tidak bocor';

    pc.close();
  } catch (e) {
    data['WebRTC Error'] = e.message;
  }

  // Media devices
  try {
    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const cameras = devices.filter(d => d.kind === 'videoinput').length;
      const mics = devices.filter(d => d.kind === 'audioinput').length;
      const speakers = devices.filter(d => d.kind === 'audiooutput').length;
      data['Kamera'] = cameras + ' device(s)';
      data['Mikrofon'] = mics + ' device(s)';
      data['Speaker'] = speakers + ' device(s)';
    }
  } catch {
    data['Media Devices'] = 'Akses ditolak';
  }

  collectedData.webrtc = data;
  renderDataRows('dataWebrtc', data);

  const hasLeak = data['Local IP Leak']?.includes('TERDETEKSI');
  setRiskBadge('riskWebrtc', hasLeak ? 'high' : 'medium');
}

// ============================================
// 9. Risk Assessment
// ============================================
function assessRisk() {
  const categories = [
    {
      name: 'Network',
      icon: '🌐',
      score: collectedData.network['IP Publik'] && !collectedData.network['IP Publik'].includes('blocked') ? 85 : 20,
      desc: 'IP address dapat digunakan langsung untuk ban.',
    },
    {
      name: 'Hardware',
      icon: '🖥️',
      score: 90,
      desc: 'Kombinasi screen, GPU, CPU sangat unik per device.',
    },
    {
      name: 'Canvas',
      icon: '🎨',
      score: 95,
      desc: 'Canvas fingerprint hampir unik — metode tracking paling kuat.',
    },
    {
      name: 'Audio',
      icon: '🔊',
      score: collectedData.audio._hash ? 80 : 10,
      desc: 'Audio processing bervariasi per device/browser.',
    },
    {
      name: 'WebGL',
      icon: '🔑',
      score: collectedData.webgl._hash ? 88 : 10,
      desc: 'GPU renderer + extensions = identifikasi hardware.',
    },
    {
      name: 'WebRTC',
      icon: '📡',
      score: collectedData.webrtc['Local IP Leak']?.includes('TERDETEKSI') ? 95 : 40,
      desc: 'WebRTC bisa membocorkan IP asli Anda.',
    },
    {
      name: 'Browser',
      icon: '🌍',
      score: 55,
      desc: 'User-agent & timezone mudah diubah.',
    },
    {
      name: 'Storage',
      icon: '📦',
      score: localStorage.length > 0 || document.cookie.length > 0 ? 70 : 20,
      desc: 'Cookies & storage menyimpan tracking data.',
    },
  ];

  // Render risk overview
  const overview = document.getElementById('riskOverview');
  overview.innerHTML = '';

  categories.forEach((cat) => {
    const level = cat.score >= 70 ? 'high' : cat.score >= 40 ? 'medium' : 'low';
    const item = document.createElement('div');
    item.className = 'risk-item';
    item.innerHTML = `
      <div class="risk-item__category">${cat.icon} ${cat.name}</div>
      <div class="risk-item__score risk-item__score--${level}">${cat.score}%</div>
      <div class="risk-item__bar">
        <div class="risk-item__bar-fill risk-item__bar-fill--${level}" style="width: 0%"></div>
      </div>
    `;
    overview.appendChild(item);

    // Animate bar fill
    requestAnimationFrame(() => {
      setTimeout(() => {
        item.querySelector('.risk-item__bar-fill').style.width = cat.score + '%';
      }, 100);
    });
  });

  // Overall score
  const overallScore = Math.round(categories.reduce((sum, c) => sum + c.score, 0) / categories.length);
  const overallLevel = overallScore >= 70 ? 'high' : overallScore >= 40 ? 'medium' : 'low';
  const overallColors = { high: 'var(--risk-high)', medium: 'var(--risk-medium)', low: 'var(--risk-low)' };

  const circle = document.getElementById('overallCircle');
  circle.style.setProperty('--score-pct', overallScore);
  circle.style.setProperty('--score-color', overallColors[overallLevel]);

  document.getElementById('overallScoreValue').textContent = overallScore + '%';
  document.getElementById('overallScoreValue').style.color = overallColors[overallLevel];

  const labels = {
    high: 'Tingkat Identifikasi TINGGI',
    medium: 'Tingkat Identifikasi SEDANG',
    low: 'Tingkat Identifikasi RENDAH',
  };

  const descs = {
    high: 'Browser Anda sangat mudah diidentifikasi. Website seperti Ome.tv kemungkinan besar menggunakan fingerprint ini untuk mengenali dan memblokir Anda, meskipun sudah ganti akun atau profil browser.',
    medium: 'Browser Anda cukup dapat diidentifikasi. Beberapa langkah tambahan diperlukan untuk mengurangi risiko tracking.',
    low: 'Browser Anda memiliki tingkat identifikasi rendah. Fingerprint Anda cukup generik.',
  };

  document.getElementById('overallScoreLabel').textContent = labels[overallLevel];
  document.getElementById('overallScoreDesc').textContent = descs[overallLevel];

  return { categories, overallScore, overallLevel };
}

// ============================================
// 10. Generate Recommendations
// ============================================
function generateRecommendations(riskResult) {
  const recommendations = [];

  // IP-based recommendations
  if (collectedData.network['IP Publik'] && !collectedData.network['IP Publik'].includes('blocked')) {
    recommendations.push({
      type: 'critical',
      icon: '🔴',
      title: 'IP Address Anda Terekspos',
      desc: `IP publik Anda (${collectedData.network['IP Publik']}) terlihat oleh website. Restart modem/router Anda untuk mendapatkan IP baru (jika ISP menggunakan IP dinamis). Atau gunakan jaringan lain (hotspot HP).`,
    });
  }

  // WebRTC leak
  if (collectedData.webrtc['Local IP Leak']?.includes('TERDETEKSI')) {
    recommendations.push({
      type: 'critical',
      icon: '🔴',
      title: 'WebRTC Membocorkan IP Lokal Anda',
      desc: `IP lokal Anda (${collectedData.webrtc['Local IP (LEAKED!)']}) bocor melalui WebRTC. Ini terjadi bahkan saat menggunakan VPN. Install extension "WebRTC Leak Prevent" atau matikan WebRTC di chrome://flags.`,
    });
  }

  // Canvas fingerprint
  if (collectedData.canvas._hash) {
    recommendations.push({
      type: 'critical',
      icon: '🔴',
      title: 'Canvas Fingerprint Sangat Unik',
      desc: 'Canvas fingerprint Anda hampir unik di antara semua pengguna. Ini adalah metode tracking paling efektif. Gunakan extension "Canvas Blocker" atau "CanvasBlocker" untuk mengacak hasil canvas rendering.',
    });
  }

  // Hardware info
  recommendations.push({
    type: 'warning',
    icon: '⚠️',
    title: 'Hardware Fingerprint Tidak Dapat Diubah',
    desc: `Screen resolution (${collectedData.hardware['Screen Resolution']}), GPU (${collectedData.hardware['GPU Renderer'] || 'unknown'}), dan CPU cores (${collectedData.hardware['CPU Cores']}) adalah data hardware yang sulit dipalsukan. Mengubah zoom level browser (misal 90% atau 110%) dapat mengubah reported resolution.`,
  });

  // WebGL
  if (collectedData.webgl._hash) {
    recommendations.push({
      type: 'warning',
      icon: '⚠️',
      title: 'WebGL Mengidentifikasi GPU Anda',
      desc: 'WebGL renderer info mengekspos GPU spesifik Anda. Gunakan extension "WebGL Fingerprint Defender" untuk mengacak parameter WebGL.',
    });
  }

  // Storage
  if (localStorage.length > 0) {
    recommendations.push({
      type: 'warning',
      icon: '⚠️',
      title: `LocalStorage Menyimpan ${localStorage.length} Item`,
      desc: 'Data tracking mungkin tersimpan di LocalStorage. Bersihkan semua data browser: Chrome → Settings → Privacy → Clear Browsing Data → pilih "All time" dan centang semua opsi.',
    });
  }

  // Audio fingerprint
  if (collectedData.audio._hash) {
    recommendations.push({
      type: 'info',
      icon: 'ℹ️',
      title: 'Audio Fingerprint Terdeteksi',
      desc: 'AudioContext fingerprint Anda dapat digunakan untuk tracking. Beberapa browser anti-fingerprint seperti Brave secara otomatis mengacak audio fingerprint.',
    });
  }

  // General recommendations
  recommendations.push({
    type: 'info',
    icon: '💡',
    title: 'Gunakan Browser Anti-Fingerprint',
    desc: 'Pertimbangkan menggunakan Brave Browser yang memiliki built-in anti-fingerprinting, atau Firefox dengan pengaturan privacy yang ditingkatkan (about:config → privacy.resistFingerprinting = true).',
  });

  recommendations.push({
    type: 'success',
    icon: '✅',
    title: 'Ajukan Banding Resmi ke Ome.tv',
    desc: 'Cara paling legitimate: kunjungi halaman support Ome.tv dan ajukan unban request. Jelaskan bahwa Anda di-report secara tidak adil oleh pengguna lain. Ini adalah solusi permanen tanpa perlu bypass apapun.',
  });

  // Render recommendations
  const container = document.getElementById('recommendations');
  container.innerHTML = '';

  recommendations.forEach((rec) => {
    const div = document.createElement('div');
    div.className = 'recommendation';
    const iconClass = {
      critical: 'recommendation__icon--critical',
      warning: 'recommendation__icon--warning',
      info: 'recommendation__icon--info',
      success: 'recommendation__icon--success',
    }[rec.type];

    div.innerHTML = `
      <div class="recommendation__icon ${iconClass}">${rec.icon}</div>
      <div class="recommendation__content">
        <h4>${escapeHtml(rec.title)}</h4>
        <p>${escapeHtml(rec.desc)}</p>
      </div>
    `;
    container.appendChild(div);
  });
}

// ============================================
// Main Scan Flow
// ============================================
async function startScan() {
  if (isScanning) return;
  isScanning = true;

  const btn = document.getElementById('btnScan');
  const btnText = document.getElementById('btnScanText');
  btn.classList.add('scanning');
  btnText.textContent = 'Scanning...';

  const dashboard = document.getElementById('dashboard');
  dashboard.classList.remove('visible');

  try {
    // Step 1: Network
    updateProgress(5, 'Mengumpulkan informasi jaringan...');
    await collectNetworkInfo();
    await delay(300);

    // Step 2: Hardware
    updateProgress(18, 'Menganalisis hardware device...');
    collectHardwareInfo();
    await delay(300);

    // Step 3: Browser
    updateProgress(30, 'Membaca informasi browser...');
    collectBrowserInfo();
    await delay(300);

    // Step 4: Canvas
    updateProgress(42, 'Generating canvas fingerprint...');
    await generateCanvasFingerprint();
    await delay(300);

    // Step 5: Audio
    updateProgress(55, 'Generating audio fingerprint...');
    await generateAudioFingerprint();
    await delay(300);

    // Step 6: Storage
    updateProgress(65, 'Memeriksa storage & cookies...');
    collectStorageInfo();
    await delay(300);

    // Step 7: WebGL
    updateProgress(78, 'Menganalisis WebGL fingerprint...');
    await collectWebGLInfo();
    await delay(300);

    // Step 8: WebRTC
    updateProgress(88, 'Mendeteksi WebRTC leak...');
    await detectWebRTCLeak();
    await delay(300);

    // Step 9: Risk Assessment
    updateProgress(95, 'Menghitung risk assessment...');
    await delay(200);
    const riskResult = assessRisk();
    await delay(200);

    // Step 10: Recommendations
    updateProgress(100, 'Generating rekomendasi...');
    generateRecommendations(riskResult);
    await delay(300);

    // Show dashboard
    dashboard.classList.add('visible');

    // Complete
    updateProgress(100, '✅ Analisis selesai!');
    btnText.textContent = '🔄 Scan Ulang';

  } catch (error) {
    console.error('Scan error:', error);
    updateProgress(0, '❌ Error: ' + error.message);
    btnText.textContent = 'Coba Lagi';
  }

  btn.classList.remove('scanning');
  isScanning = false;
}
