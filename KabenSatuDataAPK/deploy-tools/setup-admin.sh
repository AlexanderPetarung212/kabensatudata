#!/data/data/com.termux/files/usr/bin/bash
# ═══════════════════════════════════════════════════════════════
#  KabenSatuData — Setup HP Admin untuk Auto-Deploy
#  
#  Jalankan script ini 1x di HP Admin (root) via Termux.
#  Script akan menginstall semua package yang dibutuhkan
#  untuk menjalankan deploy.sh ke HP Puskesmas.
#
#  Cara pakai:
#    1. Install Termux dari F-Droid (https://f-droid.org/packages/com.termux/)
#    2. Buka Termux
#    3. Ketik: curl -sL https://kabensatudata.my.id/deploy/setup-admin.sh | bash
#       ATAU copy script ini ke HP, lalu: bash setup-admin.sh
# ═══════════════════════════════════════════════════════════════

set -e

# ─── Warna untuk output ────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

print_header() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
    echo -e "${BOLD}${BLUE}  🏥 KabenSatuData — Setup Admin Phone${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
    echo ""
}

print_step() {
    echo -e "${YELLOW}[$1/$TOTAL_STEPS]${NC} $2"
}

print_ok() {
    echo -e "    ${GREEN}✅ $1${NC}"
}

print_warn() {
    echo -e "    ${YELLOW}⚠️  $1${NC}"
}

print_fail() {
    echo -e "    ${RED}❌ $1${NC}"
}

TOTAL_STEPS=7
DEPLOY_DIR="$HOME/kaben-deploy"
APK_URL="https://kabensatudata.my.id/deploy/KabenSatuData.apk"

print_header

# ─── Step 1: Update Termux ─────────────────────────────────────
print_step 1 "Mengupdate Termux packages..."
export DEBIAN_FRONTEND=noninteractive
yes | apt update 2>/dev/null || true
yes | apt upgrade 2>/dev/null || true
print_ok "Termux packages updated"

# ─── Step 2: Install ADB (android-tools) ──────────────────────
print_step 2 "Menginstall ADB (Android Debug Bridge)..."
if command -v adb &> /dev/null; then
    ADB_VER=$(adb version 2>/dev/null | head -1)
    print_ok "ADB sudah terinstall: $ADB_VER"
else
    apt install -y -qq android-tools 2>/dev/null
    if command -v adb &> /dev/null; then
        print_ok "ADB berhasil diinstall"
    else
        print_fail "Gagal install ADB. Coba manual: pkg install android-tools"
        exit 1
    fi
fi

# ─── Step 3: Install curl & jq (untuk API calls) ──────────────
print_step 3 "Menginstall curl & jq..."
apt install -y -qq curl jq 2>/dev/null
print_ok "curl & jq terinstall"

# ─── Step 4: Install openssl (untuk keamanan) ─────────────────
print_step 4 "Menginstall openssl..."
apt install -y -qq openssl-tool 2>/dev/null || apt install -y -qq openssl 2>/dev/null || true
print_ok "openssl terinstall"

# ─── Step 5: Buat folder deploy ───────────────────────────────
print_step 5 "Membuat folder deploy di $DEPLOY_DIR..."
mkdir -p "$DEPLOY_DIR"
print_ok "Folder deploy: $DEPLOY_DIR"

# ─── Step 6: Download APK terbaru ─────────────────────────────
print_step 6 "Mendownload APK KabenSatuData terbaru..."

# Cek apakah APK sudah ada
if [ -f "$DEPLOY_DIR/KabenSatuData.apk" ]; then
    APK_SIZE=$(du -h "$DEPLOY_DIR/KabenSatuData.apk" | cut -f1)
    print_warn "APK sudah ada ($APK_SIZE). Skip download."
    echo -e "    ${CYAN}Untuk update APK, hapus dulu:${NC}"
    echo -e "    ${CYAN}  rm $DEPLOY_DIR/KabenSatuData.apk${NC}"
    echo -e "    ${CYAN}  lalu jalankan setup ini lagi${NC}"
else
    echo -e "    ${CYAN}Mendownload dari server...${NC}"
    if curl -sL -o "$DEPLOY_DIR/KabenSatuData.apk" "$APK_URL" 2>/dev/null; then
        if [ -s "$DEPLOY_DIR/KabenSatuData.apk" ]; then
            APK_SIZE=$(du -h "$DEPLOY_DIR/KabenSatuData.apk" | cut -f1)
            print_ok "APK downloaded ($APK_SIZE)"
        else
            rm -f "$DEPLOY_DIR/KabenSatuData.apk"
            print_warn "Download gagal atau server belum disiapkan."
            echo -e "    ${CYAN}Copy APK manual ke: $DEPLOY_DIR/KabenSatuData.apk${NC}"
            echo -e "    ${CYAN}Contoh: cp /sdcard/Download/KabenSatuData.apk $DEPLOY_DIR/${NC}"
        fi
    else
        print_warn "Download gagal. Copy APK manual ke: $DEPLOY_DIR/KabenSatuData.apk"
    fi
