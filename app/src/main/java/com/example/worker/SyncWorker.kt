package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.repository.GoogleSheetsSyncRepository
import com.example.util.AppNotificationHelper
import kotlinx.coroutines.flow.first

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = context.getSharedPreferences("bku_settings", Context.MODE_PRIVATE)
            val url = prefs.getString("google_sheets_url", "") ?: ""
            val activeRole = prefs.getString("active_role", "") ?: ""
            val deviceLock = prefs.getString("device_role_lock", "ALL") ?: "ALL"
            
            // Only notify if current device is meant for KEPALA_SEKOLAH (or ALL but active is KEPALA_SEKOLAH)
            val isKepsekDevice = deviceLock == "KEPALA_SEKOLAH" || (deviceLock == "ALL" && activeRole == "KEPALA_SEKOLAH")
            
            if (url.isBlank() || !url.contains("script.google.com")) {
                return Result.success()
            }

            val database = AppDatabase.getDatabase(context, null) // Needs scope, passing null is fine if we don't trigger prepopulate
            val dao = database.transactionDao()
            val repo = GoogleSheetsSyncRepository(dao)

            val fetchResult = repo.fetchDetailedFromGoogleSheets(url)
            val fetchedData = fetchResult.getOrNull()

            if (fetchedData != null && fetchedData.transactions.isNotEmpty()) {
                val oldTransactions = dao.getAllTransactions().first()
                val oldIds = oldTransactions.map { it.id }.toSet()
                
                val newTxs = fetchedData.transactions
                
                val newlyAdded = newTxs.filter { 
                    it.recordedByRole.contains("Bendahara", ignoreCase = true) &&
                    !oldIds.contains(it.id)
                }

                if (newlyAdded.isNotEmpty() && isKepsekDevice) {
                    val latest = newlyAdded.maxByOrNull { it.date } ?: newlyAdded.last()
                    AppNotificationHelper.showTransactionNotification(
                        context = context,
                        type = latest.type,
                        title = latest.title,
                        amount = latest.amount,
                        fundSource = latest.fundSource,
                        recordedBy = latest.recordedByRole
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
