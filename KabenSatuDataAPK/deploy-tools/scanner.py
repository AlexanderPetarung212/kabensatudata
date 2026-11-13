#!/usr/bin/env python3
"""
╔══════════════════════════════════════════════════════════════════╗
║   🏥  KabenSatuData — WiFi Network Scanner                      ║
║   Cara pakai:                                                    ║
║     python scanner.py           → Scan cepat                    ║
║     python scanner.py -d        → Scan detail (OS + port)       ║
║     python scanner.py -m        → Monitor otomatis tiap 30 detik║
║     python scanner.py -m -i 60  → Monitor tiap 60 detik         ║
║     python scanner.py -w        → Kelola whitelist               ║
╚══════════════════════════════════════════════════════════════════╝

Script ini akan otomatis install semua package yang dibutuhkan.
Jalankan di Termux (HP Android root) dengan:
  python scanner.py
"""

import sys
import os
import subprocess
import time
import json
import socket
import struct
import argparse
import re
from datetime import datetime
from pathlib import Path

# ─── Auto-install dependencies ───────────────────────────────────────────────
def install_pkg(pkg_name, import_name=None):
    import_name = import_name or pkg_name
    try:
        __import__(import_name)
        return True
    except ImportError:
        print(f"  📦 Installing {pkg_name}...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", pkg_name, "-q"])
        return True

def setup():
    print("\n  🔧 Checking dependencies...")
    pkgs = [
        ("requests", "requests"),
        ("colorama", "colorama"),
        ("python-nmap", "nmap"),
    ]
    for pkg, imp in pkgs:
        install_pkg(pkg, imp)

    # Pastikan nmap binary terinstall di Termux
    if subprocess.run(["which", "nmap"], capture_output=True).returncode != 0:
        print("  📦 Installing nmap binary...")
        subprocess.run(["pkg", "install", "-y", "nmap"], capture_output=True)

    # Install net-tools untuk arp
    if subprocess.run(["which", "arp"], capture_output=True).returncode != 0:
        subprocess.run(["pkg", "install", "-y", "net-tools", "iproute2"], capture_output=True)

setup()

# ─── Imports setelah install ─────────────────────────────────────────────────
import requests
from colorama import init, Fore, Back, Style
import nmap

init(autoreset=True)

# ─── Konfigurasi ─────────────────────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).parent
WHITELIST_FILE = SCRIPT_DIR / "whitelist-mac.txt"
OUI_CACHE_FILE = SCRIPT_DIR / "oui-cache.json"
LOG_FILE = SCRIPT_DIR / "scan-log.txt"

