package com.moneybook

import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.*
import com.moneybook.domain.repository.CategoryRepository
import com.moneybook.domain.repository.TransactionRepository
import com.moneybook.feature.transaction.TransactionFilter
import com.moneybook.feature.transaction.TransactionsViewModel
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun emptyRepositoryProducesUsableEmptyState() = runTest(dispatcher) {
        val viewModel = TransactionsViewModel(FakeTransactionRepository(), FakeCategoryRepository())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.visibleTransactions.isEmpty())
    }

    @Test fun sharedAndPersonalFiltersOnlyUseRowsReturnedByRepository() = runTest(dispatcher) {
        val repository = FakeTransactionRepository(mutableListOf(transaction("shared", TransactionScope.SHARED), transaction("mine", TransactionScope.PERSONAL)))
        val viewModel = TransactionsViewModel(repository, FakeCategoryRepository())
        advanceUntilIdle()

        viewModel.setFilter(TransactionFilter.SHARED)
        assertEquals(listOf("shared"), viewModel.state.value.visibleTransactions.map { it.id })
        viewModel.setFilter(TransactionFilter.PERSONAL)
        assertEquals(listOf("mine"), viewModel.state.value.visibleTransactions.map { it.id })
    }

    @Test fun softDeleteAndRestoreUseRepositoryAndReloadRows() = runTest(dispatcher) {
        val repository = FakeTransactionRepository(mutableListOf(transaction("shared", TransactionScope.SHARED)))
        val viewModel = TransactionsViewModel(repository, FakeCategoryRepository())
        advanceUntilIdle()
        viewModel.select(viewModel.state.value.transactions.single())
        viewModel.deleteSelected()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.transactions.isEmpty())
        assertEquals("shared", viewModel.state.value.lastDeletedId)
        viewModel.restoreLastDeleted()
        advanceUntilIdle()
        assertEquals(listOf("shared"), viewModel.state.value.transactions.map { it.id })
    }

    @Test fun editingChangesAllowedFieldsButKeepsScopeAndOwnership() = runTest(dispatcher) {
        val original = transaction("shared", TransactionScope.SHARED)
        val repository = FakeTransactionRepository(mutableListOf(original))
        val viewModel = TransactionsViewModel(repository, FakeCategoryRepository())
        advanceUntilIdle()
        viewModel.select(viewModel.state.value.transactions.single())
        viewModel.beginEdit()
        viewModel.editAmount("2500")
        viewModel.editMerchant("수정 거래")
        viewModel.saveEdit()
        advanceUntilIdle()

        val updated = repository.transactions.single()
        assertEquals(2_500L, updated.amount)
        assertEquals("수정 거래", updated.merchant)
        assertEquals(original.scope, updated.scope)
        assertEquals(original.paidBy, updated.paidBy)
        assertEquals("거래를 수정했습니다.", viewModel.state.value.notice)
        assertEquals(null, viewModel.state.value.editor)
        assertFalse(viewModel.state.value.saving)
    }

    @Test fun updateFailureIsExposedAndSavingStateIsReleased() = runTest(dispatcher) {
        val repository = FakeTransactionRepository(
            mutableListOf(transaction("shared", TransactionScope.SHARED)),
            failUpdates = true,
        )
        val viewModel = TransactionsViewModel(repository, FakeCategoryRepository())
        advanceUntilIdle()
        viewModel.select(viewModel.state.value.transactions.single())
        viewModel.beginEdit()
        viewModel.editAmount("2500")
        viewModel.saveEdit()
        advanceUntilIdle()

        assertEquals(1, repository.updateCalls)
        assertEquals("update failed", viewModel.state.value.error)
        assertTrue(viewModel.state.value.editor != null)
        assertFalse(viewModel.state.value.saving)
    }

    @Test fun partialThenFullRefundTransitionsFromConfirmedToCanceledAndSurvivesReload() = runTest(dispatcher) {
        val repository = FakeTransactionRepository(mutableListOf(transaction("expense", TransactionScope.SHARED, 10_000)))
        val viewModel = TransactionsViewModel(repository, FakeCategoryRepository())
        advanceUntilIdle()
        viewModel.select(viewModel.state.value.transactions.single())
        advanceUntilIdle()

        viewModel.setRefundAmount("3000")
        viewModel.createRefund()
        advanceUntilIdle()
        assertEquals(TransactionStatus.CONFIRMED, viewModel.state.value.selected?.status)

        viewModel.setRefundAmount("7000")
        viewModel.createRefund()
        advanceUntilIdle()

        assertEquals(10_000L, repository.refunds.sumOf { it.amount })
        assertEquals(TransactionStatus.CANCELED, viewModel.state.value.selected?.status)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(TransactionStatus.CANCELED, viewModel.state.value.transactions.single().status)
    }

    @Test fun oneFullRefundCancelsTransaction() = runTest(dispatcher) {
        val repository = FakeTransactionRepository(mutableListOf(transaction("expense", TransactionScope.SHARED, 10_000)))
        val viewModel = TransactionsViewModel(repository, FakeCategoryRepository())
        advanceUntilIdle()
        viewModel.select(viewModel.state.value.transactions.single())
        advanceUntilIdle()

        viewModel.setRefundAmount("10000")
        viewModel.createRefund()
        advanceUntilIdle()

        assertEquals(TransactionStatus.CANCELED, viewModel.state.value.selected?.status)
        assertEquals(10_000L, repository.refunds.sumOf { it.amount })
    }

    @Test fun refundCannotExceedRemainingAmount() = runTest(dispatcher) {
        val repository = FakeTransactionRepository(mutableListOf(transaction("expense", TransactionScope.SHARED, 10_000)))
        val viewModel = TransactionsViewModel(repository, FakeCategoryRepository())
        advanceUntilIdle()
        viewModel.select(viewModel.state.value.transactions.single())
        advanceUntilIdle()
        viewModel.setRefundAmount("3000")
        viewModel.createRefund()
        advanceUntilIdle()

        viewModel.setRefundAmount("7001")
        viewModel.createRefund()
        advanceUntilIdle()

        assertEquals(3_000L, repository.refunds.sumOf { it.amount })
        assertEquals("환불금액은 남은 결제금액 이하의 양수여야 합니다.", viewModel.state.value.error)
        assertEquals(TransactionStatus.CONFIRMED, viewModel.state.value.selected?.status)
    }

    @Test fun loadingErrorIsExposed() = runTest(dispatcher) {
        val viewModel = TransactionsViewModel(FakeTransactionRepository(failLoads = true), FakeCategoryRepository())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals("load failed", viewModel.state.value.error)
    }

    private fun transaction(id: String, scope: TransactionScope, amount: Long = 1_000) = Transaction(
        id, "house", "user", "user", TransactionType.EXPENSE, scope, amount,
        "food", null, "거래 $id", null, Instant.parse("2026-09-21T10:00:00Z"), TransactionStatus.CONFIRMED,
    )
}

