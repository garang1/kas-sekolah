package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EncryptedMailboxPackage(
    val schoolNpsn: String,
    val senderRole: String, // "BENDAHARA" or "KEPALA_SEKOLAH"
    val senderName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val encryptedPayloadBase64: String,
    val ivBase64: String,
    val packageHash: String = "",
    val dataVersion: Int = 2
)

@JsonClass(generateAdapter = true)
data class DecryptedMailboxPayload(
    val schoolNpsn: String,
    val transactions: List<TransactionPayloadItem>,
    val fundSources: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val senderRole: String = "BENDAHARA",
    val actionType: String = "FULL_SYNC" // "NEW_TRANSACTION", "APPROVAL_UPDATE", "FULL_SYNC"
)

@JsonClass(generateAdapter = true)
data class TransactionPayloadItem(
    val remoteId: String, // String identifier or ID
    val title: String,
    val amount: Double,
    val type: String,
    val fundSource: String,
    val category: String = "Umum",
    val volume: Double = 1.0,
    val unitName: String = "buah",
    val unitPrice: Double = 0.0,
    val date: Long,
    val notes: String = "",
    val receiptUri: String? = null,
    val approvalStatus: String = "VERIFIED",
    val recordedByRole: String = "Bendahara",
    val createdAt: Long = System.currentTimeMillis()
)
