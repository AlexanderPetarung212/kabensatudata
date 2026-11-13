#!/data/data/com.termux/files/usr/bin/bash
# ═══════════════════════════════════════════════════════════════════════
#  KabenSatuData — WiFi Network Scanner (Powered by nmap + ARP + OUI)
#
#  Cara pakai:
#    chmod +x scan-jaringan.sh
#    ./scan-jaringan.sh              # Scan semua device
#    ./scan-jaringan.sh -d           # Scan detail (lebih lambat, lebih lengkap)
#    ./scan-jaringan.sh -m           # Monitor mode (scan terus tiap 30 detik)
#    ./scan-jaringan.sh -w whitelist.txt  # Bandingkan dengan whitelist
#
#  Kebutuhan (jalankan setup-scanner.sh dulu jika belum):
#    pkg install nmap curl jq net-tools iproute2
# ═══════════════════════════════════════════════════════════════════════

# ─── Warna ─────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
WHITE='\033[1;37m'
DIM='\033[2m'
NC='\033[0m'
BOLD='\033[1m'
BG_RED='\033[41m'
BG_GREEN='\033[42m'
BG_YELLOW='\033[43m'

# ─── Konfigurasi ────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WHITELIST_FILE="$SCRIPT_DIR/whitelist-mac.txt"
OUI_CACHE="$SCRIPT_DIR/oui-cache.json"
LOG_FILE="$SCRIPT_DIR/scan-log.txt"
SCAN_DETAIL=false
MONITOR_MODE=false
MONITOR_INTERVAL=30

# ─── Parse argumen ──────────────────────────────────────────────────────
while [[ "$1" != "" ]]; do
    case $1 in
        -d | --detail )   SCAN_DETAIL=true ;;
        -m | --monitor )  MONITOR_MODE=true ;;
        -i | --interval ) shift; MONITOR_INTERVAL=$1 ;;
        -w | --whitelist ) shift; WHITELIST_FILE=$1 ;;
        -h | --help )
            echo ""
            echo -e "${BOLD}${CYAN}WiFi Scanner — Cara Pakai:${NC}"
            echo -e "  ${GREEN}./scan-jaringan.sh${NC}              Scan cepat semua device"
            echo -e "  ${GREEN}./scan-jaringan.sh -d${NC}           Scan detail (OS, services)"
            echo -e "  ${GREEN}./scan-jaringan.sh -m${NC}           Monitor (scan tiap 30 detik)"
            echo -e "  ${GREEN}./scan-jaringan.sh -m -i 60${NC}     Monitor tiap 60 detik"
            echo -e "  ${GREEN}./scan-jaringan.sh -w mac.txt${NC}   Bandingkan dengan whitelist"
            echo ""
            exit 0
            ;;
    esac
    shift
done

