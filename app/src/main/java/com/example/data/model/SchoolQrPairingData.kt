package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SchoolQrPairingData(
    val npsn: String,
    val schoolName: String,
    val pairingKey: String,
    val kepalaSekolahName: String = "",
    val bendaharaName: String = "",
    val relayUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val appTag: String = "SIMPAS_BKU"
)
