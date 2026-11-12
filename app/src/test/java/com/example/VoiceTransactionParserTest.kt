package com.example

import com.example.util.VoiceTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTransactionParserTest {

    private val sampleFundSources = listOf("BOS Reguler", "BOS Kinerja", "Dana Komite", "Hibah / Lainnya")

    @Test
    fun testParsePurchaseWithVolumeAndUnitPrice() {
        val input = "Beli kertas HVS 5 rim harga 50 ribu per rim dari BOS Reguler"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PENGELUARAN", result.type)
        assertEquals("BOS Reguler", result.fundSource)
        assertEquals(5.0, result.volume, 0.001)
        assertEquals("rim", result.unitName)
        assertEquals(50000.0, result.unitPrice, 0.001)
        assertEquals(250000.0, result.amount, 0.001)
        assertTrue(result.title.contains("Kertas Hvs", ignoreCase = true))
    }

    @Test
    fun testParsePurchaseWithUnitPriceDigits() {
        val input = "Pembelian spidol papan tulis 12 buah harga 8500 dari BOS Kinerja"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PENGELUARAN", result.type)
        assertEquals("BOS Kinerja", result.fundSource)
        assertEquals(12.0, result.volume, 0.001)
        assertEquals("buah", result.unitName)
        assertEquals(8500.0, result.unitPrice, 0.001)
        assertEquals(102000.0, result.amount, 0.001)
        assertTrue(result.title.contains("Spidol", ignoreCase = true))
    }

    @Test
    fun testParseIncome() {
        val input = "Penerimaan dana BOS Reguler tahap 1 25 juta"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PEMASUKAN", result.type)
        assertEquals("BOS Reguler", result.fundSource)
        assertEquals(25000000.0, result.amount, 0.001)
        assertTrue(result.title.contains("Penerimaan", ignoreCase = true))
    }

    @Test
    fun testParseSimpleExpense() {
        val input = "Perbaikan printer kantor 150 ribu dana komite"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PENGELUARAN", result.type)
        assertEquals("Dana Komite", result.fundSource)
        assertEquals(150000.0, result.amount, 0.001)
        assertEquals(1.0, result.volume, 0.001)
    }

    @Test
    fun testParseConsumptionBoxes() {
        val input = "Konsumsi rapat guru 25 kotak harga 20 ribu"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PENGELUARAN", result.type)
        assertEquals(25.0, result.volume, 0.001)
        assertEquals("kotak", result.unitName)
        assertEquals(20000.0, result.unitPrice, 0.001)
        assertEquals(500000.0, result.amount, 0.001)
    }
}