# ─── Fungsi Header ──────────────────────────────────────────────────────
print_header() {
    clear
    echo -e "${CYAN}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║   🏥  KABEN SATU DATA — WiFi Network Scanner v2.0           ║"
    echo "║   Redmi Note 9 Pro (Root) + Termux + nmap                   ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# ─── Cek tools ─────────────────────────────────────────────────────────
check_tools() {
    local missing=()
    for tool in nmap curl ip arp; do
        if ! command -v "$tool" &>/dev/null; then
            missing+=("$tool")
        fi
    done
    if [ ${#missing[@]} -ne 0 ]; then
        echo -e "${RED}❌ Tools yang belum terinstall: ${missing[*]}${NC}"
        echo -e "${YELLOW}Jalankan: pkg install nmap curl net-tools iproute2${NC}"
        exit 1
    fi
}

# ─── Deteksi Interface & Subnet ─────────────────────────────────────────
detect_network() {
    # Coba dapat interface WiFi
    IFACE=$(ip route 2>/dev/null | grep "wlan" | head -1 | awk '{print $5}')
    if [ -z "$IFACE" ]; then
        IFACE=$(ip route 2>/dev/null | grep default | awk '{print $5}' | head -1)
    fi

    # Dapat IP sendiri
    MY_IP=$(ip addr show "$IFACE" 2>/dev/null | grep "inet " | awk '{print $2}' | cut -d/ -f1 | head -1)

    # Dapat gateway (IP router)
    GATEWAY=$(ip route 2>/dev/null | grep default | awk '{print $3}' | head -1)

    # Hitung subnet CIDR
    SUBNET_CIDR=$(ip addr show "$IFACE" 2>/dev/null | grep "inet " | awk '{print $2}' | head -1)
    # Convert ke x.x.x.0/24 format
    SUBNET=$(echo "$MY_IP" | cut -d. -f1-3).0/24

    if [ -z "$MY_IP" ] || [ -z "$GATEWAY" ]; then
        echo -e "${RED}❌ Tidak terhubung ke jaringan WiFi!${NC}"
        echo -e "${YELLOW}Pastikan WiFi sudah terhubung.${NC}"
        exit 1
    fi
}

# ─── Lookup Vendor dari MAC via OUI Database ────────────────────────────
lookup_vendor() {
    local mac="$1"
    local oui=$(echo "$mac" | tr '[:lower:]' '[:upper:]' | sed 's/://g' | cut -c1-6)

    # Cek cache dulu
    if [ -f "$OUI_CACHE" ] && command -v jq &>/dev/null; then
        local cached=$(jq -r --arg oui "$oui" '.[$oui] // empty' "$OUI_CACHE" 2>/dev/null)
        if [ -n "$cached" ] && [ "$cached" != "null" ]; then
            echo "$cached"
            return
        fi
    fi

    # Query via API (butuh internet)
    local vendor=$(curl -s --max-time 3 "https://api.macvendors.com/${oui}" 2>/dev/null | head -c 50)

    # Kalau tidak dapat dari API, gunakan local lookup
    if [ -z "$vendor" ] || echo "$vendor" | grep -qi "error\|not found\|Too Many"; then
        vendor=$(lookup_vendor_local "$oui")
    fi

    # Simpan ke cache
    if [ -n "$vendor" ] && command -v jq &>/dev/null; then
        if [ ! -f "$OUI_CACHE" ]; then echo "{}" > "$OUI_CACHE"; fi
        local tmp=$(mktemp)
        jq --arg oui "$oui" --arg vendor "$vendor" '.[$oui] = $vendor' "$OUI_CACHE" > "$tmp" && mv "$tmp" "$OUI_CACHE"
    fi

    echo "${vendor:-Unknown}"
}

# ─── Local OUI Database (Top brands) ────────────────────────────────────
lookup_vendor_local() {
    local oui="$1"
    case "${oui^^}" in
        # Samsung
        "001599"|"0016DB"|"001E7D"|"0021D1"|"0024E9"|"002693"|"0026E1"|"308D99"|
        "5CFF35"|"8425DB"|"9CD91F"|"A8F274"|"B47443"|"B4EF39"|"D0DCE1"|"D87195"|
        "F4F5E8"|"FC4B2F"|"001A25"|"784B87"|"AC5F3E"|"D0DCE1") echo "Samsung" ;;
        # Xiaomi / Redmi
        "0C1DAF"|"10BA38"|"28E31F"|"34CE00"|"38A428"|"3C9872"|"58447A"|"64B473"|
        "64CC2E"|"7453D6"|"7EC671"|"8CACAB"|"9829A9"|"A086C6"|"A09469"|"B0E235"|
        "D02544"|"F48B32"|"F0B429"|"005007"|"001849") echo "Xiaomi/Redmi" ;;
        # OPPO
        "0012C2"|"00169D"|"40452C"|"50A4C8"|"706F67"|"AC1101"|"BC7F89"|
        "C84B55"|"CCB265"|"D4500F") echo "OPPO" ;;
        # Vivo
        "00E04C"|"002548"|"242C44"|"3C5771"|"484646"|"601466"|"6C5C14"|
        "701CE7"|"8093BB"|"90E8BA"|"D4207B") echo "Vivo" ;;
        # Realme
        "1C771B"|"9C5316"|"BC4C89"|"D0293C") echo "Realme" ;;
        # Huawei
        "001882"|"00259E"|"0090FC"|"00A0C6"|"001E67"|"002389"|"BC764E"|
        "286ED4"|"30FB08"|"34A84E"|"40CBAD"|"401485"|"4CAC0A"|"54C06F"|
        "5C4CCA"|"5CBA37"|"6485D4"|"70725D"|"7098C6"|"8C34FD"|"94DBDA"|
        "C4072F"|"D46E5C"|"D4F9A1"|"E02861"|"E0247F"|"E4A8B6"|"F4015E"|
        "F8E811"|"FC348C") echo "Huawei" ;;
        # Apple iPhone
        "001451"|"0016CB"|"001B63"|"001D4F"|"001EC2"|"001F5B"|"00238C"|
        "003065"|"0050E4"|"005056"|"0C4DE9"|"0C7CB5"|"0C8BFD"|"10DDB1"|
        "183451"|"18AF8F"|"1C5CF2"|"2453DF"|"2CB43A"|"34159E"|"38C986"|
        "3C2EFF"|"3C7D0A"|"4C57CA"|"4C8D79"|"5C8D4E"|"60FB42"|"685B35"|
        "6CEC5F"|"8C2937"|"8C7B9D"|"940C6D"|"989E63"|"98B8E3"|"9CF387"|
        "A43135"|"A860B6"|"AC7BA1"|"B065BD"|"B88D12"|"B8FF61"|"BC9FEF"|
        "C86000"|"F0DBE2"|"F4F951") echo "Apple (iPhone/iPad)" ;;
        # TP-Link Router
        "000AEB"|"001D0F"|"008C54"|"00F81D"|"0C8268"|"14CC20"|"1C61B4"|
        "2016B9"|"2C4D54"|"30B5C2"|"50FA84"|"5409A4"|"544A16"|"547AE5"|
        "68FF7B"|"6C5AB0"|"706F81"|"74DADA"|"741A4C"|"78A353"|"88D7F6"|
        "90F6521"|"94D9B3"|"A0F3C1"|"ACF1DF"|"B0A7B9"|"B4B024"|"D80D17"|
        "D84910"|"E83E26"|"EC08E5"|"F4F26D"|"FC7516") echo "TP-Link (Router)" ;;
        # MikroTik Router
        "000C42"|"2CC8600"|"4C5E0C"|"6C3B6B"|"74AD17"|"8C88E1"|"B8698A"|
        "CC2DE0"|"D4CA6D"|"DC2C6E"|"E48D8C") echo "MikroTik (Router)" ;;
        # Indihome ONT/Router
        "001EC0"|"3C5AB4"|"ACCFA0"|"008E73"|"485754") echo "ZTE/Indihome" ;;
        # Notebook/PC
        "000D3A"|"001DD8"|"00155D"|"002248") echo "Microsoft/Windows PC" ;;
        "000C29"|"005056"|"000569") echo "VMware (Virtual)" ;;
        *) echo "" ;;
    esac
}

