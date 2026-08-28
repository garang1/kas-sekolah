package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppBackupData(
    val version: Int = 1,
    val timestamp: Long,
    val schoolProfile: SchoolProfile,
    val transactions: List<TransactionEntity>
)
