# 📚 Kas Segitiga (Buku Kas Pintar) v7.0

**Sistem Pengelolaan Buku Kas Umum (BKU), RKAS & Keuangan Sekolah Terintegrasi (BOS, BOP, Komite) dengan Alur Verifikasi & Notifikasi Real-Time**

[![App Version](https://img.shields.io/badge/Version-7.0-blue?logo=android&logoColor=white)](https://github.com)
[![Platform Android](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language Kotlin](https://img.shields.io/badge/Language-Kotlin%202.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Database Room](https://img.shields.io/badge/Database-Room%20SQLite%20(Offline%20First)-FFA000?logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Background WorkManager](https://img.shields.io/badge/Sync-WorkManager%20%2B%20Google%20Sheets-34A853?logo=google&logoColor=white)](https://developer.android.com/topic/libraries/architecture/workmanager)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 🌟 Tentang Kas Segitiga

**Kas Segitiga (Buku Kas Pintar)** adalah aplikasi tata kelola keuangan sekolah modern berbasis Android yang dirancang untuk mewujudkan transparansi, akuntabilitas, dan efisiensi pelaporan kas sekolah (seperti pada **SD Negeri 33/III Air Tenang** dan sekolah-sekolah di Indonesia).

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

## 📖 Panduan Lengkap Cara Penggunaan Aplikasi (User Manual)

### 1. 📥 Instalasi APK di HP Android
1. Unduh file **Kas-Segitiga-v7.0.apk** dari tab **Releases** di repositori GitHub ini.
2. Buka file APK yang telah diunduh di HP Android Anda.
3. Jika muncul peringatan keamanan *"Instal aplikasi dari sumber tidak dikenal"*, pilih **Setelan (Settings)** lalu aktifkan **Izinkan dari sumber ini**.
4. Tekan **Instal** dan tunggu hingga selesai. Buka aplikasi.

---

### 2. 🏫 Pengisian Identitas Sekolah & NPSN (Wajib Awal)
Saat pertama kali membuka aplikasi:
1. Masukkan **Nomor Pokok Sekolah Nasional (NPSN)** sekolah Anda (contoh: `10103214`). *NPSN wajib diisi sebagai identitas unik lisensi pembukuan sekolah.*
2. Masukkan **Nama Sekolah** (contoh: `SD Negeri 33/III Air Tenang`).
3. Masukkan identitas pejabat penatausahaan kas:
   - **Nama Kepala Sekolah** beserta **NIP**.
   - **Nama Bendahara Sekolah** beserta **NIP**.
4. Atur **PIN Keamanan 6-Digit** (PIN default: `123456`) untuk melindungi transaksi.
5. Pilih **Mode Kunci Perangkat (Device Role Lock)**:
   - **Khusus HP Bendahara**: HP ini dikhususkan untuk pencatatan belanja kas harian, input nota, dan rekapitulasi.
   - **Khusus HP Kepala Sekolah**: HP ini dikhususkan untuk verifikasi/persetujuan (*approval*) nota pengeluaran dan pengawasan kas.
   - **Bebas (Dual Role)**: Dapat beralih peran kapan saja dengan memasukkan PIN.

---

### 3. 🎙️ Input Transaksi Kas Cepat dengan Suara (Voice Recognition - v7.0)
Fitur unggulan di versi 7.0 memungkinkan Bendahara mencatat kas cukup dengan berbicara dalam bahasa Indonesia:
1. **Buka Fitur Suara**:
   - Ketuk tombol melayang biru **"Input Suara"** (FAB ikon mikrofon) di pojok kanan bawah Beranda, ATAU
   - Ketuk kartu banner **"Input Kas Cepat dengan Suara"** di tab menu **Catat Kas**, ATAU
   - Ketuk ikon **Mikrofon** di sebelah kanan kolom teks *Uraian Transaksi*.
2. **Ucapkan Transaksi Secara Alami**:
   Saat indikator mikrofon berdenyut, sebutkan transaksi belanja atau penerimaan dana:
   - **Belanja ATK / Barang**: *"Beli kertas HVS 5 rim harga 50 ribu per rim dari BOS Reguler"*
   - **Belanja Spidol / Alat Tulis**: *"Pembelian spidol papan tulis 12 buah harga satuan 8500 dari BOS Kinerja"*
   - **Penerimaan Dana BOS**: *"Penerimaan dana BOS Reguler tahap 1 25 juta"*
   - **Konsumsi / Rapat**: *"Konsumsi rapat guru 25 kotak harga 20 ribu rupiah"*
   - **Jasa / Fotocopy**: *"Foto copy naskah ujian 500 lembar harga 300 rupiah"*
   - **Operasional / Perbaikan**: *"Perbaikan printer kantor 150 ribu dana komite"*
3. **Tinjau & Koreksi Hasil Suara**:
   Sistem otomatis mengurai:
   - **Jenis Kas**: PENGELUARAN (Merah) atau PEMASUKAN (Hijau). *Dapat diubah cukup dengan mengetuk badge jenis.*
   - **Uraian Transaksi**: Dirapikan tanpa kata sambung perintah.
   - **Volume & Satuan**: Kuantitas (misal: *5 rim, 12 buah, 25 kotak, 500 lembar*).
   - **Harga Satuan & Total**: Dihitung otomatis (*Volume × Harga Satuan = Total Rp*).
   - **Buku Kas / Sumber Dana**: Dapat langsung diganti dengan mengetuk deretan chip sumber dana (BOS Reguler, BOS Kinerja, Komite, dll.).
4. **Simpan Transaksi**:
   - Pilih **"Langsung Simpan Kas"** untuk simpan instan ke database Room & Google Sheets.
   - Atau pilih **"Terapkan ke Form Input"** jika ingin menambahkan foto bukti nota/kwitansi sebelum disimpan.

---

### 4. ✍️ Input Transaksi Kas Manual & Perhitungan Rincian
Jika mencatat secara manual melalui tab **Catat Kas**:
1. Pilih jenis transaksi: **PENGELUARAN** (merah) atau **PEMASUKAN** (hijau).
2. Tentukan **Tanggal Transaksi** melalui kalender (*Date Picker*).
3. Masukkan **Uraian / Deskripsi Transaksi** (misal: *Pembelian Kertas HVS & ATK Kantor*).
4. Pada kartu rincian perhitungan:
   - Masukkan **Harga Satuan (Rp)** (misal: `50000`).
   - Masukkan **Volume / Banyaknya** (misal: `5`).
   - Tentukan **Satuan** (pilih dari chip: `rim`, `buah`, `kotak`, `lembar`, `siswa`, `paket`, dsb. — *bukan angka/rupiah*).
   - Sistem akan otomatis menghitung perkalian: *5 rim × Rp 50.000 = Rp 250.000*.
5. Pilih **Sumber Dana / Buku Kas** (BOS Reguler, BOS Kinerja, Dana Komite, Hibah, dll.).
6. Isi **Catatan / Toko Rekanan / Nomor Nota** (misal: *Toko Sinar Jaya, Nota #014*).
7. Tentukan kelengkapan bukti: **Kwitansi (Ada/Tidak)** dan **Nota/Faktur (Ada/Tidak)**.
8. Klik tombol **Simpan Kas**.

---

### 5. 🔍 Alur Verifikasi & Persetujuan (Khusus Kepala Sekolah)
Untuk menjaga agar anggaran tidak defisit dan seluruh belanja terawasi:
1. Setiap transaksi pengeluaran yang disimpan Bendahara otomatis berstatus `PENDING_APPROVAL` dan belum memotong saldo resmi kas.
2. HP Kepala Sekolah akan menerima notifikasi lonceng: *"Transaksi Baru Menunggu Verifikasi"*.
3. Kepala Sekolah membuka tab menu **Verifikasi** (tab ke-3):
   - Periksa nama barang, kuantitas volume, harga satuan, dan foto nota fisik.
   - Jika belanja sudah sesuai, tekan **Setujui (Approve)** dan masukkan PIN 6-digit. Status transaksi berubah menjadi `VERIFIED`, dan saldo kas resmi di Buku Kas Umum (BKU) langsung terpotong.
   - Jika belanja tidak sesuai atau nota kurang jelas, tekan **Tolak (Reject)** dengan alasan penolakan.

---

### 6. 📑 Melihat Buku Kas Umum (BKU) & Cetak Laporan SPJ
1. Buka tab menu **BKU Kas** (tab ke-4).
2. Pilih pos sumber dana kas yang ingin diperiksa (misal: *BOS Reguler*).
3. Tabel BKU akan menampilkan ringkasan debit, kredit, dan saldo berjalan (*running balance*) secara urut tanggal.
4. **Cetak PDF**: Tekan tombol **Cetak PDF** untuk mencetak laporan resmi ber-kop sekolah yang dilengkapi kolom tanda tangan Kepala Sekolah dan Bendahara beserta NIP.
5. **Ekspor CSV**: Tekan tombol **Ekspor CSV** untuk mengunduh rekap spreadsheet yang dapat langsung dibuka di Microsoft Excel komputer/laptop.

---

### 7. 💾 Pencadangan & Ekspor Database Room (Format JSON)
Untuk mengamankan data pembukuan atau berpindah perangkat:
1. Buka tab **BKU Kas** lalu ketuk tombol **Backup JSON** (warna hijau zamrud), ATAU buka menu **Pengaturan Akun** > bagian **Pencadangan Data**.
2. File cadangan akan diekspor otomatis ke folder **Download** HP dengan format nama:
   `Backup_BKU_<NamaSekolah>_<TahunBulanTanggal_Jam>.json`
3. Layar berbagi Android (*Share Sheet*) akan langsung terbuka, memungkinkan Anda mengirimkan file cadangan ke **WhatsApp**, **Google Drive**, **Email**, atau cloud storage.
4. **Memulihkan Data (Restore)**: Di HP baru, buka menu Pengaturan Akun ➔ tekan **Pulihkan Data (Restore)** ➔ pilih file backup JSON tersebut. Seluruh transaksi kas, profil sekolah, dan daftar sumber dana akan pulih sempurna.

---

### 8. ☁️ Pengaturan Sinkronisasi Dua Arah Google Sheets
Agar data di HP Bendahara dan HP Kepala Sekolah tersinkronisasi otomatis secara real-time:
1. Buat lembar kerja baru di **Google Sheets** komputer sekolah.
2. Klik menu **Ekstensi (Extensions)** > **Apps Script**.
3. Pasang kode skrip Google Apps Script untuk menerima & mengirim data JSON transaksi Kas Segitiga.
4. Klik **Deploy** > **New Deployment** > Pilih jenis **Web App**:
   - *Execute as*: **Me** (akun Google Anda).
   - *Who has access*: **Anyone** (siapa saja).
5. Salin tautan **Web App URL** yang didapat.
6. Buka aplikasi **Kas Segitiga** di HP Kepala Sekolah dan Bendahara:
   - Ketuk ikon awan / pengaturan Google Sheets di pojok kanan atas.
   - Tempelkan URL Web App tersebut dan klik **Simpan & Uji Koneksi**.
7. Kedua HP kini terhubung secara *real-time*: setiap transaksi baru, penghapusan, atau persetujuan kas akan langsung tersinkron ke spreadsheet dan ke HP mitra secara otomatis.

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
   git clone https://github.com/username/kas-segitiga.git
   cd kas-segitiga
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
6. Buka aplikasi **Kas Segitiga** di HP:
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
