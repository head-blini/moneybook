package com.moneybook

import com.moneybook.data.repository.withConfirmedRefunds
import com.moneybook.domain.model.*
import com.moneybook.feature.home.recentTransactions
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionRefundAmountTest {
    @Test fun partialAndFullRefundsUpdateListAndHomeWithoutChangingOriginalAmounts() {
        val at = Instant.parse("2026-09-21T00:00:00Z")
        val rows = listOf(
            homeTransaction("partial", at, null, "food").copy(amount = 10_000),
            homeTransaction("full", at, null, "food").copy(amount = 5_000, status = TransactionStatus.CANCELED),
            homeTransaction("income", at, null, "food").copy(amount = 8_000, type = TransactionType.INCOME),
            homeTransaction("untouched", at, null, "food").copy(amount = 2_000),
        ).withConfirmedRefunds(listOf(
            TransactionRefund("a", "partial", 1_000, RefundStatus.CONFIRMED, at),
            TransactionRefund("b", "partial", 2_000, RefundStatus.CONFIRMED, at.plusSeconds(86400 * 40L)),
            TransactionRefund("c", "partial", 500, RefundStatus.PENDING, at),
            TransactionRefund("d", "partial", 500, RefundStatus.CANCELED, at),
            TransactionRefund("e", "full", 5_000, RefundStatus.CONFIRMED, at),
            TransactionRefund("f", "other", 9_000, RefundStatus.CONFIRMED, at),
        ))
        assertEquals(listOf(10_000L, 5_000L, 8_000L, 2_000L), rows.map { it.amount })
        assertEquals(listOf(7_000L, 0L, 8_000L, 2_000L), rows.map { it.netAmount })
        val home = recentTransactions(rows, emptyList()).associateBy { it.id }
        assertEquals(7_000L, home.getValue("partial").amount)
        assertEquals(10_000L, home.getValue("partial").originalAmount)
        assertEquals(0L, home.getValue("full").amount)
        assertEquals(8_000L, home.getValue("income").amount)
    }
}
