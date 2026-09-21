package com.moneybook

import com.moneybook.domain.model.RefundStatus
import com.moneybook.domain.model.TransactionStatus
import com.moneybook.feature.transaction.refundStatusLabel
import com.moneybook.feature.transaction.transactionStatusLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransactionStatusLabelTest {
    @Test fun transactionStatusesUseConsistentUserFacingLabels() {
        assertEquals("확정", transactionStatusLabel(TransactionStatus.CONFIRMED))
        assertEquals("전액 환불", transactionStatusLabel(TransactionStatus.CANCELED))
    }

    @Test fun refundStatusesNeverExposeRawEnumNames() {
        RefundStatus.entries.forEach { status ->
            val label = refundStatusLabel(status)
            assertFalse(label.contains("CONFIRMED"))
            assertFalse(label.contains("CANCELED"))
            assertFalse(label.contains("PENDING"))
        }
        assertEquals("환불 완료", refundStatusLabel(RefundStatus.CONFIRMED))
    }
}
