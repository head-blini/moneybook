package com.moneybook

import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.*
import com.moneybook.domain.repository.TransactionRepository
import com.moneybook.feature.statistics.StatisticsViewModel
import com.moneybook.feature.statistics.categorySpending
import com.moneybook.feature.transaction.SEOUL
import java.time.Instant
import java.time.YearMonth
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun confirmedRefundsMatchMonthlyExpenseAndCategoryOrder() = runTest(dispatcher) {
        val repository = StatisticsRepository()
        repository.summary = MonthlySummary(2000, 700, 1000, 300)
        val at = Instant.parse("2026-09-01T00:00:00Z")
        repository.rows = listOf(
            homeTransaction("partial", at, null, "food").copy(amount = 1000),
            homeTransaction("full", at, null, "food").copy(amount = 500, status = TransactionStatus.CANCELED),
            homeTransaction("other", at, null, "unknown").copy(amount = 300),
            homeTransaction("pending", at, null, "food").copy(amount = 200, status = TransactionStatus.PENDING),
            homeTransaction("income", at, null, "food").copy(amount = 900, type = TransactionType.INCOME),
        )
        repository.refunds = listOf(
            TransactionRefund("r1", "partial", 300, RefundStatus.CONFIRMED, at.plusSeconds(86400 * 40L)),
            TransactionRefund("r2", "full", 500, RefundStatus.CONFIRMED, at),
            TransactionRefund("r3", "partial", 100, RefundStatus.PENDING, at),
            TransactionRefund("r4", "partial", 100, RefundStatus.CANCELED, at),
        )
        val viewModel = StatisticsViewModel(repository, HomeCategories())
        viewModel.refresh()
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(3000L, state.summary?.income)
        assertEquals(1000L, state.summary?.expense)
        assertEquals(2000L, state.summary?.balance)
        assertEquals(listOf("식비", "기타"), state.categories.map { it.name })
        assertEquals(listOf(700L, 300L), state.categories.map { it.amount })
        assertEquals(listOf(70, 30), state.categories.map { it.percentage })
        assertEquals(state.summary!!.expense, state.categories.sumOf { it.amount })
        assertEquals(setOf("partial", "full", "other"), repository.lastRefundIds.toSet())
    }

    @Test fun zeroDenominatorIncomeOnlyEmptyAndRetry() = runTest(dispatcher) {
        val repository = StatisticsRepository()
        val viewModel = StatisticsViewModel(repository, HomeCategories())
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.empty)
        assertTrue(viewModel.state.value.categories.isEmpty())
        repository.summary = MonthlySummary(200, 0, 0, 0)
        repository.rows = listOf(homeTransaction("income", Instant.now(), null, "food").copy(type = TransactionType.INCOME))
        viewModel.refresh()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.empty)
        assertEquals(200L, viewModel.state.value.summary?.income)
        assertTrue(viewModel.state.value.categories.isEmpty())
        assertEquals(0, categorySpending(
            listOf(homeTransaction("stale", Instant.now(), null, "food")),
            emptyList(), emptyList(), 0,
        ).single().percentage)
        repository.fail = true
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("summary failed", viewModel.state.value.error)
        repository.fail = false
        viewModel.refresh()
        advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    @Test fun monthNavigationStopsAtCurrentMonthAndOldResponseCannotOverwriteNewMonth() = runTest(dispatcher) {
        val repository = StatisticsRepository()
        val current = YearMonth.now(SEOUL)
        repository.delayedMonth = current
        val viewModel = StatisticsViewModel(repository, HomeCategories())
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value.loading)
        viewModel.previousMonth()
        advanceUntilIdle()
        assertEquals(current.minusMonths(1), viewModel.state.value.month)
        assertFalse(viewModel.state.value.loading)
        repository.releaseDelayed(MonthlySummary(0, 999, 0, 0))
        advanceUntilIdle()
        assertEquals(current.minusMonths(1), viewModel.state.value.month)
        assertEquals(0L, viewModel.state.value.summary?.expense)
        repository.delayedMonth = null
        viewModel.nextMonth()
        advanceUntilIdle()
        assertEquals(current, viewModel.state.value.month)
        viewModel.nextMonth()
        assertEquals(current, viewModel.state.value.month)
        assertEquals(listOf(current, current.minusMonths(1), current), repository.requestedMonths)
    }

    @Test fun seoulMonthBoundsIncludeStartAndExcludeNextMonth() {
        val (start, end) = seoulMonthBounds(YearMonth.of(2026, 9))
        assertEquals(Instant.parse("2026-08-31T15:00:00Z"), start)
        assertEquals(Instant.parse("2026-09-30T15:00:00Z"), end)
        assertTrue(start < end)
    }

    @Test fun repositoryVisibleRowsExcludeDeletedAndOtherMonthTransactions() = runTest(dispatcher) {
        val repository = StatisticsRepository()
        repository.summary = MonthlySummary(0, 400, 0, 0)
        // getTransactions already applies RLS, soft-delete, and Seoul month boundaries.
        repository.rows = listOf(homeTransaction("visible", Instant.now(), null, "food").copy(amount = 400))
        val viewModel = StatisticsViewModel(repository, HomeCategories())
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(400L, viewModel.state.value.categories.sumOf { it.amount })
        assertEquals(listOf("visible"), repository.lastRefundIds)
    }

    @Test fun returningToStatisticsReloadsChangedTransactions() = runTest(dispatcher) {
        val repository = StatisticsRepository()
        val viewModel = StatisticsViewModel(repository, HomeCategories())
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.empty)
        repository.summary = MonthlySummary(0, 500, 0, 0)
        repository.rows = listOf(homeTransaction("added", Instant.now(), null, "food").copy(amount = 500))
        viewModel.invalidate()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(500L, viewModel.state.value.categories.single().amount)
        assertFalse(viewModel.state.value.empty)
    }

    @Test fun fullyRefundedMonthHasNoSpendingButIsNotAnEmptyTransactionMonth() = runTest(dispatcher) {
        val repository = StatisticsRepository()
        repository.rows = listOf(homeTransaction("full", Instant.now(), null, "food")
            .copy(status = TransactionStatus.CANCELED))
        repository.refunds = listOf(TransactionRefund("refund", "full", 100, RefundStatus.CONFIRMED, Instant.now()))
        val viewModel = StatisticsViewModel(repository, HomeCategories())
        viewModel.refresh()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.empty)
        assertTrue(viewModel.state.value.categories.isEmpty())
    }
}

