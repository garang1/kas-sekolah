package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE approvalStatus = :status ORDER BY date DESC")
    fun getTransactionsByStatus(status: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE fundSource = :fundSource ORDER BY date DESC")
    fun getTransactionsByFundSource(fundSource: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE fundSource = :fundSource
        AND (:searchQuery = '' OR title LIKE '%' || :searchQuery || '%')
        ORDER BY date DESC, id DESC 
        LIMIT :limit OFFSET :offset
    """)
    fun getTransactionsPaged(
        fundSource: String,
        searchQuery: String,
        limit: Int,
        offset: Int
    ): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Query("SELECT * FROM transactions ORDER BY date ASC, id ASC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("UPDATE transactions SET approvalStatus = :status WHERE id = :id")
    suspend fun updateApprovalStatus(id: Int, status: String)

    @Query("SELECT SUM(amount) FROM transactions WHERE fundSource = :fundSource AND type = 'PEMASUKAN' AND approvalStatus != 'DITOLAK'")
    fun getTotalIncomeByFundSource(fundSource: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE fundSource = :fundSource AND type = 'PENGELUARAN' AND approvalStatus != 'DITOLAK'")
    fun getTotalExpenseByFundSource(fundSource: String): Flow<Double?>
}
