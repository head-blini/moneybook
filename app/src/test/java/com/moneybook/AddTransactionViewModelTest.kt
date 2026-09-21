package com.moneybook

import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.*
import com.moneybook.domain.repository.CategoryRepository
import com.moneybook.domain.repository.TransactionRepository
import com.moneybook.feature.transaction.AddTransactionViewModel
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
class AddTransactionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun incomeCanBeSavedWithoutCardAndDoubleClickCreatesOnce() = runTest(dispatcher) {
        val repository = AddFakeTransactionRepository()
        val viewModel = AddTransactionViewModel(repository, AddFakeCategoryRepository())
        advanceUntilIdle()
        viewModel.setType(TransactionType.INCOME)
        viewModel.setCategory("salary")
        viewModel.setAmount("3000000")
        var completed = 0

        viewModel.save { completed++ }
        viewModel.save { completed++ }
        advanceUntilIdle()

        assertEquals(1, repository.created.size)
        assertEquals(1, completed)
        assertEquals(TransactionType.INCOME, repository.created.single().type)
        assertEquals(null, repository.created.single().cardId)
    }

    @Test fun invalidAmountDoesNotStartSave() = runTest(dispatcher) {
        val repository = AddFakeTransactionRepository()
        val viewModel = AddTransactionViewModel(repository, AddFakeCategoryRepository())
        advanceUntilIdle()
        viewModel.setAmount("0")
        viewModel.save {}

        assertTrue(repository.created.isEmpty())
        assertFalse(viewModel.state.value.saving)
        assertEquals("금액은 1원 이상이어야 합니다.", viewModel.state.value.error)
    }

    @Test fun everyTypeAndScopeCombinationCanBeSavedWithoutChoosingPayer() = runTest(dispatcher) {
        TransactionType.entries.forEach { type ->
            TransactionScope.entries.forEach { scope ->
                val repository = AddFakeTransactionRepository()
                val viewModel = AddTransactionViewModel(repository, AddFakeCategoryRepository())
                advanceUntilIdle()
                viewModel.setType(type)
                viewModel.setScope(scope)
                viewModel.setCategory(if (type == TransactionType.EXPENSE) "food" else "salary")
                viewModel.setAmount("10000")

                viewModel.save {}
                advanceUntilIdle()

                assertEquals(type, repository.created.single().type)
                assertEquals(scope, repository.created.single().scope)
            }
        }
    }
}

private class AddFakeCategoryRepository : CategoryRepository {
    override suspend fun getActiveCategories() = AppResult.Success(
        listOf(
            Category("food", "식비", TransactionType.EXPENSE, true),
            Category("salary", "급여", TransactionType.INCOME, true),
        ),
    )
}

private class AddFakeTransactionRepository : TransactionRepository {
    val created = mutableListOf<TransactionDraft>()
    override suspend fun getTransactions(month: YearMonth) = AppResult.Success(emptyList<Transaction>())
    override suspend fun getCards() = AppResult.Success(emptyList<Card>())
    override suspend fun createTransaction(draft: TransactionDraft): AppResult<Transaction> {
        created += draft
        return AppResult.Success(
            Transaction("id", "house", "user", "user", draft.type, draft.scope, draft.amount,
                draft.categoryId, draft.cardId, draft.merchant, draft.memo, draft.transactionAt, TransactionStatus.CONFIRMED),
        )
    }
    override suspend fun updateTransaction(id: String, draft: TransactionDraft) = error("Not used")
    override suspend fun softDeleteTransaction(id: String) = error("Not used")
    override suspend fun restoreTransaction(id: String) = error("Not used")
    override suspend fun getRefunds(transactionId: String) = AppResult.Success(emptyList<TransactionRefund>())
    override suspend fun createRefund(transactionId: String, amount: Long) = error("Not used")
    override fun currentUserId() = "user"
}
