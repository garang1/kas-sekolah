package com.example.data.repository

import com.example.data.dao.TransactionDao
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    fun getPendingApprovalTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByStatus("PENDING_APPROVAL")
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long> {
        return transactionDao.insertTransactions(transactions)
    }

    suspend fun getAllTransactionsSync(): List<TransactionEntity> {
        return transactionDao.getAllTransactionsSync()
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun updateApprovalStatus(id: Int, status: String) {
        transactionDao.updateApprovalStatus(id, status)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteAllTransactions() {
        transactionDao.deleteAllTransactions()
    }

    fun getTotalIncomeByFundSource(fundSource: String): Flow<Double?> {
        return transactionDao.getTotalIncomeByFundSource(fundSource)
    }

    fun getTotalExpenseByFundSource(fundSource: String): Flow<Double?> {
        return transactionDao.getTotalExpenseByFundSource(fundSource)
    }

    fun getTransactionsPaged(
        fundSource: String,
        searchQuery: String,
        limit: Int,
        offset: Int
    ): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsPaged(fundSource, searchQuery, limit, offset)
    }
}
