package com.example.util

import com.example.network.ParsedTransactionResult
import java.util.regex.Pattern

object VoiceTransactionParser {

    private val indonesianNumberWords = mapOf(
        "setengah" to 0.5,
        "satu" to 1.0,
        "sebuah" to 1.0,
        "sebiji" to 1.0,
        "selembar" to 1.0,
        "serim" to 1.0,
        "sebotol" to 1.0,
        "sekotak" to 1.0,
        "seorang" to 1.0,
        "sepaket" to 1.0,
        "sebulan" to 1.0,
        "setahun" to 1.0,
        "dua" to 2.0,
        "tiga" to 3.0,
        "empat" to 4.0,
        "lima" to 5.0,
        "enam" to 6.0,
        "tujuh" to 7.0,
        "delapan" to 8.0,
        "sembilan" to 9.0,
        "sepuluh" to 10.0,
        "sebelas" to 11.0,
        "dua belas" to 12.0,
        "tiga belas" to 13.0,
        "empat belas" to 14.0,
        "lima belas" to 15.0,
        "dua puluh" to 20.0,
        "dua puluh lima" to 25.0,
        "tiga puluh" to 30.0,
        "empat puluh" to 40.0,
        "lima puluh" to 50.0,
        "seratus" to 100.0,
        "dua ratus" to 200.0,
        "lima ratus" to 500.0
    )

    private val commonUnits = listOf(
        "rim", "buah", "biji", "kotak", "box", "dus", "kardus", "lembar", "lbr",
        "paket", "set", "lusin", "siswa", "orang", "anak", "peserta", "guru",
        "bulan", "hari", "kegiatan", "botol", "kaleng", "galon", "meter", "batang",
        "roll", "rol", "buku", "jilid", "porsi", "unit", "pcs", "tahap", "triwulan"
    )

