package com.example.network

import com.example.util.VoiceTransactionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ParsedTransactionResult(
    val title: String,
    val amount: Double,
    val type: String, // "PENGELUARAN" or "PEMASUKAN"
    val fundSource: String,
    val category: String = "Umum",
    val notes: String = "",
    val volume: Double = 1.0,
    val unitName: String = "buah",
    val unitPrice: Double = 0.0,
    val rawSpokenText: String = ""
)

/**
 * Smart Local NLP Parser for Voice/Text input.
 * Operates 100% offline on-device without requiring any API keys or network connection.
 * Integrates with Android Speech Recognition API and Indonesian grammar detection.
 */
class GeminiNlpService {

    suspend fun parseVoiceOrTextPrompt(
        inputText: String,
        availableFundSources: List<String> = emptyList()
    ): ParsedTransactionResult = withContext(Dispatchers.Default) {
        VoiceTransactionParser.parse(inputText, availableFundSources)
    }

    fun parseLocally(
        text: String,
        availableFundSources: List<String> = emptyList()
    ): ParsedTransactionResult {
        return VoiceTransactionParser.parse(text, availableFundSources)
    }
}