# ─── Deteksi OS dari nmap output ─────────────────────────────────────────
parse_os() {
    local os_info="$1"
    if echo "$os_info" | grep -qi "android"; then echo "📱 Android"
    elif echo "$os_info" | grep -qi "ios\|iphone\|ipad\|apple"; then echo "🍎 iOS/iPhone"
    elif echo "$os_info" | grep -qi "windows"; then echo "🖥️  Windows"
    elif echo "$os_info" | grep -qi "linux"; then echo "🐧 Linux"
    elif echo "$os_info" | grep -qi "macos\|mac os\|darwin"; then echo "🍎 macOS"
    else echo "❓ Unknown"
    fi
}

# ─── Device type dari port terbuka ──────────────────────────────────────
guess_device_type() {
    local vendor="$1"
    local open_ports="$2"
    local hostname="$3"

    # Router
    if echo "$vendor" | grep -qi "TP-Link\|MikroTik\|ZTE\|Router\|Indihome\|Huawei.*HG\|D-Link"; then
        echo "📡 Router/ONT"
    elif echo "$open_ports" | grep -q "80\|443\|8080\|8443" && \
         echo "$vendor" | grep -qi "TP-Link\|MikroTik"; then
        echo "📡 Router"
    # Smart TV
    elif echo "$vendor" | grep -qi "LG Electro\|Sony\|Samsung.*Smart"; then
        echo "📺 Smart TV"
    # Printer
    elif echo "$open_ports" | grep -q "9100\|515\|631"; then
        echo "🖨️  Printer"
    # IoT
    elif echo "$vendor" | grep -qi "ESP\|Arduino\|Raspberry"; then
        echo "🔌 IoT Device"
    # HP Android
    elif echo "$vendor" | grep -qi "Samsung\|Xiaomi\|Redmi\|OPPO\|Vivo\|Realme\|Huawei"; then
        echo "📱 Android Phone"
    elif echo "$vendor" | grep -qi "Apple"; then
        echo "📱 iPhone/iPad"
    # PC/Laptop
    elif echo "$vendor" | grep -qi "Intel\|Dell\|HP\|Lenovo\|ASUS\|Acer\|Microsoft"; then
        echo "💻 PC/Laptop"
    else
        echo "📟 Device"
    fi
}

