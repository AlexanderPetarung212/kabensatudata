<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{ $ruangan->nama }} — Inventaris Puskesmas Rawat Inap Kabalsiang Benjuring</title>
    <meta name="description" content="Daftar inventaris barang di {{ $ruangan->nama }}, Puskesmas Rawat Inap Kabalsiang Benjuring">
    <meta name="robots" content="noindex">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif; background: #f8faf9; min-height: 100vh; color: #0f172a; }

        /* ─── Header ─── */
        .header {
            background: linear-gradient(135deg, #064e3b 0%, #047857 50%, #059669 100%);
            color: white; padding: 28px 20px 48px; text-align: center;
            position: relative; overflow: hidden;
        }
        .header::before {
            content: ''; position: absolute; top: -60%; right: -25%; width: 300px; height: 300px;
            background: radial-gradient(circle, rgba(255,255,255,0.07) 0%, transparent 70%);
            border-radius: 50%;
        }
        .header::after {
            content: ''; position: absolute; bottom: -50%; left: -20%; width: 250px; height: 250px;
            background: radial-gradient(circle, rgba(255,255,255,0.05) 0%, transparent 70%);
            border-radius: 50%;
        }
        .header-badge { display: inline-block; font-size: 10px; letter-spacing: 2px; text-transform: uppercase; opacity: 0.7; margin-bottom: 8px; font-weight: 500; }
        .header-title { font-size: 24px; font-weight: 800; position: relative; line-height: 1.2; }
        .header-meta { font-size: 13px; opacity: 0.8; margin-top: 6px; display: flex; align-items: center; justify-content: center; gap: 8px; }
        .header-dot { width: 3px; height: 3px; background: rgba(255,255,255,0.5); border-radius: 50%; }

        /* ─── Main container ─── */
        .main { max-width: 520px; margin: 0 auto; padding: 0 16px 32px; position: relative; z-index: 1; }

        /* ─── PJ Card ─── */
        .pj-card {
            background: white; border-radius: 16px; padding: 18px 20px;
            margin-top: -30px; position: relative; z-index: 2;
            box-shadow: 0 4px 24px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
            display: flex; align-items: center; gap: 14px;
            border: 1px solid #e2e8f0;
        }
        .pj-avatar {
            width: 56px; height: 56px; border-radius: 50%; flex-shrink: 0;
            object-fit: cover; border: 3px solid #d1fae5;
        }
        .pj-avatar-placeholder {
            width: 56px; height: 56px; border-radius: 50%; flex-shrink: 0;
            background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
            display: flex; align-items: center; justify-content: center;
            font-size: 22px; font-weight: 700; color: #059669;
            border: 3px solid #d1fae5;
        }
        .pj-label { font-size: 10px; color: #059669; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; }
        .pj-name { font-size: 16px; font-weight: 700; color: #0f172a; margin-top: 1px; }
        .pj-role { font-size: 12px; color: #64748b; margin-top: 1px; }

        /* ─── Staff pills ─── */
        .staff-section { margin-top: 16px; }
        .section-label { font-size: 12px; font-weight: 600; color: #475569; margin-bottom: 8px; }
        .staff-pills { display: flex; flex-wrap: wrap; gap: 6px; }
        .staff-pill {
            display: inline-flex; align-items: center; gap: 6px;
            padding: 5px 12px 5px 5px; border-radius: 100px;
            background: #f1f5f9; border: 1px solid #e2e8f0; font-size: 12px;
            color: #334155; font-weight: 500; transition: all 0.15s;
        }
        .staff-pill:hover { background: #e2e8f0; }
        .staff-pill img {
            width: 24px; height: 24px; border-radius: 50%; object-fit: cover;
        }
        .staff-pill-avatar {
            width: 24px; height: 24px; border-radius: 50%;
            background: #dbeafe; display: flex; align-items: center; justify-content: center;
            font-size: 10px; font-weight: 700; color: #3b82f6;
        }

        /* ─── Stats row ─── */
        .stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 20px; }
        .stat-card {
            background: white; border-radius: 12px; padding: 14px 8px; text-align: center;
            box-shadow: 0 1px 3px rgba(0,0,0,0.06); border: 1px solid #e2e8f0;
            transition: transform 0.15s;
        }
        .stat-card:hover { transform: translateY(-2px); }
        .stat-value { font-size: 22px; font-weight: 800; }
        .stat-label { font-size: 9px; color: #64748b; margin-top: 3px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600; }
        .stat-total .stat-value { color: #1e40af; }
        .stat-baik .stat-value { color: #059669; }
        .stat-rusak .stat-value { color: #d97706; }
        .stat-berat .stat-value { color: #dc2626; }

        /* ─── Items list ─── */
        .items-header {
            display: flex; align-items: center; justify-content: space-between;
            margin: 24px 0 12px;
        }
        .items-title { font-size: 16px; font-weight: 700; color: #0f172a; }
        .items-count { font-size: 12px; color: #64748b; font-weight: 500; background: #f1f5f9; padding: 3px 10px; border-radius: 100px; }
        .items { display: flex; flex-direction: column; gap: 8px; }
        .item {
            background: white; border-radius: 14px; padding: 14px 16px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.04); border: 1px solid #e2e8f0;
            display: flex; align-items: flex-start; gap: 12px;
            transition: all 0.15s;
        }
        .item:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); transform: translateY(-1px); }
        .item-info { flex: 1; min-width: 0; }
        .item-name { font-size: 14px; font-weight: 600; color: #0f172a; }
        .item-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 4px; align-items: center; }
        .item-meta span { font-size: 11px; color: #64748b; }
        .item-right { text-align: right; flex-shrink: 0; }
        .item-qty { font-size: 20px; font-weight: 800; color: #1e40af; }
        .item-qty-label { font-size: 9px; color: #94a3b8; text-transform: uppercase; font-weight: 600; }

        /* ─── Item Thumbnail ─── */
        .item-thumb {
            width: 56px; height: 56px; border-radius: 12px; flex-shrink: 0;
            object-fit: cover; cursor: pointer;
            border: 2px solid #e2e8f0;
            transition: all 0.2s ease;
        }
        .item-thumb:hover {
            border-color: #059669;
            transform: scale(1.05);
            box-shadow: 0 4px 12px rgba(5, 150, 105, 0.2);
        }
        .item-thumb-placeholder {
            width: 56px; height: 56px; border-radius: 12px; flex-shrink: 0;
            background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
            display: flex; align-items: center; justify-content: center;
            font-size: 22px; border: 2px solid #e2e8f0;
        }

        /* ─── Badge ─── */
        .badge {
            display: inline-block; padding: 3px 10px; border-radius: 100px;
            font-size: 10px; font-weight: 700; margin-top: 5px; letter-spacing: 0.3px;
        }
        .badge-baik { background: #d1fae5; color: #065f46; }
        .badge-rusak_ringan { background: #fef3c7; color: #92400e; }
        .badge-rusak_berat { background: #fee2e2; color: #991b1b; }
        .badge-tidak_aktif { background: #f1f5f9; color: #475569; }

        /* ─── Empty state ─── */
        .empty-state { text-align: center; padding: 48px 16px; color: #94a3b8; }
        .empty-state .icon { font-size: 48px; margin-bottom: 12px; }
        .empty-state p { font-size: 14px; }

        /* ─── Footer ─── */
        .footer {
            text-align: center; padding: 24px 16px; font-size: 11px; color: #94a3b8;
            border-top: 1px solid #e2e8f0; margin-top: 24px;
        }
        .footer strong { color: #64748b; }

        /* ─── Lightbox ─── */
        .lightbox-overlay {
            position: fixed; inset: 0; z-index: 9999;
            background: rgba(0, 0, 0, 0.92);
            backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
            display: flex; flex-direction: column; align-items: center; justify-content: center;
            opacity: 0; visibility: hidden;
            transition: opacity 0.3s ease, visibility 0.3s ease;
        }
        .lightbox-overlay.active {
            opacity: 1; visibility: visible;
        }
        .lightbox-close {
            position: absolute; top: 16px; right: 16px;
            width: 40px; height: 40px; border-radius: 50%;
            background: rgba(255,255,255,0.1); border: none;
            color: white; font-size: 20px; cursor: pointer;
            display: flex; align-items: center; justify-content: center;
            transition: background 0.2s;
            z-index: 10;
        }
        .lightbox-close:hover { background: rgba(255,255,255,0.2); }
        .lightbox-img-wrap {
            flex: 1; display: flex; align-items: center; justify-content: center;
            width: 100%; padding: 60px 16px 16px;
            overflow: hidden;
        }
        .lightbox-img {
            max-width: 90vw; max-height: 65vh;
            border-radius: 12px;
            object-fit: contain;
            box-shadow: 0 20px 60px rgba(0,0,0,0.4);
            transform: scale(0.9);
            opacity: 0;
            transition: transform 0.3s ease, opacity 0.3s ease;
        }
        .lightbox-overlay.active .lightbox-img {
            transform: scale(1); opacity: 1;
        }
        .lightbox-caption {
            padding: 16px 20px 24px; text-align: center;
            max-width: 480px; width: 100%;
        }
        .lightbox-caption-name {
            font-size: 16px; font-weight: 700; color: white;
        }
        .lightbox-caption-meta {
            font-size: 12px; color: rgba(255,255,255,0.6); margin-top: 4px;
        }
        .lightbox-caption-badge {
            display: inline-block; padding: 3px 12px; border-radius: 100px;
            font-size: 10px; font-weight: 700; margin-top: 8px;
            letter-spacing: 0.3px;
        }
        .lightbox-caption-badge.lb-baik { background: rgba(5,150,105,0.2); color: #6ee7b7; }
        .lightbox-caption-badge.lb-rusak_ringan { background: rgba(217,119,6,0.2); color: #fcd34d; }
        .lightbox-caption-badge.lb-rusak_berat { background: rgba(220,38,38,0.2); color: #fca5a5; }
        .lightbox-caption-badge.lb-tidak_aktif { background: rgba(148,163,184,0.2); color: #cbd5e1; }
        .lightbox-nav {
            position: absolute; top: 50%; transform: translateY(-50%);
            width: 44px; height: 44px; border-radius: 50%;
            background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.15);
            color: white; font-size: 18px; cursor: pointer;
            display: flex; align-items: center; justify-content: center;
            transition: background 0.2s, transform 0.2s;
            z-index: 10;
        }
        .lightbox-nav:hover { background: rgba(255,255,255,0.2); transform: translateY(-50%) scale(1.1); }
        .lightbox-nav.prev { left: 12px; }
        .lightbox-nav.next { right: 12px; }
        .lightbox-counter {
            position: absolute; top: 20px; left: 50%; transform: translateX(-50%);
            font-size: 12px; color: rgba(255,255,255,0.5); font-weight: 600;
            letter-spacing: 1px;
        }
        .lightbox-nav.hidden { opacity: 0; pointer-events: none; }

        /* ─── Animations ─── */
        @keyframes fadeUp { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
        .animate-in { animation: fadeUp 0.4s ease-out both; }
        .delay-1 { animation-delay: 0.1s; }
        .delay-2 { animation-delay: 0.2s; }
        .delay-3 { animation-delay: 0.3s; }
        .delay-4 { animation-delay: 0.4s; }

        @media (min-width: 640px) {
            .main { max-width: 560px; }
            .header { padding: 36px 20px 56px; }
            .header-title { font-size: 30px; }
        }

        /* Lock body scroll when lightbox open */
        body.lightbox-open { overflow: hidden; }
    </style>
</head>
<body>

    {{-- Header --}}
    <div class="header">
        <div class="header-badge">Puskesmas Rawat Inap Kabalsiang Benjuring</div>
        <h1 class="header-title">{{ $ruangan->nama }}</h1>
        <div class="header-meta">
            @if($ruangan->kode)<span>{{ $ruangan->kode }}</span>@endif
            @if($ruangan->kode && $ruangan->lantai)<span class="header-dot"></span>@endif
            @if($ruangan->lantai)<span>Lantai {{ $ruangan->lantai }}</span>@endif
        </div>
    </div>

    <div class="main">
        {{-- PJ Card --}}
        @if($pj)
        <div class="pj-card animate-in">
            @if($pj->foto_path)
                <img src="{{ url('storage/' . $pj->foto_path) }}" class="pj-avatar" alt="{{ $pj->nama }}">
            @else
                <div class="pj-avatar-placeholder">{{ substr($pj->nama, 0, 1) }}</div>
            @endif
            <div>
                <div class="pj-label">Penanggung Jawab</div>
                <div class="pj-name">{{ $pj->nama }}</div>
                <div class="pj-role">{{ $pj->jabatan ?? '-' }}</div>
            </div>
        </div>
        @endif

        {{-- Staff Section --}}
        @if($anggota->isNotEmpty())
        <div class="staff-section animate-in delay-1">
            <div class="section-label">Petugas Bertugas</div>
            <div class="staff-pills">
                @foreach($anggota as $a)
                <div class="staff-pill">
                    @if($a->foto_path)
                        <img src="{{ url('storage/' . $a->foto_path) }}" alt="">
                    @else
                        <div class="staff-pill-avatar">{{ substr($a->nama, 0, 1) }}</div>
                    @endif
                    {{ $a->nama }}
                </div>
                @endforeach
            </div>
        </div>
        @endif

        {{-- Stats --}}
        <div class="stats animate-in delay-2">
            <div class="stat-card stat-total">
                <div class="stat-value">{{ $totalUnit }}</div>
                <div class="stat-label">Total</div>
            </div>
            <div class="stat-card stat-baik">
                <div class="stat-value">{{ $kondisiSummary['baik'] }}</div>
                <div class="stat-label">Baik</div>
            </div>
            <div class="stat-card stat-rusak">
                <div class="stat-value">{{ $kondisiSummary['rusak_ringan'] }}</div>
                <div class="stat-label">Rusak Ringan</div>
            </div>
            <div class="stat-card stat-berat">
                <div class="stat-value">{{ $kondisiSummary['rusak_berat'] }}</div>
                <div class="stat-label">Rusak Berat</div>
            </div>
        </div>

        {{-- Item List --}}
        <div class="animate-in delay-3">
            <div class="items-header">
                <div class="items-title">Daftar Barang</div>
                <div class="items-count">{{ $ruangan->barang->count() }} jenis</div>
            </div>

            @if($ruangan->barang->isEmpty())
                <div class="empty-state">
                    <div class="icon">📦</div>
                    <p>Belum ada barang tercatat di ruangan ini.</p>
                </div>
            @else
                @php
                    $kondisiLabels = [
                        'baik' => 'Baik',
                        'rusak_ringan' => 'Rusak Ringan',
                        'rusak_berat' => 'Rusak Berat',
                        'tidak_aktif' => 'Tidak Aktif',
                    ];
                    // Collect items with photos for lightbox
                    $photoItems = $ruangan->barang->filter(function($b) { return $b->foto_path; })->values();
                @endphp
                <div class="items">
                    @foreach($ruangan->barang as $i => $b)
                        @php
                            $photoIndex = $b->foto_path ? $photoItems->search(function($p) use ($b) { return $p->id === $b->id; }) : null;
                        @endphp
                        <div class="item">
                            {{-- Thumbnail --}}
                            @if($b->foto_path)
                                <img src="{{ url('storage/' . $b->foto_path) }}"
                                     alt="{{ $b->nama }}"
                                     class="item-thumb"
                                     onclick="openLightbox({{ $photoIndex }})"
                                     loading="lazy">
                            @else
                                <div class="item-thumb-placeholder">📦</div>
                            @endif

                            {{-- Info --}}
                            <div class="item-info">
                                <div class="item-name">{{ $b->nama }}</div>
                                <div class="item-meta">
                                    @if($b->merek)<span>{{ $b->merek }}</span>@endif
                                    @if($b->kode_inventaris)<span>· {{ $b->kode_inventaris }}</span>@endif
                                    @if($b->tahun_perolehan)<span>· {{ $b->tahun_perolehan }}</span>@endif
                                </div>
                                <span class="badge badge-{{ $b->kondisi }}">{{ $kondisiLabels[$b->kondisi] ?? $b->kondisi }}</span>
                            </div>

                            {{-- Qty --}}
                            <div class="item-right">
                                <div class="item-qty">{{ $b->jumlah }}</div>
                                <div class="item-qty-label">unit</div>
                            </div>
                        </div>
                    @endforeach
                </div>
            @endif
        </div>
    </div>

    {{-- Footer --}}
    <div class="footer animate-in delay-4">
        <strong>Puskesmas Rawat Inap Kabalsiang Benjuring</strong> — Sistem Inventaris Digital<br>
        Diperbarui: {{ now()->format('d M Y, H:i') }} WIB
    </div>

    {{-- ═══ Lightbox Overlay ═══ --}}
    @if(isset($photoItems) && $photoItems->isNotEmpty())
    <div class="lightbox-overlay" id="lightbox">
        <button class="lightbox-close" onclick="closeLightbox()" aria-label="Tutup">✕</button>
        <div class="lightbox-counter" id="lb-counter"></div>
        <button class="lightbox-nav prev" onclick="prevPhoto()" id="lb-prev" aria-label="Sebelumnya">‹</button>
        <button class="lightbox-nav next" onclick="nextPhoto()" id="lb-next" aria-label="Selanjutnya">›</button>
        <div class="lightbox-img-wrap" id="lb-img-wrap">
            <img class="lightbox-img" id="lb-img" src="" alt="">
        </div>
        <div class="lightbox-caption">
            <div class="lightbox-caption-name" id="lb-name"></div>
            <div class="lightbox-caption-meta" id="lb-meta"></div>
            <span class="lightbox-caption-badge" id="lb-badge"></span>
        </div>
    </div>

    @php
        $photoData = [];
        foreach ($photoItems as $b) {
            $photoData[] = [
                'src' => url('storage/' . $b->foto_path),
                'nama' => $b->nama,
                'merek' => $b->merek ?? '',
                'kondisi' => $b->kondisi,
                'kondisiLabel' => $kondisiLabels[$b->kondisi] ?? $b->kondisi,
                'jumlah' => $b->jumlah,
                'kode' => $b->kode_inventaris ?? '',
            ];
        }
    @endphp

    <script>
    (function() {
        // Photo data from server
        const photos = @json($photoData);

        let currentIndex = 0;

        const overlay  = document.getElementById('lightbox');
        const img      = document.getElementById('lb-img');
        const name     = document.getElementById('lb-name');
        const meta     = document.getElementById('lb-meta');
        const badge    = document.getElementById('lb-badge');
        const counter  = document.getElementById('lb-counter');
        const prevBtn  = document.getElementById('lb-prev');
        const nextBtn  = document.getElementById('lb-next');
        const imgWrap  = document.getElementById('lb-img-wrap');

        function showPhoto(index) {
            const p = photos[index];
            img.src = p.src;
            img.alt = p.nama;
            name.textContent = p.nama;

            let metaParts = [];
            if (p.merek) metaParts.push(p.merek);
            if (p.kode) metaParts.push(p.kode);
            metaParts.push(p.jumlah + ' unit');
            meta.textContent = metaParts.join(' · ');

            badge.textContent = p.kondisiLabel;
            badge.className = 'lightbox-caption-badge lb-' + p.kondisi;

            counter.textContent = (index + 1) + ' / ' + photos.length;
            prevBtn.classList.toggle('hidden', index === 0);
            nextBtn.classList.toggle('hidden', index === photos.length - 1);
            currentIndex = index;
        }

        window.openLightbox = function(index) {
            if (index < 0 || index >= photos.length) return;
            showPhoto(index);
            overlay.classList.add('active');
            document.body.classList.add('lightbox-open');
        };

        window.closeLightbox = function() {
            overlay.classList.remove('active');
            document.body.classList.remove('lightbox-open');
            setTimeout(() => { img.src = ''; }, 300);
        };

        window.nextPhoto = function() {
            if (currentIndex < photos.length - 1) showPhoto(currentIndex + 1);
        };

        window.prevPhoto = function() {
            if (currentIndex > 0) showPhoto(currentIndex - 1);
        };

        // Close on backdrop click
        imgWrap.addEventListener('click', function(e) {
            if (e.target === imgWrap) closeLightbox();
        });

        // Keyboard navigation
        document.addEventListener('keydown', function(e) {
            if (!overlay.classList.contains('active')) return;
            if (e.key === 'Escape') closeLightbox();
            if (e.key === 'ArrowRight') nextPhoto();
            if (e.key === 'ArrowLeft') prevPhoto();
        });

        // Touch swipe support
        let touchStartX = 0;
        let touchEndX = 0;

        overlay.addEventListener('touchstart', function(e) {
            touchStartX = e.changedTouches[0].screenX;
        }, { passive: true });

        overlay.addEventListener('touchend', function(e) {
            touchEndX = e.changedTouches[0].screenX;
            const diff = touchStartX - touchEndX;
            if (Math.abs(diff) > 50) {
                if (diff > 0) nextPhoto();  // swipe left → next
                else prevPhoto();           // swipe right → prev
            }
        }, { passive: true });
    })();
    </script>
    @endif

</body>
</html>
