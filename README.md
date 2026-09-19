# 📚 Kas Simapas (Buku Kas Pintar)

**Sistem Pengelolaan Buku Kas Umum (BKU), RKAS & Keuangan Sekolah Terintegrasi (BOS, BOP, Komite) dengan Alur Verifikasi & Notifikasi Real-Time**

[![Platform Android](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language Kotlin](https://img.shields.io/badge/Language-Kotlin%202.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Database Room](https://img.shields.io/badge/Database-Room%20SQLite%20(Offline%20First)-FFA000?logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Background WorkManager](https://img.shields.io/badge/Sync-WorkManager%20%2B%20Google%20Sheets-34A853?logo=google&logoColor=white)](https://developer.android.com/topic/libraries/architecture/workmanager)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 🌟 Tentang Kas Simapas

**Kas Simapas (Buku Kas Pintar)** adalah aplikasi tata kelola keuangan sekolah modern berbasis Android yang dirancang untuk mewujudkan transparansi, akuntabilitas, dan efisiensi pelaporan kas sekolah (seperti pada **SD Negeri 33/III Air Tenang** dan sekolah-sekolah di Indonesia).

Aplikasi ini menghubungkan alur kerja **Bendahara Sekolah** dan **Kepala Sekolah** secara langsung:
* **Bendahara** mencatat penerimaan/pengeluaran kas, mengunggah bukti nota digital, serta menyusun SPJ.
* **Kepala Sekolah** memiliki wewenang eksklusif untuk memverifikasi, menyetujui (*approval*), atau menolak setiap transaksi pengeluaran kas sebelum memotong saldo resmi.
* Didukung dengan sistem **Sinkronisasi Dua Arah Google Sheets** dan **Background Sync Worker (WorkManager)** yang otomatis mengirimkan notifikasi ke HP Kepala Sekolah saat ada pengajuan transaksi baru.

---

## ✨ Fitur-Fitur Unggulan

### 1. 👥 Manajemen Peran & Otoritas Bertingkat (Role-Based Access)
* **Peran Bendahara (Pencatatan & SPJ)**:
  * Input transaksi kas masuk dan kas keluar dengan pembagian sumber dana dan kategori kegiatan.
  * Opsi input cerdas berbasis suara (**Voice NLP**) yang secara otomatis mengekstrak nominal, jenis transaksi, dan uraian.
  * Identifikasi otomatis nama petugas pencatat (misal: *Bendahara (Siti Rahma S.Pd.)*), mendukung pencatatan multi-bendahara yang transparan.
* **Peran Kepala Sekolah (Otoritas Approval & Pengawasan)**:
  * Wewenang eksklusif menyetujui (*Approve*) atau menolak (*Reject*) setiap nota pengeluaran dengan validasi PIN 6-digit.
  * Hak akses khusus untuk membuka dan meninjau **Sumber Dana Rahasia / Khusus** (*Secret Fund Sources*).
* **Kunci Perangkat (Device Role Lock)**:
  * Setiap HP dapat dikunci fungsinya: khusus mode **HP Bendahara** (fokus entri kas), **HP Kepala Sekolah** (fokus persetujuan & pengawasan), atau mode **Bebas (Dual Role)**.
* **Keamanan Berlapis**:
  * Dilengkapi layar kunci PIN (*AppLockScreen*) 6-digit untuk mencegah akses tidak berwenang saat berpindah peran atau membuka data sensitif.

### 2. 🔔 Sistem Persetujuan (Approval Workflow) & Notifikasi Otomatis
* **Alur Ketat Bebas Defisit**:
  * Semua transaksi pengeluaran yang diinput oleh Bendahara otomatis berstatus `PENDING_APPROVAL` dan **tidak akan memotong saldo kas Buku Kas Umum (BKU)** sebelum diverifikasi resmi oleh Kepala Sekolah.
* **Notifikasi Latar Belakang (WorkManager Background Sync)**:
  * Layanan latar belakang (`SyncWorker`) berjalan berkala (setiap 15 menit) memeriksa perubahan data di cloud Google Sheets bahkan saat aplikasi sedang ditutup.
  * Memunculkan notifikasi Android dengan prioritas tinggi, suara, dan getaran di HP Kepala Sekolah:
    * 🔴 **Pengeluaran Baru**: Notifikasi verifikasi nota belanja / SPJ yang menunggu persetujuan.
    * 🟢 **Pemasukan Baru**: Notifikasi konfirmasi pencairan atau penerimaan dana baru.

### 3. 📑 Buku Kas Umum (BKU) & Rekapitulasi SPJ Lengkap
* **Format Sesuai Juknis Standar Pendidikan**:
  * Perhitungan saldo berjalan (*running balance*) otomatis per tanggal transaksi.
  * Pemisahan Buku Kas Umum (BKU), Buku Pembantu Kas Tunai, Pembantu Bank, dan Pembantu Pajak.
* **Filter Komprehensif**:
  * Filter per Sumber Dana (BOS Reguler, BOS Kinerja, Dana Komite, BOP PAUD, SiPA, Tabungan, dll).
  * Filter per Periode / Tahap: **Tahap 1 (Januari – Juni)** dan **Tahap 2 (Juli – Desember)**.
* **Ekspor & Cetak Siap Tanda Tangan**:
  * Ekspor dokumen laporan siap cetak / PDF lengkap dengan kop sekolah, nomor NPSN, tanggal penutupan kas, serta kolom tanda tangan resmi **Kepala Sekolah** dan **Bendahara** beserta NIP masing-masing.
  * Ekspor data ke format spreadsheet (.csv) untuk arsip pembukuan dinas.

### 4. 📊 Modul Perencanaan Anggaran (RKAS)
* Penyusunan Rencana Kegiatan dan Anggaran Sekolah (RKAS) terpadu per tahun anggaran.
* Monitoring persentase realisasi anggaran vs belanja riil secara visual (*progress bar* belanja).
* Evaluasi serapan dana per program dan standar nasional pendidikan.

### 5. ☁️ Sinkronisasi Cloud Google Sheets & Backup Aman
* **Dual-Way Sync via Google Apps Script Web App**:
  * Sinkronisasi data lokal Room SQLite dengan lembar kerja Google Sheets secara dua arah tanpa perlu konfigurasi backend server yang rumit atau berbayar.
* **Akses & Monitoring Lintas Perangkat**:
  * Menyediakan tombol pintas untuk langsung membuka dokumen Google Spreadsheet di browser laptop/PC.
  * Fitur Backup dan Restore database menyeluruh dengan sekali klik.

### 6. 📷 Arsip Bukti Nota Digital & Pemindai Kamera
* Integrasi **CameraX** untuk memotret bukti nota belanja atau kuitansi fisik secara langsung di tempat.
* **Modal Peninjau Nota (Receipt Viewer)** interaktif dengan fitur *pinch-to-zoom* dan rotasi untuk audit SPJ.
* Pemindai QR Code & Barcode terintegrasi untuk verifikasi faktur dan kemudahan pairing.

---

## 🛠️ Arsitektur & Teknologi

Aplikasi dibangun mengikuti standar rekayasa perangkat lunak Android modern:

| Komponen | Teknologi / Pustaka | Keterangan |
|---|---|---|
| **Bahasa** | Kotlin 2.2 | 100% Kotlin dengan Coroutines & Flow |
| **Arsitektur** | MVVM + Clean Architecture | Pemisahan Presentation, Domain, dan Data Layer |
| **UI Framework** | Jetpack Compose & Material 3 | Desain adaptif, responsif, dan ramah aksesibilitas |
| **Database Lokal** | Android Room 2.7 (SQLite) | *Offline-first persistence* dengan migrasi skema |
| **Background Task** | AndroidX WorkManager 2.10 | Pengecekan data berkala & *push notification* lokal |
| **Jaringan & REST** | Retrofit 2.12 + OkHttp 4.10 + Moshi | Komunikasi payload JSON dengan Apps Script |
| **Kamera & QR** | CameraX 1.5 + MLKit Barcode + ZXing | Pengambilan foto nota dan pemindaian QR Code |
| **Image Loading** | Coil Compose 2.7 | Pemuatan gambar nota yang ringan dan ter-cache |

---

## 📁 Struktur Direktori Proyek

```
app/src/main/java/com/example
├── MainActivity.kt                  # Entry point aplikasi & inisialisasi layanan
├── data/
│   ├── dao/
│   │   └── TransactionDao.kt        # Query database Room (CRUD transaksi kas & approval)
│   ├── database/
│   │   └── AppDatabase.kt           # Konfigurasi Room database & migrasi
│   ├── model/
│   │   ├── FundSourceModel.kt       # Model sumber dana (BOS, Komite, dsb.)
│   │   ├── SchoolProfile.kt         # Profil sekolah & sesi akun aktif
│   │   ├── TransactionEntity.kt     # Entitas data transaksi kas
│   │   └── UserRole.kt              # Enum peran: BENDAHARA & KEPALA_SEKOLAH
│   └── repository/
│       ├── GoogleSheetsSyncRepository.kt # Logika sinkronisasi dua arah Google Sheets
│       └── TransactionRepository.kt      # Abstraksi data transaksi kas
├── network/
│   └── GeminiNlpService.kt          # Parser cerdas input suara & teks transaksi lokal
├── ui/
│   ├── components/
│   │   ├── AppLockScreen.kt         # Layar kunci keamanan PIN 6-digit
│   │   ├── CameraQrScannerView.kt   # Viewfinder kamera & pemindai barcode
│   │   ├── EditTransactionDialog.kt # Dialog edit & koreksi transaksi
│   │   ├── GoogleSheetsSetupDialog.kt# Pengaturan URL Apps Script & sinkronisasi
│   │   ├── HeaderSchoolBanner.kt    # Banner identitas sekolah & status akun
│   │   ├── ReceiptViewerModal.kt    # Peninjau foto kuitansi/nota digital
│   │   └── SchoolAccountDialog.kt   # Profil sekolah, manajemen PIN & ganti akun
│   ├── screens/
│   │   ├── AboutScreen.kt           # Informasi versi aplikasi & pengembang
│   │   ├── ApprovalScreen.kt        # Layar verifikasi & persetujuan Kepala Sekolah
│   │   ├── DashboardScreen.kt       # Ringkasan saldo kas, grafik, dan riwayat
│   │   ├── ReportScreen.kt          # Rekapitulasi BKU resmi, ekspor & cetak
│   │   ├── RkasScreen.kt            # Perencanaan & realisasi anggaran (RKAS)
│   │   └── TransactionEntryScreen.kt# Form input transaksi kas & nota
│   └── theme/                       # Warna, tipografi & bentuk Material Design 3
├── util/
│   ├── AppNotificationHelper.kt     # Pengelola notification channel & builder
│   ├── LicenseManager.kt            # Manajemen lisensi penggunaan
│   ├── MailboxEncryptionHelper.kt   # Enkripsi data pairing
│   └── QrCodeGenerator.kt           # Generator kode QR profil & pairing
└── worker/
    └── SyncWorker.kt                # Background Worker pengecek transaksi baru & notifikasi
```

---

## 🚀 Panduan Memulai & Kompilasi

### Prasyarat Pengembangan
1. **Android Studio**: Android Studio Koala / Ladybug atau versi lebih baru.
2. **JDK**: Java Development Kit versi 11 atau 17.
3. **Android SDK**: Compile SDK 36, Minimum SDK 24 (Android 7.0 Nougat ke atas).

### Langkah Menjalankan Proyek
1. **Clone repositori**:
   ```bash
   git clone https://github.com/username/kas-simapas.git
   cd kas-simapas
   ```

2. **Buka proyek di Android Studio**:
   Pilih menu `File` > `Open...` lalu arahkan ke direktori hasil klon.

3. **Sinkronkan dependensi Gradle**:
   Tunggu hingga proses *Gradle Sync* selesai mengunduh seluruh pustaka.

4. **Kompilasi dan Jalankan Aplikasi**:
   Pilih target perangkat fisik Android atau Emulator, lalu tekan tombol **Run (Shift + F10)** atau jalankan melalui terminal:
   ```bash
   gradle assembleDebug
   ```

---

## ⚙️ Panduan Konfigurasi Sinkronisasi Google Sheets

Untuk menghubungkan sinkronisasi antara HP Bendahara dan HP Kepala Sekolah melalui Google Sheets:

1. Buat **Google Spreadsheet** baru di Google Drive sekolah.
2. Buka menu **Extensions** > **Apps Script** pada Google Spreadsheet.
3. Tempelkan skrip *Web App* Google Apps Script untuk menerima dan mengirim data JSON transaksi kas.
4. Klik **Deploy** > **New Deployment** > Pilih jenis **Web App**:
   * *Execute as*: **Me** (akun Google Anda).
   * *Who has access*: **Anyone** (agar aplikasi Android dapat membaca dan menulis data).
5. Salin tautan **Web App URL** yang dihasilkan (`https://script.google.com/macros/s/.../exec`).
6. Buka aplikasi **Kas Simapas** di HP:
   * Masuk ke menu **Pengaturan Sinkronisasi Google Sheets** di pojok kanan atas.
   * Tempelkan URL Web App tersebut dan tekan tombol **Simpan & Uji Koneksi**.
7. Lakukan hal yang sama pada HP Kepala Sekolah agar kedua perangkat saling terhubung.

---

## 🔒 Alur Penggunaan Rekomendasi di Sekolah

```
┌───────────────────────────┐         ┌───────────────────────────┐
│       HP BENDAHARA        │         │    HP KEPALA SEKOLAH      │
├───────────────────────────┤         ├───────────────────────────┤
│ 1. Kunci Peran: Bendahara │         │ 1. Kunci Peran: Kepsek    │
│ 2. Catat Transaksi Belanja│         │                           │
│ 3. Foto Bukti Nota Fisik  │         │                           │
│ 4. Tekan "Simpan Kas"     │         │                           │
│   (Status: PENDING)       │         │                           │
└─────────────┬─────────────┘         └─────────────▲─────────────┘
              │                                     │
              ▼                                     │
   ┌──────────────────────┐                         │
   │    GOOGLE SHEETS     │─────────────────────────┘
   │   (Cloud Database)   │  Otomatis tersinkronisasi via WorkManager
   └──────────────────────┘  Memunculkan Notifikasi Lonceng & Suara
                                                    │
                                                    ▼
                                      ┌───────────────────────────┐
                                      │ 2. Kepsek Buka Notifikasi │
                                      │ 3. Review Nota Belanja    │
                                      │ 4. Masukkan PIN & Approve │
                                      │   (Status: VERIFIED)      │
                                      │ 5. Saldo BKU Resmi Berubah│
                                      └───────────────────────────┘
```

---

## 📄 Lisensi & Hak Cipta

Proyek ini dirilis di bawah naungan **[MIT License](LICENSE)**. Bebas digunakan, dipelajari, dan disesuaikan untuk kemajuan tata kelola administrasi keuangan sekolah di seluruh wilayah Indonesia.

---
*Dikelola dan dikembangkan dengan dedikasi untuk efisiensi pembukuan kas pendidikan Indonesia.*