private class FakeCategoryRepository : CategoryRepository {
    override suspend fun getActiveCategories() = AppResult.Success(
        listOf(Category("food", "식비", TransactionType.EXPENSE, true)),
    )
}

private class FakeTransactionRepository(
    val transactions: MutableList<Transaction> = mutableListOf(),
    private val failLoads: Boolean = false,
    private val failUpdates: Boolean = false,
) : TransactionRepository {
    val refunds = mutableListOf<TransactionRefund>()
    var updateCalls = 0
        private set
    private val deleted = mutableMapOf<String, Transaction>()

    override suspend fun getTransactions(month: YearMonth): AppResult<List<Transaction>> =
        if (failLoads) AppResult.Error("load failed") else AppResult.Success(transactions.toList())
    override suspend fun getCards() = AppResult.Success(emptyList<Card>())
    override suspend fun createTransaction(draft: TransactionDraft) = error("Not used")
    override suspend fun updateTransaction(id: String, draft: TransactionDraft): AppResult<Transaction> {
        updateCalls += 1
        if (failUpdates) return AppResult.Error("update failed")
        val index = transactions.indexOfFirst { it.id == id }
        val updated = transactions[index].copy(
            type = draft.type,
            amount = draft.amount,
            categoryId = draft.categoryId,
            cardId = draft.cardId,
            merchant = draft.merchant,
            memo = draft.memo,
            transactionAt = draft.transactionAt,
        )
        transactions[index] = updated
        return AppResult.Success(updated)
    }
    override suspend fun softDeleteTransaction(id: String): AppResult<Unit> {
        transactions.firstOrNull { it.id == id }?.let { deleted[id] = it }
        transactions.removeAll { it.id == id }
        return AppResult.Success(Unit)
    }
    override suspend fun restoreTransaction(id: String): AppResult<Unit> {
        deleted.remove(id)?.let(transactions::add)
        return AppResult.Success(Unit)
    }
    override suspend fun getRefunds(transactionId: String) = AppResult.Success(refunds.filter { it.transactionId == transactionId })
    override suspend fun createRefund(transactionId: String, amount: Long): AppResult<Unit> {
        refunds += TransactionRefund("refund-${refunds.size}", transactionId, amount, RefundStatus.CONFIRMED, Instant.now())
        val total = refunds.filter { it.transactionId == transactionId }.sumOf { it.amount }
        val index = transactions.indexOfFirst { it.id == transactionId }
        if (index >= 0 && total == transactions[index].amount) transactions[index] = transactions[index].copy(status = TransactionStatus.CANCELED)
        return AppResult.Success(Unit)
    }
    override fun currentUserId() = "user"
}