# ─── OUI Database lokal (Top merk HP Indonesia) ──────────────────────────────
OUI_LOCAL = {
    # Samsung
    "001599": "Samsung", "0016DB": "Samsung", "001E7D": "Samsung",
    "0021D1": "Samsung", "0024E9": "Samsung", "308D99": "Samsung",
    "5CFF35": "Samsung", "8425DB": "Samsung", "9CD91F": "Samsung",
    "A8F274": "Samsung", "B47443": "Samsung", "B4EF39": "Samsung",
    "D0DCE1": "Samsung", "D87195": "Samsung", "F4F5E8": "Samsung",
    "FC4B2F": "Samsung", "784B87": "Samsung", "AC5F3E": "Samsung",
    "D4870D": "Samsung", "8C77121": "Samsung",
    # Xiaomi / Redmi
    "0C1DAF": "Xiaomi/Redmi", "10BA38": "Xiaomi/Redmi", "28E31F": "Xiaomi/Redmi",
    "34CE00": "Xiaomi/Redmi", "38A428": "Xiaomi/Redmi", "3C9872": "Xiaomi/Redmi",
    "58447A": "Xiaomi/Redmi", "64B473": "Xiaomi/Redmi", "64CC2E": "Xiaomi/Redmi",
    "7453D6": "Xiaomi/Redmi", "7EC671": "Xiaomi/Redmi", "8CACAB": "Xiaomi/Redmi",
    "9829A9": "Xiaomi/Redmi", "A086C6": "Xiaomi/Redmi", "B0E235": "Xiaomi/Redmi",
    "D02544": "Xiaomi/Redmi", "F48B32": "Xiaomi/Redmi", "F0B429": "Xiaomi/Redmi",
    "AC2D65": "Xiaomi/Redmi", "5411EC": "Xiaomi/Redmi",
    # OPPO
    "0012C2": "OPPO", "40452C": "OPPO", "50A4C8": "OPPO",
    "706F67": "OPPO", "AC1101": "OPPO", "BC7F89": "OPPO",
    "C84B55": "OPPO", "CCB265": "OPPO", "D4500F": "OPPO",
    "E06F51": "OPPO", "2C5364": "OPPO",
    # Vivo
    "242C44": "Vivo", "3C5771": "Vivo", "484646": "Vivo",
    "601466": "Vivo", "6C5C14": "Vivo", "701CE7": "Vivo",
    "8093BB": "Vivo", "90E8BA": "Vivo", "D4207B": "Vivo",
    # Realme
    "1C771B": "Realme", "9C5316": "Realme", "BC4C89": "Realme", "D0293C": "Realme",
    "788CB5": "Realme", "5048EA": "Realme",
    # Huawei
    "001882": "Huawei", "00259E": "Huawei", "002389": "Huawei",
    "BC764E": "Huawei", "286ED4": "Huawei", "30FB08": "Huawei",
    "34A84E": "Huawei", "40CBAD": "Huawei", "4CAC0A": "Huawei",
    "54C06F": "Huawei", "5C4CCA": "Huawei", "5CBA37": "Huawei",
    "70725D": "Huawei", "8C34FD": "Huawei", "94DBDA": "Huawei",
    "C4072F": "Huawei", "D46E5C": "Huawei", "E4A8B6": "Huawei",
    "F8E811": "Huawei", "FC348C": "Huawei",
    # Apple iPhone / iPad
    "001451": "Apple (iPhone/iPad)", "0016CB": "Apple (iPhone/iPad)",
    "001B63": "Apple (iPhone/iPad)", "00238C": "Apple (iPhone/iPad)",
    "0C4DE9": "Apple (iPhone/iPad)", "10DDB1": "Apple (iPhone/iPad)",
    "18AF8F": "Apple (iPhone/iPad)", "2453DF": "Apple (iPhone/iPad)",
    "2CB43A": "Apple (iPhone/iPad)", "34159E": "Apple (iPhone/iPad)",
    "38C986": "Apple (iPhone/iPad)", "3C2EFF": "Apple (iPhone/iPad)",
    "4C57CA": "Apple (iPhone/iPad)", "60FB42": "Apple (iPhone/iPad)",
    "685B35": "Apple (iPhone/iPad)", "8C2937": "Apple (iPhone/iPad)",
    "940C6D": "Apple (iPhone/iPad)", "9CF387": "Apple (iPhone/iPad)",
    "A860B6": "Apple (iPhone/iPad)", "B065BD": "Apple (iPhone/iPad)",
    "F0DBE2": "Apple (iPhone/iPad)", "F4F951": "Apple (iPhone/iPad)",
    "D4F46F": "Apple (iPhone/iPad)", "BC9FEF": "Apple (iPhone/iPad)",
    # TP-Link (Router)
    "000AEB": "TP-Link (Router)", "0C8268": "TP-Link (Router)",
    "14CC20": "TP-Link (Router)", "1C61B4": "TP-Link (Router)",
    "30B5C2": "TP-Link (Router)", "50FA84": "TP-Link (Router)",
    "544A16": "TP-Link (Router)", "547AE5": "TP-Link (Router)",
    "68FF7B": "TP-Link (Router)", "6C5AB0": "TP-Link (Router)",
    "74DADA": "TP-Link (Router)", "78A353": "TP-Link (Router)",
    "88D7F6": "TP-Link (Router)", "94D9B3": "TP-Link (Router)",
    "ACF1DF": "TP-Link (Router)", "B4B024": "TP-Link (Router)",
    "D80D17": "TP-Link (Router)", "E83E26": "TP-Link (Router)",
    "EC08E5": "TP-Link (Router)", "FC7516": "TP-Link (Router)",
    # MikroTik
    "000C42": "MikroTik (Router)", "4C5E0C": "MikroTik (Router)",
    "6C3B6B": "MikroTik (Router)", "74AD17": "MikroTik (Router)",
    "8C88E1": "MikroTik (Router)", "B8698A": "MikroTik (Router)",
    "CC2DE0": "MikroTik (Router)", "DC2C6E": "MikroTik (Router)",
    # ZTE / Indihome
    "001EC0": "ZTE/Indihome ONT", "3C5AB4": "ZTE/Indihome ONT",
    "ACCFA0": "ZTE/Indihome ONT", "485754": "ZTE/Indihome ONT",
    # VMware
    "000C29": "VMware (Virtual)", "005056": "VMware (Virtual)",
}

