#!/bin/bash
# ============================================================
#  Garda Kiosk - Setup Otomatis (Mass Deployment)
#  Script ini mengotomatisasi pemasangan Kiosk ke HP Samsung
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

APK_FILE="GardaKiosk.apk"
JSON_FILE="kiosk_settings.json"
PACKAGE_NAME="com.example.kioskdeviceowner"
RECEIVER="${PACKAGE_NAME}/.receiver.KioskDeviceAdminReceiver"
NOTIFICATION_SERVICE="${PACKAGE_NAME}/.service.KioskNotificationListener"
TARGET_DIR="/sdcard/Android/data/${PACKAGE_NAME}/files"

echo -e "${CYAN}${BOLD}"
echo "╔══════════════════════════════════════════════════╗"
echo "║        GARDA KIOSK - SETUP OTOMATIS             ║"
echo "║        Device Owner Provisioning Tool           ║"
echo "╚══════════════════════════════════════════════════╝"
echo -e "${NC}"

# ---------- 1. Cek ADB ----------
echo -e "\n${YELLOW}[1/8] Mengecek ADB...${NC}"
if ! command -v adb &> /dev/null; then
    echo -e "${RED}ERROR: ADB tidak ditemukan!${NC}"
    echo "Silakan install Android SDK Platform Tools terlebih dahulu."
    echo "Download: https://developer.android.com/studio/releases/platform-tools"
    exit 1
fi
echo -e "${GREEN}✓ ADB ditemukan${NC}"

# ---------- 2. Cek Device ----------
echo -e "\n${YELLOW}[2/8] Mengecek perangkat terhubung...${NC}"
DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    echo -e "${RED}ERROR: Tidak ada perangkat terhubung!${NC}"
    echo "Pastikan:"
    echo "  1. HP tersambung via kabel USB"
    echo "  2. USB Debugging sudah AKTIF di HP"
    echo "  3. HP sudah di-Allow/Trust PC ini"
    exit 1
fi
echo -e "${GREEN}✓ Perangkat terdeteksi:${NC}"
adb devices

# ---------- 3. Cek APK ----------
echo -e "\n${YELLOW}[3/8] Mengecek file APK...${NC}"
if [ ! -f "$APK_FILE" ]; then
    echo -e "${RED}ERROR: File ${APK_FILE} tidak ditemukan!${NC}"
    echo "Pastikan file APK ada di folder yang sama dengan script ini."
    exit 1
fi
echo -e "${GREEN}✓ ${APK_FILE} ditemukan${NC}"

# ---------- 4. Cek JSON ----------
echo -e "\n${YELLOW}[4/8] Mengecek file konfigurasi JSON...${NC}"
if [ ! -f "$JSON_FILE" ]; then
    echo -e "${RED}ERROR: File ${JSON_FILE} tidak ditemukan!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ ${JSON_FILE} ditemukan${NC}"

# ---------- 5. Install APK ----------
echo -e "\n${YELLOW}[5/8] Memasang aplikasi...${NC}"
adb install -r "$APK_FILE"
echo -e "${GREEN}✓ Aplikasi terpasang${NC}"

# ---------- 6. Launch App ----------
echo -e "\n${YELLOW}[6/8] Menjalankan aplikasi pertama kali...${NC}"
adb shell am start -n "${PACKAGE_NAME}/.MainActivity"
echo -e "${GREEN}✓ Aplikasi dijalankan${NC}"
sleep 2

# ---------- 7. Push JSON ----------
echo -e "\n${YELLOW}[7/8] Mengirim konfigurasi JSON...${NC}"
adb shell mkdir -p "$TARGET_DIR"
adb push "$JSON_FILE" "${TARGET_DIR}/kiosk_settings.json"
echo -e "${GREEN}✓ Konfigurasi terkirim${NC}"

# ---------- 8. Set Device Owner ----------
echo -e "\n${YELLOW}[8/8] Mendaftarkan sebagai Device Owner...${NC}"
echo -e "${RED}${BOLD}⚠ PERHATIAN:${NC}"
echo "  Setelah langkah ini:"
echo "  • USB Debugging akan DIBLOKIR (jika pengaturan aktif)"
echo "  • HP akan terkunci dalam mode Kiosk"
echo "  • Hanya aplikasi yang diizinkan yang bisa dibuka"
echo ""

read -p "Lanjutkan set Device Owner? (y/n): " confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo -e "${YELLOW}Setup dibatalkan. Device Owner BELUM diaktifkan.${NC}"
    echo "Jalankan manual: adb shell dpm set-device-owner ${RECEIVER}"
    exit 0
fi

adb shell dpm set-device-owner "$RECEIVER"
echo -e "${GREEN}✓ Device Owner terdaftar!${NC}"

# ---------- Aktifkan Notification Listener ----------
echo -e "\n${YELLOW}Aktifkan Akses Notifikasi? (untuk GPS di Lockscreen)${NC}"
read -p "Aktifkan sekarang? (y/n): " notif_confirm
if [ "$notif_confirm" = "y" ] || [ "$notif_confirm" = "Y" ]; then
    CURRENT_LISTENERS=$(adb shell settings get secure enabled_notification_listeners)
    if [ "$CURRENT_LISTENERS" = "null" ]; then
        adb shell settings put secure enabled_notification_listeners "${NOTIFICATION_SERVICE}"
    else
        adb shell settings put secure enabled_notification_listeners "${CURRENT_LISTENERS}:${NOTIFICATION_SERVICE}"
    fi
    echo -e "${GREEN}✓ Akses notifikasi diaktifkan${NC}"
fi

# ---------- SELESAI ----------
echo -e "\n${GREEN}${BOLD}╔══════════════════════════════════════════════════╗"
echo "║          SETUP SELESAI!                         ║"
echo "╚══════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}Informasi Penting:${NC}"
echo "  • PIN Pengaturan (default): 1234"
echo "  • PIN Lockscreen (default): 147258"
echo "  • Gestur: Ketuk jam 5x untuk buka pengaturan"
echo ""
echo -e "${CYAN}Perintah ADB Darurat:${NC}"
echo "  • Keluar Kiosk:    adb shell am broadcast -a com.kiosk.action.EXIT_KIOSK -p ${PACKAGE_NAME}"
echo "  • Buka Pengaturan: adb shell am broadcast -a com.kiosk.action.OPEN_SETTINGS -p ${PACKAGE_NAME}"
echo "  • Hapus DeviceOwner: adb shell am broadcast -a com.kiosk.action.CLEAR_DEVICE_OWNER -p ${PACKAGE_NAME}"
echo ""
