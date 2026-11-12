package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupSummary(
    val totalTransactions: Int = 0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val exportDateFormatted: String = ""
)

@JsonClass(generateAdapter = true)
data class AppBackupData(
    val version: Int = 2,
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "7.0",
    val schoolProfile: SchoolProfile,
    val transactions: List<TransactionEntity>,
    val customFundSources: List<FundSourceModel> = emptyList(),
    val summary: BackupSummary? = null
)
