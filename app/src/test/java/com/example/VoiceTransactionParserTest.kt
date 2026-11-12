package com.example

import com.example.util.VoiceTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTransactionParserTest {

    private val sampleFundSources = listOf("BOS Reguler", "BOS Kinerja", "Dana Komite", "Hibah / Lainnya", "BOSDA Provinsi")

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
    fun testDanaKeluarPrefix() {
        val input = "Dana keluar belanja perlengkapan kebersihan 4 botol harga 25 ribu dari BOS Reguler"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PENGELUARAN", result.type)
        assertEquals("BOS Reguler", result.fundSource)
        assertEquals(4.0, result.volume, 0.001)
        assertEquals("botol", result.unitName)
        assertEquals(25000.0, result.unitPrice, 0.001)
        assertEquals(100000.0, result.amount, 0.001)
        assertTrue(result.title.contains("Perlengkapan Kebersihan", ignoreCase = true))
    }

    @Test
    fun testDanaMasukPrefix() {
        val input = "Dana masuk sumbangan paguyuban komite 3 juta"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PEMASUKAN", result.type)
        assertEquals("Dana Komite", result.fundSource)
        assertEquals(3000000.0, result.amount, 0.001)
    }

    @Test
    fun testCustomFundSourceMatching() {
        val input = "Beli buku teks panduan guru 10 buku harga 65 ribu dari BOSDA Provinsi"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PENGELUARAN", result.type)
        assertEquals("BOSDA Provinsi", result.fundSource)
        assertEquals(10.0, result.volume, 0.001)
        assertEquals("buku", result.unitName)
        assertEquals(65000.0, result.unitPrice, 0.001)
        assertEquals(650000.0, result.amount, 0.001)
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

    @Test
    fun testPhotocopyExamPapers() {
        val input = "Foto copy naskah ujian semester 500 lembar harga 300 rupiah"
        val result = VoiceTransactionParser.parse(input, sampleFundSources)

        assertEquals("PENGELUARAN", result.type)
        assertEquals(500.0, result.volume, 0.001)
        assertEquals("lembar", result.unitName)
        assertEquals(300.0, result.unitPrice, 0.001)
        assertEquals(150000.0, result.amount, 0.001)
    }
}
