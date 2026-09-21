package com.moneybook

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneybook.app.MainActivity
import com.moneybook.core.ui.MoneyBookTheme
import com.moneybook.domain.model.Category
import com.moneybook.domain.model.TransactionType
import com.moneybook.feature.transaction.AddTransactionScreen
import com.moneybook.feature.transaction.AddTransactionUiState
import com.moneybook.feature.transaction.TransactionFormOptions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val state = AddTransactionUiState(
        loading = false,
        options = TransactionFormOptions(
            categories = listOf(Category("food", "식비", TransactionType.EXPENSE, true)),
        ),
    )

    @Test fun transactionFormEmitsSaveAction() {
        var saved = false
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MoneyBookTheme {
                    AddTransactionScreen(
                        state = state,
                        onClose = {}, onType = {}, onScope = {}, onAmount = {}, onDate = {}, onTime = {},
                        onCategory = {}, onCard = {}, onMerchant = {}, onMemo = {}, onSave = { saved = true },
                    )
                }
            }
        }
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, saved) }
    }

    @Test fun transactionFormIsVisibleWithoutCards() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MoneyBookTheme {
                    AddTransactionScreen(
                        state = state,
                        onClose = {}, onType = {}, onScope = {}, onAmount = {}, onDate = {}, onTime = {},
                        onCategory = {}, onCard = {}, onMerchant = {}, onMemo = {}, onSave = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("transaction_amount").assertIsDisplayed()
        compose.onNodeWithTag("transaction_category").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("내가 결제").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("배우자가 결제").fetchSemanticsNodes().size)
    }
}
