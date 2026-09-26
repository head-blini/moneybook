package com.moneybook.domain.repository

import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.Card
import com.moneybook.domain.model.Transaction
import com.moneybook.domain.model.TransactionDraft
import com.moneybook.domain.model.TransactionRefund
import com.moneybook.domain.model.MonthlySummary
import java.time.YearMonth

interface TransactionRepository {
    suspend fun getTransactions(month: YearMonth): AppResult<List<Transaction>>
    suspend fun getMonthlySummary(month: YearMonth): AppResult<MonthlySummary>
    suspend fun getRefundsForTransactions(ids: List<String>): AppResult<List<TransactionRefund>>
    suspend fun getCards(): AppResult<List<Card>>
    suspend fun createTransaction(draft: TransactionDraft): AppResult<Transaction>
    suspend fun updateTransaction(id: String, draft: TransactionDraft): AppResult<Transaction>
    suspend fun softDeleteTransaction(id: String): AppResult<Unit>
    suspend fun restoreTransaction(id: String): AppResult<Unit>
    suspend fun getRefunds(transactionId: String): AppResult<List<TransactionRefund>>
    suspend fun createRefund(transactionId: String, amount: Long): AppResult<Unit>
    fun currentUserId(): String?
}
