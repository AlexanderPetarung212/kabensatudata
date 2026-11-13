#!/data/data/com.termux/files/usr/bin/bash
# ════════════════════════════════════════════════
#  Setup Scanner Jaringan — Jalankan 1x di Termux
# ════════════════════════════════════════════════

echo ""
echo -e "\033[0;36m══════════════════════════════════════\033[0m"
echo -e "\033[1;34m  🏥 Setup WiFi Scanner — Puskesmas\033[0m"
echo -e "\033[0;36m══════════════════════════════════════\033[0m"
echo ""

# Install semua tools yang dibutuhkan
echo -e "\033[1;33m[1/4]\033[0m Install packages..."
export DEBIAN_FRONTEND=noninteractive
yes | apt update 2>/dev/null || true
yes | apt install -y nmap curl jq net-tools iproute2 dnsutils 2>/dev/null || true
echo -e "    \033[0;32m✅ Packages installed\033[0m"

# Copy scan-jaringan.sh ke home
echo -e "\033[1;33m[2/4]\033[0m Setup script..."
DEPLOY_DIR="$HOME/downloads"
mkdir -p "$DEPLOY_DIR"

# Buat shortcut mudah di $HOME
if [ -f "$DEPLOY_DIR/scan-jaringan.sh" ]; then
    chmod +x "$DEPLOY_DIR/scan-jaringan.sh"
    echo -e "    \033[0;32m✅ scan-jaringan.sh ready\033[0m"
else
    echo -e "    \033[1;33m⚠️  Copy dulu: cp /sdcard/Download/scan-jaringan.sh $DEPLOY_DIR/\033[0m"
fi

# Buat whitelist kosong jika belum ada
echo -e "\033[1;33m[3/4]\033[0m Membuat whitelist file..."
WHITELIST="$DEPLOY_DIR/whitelist-mac.txt"
if [ ! -f "$WHITELIST" ]; then
    cat > "$WHITELIST" << 'EOF'
# ══════════════════════════════════════════════════
#  Whitelist MAC Address HP Puskesmas
#  Format: MAC_ADDRESS,NAMA,KETERANGAN
# ══════════════════════════════════════════════════
# Cara cari MAC HP:
#   Settings → About Phone → Status → WiFi MAC Address
#
# Contoh (hapus tanda # dan ganti dengan data nyata):
# AC:37:43:12:34:56,Dokter Budi,Redmi Note 9 Pro
# 38:1A:52:AB:CD:EF,Bidan Ani,Samsung A32
# B0:E235:11:22:33,Perawat Cici,OPPO A54
EOF
    echo -e "    \033[0;32m✅ whitelist-mac.txt dibuat\033[0m"
else
    echo -e "    \033[0;32m✅ whitelist-mac.txt sudah ada\033[0m"
fi

# Buat alias shortcut
echo -e "\033[1;33m[4/4]\033[0m Membuat shortcut alias..."
BASHRC="$HOME/.bashrc"
if ! grep -q "scan-jaringan" "$BASHRC" 2>/dev/null; then
    echo "" >> "$BASHRC"
    echo "# KabenSatuData WiFi Scanner" >> "$BASHRC"
    echo "alias scan='bash $DEPLOY_DIR/scan-jaringan.sh'" >> "$BASHRC"
    echo "alias scan-detail='bash $DEPLOY_DIR/scan-jaringan.sh -d'" >> "$BASHRC"
    echo "alias scan-monitor='bash $DEPLOY_DIR/scan-jaringan.sh -m'" >> "$BASHRC"
fi
source "$BASHRC" 2>/dev/null || true
echo -e "    \033[0;32m✅ Alias dibuat (scan, scan-detail, scan-monitor)\033[0m"

echo ""
echo -e "\033[0;36m══════════════════════════════════════\033[0m"
echo -e "\033[1;32m  ✅ SETUP SELESAI!\033[0m"
echo -e "\033[0;36m══════════════════════════════════════\033[0m"
echo ""
echo -e "\033[1mCara pakai:\033[0m"
echo -e "  \033[0;36mscan\033[0m                  Scan cepat"
echo -e "  \033[0;36mscan-detail\033[0m           Scan detail (OS + port)"
echo -e "  \033[0;36mscan-monitor\033[0m          Monitor otomatis tiap 30 detik"
echo ""
echo -e "\033[1mEdit whitelist HP Puskesmas:\033[0m"
echo -e "  \033[0;36mnano $WHITELIST\033[0m"
echo ""