# ─── OUI Lookup ──────────────────────────────────────────────────────────────
def load_oui_cache():
    if OUI_CACHE_FILE.exists():
        try:
            return json.loads(OUI_CACHE_FILE.read_text())
        except Exception:
            return {}
    return {}

def save_oui_cache(cache):
    OUI_CACHE_FILE.write_text(json.dumps(cache, indent=2))

OUI_CACHE = load_oui_cache()

def lookup_vendor(mac: str) -> str:
    if not mac or mac == "??:??:??:??:??:??":
        return "Unknown"
    oui = mac.upper().replace(":", "").replace("-", "")[:6]

    # 1. Local DB dulu (cepat, offline)
    if oui in OUI_LOCAL:
        return OUI_LOCAL[oui]

    # 2. Cache
    if oui in OUI_CACHE:
        return OUI_CACHE[oui]

    # 3. API online (jika ada internet)
    try:
        r = requests.get(f"https://api.macvendors.com/{oui}", timeout=3)
        if r.status_code == 200 and len(r.text) < 80:
            vendor = r.text.strip()
            OUI_CACHE[oui] = vendor
            save_oui_cache(OUI_CACHE)
            return vendor
    except Exception:
        pass

    return "Unknown"

# ─── Network Utils ────────────────────────────────────────────────────────────
def get_my_ip_and_gateway():
    """Deteksi IP, gateway, dan subnet secara otomatis."""
    try:
        result = subprocess.run(["ip", "route"], capture_output=True, text=True)
        lines = result.stdout.strip().split("\n")
        gateway = ""
        iface = ""
        my_ip = ""
        for line in lines:
            if line.startswith("default"):
                parts = line.split()
                gateway = parts[2] if len(parts) > 2 else ""
                iface = parts[4] if len(parts) > 4 else ""
                break

        if iface:
            result2 = subprocess.run(["ip", "addr", "show", iface], capture_output=True, text=True)
            match = re.search(r'inet (\d+\.\d+\.\d+\.\d+)', result2.stdout)
            if match:
                my_ip = match.group(1)

        if not my_ip:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            my_ip = s.getsockname()[0]
            s.close()

        subnet = ".".join(my_ip.split(".")[:3]) + ".0/24"
        return my_ip, gateway, iface, subnet
    except Exception as e:
        print(f"{Fore.RED}❌ Gagal deteksi jaringan: {e}{Style.RESET_ALL}")
        sys.exit(1)

def get_arp_table():
    """Ambil ARP table dari semua sumber yang tersedia di Android."""
    arp_map = {}

    # Sumber 1: /proc/net/arp — PALING RELIABLE di Android, tidak perlu root
    try:
        with open("/proc/net/arp", "r") as f:
            for line in f.readlines()[1:]:  # skip header
                parts = line.split()
                if len(parts) >= 4:
                    ip = parts[0]
                    mac = parts[3]
                    # Filter MAC yang valid (bukan 00:00:00:00:00:00)
                    if mac and mac != "00:00:00:00:00:00" and ":" in mac:
                        arp_map[ip] = mac.upper()
    except Exception:
        pass

    # Sumber 2: ip neigh
    try:
        result = subprocess.run(["ip", "neigh"], capture_output=True, text=True)
        for line in result.stdout.strip().split("\n"):
            parts = line.split()
            if len(parts) >= 5 and "lladdr" in parts:
                ip = parts[0]
                mac_idx = parts.index("lladdr") + 1
                if mac_idx < len(parts):
                    mac = parts[mac_idx].upper()
                    if ip not in arp_map and ":" in mac:
                        arp_map[ip] = mac
    except Exception:
        pass

    # Sumber 3: arp -a (fallback)
    try:
        result = subprocess.run(["arp", "-a"], capture_output=True, text=True)
        matches = re.findall(r'\((\d+\.\d+\.\d+\.\d+)\)\s+at\s+([0-9a-fA-F:]{17})', result.stdout)
        for ip, mac in matches:
            if ip not in arp_map:
                arp_map[ip] = mac.upper()
    except Exception:
        pass

    return arp_map

