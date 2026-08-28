package com.example.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.TransactionDao
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TransactionDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).allowMainThreadQueries().build()
        transactionDao = db.transactionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadTransaction_HappyPath() = runBlocking {
        // Arrange
        val transaction = TransactionEntity(
            id = 1,
            title = "Test Pemasukan",
            amount = 100000.0,
            type = "PEMASUKAN",
            date = System.currentTimeMillis(),
            fundSource = "BOS",
            volume = 1.0,
            unitName = "Paket",
            approvalStatus = "DISETUJUI"
        )

        // Act
        transactionDao.insertTransaction(transaction)
        val transactions = transactionDao.getAllTransactions().first()

        // Assert
        assertEquals(1, transactions.size)
        assertEquals("Test Pemasukan", transactions[0].title)
        assertEquals(100000.0, transactions[0].amount, 0.0)
        assertEquals("BOS", transactions[0].fundSource)
        assertEquals(1.0, transactions[0].volume, 0.0)
        assertEquals("Paket", transactions[0].unitName)
    }
    
    @Test
    fun insertDuplicateTransaction_UpdatesExisting_ErrorHandling() = runBlocking {
        // Arrange
        val transaction = TransactionEntity(
            id = 1,
            title = "Test Pemasukan Awal",
            amount = 100000.0,
            type = "PEMASUKAN",
            date = System.currentTimeMillis(),
            fundSource = "BOS"
        )
        transactionDao.insertTransaction(transaction)
        
        // Act (Inserting a transaction with same ID updates it due to REPLACE strategy)
        val updatedTransaction = transaction.copy(title = "Test Pemasukan Updated", amount = 150000.0)
        transactionDao.insertTransaction(updatedTransaction)
        
        // Assert
        val transactions = transactionDao.getAllTransactions().first()
        assertEquals(1, transactions.size) // No new row should be created
        assertEquals("Test Pemasukan Updated", transactions[0].title)
        assertEquals(150000.0, transactions[0].amount, 0.0)
    }

    @Test
    fun calculateTotalIncome_HappyPath() = runBlocking {
        // Arrange
        val income1 = TransactionEntity(
            title = "Income 1", amount = 100000.0, type = "PEMASUKAN", date = 0, fundSource = "BOS", approvalStatus = "DISETUJUI"
        )
        val income2 = TransactionEntity(
            title = "Income 2", amount = 200000.0, type = "PEMASUKAN", date = 0, fundSource = "BOS", approvalStatus = "PENDING"
        )
        val expense1 = TransactionEntity(
            title = "Expense 1", amount = 50000.0, type = "PENGELUARAN", date = 0, fundSource = "BOS", approvalStatus = "DISETUJUI"
        )
        val rejectedIncome = TransactionEntity(
            title = "Rejected Income", amount = 500000.0, type = "PEMASUKAN", date = 0, fundSource = "BOS", approvalStatus = "DITOLAK"
        )
        
        transactionDao.insertTransactions(listOf(income1, income2, expense1, rejectedIncome))

        // Act
        // Both PENDING and DISETUJUI are counted, DITOLAK is not. (100000 + 200000)
        val totalIncome = transactionDao.getTotalIncomeByFundSource("BOS").first()

        // Assert
        assertEquals(300000.0, totalIncome ?: 0.0, 0.0)
    }
}
