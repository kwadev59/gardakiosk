# 📱 Garda Kiosk

**Aplikasi Pengunci HP untuk Perusahaan**

Garda Kiosk adalah aplikasi Android yang mengubah HP Samsung biasa menjadi **HP khusus pelacak GPS** yang tidak bisa disalahgunakan oleh pekerja lapangan.

___

## ❓ Apa Ini? (Versi Sederhana)

Bayangkan Anda meminjamkan HP ke orang lain. Anda ingin dia **hanya bisa membuka 1-2 aplikasi GPS saja** — tidak bisa main game, buka YouTube, main Instagram, atau ubah-ubah pengaturan HP.

**Garda Kiosk** melakukan itu. Setelah dipasang:

| Pekerja HANYA bisa | Pekerja TIDAK bisa |
|---|---|
| ✅ Buka aplikasi GPS Tanaman | ❌ Buka aplikasi lain |
| ✅ Buka aplikasi GPS Unit | ❌ Tarik bilah notifikasi |
| ✅ Kirim data via QuickShare/Bluetooth | ❌ Tekan tombol Home/Back |
| ✅ Buka File Manager bawaan | ❌ Buka Pengaturan Android |
| ✅ Cek status GPS via Lockscreen | ❌ Uninstall aplikasi ini |

**Tujuan utama**: Memastikan HP hanya dipakai untuk **melacak posisi GPS pekerja**, tidak untuk hal lain.

___

## 📋 HP yang Didukung

| Spesifikasi | Detail |
|---|---|
| Merek | Samsung (diuji di Galaxy A07) |
| Android | Minimal 7.0, direkomendasikan 14+ |
| Status | Harus diaktifkan sebagai **Device Owner** |

___

## 🚀 Cara Pasang di HP Baru (Step-by-Step)

### Sebelum mulai, pastikan:
- [ ] Anda punya kabel USB data (bukan kabel charger saja)
- [ ] Anda sudah install **ADB** di laptop/PC *(cari "minimal ADB fastboot" di Google)*
- [ ] Mode **Developer Options** sudah aktif di HP Samsung *(cari tutorialnya di YouTube: "cara aktifkan developer options samsung")*
- [ ] **USB Debugging** di HP dalam keadaan **ON**

### Langkah otomatis (rekomendasi):

1. Hubungkan HP ke laptop/PC pakai kabel USB
2. Buka terminal / CMD di folder ini
3. Jalankan:

```bash
./setup_kiosk.sh
```

4. Ikuti petunjuk di layar, ketik `y` saat diminta konfirmasi
5. Selesai! HP sekarang terkunci dalam mode Kiosk

> 💡 **Catatan**: Script akan otomatis meng-install APK, mengirim file konfigurasi, dan mengaktifkan Device Owner.

### Langkah manual (jika script bermasalah):

```bash
# 1. Pasang APK
adb install -r GardaKiosk.apk

# 2. Buka aplikasi
adb shell am start -n com.example.kioskdeviceowner/.MainActivity

# 3. Tunggu 3 detik, lalu kirim konfigurasi
sleep 3
adb shell mkdir -p /sdcard/Android/data/com.example.kioskdeviceowner/files/
adb push kiosk_settings.json /sdcard/Android/data/com.example.kioskdeviceowner/files/kiosk_settings.json

# 4. Aktifkan Device Owner
adb shell dpm set-device-owner com.example.kioskdeviceowner/.receiver.KioskDeviceAdminReceiver
```

___

## 🔐 PIN & Kata Sandi Default

| Fungsi | PIN Default | Cara Pakai |
|---|---|---|
| Buka Menu Pengaturan Admin | `1234` | Ketuk jam 5x di layar utama, lalu masukkan PIN |
| Buka Lockscreen (jika mode PIN) | `147258` | Setelah swipe-up, masukkan PIN |

> ⚠️ **UBAH PIN DEFAULT SEGERA** setelah pemasangan! Buka Pengaturan Admin untuk mengubahnya.

___

## 🔧 Cara Masuk Menu Admin (Setting)

**Tidak ada tombol setting yang terlihat** — ini sengaja disembunyikan supaya pekerja tidak bisa masuk.

**Caranya**:
1. Di layar utama, **ketuk tulisan jam di pojok kiri atas sebanyak 5 kali berturut-turut dengan cepat**
2. Akan muncul layar input PIN
3. Masukkan **PIN Pengaturan** (default: `1234`)
4. Anda masuk ke menu konfigurasi

Di dalam menu admin Anda bisa:
- Menambah/menghapus aplikasi yang diizinkan
- Mengubah mode lockscreen (SWIPE atau PIN)
- Mengubah PIN pengaturan dan PIN lockscreen
- Mengatur waktu idle sebelum layar terkunci
- Ekspor/impor konfigurasi ke file JSON
- Membuka pengaturan sistem Samsung

___

## 📱 Fitur-Fitur Utama

### 🏠 Layar Utama (Dashboard)
- Menampilkan **jam digital** di pojok kiri atas
- Menampilkan **indikator baterai** real-time
- Menampilkan **daftar ikon aplikasi** yang diizinkan
- Menampilkan **teks footer**: "PROPERTI PERUSAHAAN - GPS TRACKING AKTIF"
- Klik ikon aplikasi untuk langsung membukanya
- Tekan lama ikon untuk **Info Aplikasi** (cek izin, hapus cache, dll)

