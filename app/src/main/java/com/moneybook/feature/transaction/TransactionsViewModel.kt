package com.moneybook.feature.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.Card
import com.moneybook.domain.model.Category
import com.moneybook.domain.model.RefundStatus
import com.moneybook.domain.model.Transaction
import com.moneybook.domain.model.TransactionRefund
import com.moneybook.domain.model.TransactionScope
import com.moneybook.domain.model.TransactionType
import com.moneybook.domain.repository.CategoryRepository
import com.moneybook.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TransactionFilter { ALL, SHARED, PERSONAL }

data class TransactionsUiState(
    val month: YearMonth = YearMonth.now(SEOUL),
    val filter: TransactionFilter = TransactionFilter.ALL,
    val loading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val cards: List<Card> = emptyList(),
    val selected: Transaction? = null,
    val refunds: List<TransactionRefund> = emptyList(),
    val editor: TransactionFormState? = null,
    val saving: Boolean = false,
    val refundAmount: String = "",
    val error: String? = null,
    val notice: String? = null,
    val lastDeletedId: String? = null,
) {
    val visibleTransactions: List<Transaction>
        get() = transactions.filter {
            when (filter) {
                TransactionFilter.ALL -> true
                TransactionFilter.SHARED -> it.scope == TransactionScope.SHARED
                TransactionFilter.PERSONAL -> it.scope == TransactionScope.PERSONAL
            }
        }
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TransactionsUiState())
    val state: StateFlow<TransactionsUiState> = mutableState.asStateFlow()

    init { load() }

    fun retry() = load()
    fun previousMonth() { mutableState.value = mutableState.value.copy(month = mutableState.value.month.minusMonths(1)); load() }
    fun nextMonth() { mutableState.value = mutableState.value.copy(month = mutableState.value.month.plusMonths(1)); load() }
    fun setFilter(filter: TransactionFilter) { mutableState.value = mutableState.value.copy(filter = filter) }
    fun clearNotice() { mutableState.value = mutableState.value.copy(notice = null) }

    fun select(transaction: Transaction?) {
        mutableState.value = mutableState.value.copy(selected = transaction, refunds = emptyList(), editor = null, error = null)
        if (transaction != null) loadRefunds(transaction.id)
    }

    fun beginEdit() {
        mutableState.value.selected?.let { mutableState.value = mutableState.value.copy(editor = it.toFormState()) }
    }
    fun cancelEdit() { mutableState.value = mutableState.value.copy(editor = null) }
    fun editType(value: TransactionType) = updateEditor {
        copy(type = value, categoryId = null, cardId = cardId.takeIf { value == TransactionType.EXPENSE })
    }
    fun editAmount(value: String) = updateEditor { copy(amount = value.filter(Char::isDigit)) }
    fun editDate(value: String) = updateEditor { copy(date = value) }
    fun editTime(value: String) = updateEditor { copy(time = value) }
    fun editCategory(value: String) = updateEditor { copy(categoryId = value) }
    fun editCard(value: String?) = updateEditor { copy(cardId = value) }
    fun editMerchant(value: String) = updateEditor { copy(merchant = value) }
    fun editMemo(value: String) = updateEditor { copy(memo = value) }

    fun saveEdit() {
        val selected = mutableState.value.selected ?: return
        val editor = mutableState.value.editor ?: return
        when (val result = editor.validateAndBuild()) {
            is FormResult.Invalid -> mutableState.value = mutableState.value.copy(error = result.message)
            is FormResult.Valid -> viewModelScope.launch {
                mutableState.value = mutableState.value.copy(saving = true, error = null)
                when (val updated = transactionRepository.updateTransaction(selected.id, result.draft)) {
                    is AppResult.Success -> {
                        val transactions = mutableState.value.transactions.map {
                            if (it.id == updated.value.id) updated.value else it
                        }
                        mutableState.value = mutableState.value.copy(
                            saving = false, transactions = transactions, selected = updated.value,
                            editor = null, notice = "거래를 수정했습니다.",
                        )
                    }
                    is AppResult.Error -> mutableState.value = mutableState.value.copy(saving = false, error = updated.message)
                }
            }
        }
    }

    fun deleteSelected() {
        val transaction = mutableState.value.selected ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true, error = null)
            when (val result = transactionRepository.softDeleteTransaction(transaction.id)) {
                is AppResult.Success -> mutableState.value = mutableState.value.copy(
                    saving = false,
                    transactions = mutableState.value.transactions.filterNot { it.id == transaction.id },
                    selected = null,
                    refunds = emptyList(),
                    lastDeletedId = transaction.id,
                    notice = "거래를 삭제했습니다.",
                )
                is AppResult.Error -> mutableState.value = mutableState.value.copy(saving = false, error = result.message)
            }
        }
    }

    fun restoreLastDeleted() {
        val id = mutableState.value.lastDeletedId ?: return
        viewModelScope.launch {
            when (val result = transactionRepository.restoreTransaction(id)) {
                is AppResult.Success -> {
                    mutableState.value = mutableState.value.copy(lastDeletedId = null, notice = "거래를 복구했습니다.")
                    loadData()
                }
                is AppResult.Error -> mutableState.value = mutableState.value.copy(error = result.message)
            }
        }
    }

    fun setRefundAmount(value: String) {
        mutableState.value = mutableState.value.copy(refundAmount = value.filter(Char::isDigit), error = null)
    }

    fun createRefund() {
        val transaction = mutableState.value.selected ?: return
        val amount = mutableState.value.refundAmount.toLongOrNull()
        val refunded = mutableState.value.refunds.filter { it.status == RefundStatus.CONFIRMED }.sumOf { it.amount }
        val remaining = transaction.amount - refunded
        if (transaction.type != TransactionType.EXPENSE || amount == null || amount <= 0 || amount > remaining) {
            mutableState.value = mutableState.value.copy(error = "환불금액은 남은 결제금액 이하의 양수여야 합니다.")
            return
        }
        if (mutableState.value.saving) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true, error = null)
            when (val result = transactionRepository.createRefund(transaction.id, amount)) {
                is AppResult.Success -> {
                    val transactions = transactionRepository.getTransactions(mutableState.value.month)
                    val refunds = transactionRepository.getRefunds(transaction.id)
                    if (transactions is AppResult.Success && refunds is AppResult.Success) {
                        val refreshed = transactions.value.firstOrNull { it.id == transaction.id }
                        mutableState.value = mutableState.value.copy(
                            saving = false,
                            refundAmount = "",
                            transactions = transactions.value,
                            selected = refreshed,
                            refunds = refunds.value,
                            notice = "환불을 등록했습니다.",
                            error = if (refreshed == null) "갱신된 거래를 찾지 못했습니다." else null,
                        )
                    } else {
                        mutableState.value = mutableState.value.copy(
                            saving = false,
                            refundAmount = "",
                            error = "환불은 등록됐지만 최신 상태를 불러오지 못했습니다. 다시 시도해 주세요.",
                        )
                    }
                }
                is AppResult.Error -> mutableState.value = mutableState.value.copy(saving = false, error = result.message)
            }
        }
    }

    private fun load() = viewModelScope.launch { loadData() }

    private suspend fun loadData() {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        val categories = categoryRepository.getActiveCategories()
        val cards = transactionRepository.getCards()
        val transactions = transactionRepository.getTransactions(mutableState.value.month)
        mutableState.value = when {
            categories is AppResult.Error -> mutableState.value.copy(loading = false, error = categories.message)
            cards is AppResult.Error -> mutableState.value.copy(loading = false, error = cards.message)
            transactions is AppResult.Error -> mutableState.value.copy(loading = false, error = transactions.message)
            else -> mutableState.value.copy(
                loading = false,
                categories = (categories as AppResult.Success).value,
                cards = (cards as AppResult.Success).value,
                transactions = (transactions as AppResult.Success).value,
            )
        }
    }

    private fun loadRefunds(transactionId: String) = viewModelScope.launch {
        when (val result = transactionRepository.getRefunds(transactionId)) {
            is AppResult.Success -> mutableState.value = mutableState.value.copy(refunds = result.value)
            is AppResult.Error -> mutableState.value = mutableState.value.copy(error = result.message)
        }
    }

    private fun updateEditor(block: TransactionFormState.() -> TransactionFormState) {
        mutableState.value.editor?.let {
            mutableState.value = mutableState.value.copy(editor = it.block(), error = null)
        }
    }

    fun currentUserId(): String? = transactionRepository.currentUserId()
}