def ping_sweep(subnet_base):
    """Ping semua IP di subnet untuk populate ARP table.
    Ini WAJIB dilakukan sebelum baca ARP — Android tidak otomatis populate."""
    import threading
    print(f"  {Fore.YELLOW}⚡ Ping sweep {subnet_base}.1-254 untuk temukan device...{Style.RESET_ALL}")

    def _ping(ip):
        subprocess.run(
            ["ping", "-c", "1", "-W", "1", ip],
            capture_output=True
        )

    threads = []
    for i in range(1, 255):
        ip = f"{subnet_base}.{i}"
        t = threading.Thread(target=_ping, args=(ip,), daemon=True)
        threads.append(t)
        t.start()
        # Batch 50 thread sekaligus agar tidak overload
        if len(threads) >= 50:
            for t in threads:
                t.join(timeout=2)
            threads = []

    for t in threads:
        t.join(timeout=2)

    # Tunggu ARP table update
    time.sleep(1)

def ping(ip, timeout=1):
    """Cek apakah IP aktif."""
    result = subprocess.run(
        ["ping", "-c", "1", "-W", str(timeout), ip],
        capture_output=True, text=True
    )
    if result.returncode == 0:
        match = re.search(r'time=([0-9.]+)', result.stdout)
        return True, match.group(1) if match else "?"
    return False, None

def get_hostname(ip):
    try:
        return socket.gethostbyaddr(ip)[0]
    except Exception:
        return ""

def guess_device_type(vendor, open_ports=None):
    v = vendor.lower()
    p = ",".join(str(x) for x in (open_ports or []))
    if any(x in v for x in ["tp-link", "mikrotik", "zte", "router", "indihome", "d-link"]):
        return "📡 Router/ONT"
    if any(x in v for x in ["samsung", "xiaomi", "redmi", "oppo", "vivo", "realme"]):
        return "📱 Android Phone"
    if "huawei" in v:
        return "📱 Android / Huawei"
    if "apple" in v or "iphone" in v or "ipad" in v:
        return "📱 iPhone/iPad"
    if any(x in v for x in ["intel", "dell", "lenovo", "asus", "acer", "hp inc"]):
        return "💻 PC/Laptop"
    if "vmware" in v:
        return "🖥️  Virtual Machine"
    if any(x in p for x in ["9100", "515", "631"]):
        return "🖨️  Printer"
    return "📟 Device"

# ─── Whitelist ────────────────────────────────────────────────────────────────
def load_whitelist():
    entries = {}
    if not WHITELIST_FILE.exists():
        return entries
    for line in WHITELIST_FILE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = [p.strip() for p in line.split(",")]
        if parts:
            mac = parts[0].upper().replace("-", ":").replace(" ", "")
            name = parts[1] if len(parts) > 1 else "Dikenal"
            note = parts[2] if len(parts) > 2 else ""
            entries[mac] = {"name": name, "note": note}
    return entries

def check_whitelist(mac, whitelist):
    if not whitelist:
        return None, False  # no whitelist mode
    clean = mac.upper().replace("-", ":").strip()
    if clean in whitelist:
        return whitelist[clean]["name"], True
    return None, False