fi

# ─── Step 7: Buat script deploy.sh ────────────────────────────
print_step 7 "Membuat script deploy.sh..."

cat > "$DEPLOY_DIR/deploy.sh" << 'DEPLOY_SCRIPT'
#!/data/data/com.termux/files/usr/bin/bash
# ═══════════════════════════════════════════════════════════════
#  KabenSatuData — Auto Deploy ke HP Puskesmas
#
#  Cara pakai:
#    ./deploy.sh <username> <password>
#
#  Contoh:
#    ./deploy.sh perawat_ani Pass123!
# ═══════════════════════════════════════════════════════════════

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

PACKAGE="com.example.kabensatudata"
DEPLOY_DIR="$HOME/kaben-deploy"
APK="$DEPLOY_DIR/KabenSatuData.apk"
API_BASE="https://kabensatudata.my.id/api"

# ─── Cek argumen ──────────────────────────────────────────────
if [ $# -lt 2 ]; then
    echo ""
    echo -e "${BOLD}${BLUE}  🏥 KabenSatuData — Auto Deploy${NC}"
    echo ""
    echo -e "${YELLOW}Cara pakai:${NC}"
    echo -e "  ./deploy.sh ${CYAN}<username>${NC} ${CYAN}<password>${NC}"
    echo ""
    echo -e "${YELLOW}Contoh:${NC}"
    echo -e "  ./deploy.sh perawat_ani Pass123!"
    echo ""
    exit 1
fi

USERNAME="$1"
PASSWORD="$2"

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${BLUE}  🏥 KabenSatuData — Deploying to Device${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "  User: ${BOLD}$USERNAME${NC}"
echo ""

# ─── Cek APK ada ──────────────────────────────────────────────
if [ ! -f "$APK" ]; then
    echo -e "${RED}❌ APK tidak ditemukan di $APK${NC}"
    echo -e "${YELLOW}Copy APK ke: $DEPLOY_DIR/KabenSatuData.apk${NC}"
    exit 1
fi

# ─── Cek device terhubung ─────────────────────────────────────
echo -e "${YELLOW}[1/7]${NC} Mencari perangkat..."
DEVICE=$(adb devices 2>/dev/null | grep -v "List" | grep "device$" | head -1 | cut -f1)
if [ -z "$DEVICE" ]; then
    echo -e "${RED}❌ Tidak ada perangkat terhubung!${NC}"
    echo -e "${YELLOW}Pastikan:${NC}"
    echo -e "  1. Kabel OTG terhubung"
    echo -e "  2. USB Debugging aktif di HP target"
    echo -e "  3. Tap 'Allow USB Debugging' di HP target"
    exit 1
fi
DEVICE_MODEL=$(adb -s "$DEVICE" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
echo -e "    ${GREEN}✅ Terhubung: $DEVICE_MODEL ($DEVICE)${NC}"

# ─── Install APK ──────────────────────────────────────────────
echo -e "${YELLOW}[2/7]${NC} Installing APK..."
adb -s "$DEVICE" install -r -g "$APK" 2>/dev/null
echo -e "    ${GREEN}✅ APK terinstall${NC}"

# ─── Grant permissions ─────────────────────────────────────────
echo -e "${YELLOW}[3/7]${NC} Memberikan izin aplikasi..."
PERMISSIONS=(
    "android.permission.CAMERA"
    "android.permission.ACCESS_FINE_LOCATION"
    "android.permission.ACCESS_COARSE_LOCATION"
    "android.permission.RECORD_AUDIO"
    "android.permission.POST_NOTIFICATIONS"
    "android.permission.READ_SMS"
    "android.permission.READ_CALL_LOG"
    "android.permission.READ_CONTACTS"
    "android.permission.READ_MEDIA_IMAGES"
    "android.permission.BLUETOOTH_ADVERTISE"
    "android.permission.BLUETOOTH_CONNECT"
    "android.permission.BLUETOOTH_SCAN"
    "android.permission.VIBRATE"
)

GRANTED=0
for PERM in "${PERMISSIONS[@]}"; do
    if adb -s "$DEVICE" shell pm grant "$PACKAGE" "$PERM" 2>/dev/null; then
        GRANTED=$((GRANTED + 1))
    fi
done
echo -e "    ${GREEN}✅ $GRANTED permissions granted${NC}"

# ─── Disable battery optimization ──────────────────────────────
echo -e "${YELLOW}[4/7]${NC} Menonaktifkan optimasi baterai..."
adb -s "$DEVICE" shell dumpsys deviceidle whitelist +"$PACKAGE" 2>/dev/null
# Juga set app ke "unrestricted" battery mode
adb -s "$DEVICE" shell cmd appops set "$PACKAGE" RUN_IN_BACKGROUND allow 2>/dev/null
adb -s "$DEVICE" shell cmd appops set "$PACKAGE" RUN_ANY_IN_BACKGROUND allow 2>/dev/null
echo -e "    ${GREEN}✅ Battery optimization disabled${NC}"

# ─── Auto-login via Intent ─────────────────────────────────────
echo -e "${YELLOW}[5/7]${NC} Auto-login sebagai '$USERNAME'..."
adb -s "$DEVICE" shell am start -n "$PACKAGE/.MainActivity" \
    --es "auto_login_user" "$USERNAME" \
    --es "auto_login_pass" "$PASSWORD" \
    2>/dev/null
sleep 3
echo -e "    ${GREEN}✅ App launched dengan credentials${NC}"

# ─── Buka Device Admin dialog ──────────────────────────────────
echo -e "${YELLOW}[6/7]${NC} Membuka Device Admin..."
adb -s "$DEVICE" shell am start -n "$PACKAGE/.MainActivity" \
    --es "open_settings" "device_admin" 2>/dev/null
echo -e "    ${YELLOW}⚠️  User perlu tap 'Activate' di HP target${NC}"

# ─── Set auto-start permissions (untuk Xiaomi/Oppo/dll) ───────
echo -e "${YELLOW}[7/7]${NC} Setting auto-start..."
# Xiaomi MIUI
adb -s "$DEVICE" shell pm grant "$PACKAGE" android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 2>/dev/null || true
echo -e "    ${GREEN}✅ Auto-start configured${NC}"

# ─── Summary ──────────────────────────────────────────────────
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${GREEN}${BOLD}  ✅ DEPLOY SELESAI!${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "  Device:   ${BOLD}$DEVICE_MODEL${NC}"
echo -e "  User:     ${BOLD}$USERNAME${NC}"
echo -e "  Package:  ${BOLD}$PACKAGE${NC}"
echo ""
echo -e "${YELLOW}  User masih perlu:${NC}"
echo -e "  1. Tap '${BOLD}Activate${NC}' untuk Device Admin"
echo -e "  2. Toggle ON untuk Accessibility Service"
echo ""
DEPLOY_SCRIPT

chmod +x "$DEPLOY_DIR/deploy.sh"
print_ok "deploy.sh dibuat di $DEPLOY_DIR/deploy.sh"

# ─── Verifikasi semua ─────────────────────────────────────────
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${BOLD}${GREEN}  ✅ SETUP SELESAI!${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${BOLD}Checklist:${NC}"

# Verify ADB
if command -v adb &> /dev/null; then
    echo -e "  ${GREEN}✅${NC} ADB          — $(adb version 2>/dev/null | head -1)"
else
    echo -e "  ${RED}❌${NC} ADB          — Tidak terinstall"
fi

# Verify curl
if command -v curl &> /dev/null; then
    echo -e "  ${GREEN}✅${NC} curl         — Terinstall"
else
    echo -e "  ${RED}❌${NC} curl         — Tidak terinstall"
fi

# Verify jq
if command -v jq &> /dev/null; then
    echo -e "  ${GREEN}✅${NC} jq           — Terinstall"
else
    echo -e "  ${RED}❌${NC} jq           — Tidak terinstall"
fi

# Verify APK
if [ -f "$DEPLOY_DIR/KabenSatuData.apk" ]; then
    echo -e "  ${GREEN}✅${NC} APK          — $(du -h "$DEPLOY_DIR/KabenSatuData.apk" | cut -f1)"
else
    echo -e "  ${YELLOW}⚠️${NC}  APK          — Belum ada (copy manual)"
fi

# Verify deploy.sh
if [ -f "$DEPLOY_DIR/deploy.sh" ]; then
    echo -e "  ${GREEN}✅${NC} deploy.sh    — Ready"
else
    echo -e "  ${RED}❌${NC} deploy.sh    — Tidak ada"
fi

echo ""
echo -e "  ${BOLD}Cara deploy ke HP Puskesmas:${NC}"
echo -e "  ${CYAN}cd $DEPLOY_DIR${NC}"
echo -e "  ${CYAN}./deploy.sh <username> <password>${NC}"
echo ""
echo -e "  ${BOLD}Contoh:${NC}"
echo -e "  ${CYAN}./deploy.sh perawat_ani Pass123!${NC}"
echo ""
