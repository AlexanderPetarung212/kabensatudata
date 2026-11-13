#!/bin/bash
# ═══════════════════════════════════════════════════════
# rebuild.sh — Ganti IP server & build ulang APK
# ═══════════════════════════════════════════════════════
# Cara pakai:
#   ./rebuild.sh 192.168.1.100
#   ./rebuild.sh              (tanpa argumen = lihat IP saat ini)
# ═══════════════════════════════════════════════════════

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/app/src/main/java/com/example/kabensatudata"

# File yang berisi IP server
FILES=(
    "$SRC_DIR/di/AppModule.kt"
    "$SRC_DIR/ui/screen/nearby/NearbyScreen.kt"
    "$SRC_DIR/update/InAppUpdateManager.kt"
)

# Deteksi IP saat ini dari AppModule.kt
CURRENT_IP=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' "$SRC_DIR/di/AppModule.kt" | head -1)

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   KabenSatuData — IP Rebuild Tool   ║"
echo "╚══════════════════════════════════════╝"
echo ""
echo "  IP saat ini: $CURRENT_IP"
echo ""

# Jika tanpa argumen, tampilkan info saja
if [ -z "$1" ]; then
    echo "  Untuk ganti IP, jalankan:"
    echo "  ./rebuild.sh <IP_BARU>"
    echo ""
    echo "  Contoh: ./rebuild.sh 192.168.1.100"
    echo ""
    echo "  File yang akan diubah:"
    for f in "${FILES[@]}"; do
        echo "    - $(basename "$f")"
    done
    echo ""
    exit 0
fi

NEW_IP="$1"

# Validasi format IP
if ! echo "$NEW_IP" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "  ❌ Format IP tidak valid: $NEW_IP"
    echo "  Gunakan format: xxx.xxx.xxx.xxx"
    exit 1
fi

# Jika IP sama, skip
if [ "$CURRENT_IP" = "$NEW_IP" ]; then
    echo "  ⚡ IP sudah sama ($CURRENT_IP), langsung build..."
else
    echo "  🔄 Mengganti IP: $CURRENT_IP → $NEW_IP"
    echo ""

    for f in "${FILES[@]}"; do
        if [ -f "$f" ]; then
            MATCHES=$(grep -c "$CURRENT_IP" "$f" 2>/dev/null || true)
            if [ "$MATCHES" -gt 0 ]; then
                sed -i '' "s/$CURRENT_IP/$NEW_IP/g" "$f"
                echo "    ✓ $(basename "$f") ($MATCHES penggantian)"
            fi
        fi
    done

    echo ""
    echo "  ✅ IP berhasil diganti ke $NEW_IP"
fi

echo ""
echo "  🔨 Memulai build APK..."
echo ""

cd "$PROJECT_DIR"
./gradlew assembleRelease

echo ""
echo "  ✅ BUILD SELESAI!"
echo ""

# Tampilkan lokasi APK
APK_PATH=$(find "$PROJECT_DIR/app/build/outputs/apk/release" -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK_PATH" ]; then
    echo "  📦 APK: $APK_PATH"
    echo "  📏 Ukuran: $(du -h "$APK_PATH" | cut -f1)"
fi
echo ""
