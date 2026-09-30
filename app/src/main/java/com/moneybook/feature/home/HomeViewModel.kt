package com.moneybook.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.Category
import com.moneybook.domain.model.MonthlySummary
import com.moneybook.domain.model.Transaction
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

data class RecentTransaction(
    val id: String,
    val title: String,
    val amount: Long,
    val income: Boolean,
    val categoryName: String? = null,
    val originalAmount: Long = amount,
)

data class HomeUiState(
    val month: YearMonth = YearMonth.now(SEOUL),
    val loading: Boolean = true,
    val summary: MonthlySummary? = null,
    val recentTransactions: List<RecentTransaction> = emptyList(),
    val empty: Boolean = false,
    val error: String? = null,
)

internal fun recentTransactions(transactions: List<Transaction>, categories: List<Category>): List<RecentTransaction> {
    val names = categories.associate { it.id to it.name }
    return transactions.sortedWith(compareByDescending<Transaction> { it.transactionAt }.thenBy { it.id })
        .take(5).map { transaction ->
            val categoryName = names[transaction.categoryId]?.trim()?.takeIf(String::isNotEmpty)
            val title = transaction.merchant?.trim()?.takeIf(String::isNotEmpty)
                ?: transaction.memo?.trim()?.takeIf(String::isNotEmpty)
                ?: categoryName ?: "거래"
            RecentTransaction(
                transaction.id,
                title,
                transaction.netAmount,
                transaction.type == TransactionType.INCOME,
                categoryName?.takeUnless { it == title },
                transaction.amount,
            )
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableState.asStateFlow()
    private var request: Job? = null
    private var generation = 0

    fun refresh() {
        val user = transactions.currentUserId()
        val month = YearMonth.now(SEOUL)
        generation++
        request?.cancel()
        mutableState.value = HomeUiState(month = month)
        if (user == null) {
            mutableState.value = HomeUiState(month = month, loading = false, error = "로그인이 필요합니다.")
            return
        }
        val current = generation
        request = viewModelScope.launch {
            val summary = transactions.getMonthlySummary(month)
            val rows = if (summary is AppResult.Success) transactions.getTransactions(month) else null
            val labels = if (rows is AppResult.Success) categories.getCategories() else null
            if (current != generation || user != transactions.currentUserId()) return@launch
            mutableState.value = when {
                summary is AppResult.Error -> HomeUiState(month, loading = false, error = summary.message)
                rows is AppResult.Error -> HomeUiState(month, loading = false, error = rows.message)
                labels is AppResult.Error -> HomeUiState(month, loading = false, error = labels.message)
                else -> {
                    val visible = (rows as AppResult.Success).value
                    HomeUiState(
                        month = month, loading = false,
                        summary = (summary as AppResult.Success).value,
                        recentTransactions = recentTransactions(visible, (labels as AppResult.Success).value),
                        empty = visible.isEmpty(),
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