def manage_whitelist():
    """Interaktif kelola whitelist."""
    print(f"\n{Fore.CYAN}{'═'*50}")
    print(f"{Fore.BOLD}{Fore.BLUE}  📋 Kelola Whitelist HP Puskesmas")
    print(f"{Fore.CYAN}{'═'*50}{Style.RESET_ALL}")

    if not WHITELIST_FILE.exists():
        WHITELIST_FILE.write_text(
            "# Whitelist MAC Address HP Puskesmas\n"
            "# Format: MAC_ADDRESS, NAMA, KETERANGAN\n"
            "# Contoh:\n"
            "# AC:37:43:12:34:56, Dokter Budi, Redmi Note 9 Pro\n"
        )

    while True:
        wl = load_whitelist()
        print(f"\n{Fore.YELLOW}  [1]{Style.RESET_ALL} Lihat whitelist ({len(wl)} device)")
        print(f"{Fore.YELLOW}  [2]{Style.RESET_ALL} Tambah device baru")
        print(f"{Fore.YELLOW}  [3]{Style.RESET_ALL} Hapus device")
        print(f"{Fore.YELLOW}  [0]{Style.RESET_ALL} Kembali ke scan")
        choice = input(f"\n  Pilihan: ").strip()

        if choice == "1":
            if not wl:
                print(f"  {Fore.YELLOW}Whitelist kosong.{Style.RESET_ALL}")
            for mac, info in wl.items():
                print(f"  {Fore.GREEN}✅{Style.RESET_ALL} {mac}  {Fore.BOLD}{info['name']}{Style.RESET_ALL}  {Style.DIM}{info['note']}{Style.RESET_ALL}")

        elif choice == "2":
            mac = input("  MAC Address (contoh: AC:37:43:12:34:56): ").strip().upper()
            name = input("  Nama pegawai: ").strip()
            note = input("  Keterangan (contoh: Redmi Note 9 Pro): ").strip()
            with open(WHITELIST_FILE, "a") as f:
                f.write(f"{mac}, {name}, {note}\n")
            print(f"  {Fore.GREEN}✅ Ditambahkan: {mac} → {name}{Style.RESET_ALL}")

        elif choice == "3":
            mac = input("  MAC Address yang ingin dihapus: ").strip().upper()
            lines = WHITELIST_FILE.read_text().splitlines()
            new_lines = [l for l in lines if not l.strip().startswith(mac)]
            WHITELIST_FILE.write_text("\n".join(new_lines) + "\n")
            print(f"  {Fore.GREEN}✅ Dihapus{Style.RESET_ALL}")

        elif choice == "0":
            break

