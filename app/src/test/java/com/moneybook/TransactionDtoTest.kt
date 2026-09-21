package com.moneybook

import com.moneybook.data.remote.supabase.TransactionInsertDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDtoTest {
    @Test fun manualSourceIsIncludedInInsertPayload() {
        val payload = Json.encodeToString(
            TransactionInsertDto(
                id = "transaction",
                householdId = "household",
                createdBy = "user",
                paidBy = "user",
                type = "EXPENSE",
                scope = "SHARED",
                amount = 1_000,
                categoryId = "category",
                cardId = null,
                merchant = null,
                memo = null,
                transactionAt = "2026-09-21T00:00:00Z",
                source = "MANUAL",
            ),
        )

        assertTrue(payload.contains("\"source\":\"MANUAL\""))
    }
}
