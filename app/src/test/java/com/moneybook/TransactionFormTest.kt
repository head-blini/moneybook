package com.moneybook

import com.moneybook.feature.transaction.FormResult
import com.moneybook.feature.transaction.TransactionFormState
import com.moneybook.feature.transaction.validateAndBuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFormTest {
    @Test fun positiveIntegerAmountAndSeoulDateCreateDraft() {
        val result = TransactionFormState(
            amount = "54900",
            date = "2026-09-21",
            time = "19:30",
            categoryId = "food",
        ).validateAndBuild()

        assertTrue(result is FormResult.Valid)
        result as FormResult.Valid
        assertEquals(54_900L, result.draft.amount)
        assertEquals("2026-09-21T10:30:00Z", result.draft.transactionAt.toString())
    }

    @Test fun zeroAndMalformedAmountAreRejected() {
        assertTrue(TransactionFormState(amount = "0", categoryId = "food").validateAndBuild() is FormResult.Invalid)
        assertTrue(TransactionFormState(amount = "", categoryId = "food").validateAndBuild() is FormResult.Invalid)
    }

    @Test fun categoryAndValidDateAreRequired() {
        assertTrue(TransactionFormState(amount = "1000").validateAndBuild() is FormResult.Invalid)
        assertTrue(TransactionFormState(amount = "1000", categoryId = "food", date = "2026-02-30")
            .validateAndBuild() is FormResult.Invalid)
    }
}