# ─── Scanner Utama ────────────────────────────────────────────────────────────
def scan(detail=False):
    my_ip, gateway, iface, subnet = get_my_ip_and_gateway()
    whitelist = load_whitelist()
    has_whitelist = WHITELIST_FILE.exists() and len(whitelist) > 0
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # Header
    os.system("clear")
    print(f"\n{Fore.CYAN}╔{'═'*62}╗")
    print(f"║   🏥  KabenSatuData — WiFi Network Scanner{' '*19}║")
    print(f"╚{'═'*62}╝{Style.RESET_ALL}")
    print(f"  {Style.DIM}Interface : {iface} | IP : {my_ip} | Gateway : {gateway}")
    print(f"  Subnet    : {subnet} | Mode : {'Detail' if detail else 'Cepat'}")
    print(f"  Waktu     : {now}{Style.RESET_ALL}\n")

    print(f"  {Fore.YELLOW}⏳ Scanning {subnet}...{Style.RESET_ALL}")
    base = ".".join(my_ip.split(".")[:3])

    # ── Step 1: Ping sweep DULU untuk populate ARP table ──
    ping_sweep(base)

    # ── Step 2: Baca ARP table (sekarang sudah berisi MAC) ──
    arp_map = get_arp_table()

    # ── Step 3: nmap scan (tanpa root untuk scan biasa) ──
    nm = nmap.PortScanner()
    try:
        if detail:
            # Detail scan: coba sudo dulu, fallback ke non-root
            try:
                nm.scan(hosts=subnet, sudo=True,
                        arguments="-sV -O --osscan-guess -p 22,80,443,8080,7070,5555 --host-timeout 8s")
            except Exception:
                nm.scan(hosts=subnet,
                        arguments="-sV -p 22,80,443,8080,7070,5555 --host-timeout 8s")
        else:
            # Scan biasa: gunakan TCP connect scan yang TIDAK butuh root
            nm.scan(hosts=subnet,
                    arguments="-sn -PS80,443,8080 -PA80 --host-timeout 3s -n")
    except Exception as e:
        print(f"  {Fore.YELLOW}⚠️  nmap tidak tersedia, tetap lanjut dengan ARP sweep{Style.RESET_ALL}")

    # ── Step 4: Gabungkan host dari nmap + ARP ──
    found_hosts = list(nm.all_hosts())
    for ip in arp_map:
        if ip.startswith(base + ".") and ip not in found_hosts:
            found_hosts.append(ip)
    # Pastikan IP sendiri dan gateway masuk
    for extra_ip in [my_ip, gateway]:
        if extra_ip and extra_ip not in found_hosts:
            found_hosts.append(extra_ip)

    # Sort IP
    try:
        found_hosts.sort(key=lambda ip: [int(x) for x in ip.split(".")])
    except Exception:
        pass

    total = len(found_hosts)
    print(f"  {Fore.GREEN}✅ Ditemukan {Style.BRIGHT}{total}{Style.RESET_ALL}{Fore.GREEN} device. Menganalisis...{Style.RESET_ALL}\n")

    print(f"{Fore.CYAN}╔{'═'*62}╗{Style.RESET_ALL}")
    print(f"{Fore.CYAN}║  📊  HASIL SCAN — {now}{' '*(62-22-len(now))}║{Style.RESET_ALL}")
    print(f"{Fore.CYAN}╠{'═'*62}╣{Style.RESET_ALL}")

    results = []
    unknown_count = 0

    for ip in found_hosts:
        # MAC dari nmap atau ARP
        mac = "??:??:??:??:??:??"
        try:
            if ip in nm and nm[ip]['addresses'].get('mac'):
                mac = nm[ip]['addresses']['mac']
        except Exception:
            pass
        if mac == "??:??:??:??:??:??" and ip in arp_map:
            mac = arp_map[ip]

        # Vendor
        vendor = lookup_vendor(mac)
        if not vendor or vendor == "Unknown":
            try:
                nmap_vendor = nm[ip]['vendor'].get(mac, "")
                if nmap_vendor:
                    vendor = nmap_vendor
            except Exception:
                pass

        # Device type
        open_ports = []
        os_info = ""
        hostname = get_hostname(ip)
        try:
            if ip in nm:
                for proto in nm[ip].all_protocols():
                    ports = nm[ip][proto].keys()
                    for port in ports:
                        if nm[ip][proto][port]['state'] == 'open':
                            open_ports.append(port)
                if nm[ip].get('osmatch'):
                    os_info = nm[ip]['osmatch'][0]['name']
        except Exception:
            pass

        device_type = guess_device_type(vendor, open_ports)

        # Latency
        _, latency = ping(ip, timeout=1)

        # Whitelist check
        wl_name, in_whitelist = check_whitelist(mac, whitelist)

        # Status
        if ip == my_ip:
            status_icon = "👤"
            status_color = Fore.BLUE
            status_label = "HP SAYA"
        elif ip == gateway:
            status_icon = "📡"
            status_color = Fore.CYAN
            status_label = "ROUTER/GATEWAY"
        elif has_whitelist and in_whitelist:
            status_icon = "✅"
            status_color = Fore.GREEN
            status_label = f"STAFF: {wl_name}"
        elif has_whitelist and not in_whitelist:
            status_icon = "⚠️ "
            status_color = Fore.RED
            status_label = "ASING! TIDAK DIKENAL"
            unknown_count += 1
        else:
            status_icon = "🔍"
            status_color = Fore.WHITE
            status_label = "Device"

        # Tampilkan
        print(f"{Fore.CYAN}║{Style.RESET_ALL}")
        print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}{status_color}{status_icon}  {status_label}{Style.RESET_ALL}")
        print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}IP Address  :{Style.RESET_ALL} {Fore.WHITE}{ip}{Style.RESET_ALL}  {Style.DIM}(ping: {latency or '?'}ms){Style.RESET_ALL}")
        print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}MAC Address :{Style.RESET_ALL} {Fore.YELLOW}{mac}{Style.RESET_ALL}")
        print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}Vendor/Merk :{Style.RESET_ALL} {vendor}")
        print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}Tipe Device :{Style.RESET_ALL} {device_type}")
        if hostname:
            print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}Hostname    :{Style.RESET_ALL} {Style.DIM}{hostname}{Style.RESET_ALL}")
        if detail and os_info:
            print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}OS          :{Style.RESET_ALL} {os_info}")
        if detail and open_ports:
            print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}Port Terbuka:{Style.RESET_ALL} {Style.DIM}{', '.join(str(p) for p in open_ports)}{Style.RESET_ALL}")
        print(f"{Fore.CYAN}╠{'═'*62}╣{Style.RESET_ALL}")

        results.append({
            "ip": ip, "mac": mac, "vendor": vendor,
            "device_type": device_type, "hostname": hostname,
            "status_label": status_label, "latency": latency
        })

    # Summary
    print(f"{Fore.CYAN}║{Style.RESET_ALL}")
    print(f"{Fore.CYAN}║{Style.RESET_ALL}  {Style.BRIGHT}📈 RINGKASAN{Style.RESET_ALL}")
    print(f"{Fore.CYAN}║{Style.RESET_ALL}  Total Device   : {Style.BRIGHT}{total}{Style.RESET_ALL}")
    if has_whitelist:
        print(f"{Fore.CYAN}║{Style.RESET_ALL}  Staff (✅)    : {Fore.GREEN}{total - unknown_count}{Style.RESET_ALL}")
        print(f"{Fore.CYAN}║{Style.RESET_ALL}  Asing (⚠️ )   : {Fore.RED}{Style.BRIGHT}{unknown_count}{Style.RESET_ALL}")
    print(f"{Fore.CYAN}║{Style.RESET_ALL}  Waktu scan     : {now}")
    print(f"{Fore.CYAN}╚{'═'*62}╝{Style.RESET_ALL}")

    # Alert merah jika ada device asing
    if has_whitelist and unknown_count > 0:
        print()
        print(f"{Back.RED}{Fore.WHITE}{Style.BRIGHT}{'':^64}{Style.RESET_ALL}")
        print(f"{Back.RED}{Fore.WHITE}{Style.BRIGHT}   ⚠️  {unknown_count} DEVICE ASING TERDETEKSI DI JARINGAN PUSKESMAS!   {Style.RESET_ALL}")
        print(f"{Back.RED}{Fore.WHITE}{Style.BRIGHT}{'':^64}{Style.RESET_ALL}")
        print()

    # Log
    with open(LOG_FILE, "a") as f:
        f.write(f"=== {now} | Total: {total} | Asing: {unknown_count} ===\n")
        for r in results:
            f.write(f"  {r['ip']} | {r['mac']} | {r['vendor']} | {r['status_label']}\n")
        f.write("\n")

    return unknown_count

