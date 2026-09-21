package com.moneybook.feature.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.TransactionScope
import com.moneybook.domain.model.TransactionType
import com.moneybook.domain.repository.CategoryRepository
import com.moneybook.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddTransactionUiState(
    val form: TransactionFormState = TransactionFormState(),
    val options: TransactionFormOptions = TransactionFormOptions(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AddTransactionUiState())
    val state: StateFlow<AddTransactionUiState> = mutableState.asStateFlow()

    init { loadOptions() }

    fun setType(value: TransactionType) = updateForm {
        copy(type = value, categoryId = null, cardId = cardId.takeIf { value == TransactionType.EXPENSE })
    }
    fun setScope(value: TransactionScope) = updateForm { copy(scope = value) }
    fun setAmount(value: String) = updateForm { copy(amount = value.filter(Char::isDigit)) }
    fun setDate(value: String) = updateForm { copy(date = value) }
    fun setTime(value: String) = updateForm { copy(time = value) }
    fun setCategory(value: String) = updateForm { copy(categoryId = value) }
    fun setCard(value: String?) = updateForm { copy(cardId = value) }
    fun setMerchant(value: String) = updateForm { copy(merchant = value) }
    fun setMemo(value: String) = updateForm { copy(memo = value) }

    fun save(onSaved: () -> Unit) {
        if (mutableState.value.saving) return
        when (val result = mutableState.value.form.validateAndBuild()) {
            is FormResult.Invalid -> mutableState.value = mutableState.value.copy(error = result.message)
            is FormResult.Valid -> {
                mutableState.value = mutableState.value.copy(saving = true, error = null)
                viewModelScope.launch {
                when (val saved = transactionRepository.createTransaction(result.draft)) {
                    is AppResult.Success -> onSaved()
                    is AppResult.Error -> mutableState.value = mutableState.value.copy(saving = false, error = saved.message)
                }
                }
            }
        }
    }

    private fun loadOptions() = viewModelScope.launch {
        val categories = categoryRepository.getActiveCategories()
        val cards = transactionRepository.getCards()
        mutableState.value = when {
            categories is AppResult.Error -> mutableState.value.copy(loading = false, error = categories.message)
            cards is AppResult.Error -> mutableState.value.copy(loading = false, error = cards.message)
            else -> {
                val categoryValues = (categories as AppResult.Success).value
                val cardValues = (cards as AppResult.Success).value.filter {
                    it.ownerUserId == transactionRepository.currentUserId()
                }
                mutableState.value.copy(
                    loading = false,
                    options = TransactionFormOptions(categoryValues, cardValues),
                    form = mutableState.value.form.copy(
                        categoryId = categoryValues.firstOrNull { it.type == TransactionType.EXPENSE }?.id,
                    ),
                )
            }
        }
    }

    private fun updateForm(block: TransactionFormState.() -> TransactionFormState) {
        mutableState.value = mutableState.value.copy(form = mutableState.value.form.block(), error = null)
    }
}
