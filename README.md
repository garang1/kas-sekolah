# 📚 Buku Kas Pintar (Kas Simapas)

**Aplikasi Pengelolaan Buku Kas Utama (BKU) & Keuangan Sekolah Terintegrasi untuk SD/SMP/SMA**

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)
![Room Database](https://img.shields.io/badge/Database-Room%20SQLite-orange.svg)

---

## 🌟 Tentang Aplikasi

**Buku Kas Pintar** adalah aplikasi Android berbasis **Jetpack Compose** dan **Room Database** yang dirancang khusus untuk mempermudah Bendahara dan Kepala Sekolah dalam mencatat, mengelola, menyetujui, dan mengarsipkan transaksi keuangan sekolah (BOS Reguler, BOS Kinerja, Dana Komite, BOP, SiPA).

Aplikasi ini dilengkapi dengan **Sinkronisasi Real-Time Google Sheets (Apps Script)** yang memungkinkan akuntabilitas transparan antara HP Bendahara dan HP Kepala Sekolah.

---

## ✨ Fitur-Fitur Utama

### 1. 👥 Multi-Akun & Otoritas Peran
* **Bendahara Sekolah**: Pencatatan transaksi penerimaan/pengeluaran kas, unggah bukti nota, dan manajemen SPJ.
* **Kepala Sekolah**: Otoritas verifikasi & approval transaksi pengeluaran (khusus pengeluaran ≥ Rp 50.000 atau sesuai ketentuan).
* **Proteksi PIN Akses**: Sistem keamanan akun dengan PIN login yang dapat disesuaikan.

### 2. ☁️ Sinkronisasi Multi-HP via Google Sheets
* **Database Cloud Gratis**: Menggunakan **Google Apps Script Web App** yang terhubung langsung ke Google Spreadsheet milik sekolah.
* **Kolaborasi Real-Time**: Bendahara mencatat dari HP-nya, lalu tekan **Sinkron ke Sheets**. Kepala Sekolah dapat menekan **Tarik Data** dari HP-nya untuk melihat dan memberikan persetujuan (approval) secara instan.
* **Pengisian & Revisi Langsung di Spreadsheet**: Fleksibilitas mengisi atau mengedit baris data transaksi dalam jumlah banyak langsung di Google Sheets komputer, kemudian ditarik (*pull*) kembali ke aplikasi.

### 3. 📷 Bukti Digital Nota & Kuitansi (Opsional)
* Pengambilan foto nota/kuitansi belanja menggunakan kamera atau galeri.
* Bersifat **opsional** (tidak menghambat proses pencatatan kas awal).
* Modul **Viewer Nota** interaktif untuk peninjauan SPJ.

### 4. 📊 Laporan BKU & Rekap SPJ Otomatis
* Rekapitulasi per Sumber Dana (BOS, Komite, dll) dan per Tahap/Gelombang (Tahap 1 Jan-Jun, Tahap 2 Jul-Des).
* Format Laporan BKU Resmi dengan blok penandatanganan **Kepala Sekolah** & **Bendahara** beserta NIP resmi.

### 5. 🏫 Profil Identitas Sekolah Dinamis
* Pengaturan nama sekolah, NPSN, alamat, nama & NIP Kepala Sekolah, serta Bendahara yang dapat disesuaikan untuk sekolah manapun.

---

## 🛠️ Arsitektur & Teknologi

* **Bahasa Pemrograman**: Kotlin (100%)
* **UI Framework**: Jetpack Compose dengan Material Design 3 (M3)
* **Arsitektur**: MVVM (Model-View-ViewModel) + Clean Architecture pattern
* **Database Lokal**: Android Room Database (Offline First Approach)
* **Asynchronous**: Kotlin Coroutines & `StateFlow` / `SharedFlow`
* **Cloud Sync**: Google Apps Script Web App (JSON Endpoint) & Google Sheets

---

## 🚀 Panduan Memulai & Sinkronisasi

### Persyaratan Sistem
* Perangkat Android dengan OS **Android 8.0 (API Level 26)** atau versi yang lebih baru.
* Koneksi internet (untuk fitur Sinkronisasi Google Sheets).

### Cara Menghubungkan Google Sheets (Multi-HP)
1. Buka Google Sheets baru di akun Google/Gmail Sekolah.
2. Pilih menu **Ekstensi** > **Apps Script**.
3. Tempelkan skrip penangan Web App (tersedia panduan di tab **Laporan** aplikasi).
4. Klik **Deploy** > **New Deployment** > Akses: **Anyone** (Siapa Saja).
5. Salin **URL Web App** dan tempelkan di menu **Pengaturan Database Sheets** pada aplikasi.
6. Masukkan URL yang SAMA pada HP Bendahara dan HP Kepala Sekolah.

---

## 📁 Struktur Folder Proyek

```
/app/src/main/java/com/example
├── data/
│   ├── dao/                 # Data Access Object Room (TransactionDao)
│   ├── database/            # Database Room (AppDatabase)
│   ├── model/               # Data model (TransactionEntity, SchoolProfile, UserRole)
│   └── repository/          # Repository & Google Sheets Sync Logic
├── ui/
│   ├── components/          # Komponen UI Reusable (Banner, Modals, Dialogs)
│   ├── screens/             # Skrin Aplikasi (Dashboard, Entry, Approval, Report)
│   └── theme/               # Tema & Warna Material Design 3
├── viewmodel/
│   └── MainViewModel.kt     # Main ViewModel Pengelola State & Alur Bisnis
└── MainActivity.kt          # Main Activity & Compose Navigation Engine
```

---

## 📜 Lisensi & Kontribusi

Pengembangan aplikasi **Buku Kas Pintar** didedikasikan untuk transparansi dan efisiensi tata kelola keuangan pendidikan di Indonesia.

* **Lisensi**: MIT License
* **Kontribusi**: Bebas dikembangkan lebih lanjut untuk kebutuhan sekolah masing-masing.

---
*Dibuat untuk kemudahan pengelolaan kas sekolah Indonesia.*
