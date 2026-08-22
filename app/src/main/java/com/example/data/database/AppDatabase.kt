package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.TransactionDao
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TransactionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN volume REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN unitName TEXT NOT NULL DEFAULT 'buah'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN unitPrice REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getDatabase(context: Context, externalScope: CoroutineScope? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "buku_kas_pintar_db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial demo school transactions for SD Negeri 33/III Air Tenang
                        externalScope?.launch(Dispatchers.IO) {
                            seedInitialData(getDatabase(context))
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(database: AppDatabase) {
            val dao = database.transactionDao()
            val now = System.currentTimeMillis()
            val dayMs = 86400000L

            val initialList = listOf(
                TransactionEntity(
                    title = "Penerimaan Dana BOS Reguler",
                    amount = 45000000.0,
                    type = "PEMASUKAN",
                    fundSource = "BOS Reguler",
                    category = "Lain-lain",
                    date = now - (10 * dayMs),
                    notes = "Pencairan Dana dari Kas Daerah",
                    approvalStatus = "VERIFIED",
                    recordedByRole = "Bendahara"
                ),
                TransactionEntity(
                    title = "Pembelian Kertas HVS A4 & Tinta Spidol Boardmarker",
                    amount = 1250000.0,
                    type = "PENGELUARAN",
                    fundSource = "BOS Reguler",
                    category = "Alat Tulis Kantor",
                    date = now - (7 * dayMs),
                    notes = "Nota Toko Buku Kerinci Mandiri No. 042",
                    approvalStatus = "VERIFIED",
                    recordedByRole = "Bendahara"
                ),
                TransactionEntity(
                    title = "Perbaikan Atap Ruang Kelas 4 & Cat Dinding",
                    amount = 3800000.0,
                    type = "PENGELUARAN",
                    fundSource = "BOS Kinerja",
                    category = "Pemeliharaan & Sarpras",
                    date = now - (4 * dayMs),
                    notes = "Perbaikan kebocoran atap seng jelang musim hujan",
                    approvalStatus = "VERIFIED",
                    recordedByRole = "Bendahara"
                ),
                TransactionEntity(
                    title = "Pembelian Buku Pengayaan Perpustakaan & Modul Ajar",
                    amount = 2750000.0,
                    type = "PENGELUARAN",
                    fundSource = "BOS Reguler",
                    category = "Kegiatan Pembelajaran",
                    date = now - (2 * dayMs),
                    notes = "Pemesanan modul Kurikulum Merdeka",
                    approvalStatus = "PENDING_APPROVAL",
                    recordedByRole = "Bendahara"
                ),
                TransactionEntity(
                    title = "Sumbangan Sukarela Kegiatan Lomba Pramuka",
                    amount = 2500000.0,
                    type = "PEMASUKAN",
                    fundSource = "Dana Komite",
                    category = "Kegiatan Ekstrakurikuler",
                    date = now - (1 * dayMs),
                    notes = "Diterima dari Pengurus Komite Sekolah",
                    approvalStatus = "VERIFIED",
                    recordedByRole = "Bendahara"
                )
            )

            for (tx in initialList) {
                dao.insertTransaction(tx)
            }
        }
    }
}
