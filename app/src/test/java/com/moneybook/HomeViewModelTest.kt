package com.moneybook

import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.*
import com.moneybook.domain.repository.CategoryRepository
import com.moneybook.domain.repository.TransactionRepository
import com.moneybook.feature.home.HomeViewModel
import com.moneybook.feature.home.formatKrw
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun summaryAndRecentTransactionsUseVisibleRowsAndTitleFallback() = runTest(dispatcher) {
        val repository = HomeRepository()
        repository.summary = MonthlySummary(2000, 300, 1000, 200)
        repository.rows = (0..6).map { index ->
            homeTransaction(index.toString(), Instant.parse("2026-09-${(10 + index).toString().padStart(2, '0')}T00:00:00Z"),
                when (index) { 6 -> "  메모  "; 5 -> "  "; else -> null },
                if (index == 4) "unknown" else "food")
        }.reversed()
        val viewModel = HomeViewModel(repository, HomeCategories())

        assertTrue(viewModel.uiState.value.loading)
        viewModel.refresh()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(3000L, state.summary?.income)
        assertEquals(500L, state.summary?.expense)
        assertEquals(2500L, state.summary?.balance)
        assertEquals(5, state.recentTransactions.size)
        assertEquals(listOf("6", "5", "4", "3", "2"), state.recentTransactions.map { it.id })
        assertEquals(listOf("메모", "식비", "거래"), state.recentTransactions.take(3).map { it.title })
    }

    @Test fun emptyErrorRetryAndReentryRefresh() = runTest(dispatcher) {
        val repository = HomeRepository()
        val viewModel = HomeViewModel(repository, HomeCategories())
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.empty)
        repository.failure = true
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("summary failed", viewModel.uiState.value.error)
        repository.failure = false
        repository.rows = listOf(homeTransaction("new", Instant.now(), null, "food"))
        viewModel.invalidate()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("new", viewModel.uiState.value.recentTransactions.single().id)
        assertEquals(3, repository.summaryCalls)
    }

    @Test fun accountChangeClearsOldData() = runTest(dispatcher) {
        val repository = HomeRepository()
        repository.rows = listOf(homeTransaction("old", Instant.now(), null, "food"))
        val viewModel = HomeViewModel(repository, HomeCategories())
        viewModel.refresh()
        advanceUntilIdle()
        repository.user = null
        viewModel.refresh()
        assertTrue(viewModel.uiState.value.recentTransactions.isEmpty())
        assertEquals("로그인이 필요합니다.", viewModel.uiState.value.error)
    }

    @Test fun wonFormattingPreservesIntegerPrecision() {
        assertEquals("₩54,900", formatKrw(54_900L))
        assertEquals("₩9,223,372,036,854,775,807", formatKrw(Long.MAX_VALUE))
    }
}

internal fun homeTransaction(id: String, at: Instant, memo: String?, category: String) = Transaction(
    id, "house", "user", "user", TransactionType.EXPENSE, TransactionScope.SHARED, 100,
    category, null, "merchant", memo, at, TransactionStatus.CONFIRMED,
)

internal class HomeCategories : CategoryRepository {
    override suspend fun getActiveCategories() = AppResult.Success(listOf(Category("food", "식비", TransactionType.EXPENSE, true)))
    override suspend fun getCategories() = getActiveCategories()
}

internal class HomeRepository : TransactionRepository {
    var user: String? = "user"
    var failure = false
    var summaryCalls = 0
    var summary = MonthlySummary(0, 0, 0, 0)
    var rows: List<Transaction> = emptyList()
    var refunds: List<TransactionRefund> = emptyList()
    override suspend fun getMonthlySummary(month: YearMonth): AppResult<MonthlySummary> {
        summaryCalls++
        return if (failure) AppResult.Error("summary failed") else AppResult.Success(summary)
    }
    override suspend fun getTransactions(month: YearMonth) = AppResult.Success(rows)
    override suspend fun getRefundsForTransactions(ids: List<String>) = AppResult.Success(refunds.filter { it.transactionId in ids })
    override suspend fun getCards() = AppResult.Success(emptyList<Card>())
    override suspend fun createTransaction(draft: TransactionDraft): AppResult<Transaction> = error("Not used")
    override suspend fun updateTransaction(id: String, draft: TransactionDraft): AppResult<Transaction> = error("Not used")
    override suspend fun softDeleteTransaction(id: String): AppResult<Unit> = error("Not used")
    override suspend fun restoreTransaction(id: String): AppResult<Unit> = error("Not used")
    override suspend fun getRefunds(transactionId: String) = AppResult.Success(emptyList<TransactionRefund>())
    override suspend fun createRefund(transactionId: String, amount: Long): AppResult<Unit> = error("Not used")
    override fun currentUserId() = user
}