    fun parse(text: String, availableFundSources: List<String> = emptyList()): ParsedTransactionResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ParsedTransactionResult(
                title = "",
                amount = 0.0,
                type = "PENGELUARAN",
                fundSource = availableFundSources.firstOrNull() ?: "BOS Reguler",
                category = "Umum",
                notes = "",
                volume = 1.0,
                unitName = "buah",
                unitPrice = 0.0,
                rawSpokenText = ""
            )
        }

        // Normalisasi kata bilangan bahasa Indonesia ke angka terstruktur
        val normalizedText = normalizeSpokenNumberWords(trimmed)
        val lower = normalizedText.lowercase()

        // 1. Deteksi Jenis Kas (Pemasukan vs Pengeluaran)
        val hasExplicitExpense = lower.contains("dana keluar") ||
                lower.contains("pengeluaran") ||
                lower.contains("belanja") ||
                lower.contains("beli ") ||
                lower.startsWith("beli") ||
                lower.contains("bayar") ||
                lower.contains("ongkos") ||
                lower.contains("konsumsi") ||
                lower.contains("honor") ||
                lower.contains("perbaikan") ||
                lower.contains("servis") ||
                lower.contains("service") ||
                lower.contains("fotocopy") ||
                lower.contains("foto copy") ||
                lower.contains("langganan")

        val hasExplicitIncome = lower.contains("dana masuk") ||
                lower.contains("pemasukan") ||
                lower.contains("penerimaan") ||
                lower.contains("terima dana") ||
                lower.contains("terima kas") ||
                lower.contains("dapat transfer") ||
                lower.contains("transfer masuk") ||
                lower.contains("cair dana") ||
                lower.contains("pencairan") ||
                lower.contains("hibah") ||
                lower.contains("sumbangan") ||
                lower.contains("saldo awal") ||
                lower.contains("tarik tunai")

        val isIncome = when {
            lower.startsWith("pemasukan") || lower.startsWith("dana masuk") || lower.startsWith("penerimaan") || lower.startsWith("terima") -> true
            lower.startsWith("pengeluaran") || lower.startsWith("dana keluar") || lower.startsWith("beli") || lower.startsWith("belanja") || lower.startsWith("bayar") -> false
            hasExplicitExpense && !hasExplicitIncome -> false
            hasExplicitIncome && !hasExplicitExpense -> true
            else -> hasExplicitIncome
        }
        val type = if (isIncome) "PEMASUKAN" else "PENGELUARAN"

        // 2. Deteksi Sumber Dana
        val fundSource = detectFundSource(lower, availableFundSources)

        // 3. Deteksi Satuan & Volume
        val (detectedVolume, detectedUnit) = detectVolumeAndUnit(lower, isIncome)

        // 4. Deteksi Harga Satuan dan/atau Total Nominal
        val (unitPrice, totalAmount) = detectPrices(lower, detectedVolume)

        // 5. Ekstraksi Judul / Uraian Transaksi yang Bersih
        val cleanTitle = extractCleanTitle(normalizedText, lower, detectedUnit, fundSource, type)

        return ParsedTransactionResult(
            title = cleanTitle,
            amount = totalAmount,
            type = type,
            fundSource = fundSource,
            category = "Umum",
            notes = "Diinput via Suara Android • $trimmed",
            volume = detectedVolume,
            unitName = detectedUnit,
            unitPrice = unitPrice,
            rawSpokenText = trimmed
        )
    }

    /**
     * Mengubah kata-kata bilangan lisan Indonesia ke angka agar mudah dianalisis secara akurat.
     */
    private fun normalizeSpokenNumberWords(input: String): String {
        var s = input

        // Angka nominal jutaan
        s = s.replace("(?i)\\bdua puluh lima juta\\b".toRegex(), "25 juta")
        s = s.replace("(?i)\\btiga puluh lima juta\\b".toRegex(), "35 juta")
        s = s.replace("(?i)\\btiga puluh juta\\b".toRegex(), "30 juta")
        s = s.replace("(?i)\\bdua puluh juta\\b".toRegex(), "20 juta")
        s = s.replace("(?i)\\blima belas juta\\b".toRegex(), "15 juta")
        s = s.replace("(?i)\\bsepuluh juta\\b".toRegex(), "10 juta")
        s = s.replace("(?i)\\blima juta\\b".toRegex(), "5 juta")
        s = s.replace("(?i)\\bempat juta\\b".toRegex(), "4 juta")
        s = s.replace("(?i)\\btiga juta\\b".toRegex(), "3 juta")
        s = s.replace("(?i)\\bdua juta\\b".toRegex(), "2 juta")
        s = s.replace("(?i)\\bsatu juta\\b".toRegex(), "1 juta")
        s = s.replace("(?i)\\bsetengah juta\\b".toRegex(), "500 ribu")

        // Angka nominal ribuan
        s = s.replace("(?i)\\bseratus lima puluh ribu\\b".toRegex(), "150 ribu")
        s = s.replace("(?i)\\bdua ratus lima puluh ribu\\b".toRegex(), "250 ribu")
        s = s.replace("(?i)\\btujuh puluh lima ribu\\b".toRegex(), "75 ribu")
        s = s.replace("(?i)\\bdua puluh lima ribu\\b".toRegex(), "25 ribu")
        s = s.replace("(?i)\\blima puluh ribu\\b".toRegex(), "50 ribu")
        s = s.replace("(?i)\\bempat puluh ribu\\b".toRegex(), "40 ribu")
        s = s.replace("(?i)\\btiga puluh ribu\\b".toRegex(), "30 ribu")
        s = s.replace("(?i)\\bdua puluh ribu\\b".toRegex(), "20 ribu")
        s = s.replace("(?i)\\blima belas ribu\\b".toRegex(), "15 ribu")
        s = s.replace("(?i)\\bsepuluh ribu\\b".toRegex(), "10 ribu")
        s = s.replace("(?i)\\bsembilan ribu\\b".toRegex(), "9 ribu")
        s = s.replace("(?i)\\bdelapan ribu lima ratus\\b".toRegex(), "8500")
        s = s.replace("(?i)\\bdelapan ribu\\b".toRegex(), "8 ribu")
        s = s.replace("(?i)\\btujuh ribu\\b".toRegex(), "7 ribu")
        s = s.replace("(?i)\\benam ribu\\b".toRegex(), "6 ribu")
        s = s.replace("(?i)\\blima ribu\\b".toRegex(), "5 ribu")
        s = s.replace("(?i)\\bempat ribu\\b".toRegex(), "4 ribu")
        s = s.replace("(?i)\\btiga ribu\\b".toRegex(), "3 ribu")
        s = s.replace("(?i)\\bdua ribu\\b".toRegex(), "2 ribu")
        s = s.replace("(?i)\\bseribu\\b".toRegex(), "1 ribu")

        s = s.replace("(?i)\\blima ratus ribu\\b".toRegex(), "500 ribu")
        s = s.replace("(?i)\\bempat ratus ribu\\b".toRegex(), "400 ribu")
        s = s.replace("(?i)\\btiga ratus ribu\\b".toRegex(), "300 ribu")
        s = s.replace("(?i)\\bdua ratus ribu\\b".toRegex(), "200 ribu")
        s = s.replace("(?i)\\bseratus ribu\\b".toRegex(), "100 ribu")

        // Tahap termin
        s = s.replace("(?i)\\btahap satu\\b".toRegex(), "tahap 1")
        s = s.replace("(?i)\\btahap dua\\b".toRegex(), "tahap 2")
        s = s.replace("(?i)\\btahap tiga\\b".toRegex(), "tahap 3")
        s = s.replace("(?i)\\btahap empat\\b".toRegex(), "tahap 4")

        return s
    }

    private fun detectFundSource(lowerText: String, availableFundSources: List<String>): String {
        // Cek kecocokan langsung dengan nama sumber dana yang ada di aplikasi
        for (source in availableFundSources) {
            val sLower = source.lowercase()
            if (sLower != "semua" && lowerText.contains(sLower)) {
                return source
            }
        }

        return when {
            lowerText.contains("kinerja") || lowerText.contains("boskin") || lowerText.contains("prestasi") -> {
                availableFundSources.firstOrNull { it.contains("kinerja", ignoreCase = true) } ?: "BOS Kinerja"
            }
            lowerText.contains("komite") || lowerText.contains("paguyuban") || lowerText.contains("wali murid") -> {
                availableFundSources.firstOrNull { it.contains("komite", ignoreCase = true) } ?: "Dana Komite"
            }
            lowerText.contains("hibah") || lowerText.contains("bantuan") || lowerText.contains("provinsi") || lowerText.contains("dak") -> {
                availableFundSources.firstOrNull { it.contains("hibah", ignoreCase = true) } ?: "Hibah / Lainnya"
            }
            lowerText.contains("bosda") -> {
                availableFundSources.firstOrNull { it.contains("bosda", ignoreCase = true) } ?: "BOS Reguler"
            }
            else -> {
                availableFundSources.firstOrNull { it.contains("reguler", ignoreCase = true) }
                    ?: availableFundSources.firstOrNull()
                    ?: "BOS Reguler"
            }
        }
    }

    private fun detectVolumeAndUnit(lowerText: String, isIncome: Boolean): Pair<Double, String> {
        var volume = 1.0
        var unit = if (isIncome) "tahap" else "buah"

        // Pola 1: Awalan se- (contoh: serim, sekotak, selembar, sebotol, seorang, sebuah)
        for (u in commonUnits) {
            if (lowerText.contains("se$u")) {
                return Pair(1.0, u)
            }
        }

        // Pola 2: Angka diikuti satuan (contoh: "5 rim", "10 buah", "2.5 meter", "12 kotak")
        val unitAlternation = commonUnits.joinToString("|")
        val numUnitPattern = Pattern.compile("(\\d+([.,]\\d+)?)\\s*($unitAlternation)\\b")
        val numUnitMatcher = numUnitPattern.matcher(lowerText)
        if (numUnitMatcher.find()) {
            val numStr = numUnitMatcher.group(1)?.replace(",", ".") ?: "1"
            val matchedUnit = numUnitMatcher.group(3) ?: unit
            val parsedVol = numStr.toDoubleOrNull() ?: 1.0
            return Pair(parsedVol, normalizeUnit(matchedUnit))
        }

        // Pola 3: Kata bilangan Bahasa Indonesia diikuti satuan (contoh: "lima rim", "dua belas buah")
        for ((word, numVal) in indonesianNumberWords) {
            for (u in commonUnits) {
                if (lowerText.contains("$word $u")) {
                    return Pair(numVal, normalizeUnit(u))
                }
            }
        }

        // Pola 4: Hanya satuan tanpa angka eksplisit sebelum satuan (contoh: "kertas hvs rim" -> 1 rim)
        for (u in commonUnits) {
            if (lowerText.contains(" $u ") || lowerText.endsWith(" $u")) {
                unit = normalizeUnit(u)
                break
            }
        }

        // Pola 5: Jumlah/Volume angka eksplisit (contoh: "sebanyak 5", "jumlah 10")
        val qtyPattern = Pattern.compile("(?:sebanyak|jumlah|volume)\\s*(\\d+([.,]\\d+)?)")
        val qtyMatcher = qtyPattern.matcher(lowerText)
        if (qtyMatcher.find()) {
            val parsedVol = qtyMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull()
            if (parsedVol != null && parsedVol > 0) {
                volume = parsedVol
            }
        }

        return Pair(volume, unit)
    }

    private fun normalizeUnit(u: String): String {
        return when (u) {
            "lbr" -> "lembar"
            "dus", "kardus", "box" -> "kotak"
            "biji", "pcs", "unit" -> "buah"
            "rol" -> "roll"
            "jilid" -> "buku"
            else -> u
        }
    }

    private fun detectPrices(lowerText: String, volume: Double): Pair<Double, Double> {
        var explicitUnitPrice: Double? = null
        var explicitTotal: Double? = null

        // 1. Cek apakah ada frasa "harga satuan / harga per / per <satuan> / seharga"
        val unitPricePattern = Pattern.compile("(?:harga(?:\\s*satuan|\\s*per\\s*\\w+)?|seharga)\\s*(?:rp\\.?\\s*)?(\\d+([.,]\\d+)?)\\s*(juta|jt|ribu|rb|k)?")
        val unitPriceMatcher = unitPricePattern.matcher(lowerText)
        if (unitPriceMatcher.find()) {
            val numStr = unitPriceMatcher.group(1)?.replace(",", ".") ?: "0"
            val multiplierStr = unitPriceMatcher.group(3)
            val baseVal = numStr.toDoubleOrNull() ?: 0.0
            val multiplier = when (multiplierStr) {
                "juta", "jt" -> 1000000.0
                "ribu", "rb", "k" -> 1000.0
                else -> if (baseVal < 1000 && baseVal > 0) 1000.0 else 1.0
            }
            explicitUnitPrice = baseVal * multiplier
        }

        // 2. Cek apakah ada frasa "total / sebesar / nominal"
        val totalPattern = Pattern.compile("(?:total(?:\\s*sebesar)?|sebesar|nominal)\\s*(?:rp\\.?\\s*)?(\\d+([.,]\\d+)?)\\s*(juta|jt|ribu|rb|k)?")
        val totalMatcher = totalPattern.matcher(lowerText)
        if (totalMatcher.find()) {
            val numStr = totalMatcher.group(1)?.replace(",", ".") ?: "0"
            val multiplierStr = totalMatcher.group(3)
            val baseVal = numStr.toDoubleOrNull() ?: 0.0
            val multiplier = when (multiplierStr) {
                "juta", "jt" -> 1000000.0
                "ribu", "rb", "k" -> 1000.0
                else -> if (baseVal < 1000 && baseVal > 0) 1000.0 else 1.0
            }
            explicitTotal = baseVal * multiplier
        }

        // 3. Jika belum ditemukan harga, cari pola nominal angka umum (juta, ribu, atau angka bulat)
        if (explicitUnitPrice == null && explicitTotal == null) {
            val jutaPattern = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(?:juta|jt)")
            val jutaMatcher = jutaPattern.matcher(lowerText)
            if (jutaMatcher.find()) {
                val numStr = jutaMatcher.group(1)?.replace(",", ".") ?: "1"
                val amount = (numStr.toDoubleOrNull() ?: 1.0) * 1000000.0
                explicitTotal = amount
            } else {
                val ribuPattern = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(?:ribu|rb|k)")
                val ribuMatcher = ribuPattern.matcher(lowerText)
                if (ribuMatcher.find()) {
                    val numStr = ribuMatcher.group(1)?.replace(",", ".") ?: "100"
                    val amount = (numStr.toDoubleOrNull() ?: 100.0) * 1000.0
                    explicitTotal = amount
                } else {
                    val rawDigitsPattern = Pattern.compile("\\b(\\d{4,10})\\b")
                    val digitsMatcher = rawDigitsPattern.matcher(lowerText)
                    if (digitsMatcher.find()) {
                        val amount = digitsMatcher.group(1)?.toDoubleOrNull() ?: 50000.0
                        explicitTotal = amount
                    }
                }
            }
        }

        val finalUnitPrice: Double
        val finalTotal: Double

        val vol = if (volume > 0) volume else 1.0

        if (explicitUnitPrice != null && explicitUnitPrice > 0) {
            finalUnitPrice = explicitUnitPrice
            finalTotal = explicitUnitPrice * vol
        } else if (explicitTotal != null && explicitTotal > 0) {
            if (vol > 1.0) {
                // Jika total disebut dan volume > 1, periksa apakah nominal wajar sebagai harga satuan atau total
                // Misalnya: "Beli 5 rim harga 50 ribu" -> 50 ribu adalah harga satuan
                if (lowerText.contains("harga")) {
                    finalUnitPrice = explicitTotal
                    finalTotal = explicitTotal * vol
                } else {
                    finalTotal = explicitTotal
                    finalUnitPrice = (explicitTotal / vol).let { Math.round(it).toDouble() }
                }
            } else {
                finalUnitPrice = explicitTotal
                finalTotal = explicitTotal
            }
        } else {
            finalUnitPrice = 50000.0
            finalTotal = finalUnitPrice * vol
        }

        return Pair(finalUnitPrice, finalTotal)
    }

    private fun extractCleanTitle(
        originalText: String,
        lowerText: String,
        unit: String,
        fundSource: String,
        type: String
    ): String {
        var cleaned = originalText

        // Hapus frasa pembuka perintah / jenis dana
        cleaned = cleaned.replace("(?i)^(?:dana\\s*(?:masuk|keluar)|pemasukan|pengeluaran|tolong\\s+catatkan?|catatkan?|masukkan?|input(?:kan)?)\\s+".toRegex(), "")

        // Hapus keterangan dana di akhir (contoh: "dari BOS Reguler", "sumber BOS Kinerja", "dana komite")
        cleaned = cleaned.replace("(?i)\\s*(?:dari|sumber|dana|pos)?\\s*(?:bos\\s*(?:reguler|kinerja|da)?|dana\\s*komite|hibah|lainnya).*$".toRegex(), "")

        // Hapus frasa harga di belakang (contoh: "harga 50 ribu per rim", "seharga 100rb", "sebesar 25 juta")
        cleaned = cleaned.replace("(?i)\\s*(?:harga(?:\\s*satuan|\\s*per\\s*\\w+)?|seharga|sebesar|total|nominal)\\s*(?:rp\\.?\\s*)?\\d+.*$".toRegex(), "")

        // Hapus penyebutan nominal yang berdiri sendiri di akhir (contoh: "50 ribu", "25 jt", "100000")
        cleaned = cleaned.replace("(?i)\\s*\\d+([.,]\\d+)?\\s*(?:juta|jt|ribu|rb|k|rupiah)\\b.*$".toRegex(), "")
        cleaned = cleaned.replace("(?i)\\s*rp\\.?\\s*\\d+.*$".toRegex(), "")

        // Hapus penyebutan volume dan satuan jika ada di akhir (contoh: "5 rim", "10 buah")
        cleaned = cleaned.replace("(?i)\\s*\\d+([.,]\\d+)?\\s*$unit\\b.*$".toRegex(), "")

        cleaned = cleaned.trim().replace("\\s+".toRegex(), " ")

        // Format huruf kapital kata
        if (cleaned.length > 2) {
            val words = cleaned.split(" ")
            return words.joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        return if (type == "PEMASUKAN") "Penerimaan Kas $fundSource" else "Pengeluaran Kas Belanja"
    }
}
