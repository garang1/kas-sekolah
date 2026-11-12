package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.model.AppBackupData
import com.example.data.model.BackupSummary
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupRestoreHelper {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        
    private val adapter = moshi.adapter(AppBackupData::class.java).indent("  ")

    // VERSI SKEMA BACKUP SAAT INI (Tingkatkan nilai ini jika struktur AppBackupData berubah di masa depan)
    private const val CURRENT_BACKUP_VERSION = 2 

    /**
     * Mengekspor seluruh data database Room ke file JSON lokal di memori HP (Folder Download),
     * memindai file dengan MediaScanner agar langsung muncul di File Manager HP,
     * serta membuka lembar berbagi (Share Intent) jika diminta agar bisa langsung disimpan
     * ke Google Drive, WhatsApp, Email, atau aplikasi penyimpanan lainnya.
     */
    suspend fun exportDatabaseToJsonFile(
        context: Context,
        backupData: AppBackupData,
        shareImmediately: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val dateStr = fileDateFormat.format(Date(backupData.timestamp))
            val cleanSchool = backupData.schoolProfile.schoolName
                .replace("[^a-zA-Z0-9]".toRegex(), "_")
                .trim('_')
                .take(20)
                .ifBlank { "Sekolah" }

            val fileName = "Backup_BKU_${cleanSchool}_$dateStr.json"

            // Simpan ke Downloads di Penyimpanan Internal HP (jika tersedia), fallback ke internal files
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
                publicDir
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            }

            val file = File(targetDir, fileName)

            // Pastikan data memiliki ringkasan dan versi terbaru
            val finalBackupData = if (backupData.summary == null) {
                val totalInc = backupData.transactions.filter { it.type == "PEMASUKAN" }.sumOf { it.amount }
                val totalExp = backupData.transactions.filter { it.type == "PENGELUARAN" }.sumOf { it.amount }
                val summaryDateFormat = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID"))
                backupData.copy(
                    version = CURRENT_BACKUP_VERSION,
                    summary = BackupSummary(
                        totalTransactions = backupData.transactions.size,
                        totalIncome = totalInc,
                        totalExpense = totalExp,
                        balance = totalInc - totalExp,
                        exportDateFormatted = summaryDateFormat.format(Date(backupData.timestamp))
                    )
                )
            } else {
                backupData.copy(version = CURRENT_BACKUP_VERSION)
            }

            val jsonString = adapter.toJson(finalBackupData)
            file.writeText(jsonString, Charsets.UTF_8)

            // Beritahukan Android Media Scanner agar file langsung muncul di File Manager HP (Storage Internal / Download)
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("application/json")
                ) { _, _ -> }
            } catch (ignored: Exception) {}

            // Jika shareImmediately aktif, panggil Intent ACTION_SEND
            if (shareImmediately) {
                withContext(Dispatchers.Main) {
                    try {
                        val contentUri: Uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )

                        val displayDateFormat = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID"))
                        val shareText = "📂 File Cadangan Database BKU Kas Segitiga\n" +
                                "🏫 Sekolah: ${backupData.schoolProfile.schoolName}\n" +
                                "🔢 Total Transaksi: ${backupData.transactions.size} transaksi\n" +
                                "📅 Tanggal Backup: ${displayDateFormat.format(Date(backupData.timestamp))}\n\n" +
                                "File ini berisi salinan lengkap database Room (JSON) untuk keperluan pencadangan dan pemulihan data."

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, "Backup Database BKU: ${backupData.schoolProfile.schoolName}")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        val chooser = Intent.createChooser(shareIntent, "Simpan / Bagikan Cadangan Database JSON").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            Result.success(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun backupDataToUri(context: Context, uri: Uri, backupData: AppBackupData): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Pastikan file backup yang ditulis selalu menggunakan versi skema terbaru dan pretty print
                val finalBackupData = backupData.copy(version = CURRENT_BACKUP_VERSION)
                val json = adapter.toJson(finalBackupData)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(json)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun restoreDataFromUri(context: Context, uri: Uri): AppBackupData? {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                }
                
                if (jsonString.isNullOrBlank()) return@withContext null

                // 1. Validasi Integritas File & Lakukan Migrasi Skema (Versi Lama -> Versi Baru)
                val migratedJsonString = validateAndMigrateSchema(jsonString)
                    ?: throw Exception("Integritas file backup gagal divalidasi atau rusak.")

                // 2. Parse menggunakan Moshi (Struktur JSON sekarang dijamin cocok dengan versi aplikasi)
                adapter.fromJson(migratedJsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Memvalidasi file JSON cadangan dan melakukan migrasi/transformasi otomatis 
     * apabila file backup berasal dari aplikasi versi lama (misal: versi 5) yang direstore di versi baru (versi 6).
     */
    private fun validateAndMigrateSchema(jsonString: String): String? {
        return try {
            val jsonObj = JSONObject(jsonString)

            // --- VALIDASI INTEGRITAS INTI ---
            // Syarat mutlak: File harus punya objek 'schoolProfile' dan array 'transactions'
            if (!jsonObj.has("schoolProfile") || !jsonObj.has("transactions")) {
                return null
            }

            var backupVersion = jsonObj.optInt("version", 1)

            // --- MESIN MIGRASI (FORWARD COMPATIBILITY) ---
            // Loop akan memutakhirkan skema secara bertahap dari versi lama ke versi terbaru
            while (backupVersion < CURRENT_BACKUP_VERSION) {
                when (backupVersion) {
                    1 -> {
                        // Skenario Migrasi dari Skema Versi 1 ke Versi 2
                        // Contoh: Memastikan semua transaksi lama memiliki status approval default "VERIFIED"
                        // jika kebetulan variabel tersebut belum pernah ada di versi 1
                        val transactionsArray = jsonObj.optJSONArray("transactions")
                        if (transactionsArray != null) {
                            for (i in 0 until transactionsArray.length()) {
                                val tx = transactionsArray.optJSONObject(i) ?: continue
                                if (!tx.has("approvalStatus")) {
                                    tx.put("approvalStatus", "VERIFIED")
                                }
                            }
                        }
                        
                        backupVersion = 2
                        jsonObj.put("version", 2)
                    }
                    // Jika di masa depan ada pembaruan ke versi 3, tambahkan case `2 -> { ... }` di sini
                    else -> {
                        // Jika menemukan versi lama yang tidak dikenali, paksa menyesuaikan ke versi terbaru
                        backupVersion = CURRENT_BACKUP_VERSION
                        jsonObj.put("version", CURRENT_BACKUP_VERSION)
                    }
                }
            }

            jsonObj.toString()
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }
}
