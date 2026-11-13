#!/data/data/com.termux/files/usr/bin/bash
# ══════════════════════════════════════════════════════
#  KabenSatuData Scanner — Launcher dengan root support
#  Jalankan: bash run-scanner.sh [argumen]
# ══════════════════════════════════════════════════════

PYTHON3="/data/data/com.termux/files/usr/bin/python3"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCANNER="$SCRIPT_DIR/scanner.py"

echo ""
echo -e "\033[0;36m  🏥 KabenSatuData WiFi Scanner\033[0m"

# Pastikan file ada
if [ ! -f "$SCANNER" ]; then
    echo -e "\033[0;31m  ❌ scanner.py tidak ditemukan di: $SCANNER\033[0m"
    exit 1
fi

# Jalankan langsung dengan python3 full path (tidak perlu su karena
# ping_sweep + /proc/net/arp tidak butuh root)
echo -e "\033[0;32m  ✅ Menjalankan scanner...\033[0m"
"$PYTHON3" "$SCANNER" "$@"