# ─── Main ─────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(
        description="🏥 KabenSatuData WiFi Scanner",
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument("-d", "--detail", action="store_true",
                        help="Scan detail: deteksi OS dan port terbuka (lebih lambat)")
    parser.add_argument("-m", "--monitor", action="store_true",
                        help="Monitor mode: scan otomatis berulang")
    parser.add_argument("-i", "--interval", type=int, default=30,
                        help="Interval monitor dalam detik (default: 30)")
    parser.add_argument("-w", "--whitelist", action="store_true",
                        help="Kelola whitelist HP pegawai Puskesmas")
    args = parser.parse_args()

    if args.whitelist:
        manage_whitelist()
        return

    if args.monitor:
        print(f"\n  {Fore.MAGENTA}🔄 MONITOR MODE — Scan tiap {args.interval} detik{Style.RESET_ALL}")
        print(f"  {Style.DIM}(Tekan Ctrl+C untuk stop){Style.RESET_ALL}")
        try:
            while True:
                scan(detail=args.detail)
                print(f"\n  {Style.DIM}⏳ Scan berikutnya dalam {args.interval} detik... (Ctrl+C untuk stop){Style.RESET_ALL}")
                time.sleep(args.interval)
        except KeyboardInterrupt:
            print(f"\n  {Fore.YELLOW}⏹  Monitor dihentikan.{Style.RESET_ALL}\n")
    else:
        scan(detail=args.detail)

if __name__ == "__main__":
    main()