### 🔒 Layar Kunci (Lockscreen)
- Otomatis muncul saat HP tidak disentuh selama waktu idle
- **Mode SWIPE**: Cukup geser ke atas untuk membuka
- **Mode PIN**: Geser ke atas lalu masukkan PIN
- Menampilkan **notifikasi GPS** dari aplikasi GPS Tanaman/Unit
- Indikator status GPS: **"Berjalan"** atau **"Berhenti"**

### 📡 Notifikasi GPS di Lockscreen
- Saat aplikasi GPS Tanaman/Unit sedang tracking, notifikasinya muncul di layar kunci
- Supervisor bisa melihat status GPS **tanpa harus membuka aplikasi**

### 💾 Cadangan & Pulihkan Konfigurasi
- **Ekspor**: Simpan semua pengaturan ke file `kiosk_settings.json`
- **Impor**: Muat pengaturan dari file JSON (otomatis/tombol)  
- **Auto-deteksi**: File yang dikirim via ADB akan otomatis diterapkan

___

## ⚡ Perintah ADB Darurat

Gunakan saat terjadi masalah, HP terhubung ke PC via USB:

| Keperluan | Perintah |
|---|---|
| **Paksa keluar dari mode Kiosk** | `adb shell am broadcast -a com.kiosk.action.EXIT_KIOSK -p com.example.kioskdeviceowner` |
| **Paksa buka menu Pengaturan** | `adb shell am broadcast -a com.kiosk.action.OPEN_SETTINGS -p com.example.kioskdeviceowner` |
| **Hapus status Device Owner** | `adb shell am broadcast -a com.kiosk.action.CLEAR_DEVICE_OWNER -p com.example.kioskdeviceowner` |
| **Sembunyikan ikon aplikasi** | `adb shell am broadcast -a com.kiosk.action.HIDE_ICON -p com.example.kioskdeviceowner --es package_name "nama.paket"` |
| **Tampilkan ikon aplikasi** | `adb shell am broadcast -a com.kiosk.action.SHOW_ICON -p com.example.kioskdeviceowner --es package_name "nama.paket"` |

___

## 📂 Struktur Folder

```
GardaKiosk_Production/
├── GardaKiosk.apk                 # Aplikasi siap install
├── kiosk_settings.json            # File konfigurasi default
├── setup_kiosk.sh                 # Script setup otomatis
├── README.md                      # Panduan ini (untuk orang awam)
├── AI_GUIDE.md                    # Panduan teknis untuk developer
├── .gitignore                     # Aturan file yang diabaikan Git
└── source_code/                   # Kode sumber Android (Kotlin)
    ├── app/                       # Modul utama aplikasi
    ├── build.gradle.kts           # Konfigurasi build
    ├── gradle.properties          # Setelan Gradle
    ├── settings.gradle.kts        # Setelan project
    └── gradle/                    # Gradle wrapper
```

___

## 🛠️ Untuk Admin / Teknisi

### Mengirim konfigurasi ke banyak HP sekaligus

1. Siapkan 1 file `kiosk_settings.json` yang sudah sesuai
2. Untuk setiap HP, jalankan 3 perintah ini:

```bash
adb install -r GardaKiosk.apk
adb push kiosk_settings.json /sdcard/Android/data/com.example.kioskdeviceowner/files/
adb shell dpm set-device-owner com.example.kioskdeviceowner/.receiver.KioskDeviceAdminReceiver
```

3. Atau pakai script `setup_kiosk.sh` satu per satu

### Mengubah konfigurasi default

Edit file `kiosk_settings.json` — struktur dan penjelasan tiap field:

```json
{
  "allowed_packages": ["paket1", "paket2"],    // Aplikasi yg boleh dibuka
  "background_packages": ["paket1"],           // Aplikasi yg TIDAK dihentikan saat layar mati
  "hidden_icons": ["paket2"],                  // Aplikasi yg diizinkan tapi ikon disembunyikan
  "lock_mode": "SWIPE",                        // "SWIPE" atau "PIN"
  "idle_timeout": 30,                          // Detik sebelum layar terkunci otomatis
  "is_kiosk_active": true,                     // true = mode kiosk menyala
  "footer_text": "TEKS DI SINI",               // Teks di bagian bawah dashboard
  "usb_debugging_blocked": true                // true = blokir USB debugging
}
```

___

## ⚠️ Masalah Umum & Solusi

| Masalah | Solusi |
|---|---|
| **GPS tidak muncul di Lockscreen** | Buka Menu Admin → Keamanan → "Buka Izin Akses Notifikasi", aktifkan Garda Kiosk |
| **Lupa PIN Admin** | Pakai ADB: `adb shell am broadcast -a com.kiosk.action.OPEN_SETTINGS -p com.example.kioskdeviceowner` |
| **Aplikasi GPS tidak bisa tracking** | Buka Info Aplikasi (tekan lama ikon), pastikan izin Lokasi = "Izinkan sepanjang waktu" |
| **HP lemot / panas** | Wajar, GPS tracking memakan baterai. Pastikan charger selalu terpasang |
| **Mau hapus total aplikasi ini** | 1. ADB: CLEAR_DEVICE_OWNER  2. Settings → Apps → Uninstall |
| **"Device Owner already set"** | Hapus dulu: Settings → Security → Device Admin → Hapus centang, lalu CLEAR_DEVICE_OWNER via ADB |

___

## 👨‍💻 Kredit

| Peran | Nama |
|---|---|
| Developer | kwadev69 |
| AI Assistants | Deepseek, Antygravity, ChatGPT, Claude Code, Open Code, GLM |

**Lisensi**: Private / Internal Perusahaan

___

> 📌 **Pertanyaan?** Simpan link GitHub ini. Jika ada masalah teknis, hubungi developer atau gunakan perintah ADB Darurat di atas.
