package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val type: String, // "PENGELUARAN" or "PEMASUKAN"
    val fundSource: String, // "BOS Reguler", "BOS Kinerja", "Dana Komite", "Hibah / Lainnya"
    val category: String = "Umum",
    val volume: Double = 1.0,
    val unitName: String = "buah",
    val unitPrice: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    val notes: String = "",
    val receiptUri: String? = null,
    val approvalStatus: String = "VERIFIED", // "VERIFIED", "PENDING_APPROVAL", "DITOLAK"
    val recordedByRole: String = "Bendahara", // "Bendahara", "Kepala Sekolah"
    val createdAt: Long = System.currentTimeMillis()
)

data class FundSourceModel(
    val name: String,
    val isSecret: Boolean = false // Jika true, hanya bisa dilihat & dikelola oleh Kepala Sekolah, offline di HP (tidak sync Google Sheet)
)

enum class UserRole(val label: String, val description: String) {
    BENDAHARA("Bendahara Sekolah", "Entri transaksi, unggah nota & cetak BKU"),
    KEPALA_SEKOLAH("Kepala Sekolah", "Monitoring live saldo, verifikasi & approval")
}

object FundSourceDefaults {
    val DEFAULT_FUND_MODELS = listOf(
        FundSourceModel("BOS Reguler", isSecret = false),
        FundSourceModel("BOS Kinerja", isSecret = false),
        FundSourceModel("Dana Komite", isSecret = false),
        FundSourceModel("Hibah / Lainnya", isSecret = false)
    )

    val SOURCES = listOf(
        "BOS Reguler",
        "BOS Kinerja",
        "Dana Komite",
        "Hibah / Lainnya"
    )

    // Default initial budgets (0.0 for a clean database start)
    val INITIAL_BUDGETS = mapOf(
        "BOS Reguler" to 0.0,
        "BOS Kinerja" to 0.0,
        "Dana Komite" to 0.0,
        "Hibah / Lainnya" to 0.0
    )
}