private class StatisticsRepository : TransactionRepository {
    var summary = MonthlySummary(0, 0, 0, 0)
    var rows: List<Transaction> = emptyList()
    var refunds: List<TransactionRefund> = emptyList()
    var fail = false
    var delayedMonth: YearMonth? = null
    var lastRefundIds: List<String> = emptyList()
    val requestedMonths = mutableListOf<YearMonth>()
    private var delayed: Continuation<AppResult<MonthlySummary>>? = null

    override suspend fun getMonthlySummary(month: YearMonth): AppResult<MonthlySummary> {
        requestedMonths += month
        if (month == delayedMonth) return suspendCoroutine { delayed = it }
        return if (fail) AppResult.Error("summary failed") else AppResult.Success(summary)
    }
    fun releaseDelayed(value: MonthlySummary) { delayed?.resume(AppResult.Success(value)) }
    override suspend fun getTransactions(month: YearMonth) = AppResult.Success(rows)
    override suspend fun getRefundsForTransactions(ids: List<String>): AppResult<List<TransactionRefund>> {
        lastRefundIds = ids
        return AppResult.Success(refunds.filter { it.transactionId in ids })
    }
    override suspend fun getCards() = AppResult.Success(emptyList<Card>())
    override suspend fun createTransaction(draft: TransactionDraft): AppResult<Transaction> = error("Not used")
    override suspend fun updateTransaction(id: String, draft: TransactionDraft): AppResult<Transaction> = error("Not used")
    override suspend fun softDeleteTransaction(id: String): AppResult<Unit> = error("Not used")
    override suspend fun restoreTransaction(id: String): AppResult<Unit> = error("Not used")
    override suspend fun getRefunds(transactionId: String) = AppResult.Success(emptyList<TransactionRefund>())
    override suspend fun createRefund(transactionId: String, amount: Long): AppResult<Unit> = error("Not used")
    override fun currentUserId() = "user"
}
