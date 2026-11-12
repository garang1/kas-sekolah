package com.example

import com.example.data.model.AppBackupData
import com.example.data.model.BackupSummary
import com.example.data.model.FundSourceModel
import com.example.data.model.SchoolProfile
import com.example.data.model.TransactionEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreHelperTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AppBackupData::class.java).indent("  ")

    @Test
    fun testAppBackupData_SerializationAndDeserialization() {
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                title = "Penerimaan BOS Reguler Tahap 1",
                amount = 45000000.0,
                type = "PEMASUKAN",
                fundSource = "BOS Reguler",
                volume = 1.0,
                unitName = "transaksi",
                unitPrice = 45000000.0,
                approvalStatus = "VERIFIED"
            ),
            TransactionEntity(
                id = 2,
                title = "Pembelian Kertas HVS & Spidol",
                amount = 500000.0,
                type = "PENGELUARAN",
                fundSource = "BOS Reguler",
                volume = 10.0,
                unitName = "rim",
                unitPrice = 50000.0,
                approvalStatus = "VERIFIED"
            )
        )

        val profile = SchoolProfile(
            schoolName = "SD Negeri 33/III Air Tenang",
            npsn = "10103214",
            address = "Kerinci, Jambi",
            kepalaSekolahName = "H. Ahmad S.Pd., M.Pd.",
            bendaharaName = "Siti Rahma S.Pd."
        )

        val sources = listOf(
            FundSourceModel("BOS Reguler", isSecret = false),
            FundSourceModel("Dana Taktis", isSecret = true)
        )

        val summary = BackupSummary(
            totalTransactions = 2,
            totalIncome = 45000000.0,
            totalExpense = 500000.0,
            balance = 44500000.0,
            exportDateFormatted = "12 November 2026 15:30:00"
        )

        val originalBackup = AppBackupData(
            version = 2,
            timestamp = 1731400000000L,
            appVersion = "7.0",
            schoolProfile = profile,
            transactions = transactions,
            customFundSources = sources,
            summary = summary
        )

        // 1. Serialize to JSON string
        val json = adapter.toJson(originalBackup)
        assertNotNull(json)
        assertTrue(json.contains("SD Negeri 33/III Air Tenang"))
        assertTrue(json.contains("Penerimaan BOS Reguler Tahap 1"))
        assertTrue(json.contains("Dana Taktis"))
        assertTrue(json.contains("44500000"))

        // 2. Deserialize back from JSON
        val restoredBackup = adapter.fromJson(json)
        assertNotNull(restoredBackup)
        assertEquals(2, restoredBackup!!.version)
        assertEquals("7.0", restoredBackup.appVersion)
        assertEquals("10103214", restoredBackup.schoolProfile.npsn)
        assertEquals(2, restoredBackup.transactions.size)
        assertEquals("Penerimaan BOS Reguler Tahap 1", restoredBackup.transactions[0].title)
        assertEquals(45000000.0, restoredBackup.transactions[0].amount, 0.0)
        assertEquals("rim", restoredBackup.transactions[1].unitName)
        assertEquals(2, restoredBackup.customFundSources.size)
        assertEquals(44500000.0, restoredBackup.summary?.balance ?: 0.0, 0.0)
    }

    @Test
    fun testLegacyBackup_BackwardCompatibility() {
        // Simulating legacy JSON from version 1 without summary or customFundSources
        val legacyJson = """
            {
              "version": 1,
              "timestamp": 1700000000000,
              "schoolProfile": {
                "schoolName": "SD Negeri 33/III Air Tenang",
                "npsn": "10103214",
                "address": "Kerinci, Jambi",
                "kepalaSekolahName": "H. Ahmad S.Pd.",
                "kepalaSekolahNip": "197203151998031002",
                "bendaharaName": "Siti Rahma S.Pd.",
                "bendaharaNip": "198507202010012015"
              },
              "transactions": [
                {
                  "id": 1,
                  "title": "BOS Tahap 1",
                  "amount": 20000000.0,
                  "type": "PEMASUKAN",
                  "fundSource": "BOS Reguler"
                }
              ]
            }
        """.trimIndent()

        val parsed = adapter.fromJson(legacyJson)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.version)
        assertEquals(1, parsed.transactions.size)
        assertEquals("BOS Tahap 1", parsed.transactions[0].title)
        assertEquals("buah", parsed.transactions[0].unitName) // Default value preserved
        assertTrue(parsed.customFundSources.isEmpty())
    }
}
