package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

object VoiceInputHelper {

    /**
     * Mengecek apakah layanan Speech Recognition Android tersedia di perangkat.
     */
    fun isSpeechRecognitionAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context) ||
                context.packageManager.queryIntentActivities(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                    PackageManager.MATCH_DEFAULT_ONLY
                ).isNotEmpty()
    }

    /**
     * Membuat Intent pengenalan suara Android standar dengan konfigurasi Bahasa Indonesia (id-ID).
     */
    fun createSpeechIntent(
        prompt: String = "Sebutkan transaksi kas (contoh: 'Beli kertas HVS 5 rim harga 50 ribu per rim dari BOS Reguler')"
    ): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    /**
     * Mengambil teks hasil transkripsi suara dari Intent result.
     */
    fun extractSpokenText(data: Intent?): String? {
        if (data == null) return null
        val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return results?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Contoh-contoh kalimat suara yang lazim diucapkan oleh Bendahara Sekolah.
     */
    val samplePrompts = listOf(
        "Beli kertas HVS 5 rim harga 50 ribu per rim dari BOS Reguler",
        "Pembelian spidol papan tulis 12 buah harga 8500 dari BOS Kinerja",
        "Konsumsi rapat guru 25 kotak harga 20 ribu rupiah",
        "Penerimaan dana BOS Reguler tahap 1 25 juta",
        "Fotocopy modul ujian 500 lembar harga 300 rupiah",
        "Bayar langganan internet bulanan 1 bulan 350 ribu",
        "Beli tinta printer epson 4 botol harga 95 ribu",
        "Perbaikan printer kantor 150 ribu dana komite"
    )
}
