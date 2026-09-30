package com.moneybook.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.Category
import com.moneybook.domain.model.MonthlySummary
import com.moneybook.domain.model.RefundStatus
import com.moneybook.domain.model.Transaction
import com.moneybook.domain.model.TransactionRefund
import com.moneybook.domain.model.TransactionStatus
import com.moneybook.domain.model.TransactionType
import com.moneybook.domain.repository.CategoryRepository
import com.moneybook.domain.repository.TransactionRepository
import com.moneybook.feature.transaction.SEOUL
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CategorySpending(val name: String, val amount: Long, val percentage: Int)

data class StatisticsUiState(
    val month: YearMonth = YearMonth.now(SEOUL),
    val loading: Boolean = true,
    val summary: MonthlySummary? = null,
    val categories: List<CategorySpending> = emptyList(),
    val empty: Boolean = false,
    val error: String? = null,
)

/** Mirrors get_monthly_summary: transaction month, visible confirmed/canceled rows, confirmed refunds. */
internal fun categorySpending(
    transactions: List<Transaction>,
    refunds: List<TransactionRefund>,
    categories: List<Category>,
    totalExpense: Long,
): List<CategorySpending> {
    val names = categories.associate { it.id to it.name.trim().ifEmpty { "기타" } }
    val refunded = refunds.filter { it.status == RefundStatus.CONFIRMED }
        .groupBy { it.transactionId }.mapValues { (_, values) -> values.sumOf { it.amount } }
    return transactions.asSequence()
        .filter { it.type == TransactionType.EXPENSE && it.status != TransactionStatus.PENDING }
        .groupBy { names[it.categoryId] ?: "기타" }
        .map { (name, rows) ->
            val amount = rows.sumOf { (it.amount - (refunded[it.id] ?: 0L)).coerceAtLeast(0L) }
            CategorySpending(name, amount, if (totalExpense == 0L) 0 else (amount.toDouble() * 100 / totalExpense).roundToInt())
        }
        .filter { it.amount > 0L }
        .sortedWith(compareByDescending<CategorySpending> { it.amount }.thenBy { it.name })
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = mutableState.asStateFlow()
    private var request: Job? = null
    private var generation = 0

    fun previousMonth() {
        mutableState.value = mutableState.value.copy(month = mutableState.value.month.minusMonths(1))
        refresh()
    }

    fun nextMonth() {
        if (mutableState.value.month >= YearMonth.now(SEOUL)) return
        mutableState.value = mutableState.value.copy(month = mutableState.value.month.plusMonths(1))
        refresh()
    }

    fun refresh() {
        val month = mutableState.value.month
        val user = transactions.currentUserId()
        generation++
        request?.cancel()
        mutableState.value = StatisticsUiState(month = month)
        if (user == null) {
            mutableState.value = StatisticsUiState(month = month, loading = false, error = "로그인이 필요합니다.")
            return
        }
        val current = generation
        request = viewModelScope.launch {
            val summary = transactions.getMonthlySummary(month)
            val rows = if (summary is AppResult.Success) transactions.getTransactions(month) else null
            val expenses = (rows as? AppResult.Success)?.value.orEmpty()
                .filter { it.type == TransactionType.EXPENSE && it.status != TransactionStatus.PENDING }
            val refunds = if (rows is AppResult.Success) transactions.getRefundsForTransactions(expenses.map { it.id }) else null
            val categories = if (refunds is AppResult.Success) categoryRepository.getCategories() else null
            if (current != generation || user != transactions.currentUserId()) return@launch
            mutableState.value = when {
                summary is AppResult.Error -> StatisticsUiState(month, loading = false, error = summary.message)
                rows is AppResult.Error -> StatisticsUiState(month, loading = false, error = rows.message)
                refunds is AppResult.Error -> StatisticsUiState(month, loading = false, error = refunds.message)
                categories is AppResult.Error -> StatisticsUiState(month, loading = false, error = categories.message)
                else -> {
                    val totals = (summary as AppResult.Success).value
                    StatisticsUiState(
                        month = month, loading = false, summary = totals,
                        categories = categorySpending(
                            (rows as AppResult.Success).value,
                            (refunds as AppResult.Success).value,
                            (categories as AppResult.Success).value,
                            totals.expense,
                        ),
                        empty = (rows as AppResult.Success).value.isEmpty(),
                    )
                }
            }
        }
    }

    fun invalidate() {
        generation++
        request?.cancel()
        request = null
    }
}
