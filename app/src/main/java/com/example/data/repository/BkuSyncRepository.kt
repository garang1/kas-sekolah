package com.example.data.repository

import android.content.Context
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.StateFlow

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncState()
    data class Error(val errorMessage: String) : SyncState()
}

/**
 * Interface for Google Sheets Export and Web App Syncing of BKU (Buku Kas Umum) transactions.
 */
interface BkuSyncRepository {
    val syncState: StateFlow<SyncState>

    /**
     * Exports local BKU transactions as a formatted CSV file for Google Sheets / Excel.
     */
    suspend fun exportToCsv(context: Context, transactions: List<TransactionEntity>): Result<String>

    /**
     * Uploads/Syncs all local Room BKU transactions to Google Sheets via Web App Endpoint.
     */
    suspend fun syncToGoogleSheets(webAppUrl: String, transactions: List<TransactionEntity>): Result<Int>

    /**
     * Fetches BKU transactions from Google Sheets Web App Endpoint and merges into local Room DB.
     */
    suspend fun fetchFromGoogleSheets(webAppUrl: String): Result<List<TransactionEntity>>
}
