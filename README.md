# 📚 Buku Kas Pintar (Kas Simapas)

**Aplikasi Pengelolaan Buku Kas Utama (BKU) & Keuangan Sekolah Terintegrasi untuk SD/SMP/SMA**

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)
![Room Database](https://img.shields.io/badge/Database-Room%20SQLite-orange.svg)

---

## 🌟 Tentang Aplikasi

**Buku Kas Pintar** adalah aplikasi Android berbasis **Jetpack Compose** dan **Room Database** yang dirancang khusus untuk mempermudah Bendahara dan Kepala Sekolah dalam mencatat, mengelola, menyetujui, dan mengarsipkan transaksi keuangan sekolah (BOS Reguler, BOS Kinerja, Dana Komite, BOP, SiPA).

Aplikasi ini dilengkapi dengan fitur **Pairing Antar HP (Cloud Mailbox)** yang memungkinkan akuntabilitas transparan antara HP Bendahara dan HP Kepala Sekolah secara real-time tanpa perlu akun terpusat.

---

## ✨ Fitur-Fitur Utama

### 1. 👥 Multi-Akun & Otoritas Peran
* **Bendahara Sekolah**: Pencatatan transaksi penerimaan/pengeluaran kas, unggah bukti nota, dan manajemen SPJ.
* **Kepala Sekolah**: Otoritas verifikasi & approval transaksi pengeluaran (khusus pengeluaran ≥ Rp 50.000 atau sesuai ketentuan).
* **Proteksi PIN Akses**: Sistem keamanan akun dengan PIN login yang dapat disesuaikan.

### 2. ☁️ Pairing Cerdas Multi-HP (Cloud Mailbox)
* **Koneksi Cepat via QR Code**: Bendahara dan Kepala Sekolah dapat saling menautkan aplikasi mereka hanya dengan memindai QR Code.
* **Kolaborasi Real-Time**: Bendahara mencatat dari HP-nya, lalu data secara otomatis tersinkronisasi. Kepala Sekolah dapat melihat dan memberikan persetujuan (approval) secara instan dari HP-nya sendiri.
* **Keamanan Maksimal (Enkripsi AES-256)**: Data yang disalurkan melalui internet dienkripsi menggunakan kunci rahasia (*Pairing Key*) sehingga tidak bisa diintip oleh pihak luar.

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
* **Cloud Sync**: Cloud Mailbox Relay (KVDB) dengan Enkripsi AES-256

---

## 🚀 Panduan Memulai & Sinkronisasi

### Persyaratan Sistem
* Perangkat Android dengan OS **Android 8.0 (API Level 26)** atau versi yang lebih baru.
* Koneksi internet (untuk fitur Sinkronisasi Multi-HP).

---

## 📁 Struktur Folder Proyek

```
/app/src/main/java/com/example
├── data/
│   ├── dao/                 # Data Access Object Room (TransactionDao)
│   ├── database/            # Database Room (AppDatabase)
│   ├── model/               # Data model (TransactionEntity, SchoolProfile, UserRole)
│   └── repository/          # Repository & Cloud Mailbox Sync Logic
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