# ─── Cek apakah MAC ada di whitelist ────────────────────────────────────
check_whitelist() {
    local mac="$1"
    if [ ! -f "$WHITELIST_FILE" ]; then
        echo "NO_WHITELIST"
        return
    fi
    local clean_mac=$(echo "$mac" | tr '[:upper:]' '[:lower:]' | sed 's/://g')
    while IFS=',' read -r w_mac w_name w_note || [ -n "$w_mac" ]; do
        # Skip komentar dan baris kosong
        [[ "$w_mac" =~ ^#.*$ ]] && continue
        [[ -z "$w_mac" ]] && continue
        local clean_w=$(echo "$w_mac" | tr '[:upper:]' '[:lower:]' | sed 's/[:-]//g' | xargs)
        if [ "$clean_mac" = "$clean_w" ]; then
            echo "OK:${w_name:-Dikenal}"
            return
        fi
    done < "$WHITELIST_FILE"
    echo "UNKNOWN"
}

# ─── Scan utama ─────────────────────────────────────────────────────────
do_scan() {
    local scan_time=$(date +"%Y-%m-%d %H:%M:%S")

    echo -e "${DIM}  Interface : ${IFACE}${NC}"
    echo -e "${DIM}  IP Saya   : ${MY_IP}${NC}"
    echo -e "${DIM}  Gateway   : ${GATEWAY}${NC}"
    echo -e "${DIM}  Subnet    : ${SUBNET}${NC}"
    echo -e "${DIM}  Mode      : $([ "$SCAN_DETAIL" = true ] && echo "Detail (OS + Ports)" || echo "Cepat (ARP)")${NC}"
    echo -e "${DIM}  Waktu     : ${scan_time}${NC}"
    echo ""

    echo -e "${YELLOW}  ⏳ Scanning ${SUBNET}...${NC}"

    # ── Step 1: ARP scan cepat untuk temukan semua host ──
    local arp_results=""
    if command -v nmap &>/dev/null; then
        # nmap ping scan (lebih akurat dari ping biasa)
        arp_results=$(nmap -sn --send-eth "$SUBNET" 2>/dev/null)
    fi

    # Tambah ARP table lokal (device yang baru-baru ini konek)
    local arp_table=$(arp -a 2>/dev/null || ip neigh 2>/dev/null)

    # Kumpulkan IP yang ditemukan
    local hosts=()

    # Dari nmap
    if [ -n "$arp_results" ]; then
        while IFS= read -r line; do
            if echo "$line" | grep -q "Nmap scan report"; then
                local ip=$(echo "$line" | awk '{print $NF}' | tr -d '()')
                hosts+=("$ip")
            fi
        done <<< "$arp_results"
    fi

    # Dari ARP table (tambahan)
    while IFS= read -r line; do
        local ip=$(echo "$line" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | head -1)
        if [ -n "$ip" ] && ! printf '%s\n' "${hosts[@]}" | grep -q "^${ip}$"; then
            # Hanya ambil yang di subnet yang sama
            local base=$(echo "$MY_IP" | cut -d. -f1-3)
            if echo "$ip" | grep -q "^${base}\."; then
                hosts+=("$ip")
            fi
        fi
    done <<< "$arp_table"

    # Sort IP
    IFS=$'\n' hosts=($(sort -t. -k1,1n -k2,2n -k3,3n -k4,4n <<< "${hosts[*]}")); unset IFS

    local total=${#hosts[@]}
    local unknown_count=0
    local devices_data=()

    echo -e "${YELLOW}  ✅ Ditemukan ${BOLD}${total}${NC}${YELLOW} device. Menganalisis...${NC}"
    echo ""

    # ── Step 2: Analisis tiap host ──
    for ip in "${hosts[@]}"; do
        local mac="" vendor="" hostname="" os_info="" open_ports="" latency=""

        # Ambil MAC dari ARP
        mac=$(arp -n "$ip" 2>/dev/null | grep -oE '([0-9A-Fa-f]{1,2}:){5}[0-9A-Fa-f]{1,2}' | head -1)
        if [ -z "$mac" ]; then
            mac=$(ip neigh show "$ip" 2>/dev/null | grep -oE '([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}' | head -1)
        fi

        # Kalau MAC masih kosong, paksa dengan ping dulu lalu ambil ARP
        if [ -z "$mac" ]; then
            ping -c 1 -W 1 "$ip" &>/dev/null
            mac=$(arp -n "$ip" 2>/dev/null | grep -oE '([0-9A-Fa-f]{1,2}:){5}[0-9A-Fa-f]{1,2}' | head -1)
        fi

        # Latency (ping)
        latency=$(ping -c 1 -W 1 "$ip" 2>/dev/null | grep "time=" | grep -oE 'time=[0-9.]+' | cut -d= -f2)
        latency="${latency:-??}"

        # Hostname via reverse DNS
        hostname=$(nmap -sn "$ip" 2>/dev/null | grep "Nmap scan report" | grep -oE '\(.*\)' | tr -d '()' | head -1)
        if [ -z "$hostname" ]; then
            hostname=$(host "$ip" 2>/dev/null | grep "domain name pointer" | awk '{print $NF}' | sed 's/\.$//g' | head -1)
        fi

        # Lookup vendor
        if [ -n "$mac" ]; then
            vendor=$(lookup_vendor "$mac")
        else
            mac="??:??:??:??:??:??"
            vendor="Unknown"
        fi

        # Scan detail jika diminta
        if [ "$SCAN_DETAIL" = true ]; then
            # OS detection + common ports
            local nmap_detail=$(nmap -O --osscan-guess -p 22,80,443,8080,5555,62078,62078 \
                                      --open --host-timeout 5s "$ip" 2>/dev/null)
            os_info=$(echo "$nmap_detail" | grep -A2 "OS details\|Running\|OS CPE" | head -2)
            open_ports=$(echo "$nmap_detail" | grep "^[0-9]" | awk '{print $1}' | tr '\n' ',' | sed 's/,$//')
        fi

        local os_display=$(parse_os "$os_info $vendor")
        local device_type=$(guess_device_type "$vendor" "$open_ports" "$hostname")

        # Cek whitelist
        local wl_status=$(check_whitelist "$mac")
        local wl_name=""
        if echo "$wl_status" | grep -q "^OK:"; then
            wl_name=$(echo "$wl_status" | cut -d: -f2)
        elif [ "$wl_status" = "UNKNOWN" ]; then
            unknown_count=$((unknown_count + 1))
        fi

        # Simpan data
        devices_data+=("${ip}|${mac}|${vendor}|${hostname}|${os_display}|${device_type}|${open_ports}|${latency}|${wl_status}|${wl_name}")
    done

    # ── Step 3: Tampilkan hasil ──
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║  📊  HASIL SCAN JARINGAN PUSKESMAS — ${scan_time}  ║${NC}"
    echo -e "${CYAN}╠══════════════════════════════════════════════════════════════════════╣${NC}"

    local counter=0
    for entry in "${devices_data[@]}"; do
        IFS='|' read -r ip mac vendor hostname os_display device_type open_ports latency wl_status wl_name <<< "$entry"
        counter=$((counter + 1))

        # Warna baris berdasarkan status
        local status_icon=""
        local status_color=""
        local status_label=""

        if [ "$ip" = "$MY_IP" ]; then
            status_icon="👤"
            status_color="$BLUE"
            status_label="HP SAYA"
        elif [ "$ip" = "$GATEWAY" ]; then
            status_icon="📡"
            status_color="$CYAN"
            status_label="ROUTER"
        elif echo "$wl_status" | grep -q "^OK:"; then
            status_icon="✅"
            status_color="$GREEN"
            status_label="STAFF: ${wl_name}"
        elif [ "$wl_status" = "UNKNOWN" ] && [ -f "$WHITELIST_FILE" ]; then
            status_icon="⚠️ "
            status_color="$RED"
            status_label="ASING!"
        else
            status_icon="🔍"
            status_color="$WHITE"
            status_label="Tidak dikenal"
        fi

        echo -e "${CYAN}║${NC}"
        echo -e "${CYAN}║${NC}  ${BOLD}#${counter}${NC}  ${status_color}${status_icon} ${status_label}${NC}"
        echo -e "${CYAN}║${NC}  ${BOLD}IP Address  :${NC} ${WHITE}${ip}${NC}   ${DIM}(ping: ${latency}ms)${NC}"
        echo -e "${CYAN}║${NC}  ${BOLD}MAC Address :${NC} ${YELLOW}${mac}${NC}"
        echo -e "${CYAN}║${NC}  ${BOLD}Vendor/Merk :${NC} ${vendor}"
        echo -e "${CYAN}║${NC}  ${BOLD}Tipe Device :${NC} ${device_type}"
        [ -n "$hostname" ] && echo -e "${CYAN}║${NC}  ${BOLD}Hostname    :${NC} ${DIM}${hostname}${NC}"
        [ "$SCAN_DETAIL" = true ] && echo -e "${CYAN}║${NC}  ${BOLD}OS          :${NC} ${os_display}"
        [ -n "$open_ports" ] && echo -e "${CYAN}║${NC}  ${BOLD}Port Terbuka:${NC} ${DIM}${open_ports}${NC}"
        echo -e "${CYAN}╠══════════════════════════════════════════════════════════════════════╣${NC}"
    done

    # ── Summary ──
    echo -e "${CYAN}║${NC}"
    echo -e "${CYAN}║  📈  RINGKASAN                                                       ${CYAN}║${NC}"
    echo -e "${CYAN}║${NC}  Total Device   : ${BOLD}${total}${NC}"
    echo -e "${CYAN}║${NC}  Staff (✅)     : ${GREEN}$((total - unknown_count))${NC}"
    if [ -f "$WHITELIST_FILE" ] && [ $unknown_count -gt 0 ]; then
        echo -e "${CYAN}║${NC}  Asing (⚠️ )    : ${RED}${BOLD}${unknown_count} DEVICE ASING TERDETEKSI!${NC}"
    fi
    echo -e "${CYAN}║${NC}  Interface      : ${IFACE} | Gateway: ${GATEWAY}"
    echo -e "${CYAN}║${NC}  Waktu scan     : ${scan_time}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════════╝${NC}"

    # ── Log ke file ──
    echo "=== SCAN: ${scan_time} | Total: ${total} | Asing: ${unknown_count} ===" >> "$LOG_FILE"
    for entry in "${devices_data[@]}"; do
        IFS='|' read -r ip mac vendor hostname os_display device_type open_ports latency wl_status wl_name <<< "$entry"
        echo "  ${ip} | ${mac} | ${vendor} | ${wl_status}" >> "$LOG_FILE"
    done
    echo "" >> "$LOG_FILE"

    # ── Alert jika ada device asing ──
    if [ -f "$WHITELIST_FILE" ] && [ $unknown_count -gt 0 ]; then
        echo ""
        echo -e "${BG_RED}${WHITE}${BOLD}                                                    ${NC}"
        echo -e "${BG_RED}${WHITE}${BOLD}   ⚠️  ${unknown_count} DEVICE ASING TERDETEKSI DI JARINGAN!   ${NC}"
        echo -e "${BG_RED}${WHITE}${BOLD}                                                    ${NC}"
        echo ""
    fi
}

# ─── Setup whitelist kosong jika belum ada ───────────────────────────────
create_sample_whitelist() {
    if [ ! -f "$WHITELIST_FILE" ]; then
        cat > "$WHITELIST_FILE" << 'EOF'
# Whitelist MAC Address HP Puskesmas
# Format: MAC_ADDRESS,NAMA,KETERANGAN
# Contoh:
# AC:37:43:xx:xx:xx,Bidan Ani,Redmi Note 9
# 38:1A:52:xx:xx:xx,Dokter Budi,Samsung A52
#
# Tambahkan MAC HP pegawai Puskesmas di bawah ini:
EOF
        echo -e "${YELLOW}  ℹ  File whitelist dibuat: ${WHITELIST_FILE}${NC}"
        echo -e "${YELLOW}     Tambahkan MAC address HP pegawai ke file tersebut.${NC}"
        echo ""
    fi
}

# ─── Main ────────────────────────────────────────────────────────────────
print_header
check_tools
detect_network
create_sample_whitelist

if [ "$MONITOR_MODE" = true ]; then
    echo -e "${MAGENTA}  🔄 MONITOR MODE — Scan otomatis tiap ${MONITOR_INTERVAL} detik${NC}"
    echo -e "${DIM}  (Tekan Ctrl+C untuk stop)${NC}"
    echo ""
    while true; do
        do_scan
        echo ""
        echo -e "${DIM}  ⏳ Scan berikutnya dalam ${MONITOR_INTERVAL} detik... (Ctrl+C untuk stop)${NC}"
        sleep "$MONITOR_INTERVAL"
        print_header
    done
else
    do_scan
fi
