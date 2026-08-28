package com.example.viewmodel

import android.content.Context
import android.net.Uri
import com.example.data.model.AppBackupData
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object BackupRestoreHelper {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        
    private val adapter = moshi.adapter(AppBackupData::class.java)

    // VERSI SKEMA BACKUP SAAT INI (Tingkatkan nilai ini jika struktur AppBackupData berubah di masa depan)
    private const val CURRENT_BACKUP_VERSION = 2 

    suspend fun backupDataToUri(context: Context, uri: Uri, backupData: AppBackupData): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Pastikan file backup yang ditulis selalu menggunakan versi skema terbaru
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
