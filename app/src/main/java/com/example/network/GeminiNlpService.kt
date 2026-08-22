package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class ParsedTransactionResult(
    val title: String,
    val amount: Double,
    val type: String, // "PENGELUARAN" or "PEMASUKAN"
    val fundSource: String,
    val category: String,
    val notes: String
)

/**
 * Smart Local NLP Parser for Voice/Text input.
 * Operates 100% offline on-device without requiring any API keys or network connection.
 */
class GeminiNlpService {

    suspend fun parseVoiceOrTextPrompt(inputText: String): ParsedTransactionResult = withContext(Dispatchers.Default) {
        parseLocally(inputText)
    }

    private fun parseLocally(text: String): ParsedTransactionResult {
        val lowerText = text.lowercase().trim()

        // 1. Type Detection
        val isIncome = lowerText.contains("terima") || 
                       lowerText.contains("pencairan") || 
                       lowerText.contains("masuk") || 
                       lowerText.contains("pemasukan") || 
                       lowerText.contains("hibah") || 
                       lowerText.contains("sumbangan") ||
                       lowerText.contains("tarik tunai") ||
                       lowerText.contains("saldo awal")
        val type = if (isIncome) "PEMASUKAN" else "PENGELUARAN"

        // 2. Fund Source Detection
        val fundSource = when {
            lowerText.contains("kinerja") || lowerText.contains("boskin") -> "BOS Kinerja"
            lowerText.contains("komite") || lowerText.contains("paguyuban") -> "Dana Komite"
            lowerText.contains("hibah") || lowerText.contains("bantuan") -> "Hibah / Lainnya"
            else -> "BOS Reguler"
        }

        // 3. Nominal parsing (e.g., 250 ribu, 1.5 juta, 500000)
        var amount = 100000.0

        val jutaPattern = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(juta|jt)")
        val jutaMatcher = jutaPattern.matcher(lowerText)
        if (jutaMatcher.find()) {
            val numStr = jutaMatcher.group(1)?.replace(",", ".") ?: "1"
            amount = (numStr.toDoubleOrNull() ?: 1.0) * 1000000.0
        } else {
            val ribuPattern = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(ribu|rb|k)")
            val ribuMatcher = ribuPattern.matcher(lowerText)
            if (ribuMatcher.find()) {
                val numStr = ribuMatcher.group(1)?.replace(",", ".") ?: "100"
                amount = (numStr.toDoubleOrNull() ?: 100.0) * 1000.0
            } else {
                val rawDigitsPattern = Pattern.compile("\\b(\\d{4,9})\\b")
                val digitsMatcher = rawDigitsPattern.matcher(lowerText)
                if (digitsMatcher.find()) {
                    amount = digitsMatcher.group(1)?.toDoubleOrNull() ?: 100000.0
                }
            }
        }

        val title = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        return ParsedTransactionResult(
            title = title,
            amount = amount,
            type = type,
            fundSource = fundSource,
            category = "Umum",
            notes = "Otomatis dianalisis lokal (Smart On-Device Input)"
        )
    }
}
