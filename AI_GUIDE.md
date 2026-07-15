# 🤖 Garda Kiosk - AI Developer & Handover Guide

Panduan ini ditujukan bagi AI Asisten Coding lain agar dapat langsung memahami arsitektur, kode sumber, alur kerja, dan riwayat fitur dari aplikasi **Garda Kiosk**.

---

## 📌 Deskripsi Proyek & Tujuan

Garda Kiosk adalah aplikasi **Launcher Kiosk Mode** tingkat sistem (**Device Owner**) berbasis Android Kotlin (Jetpack Compose) yang dirancang khusus untuk HP Samsung A07 operasional lapangan.
*   **Tujuan Utama**: Mengunci HP agar pengemudi/pekerja hanya bisa membuka aplikasi pelacak GPS (**GPS Tanaman** & **GPS Unit**).
*   **Keamanan**: Memblokir status bar, tombol Home/Recent, factory reset, USB debugging (bisa diatur), dan uninstall aplikasi.

---

## 📂 Struktur File Sumber Utama (Source Code)

Seluruh kode sumber Kotlin telah dipaketkan ke dalam folder **`source_code/`**. Berikut adalah kelas-kelas krusial yang perlu dipelajari:

### 1. **MainActivity.kt**
*   **Lokasi**: `source_code/app/src/main/java/com/example/kioskdeviceowner/MainActivity.kt`
*   **Peran**:
    *   Titik masuk utama aplikasi Kiosk.
    *   Memulai (`startLockTask()`) dan menghentikan (`stopLockTask()`) Mode Kunci Layar Sistem (LockTask).
    *   Melacak status *Idle Timeout* untuk mengunci HP otomatis jika tidak disentuh.
    *   Melakukan impor otomatis berkas JSON setelan (`kiosk_settings.json`) saat awal diluncurkan (`onCreate`).
    *   Memasang `FileObserver` untuk memantau perubahan berkas JSON secara real-time via `adb push`.

### 2. **KioskDeviceAdminReceiver.kt**
*   **Lokasi**: `source_code/app/src/main/java/com/example/kioskdeviceowner/receiver/KioskDeviceAdminReceiver.kt`
*   **Peran**:
    *   Mengatur seluruh kebijakan administratif Device Owner (`setupDeviceOwnerPolicies`).
    *   Mengatur daftar paket aplikasi yang diizinkan berjalan di Kiosk Mode (`setLockTaskPackages`).
    *   **Whitelist Bluetooth & QuickShare**: Menambahkan dependensi paket Samsung QuickShare, Bluetooth, dan sharing chooser agar fitur kirim data nirkabel dapat dibuka dari dalam aplikasi GPS.
    *   Memberikan izin akses lokasi latar belakang (`ACCESS_BACKGROUND_LOCATION`) secara otomatis kepada aplikasi GPS Tanaman & Unit.

### 3. **LockscreenScreen.kt**
*   **Lokasi**: `source_code/app/src/main/java/com/example/kioskdeviceowner/ui/kiosk/LockscreenScreen.kt`
*   **Peran**:
    *   Merender halaman kunci layar kustom (Kunci Layar Kiosk).
    *   **Desain Minimalis**: Jam tipis di pojok kiri atas, status indikator GPS & App di pojok kanan atas, serta kolom tengah bersih.
    *   **Alur 2-Tahap**: Selalu menampilkan "Geser Ke Atas" (*Swipe Up*) terlebih dahulu. Jika mode PIN aktif, setelah digeser baru menampilkan keypad PIN pengaman.
    *   **Monitoring Notifikasi**: Menampilkan list notifikasi asli (*native*) dari aplikasi GPS Tanaman/Unit langsung di layar kunci.

### 4. **KioskNotificationListener.kt**
*   **Lokasi**: `source_code/app/src/main/java/com/example/kioskdeviceowner/service/KioskNotificationListener.kt`
*   **Peran**:
    *   Layanan pembaca notifikasi sistem (`NotificationListenerService`).
    *   Menangkap notifikasi logging aktif dari aplikasi GPS Tanaman/Unit untuk ditampilkan di layar kunci.

### 5. **KioskSettingsManager.kt**
*   **Lokasi**: `source_code/app/src/main/java/com/example/kioskdeviceowner/KioskSettingsManager.kt`
*   **Peran**:
    *   Membaca dan menulis setelan ke penyimpanan aman `SharedPreferences` (menggunakan *Device Protected Storage* agar data terbaca saat layar terkunci).
    *   Melakukan konversi Ekspor/Impor berkas konfigurasi JSON.
    *   **Consume Pattern**: Menghapus file `kiosk_settings.json` setelah impor berhasil untuk menghindari *looping* impor.

---

## ⚙️ Skema Konfigurasi JSON (`kiosk_settings.json`)

Struktur JSON digunakan untuk memindahkan konfigurasi antar perangkat secara massal:
```json
{
  "allowed_packages": [ ... ],       // Aplikasi yang boleh dibuka di dashboard
  "background_packages": [ ... ],    // Aplikasi yang tidak boleh di-suspend saat layar mati (misal GPS Tanaman)
  "hidden_icons": [ ... ],           // Aplikasi yang diwhitelisting tapi ikonnya disembunyikan dari dashboard
  "lock_mode": "SWIPE",              // Mode kunci layar: "SWIPE" atau "PIN"
  "idle_timeout": 30,                // Detik batas diam sebelum layar mengunci otomatis
  "is_kiosk_active": true,           // Status kiosk aktif
  "footer_text": "TEXT DISPLAY",     // Teks bagian bawah dashboard
  "usb_debugging_blocked": true      // Blokir USB debugging untuk keamanan
}
```

---

## ⚡ Alur Setup Otomatis (Auto Provisioning)

Urutan instalasi massal yang paling stabil dan tidak memicu terputusnya USB debugging sebelum setelan masuk:

1.  **Install APK**: Pasang paket aplikasi.
2.  **Launch App**: Jalankan MainActivity untuk pertama kali.
3.  **Create Directory**: Jalankan perintah `mkdir -p` untuk folder data eksternal aplikasi.
4.  **Push JSON**: Kirim berkas `kiosk_settings.json` ke folder data eksternal HP.
5.  **Set Device Owner**: Terakhir, daftarkan paket sebagai Device Owner (setelah langkah ini, USB debugging akan otomatis dinonaktifkan jika setelan `usb_debugging_blocked` bernilai true).

*Seluruh alur ini telah diotomatisasi secara interaktif dalam berkas script **`setup_kiosk.sh`**.*
