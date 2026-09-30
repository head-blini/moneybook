package com.moneybook

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneybook.data.remote.supabase.TransactionDto
import com.moneybook.data.remote.supabase.TransactionRefundDto
import com.moneybook.data.repository.toDomain
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the device's java.time implementation, not just the desktop JDK. */
@RunWith(AndroidJUnit4::class)
class TransactionTimestampTest {
    private val timestamps = listOf(
        "2026-09-21T00:00:00Z",
        "2026-09-21T00:00:00+00:00",
        "2026-09-21T09:00:00+09:00",
        "2026-09-20T19:00:00-05:00",
    )

    @Test fun transactionDatesAcceptPostgresOffsets() {
        for (timestamp in timestamps) {
            val dto = TransactionDto(
                id = "transaction", householdId = "household", createdBy = "user",
                paidBy = "user", type = "EXPENSE", scope = "SHARED", amount = 1000,
                categoryId = "category", transactionAt = timestamp, status = "CONFIRMED",
            )
            assertEquals(timestamp, Instant.parse("2026-09-21T00:00:00Z"), dto.toDomain().transactionAt)
        }
    }

    @Test fun refundDatesPreservePostgresMicroseconds() {
        val dto = TransactionRefundDto(
            "refund", "transaction", 100, "CONFIRMED", "2026-09-21T09:00:00.123456+09:00",
        )
        assertEquals(Instant.parse("2026-09-21T00:00:00.123456Z"), dto.toDomain().refundedAt)
    }
}
