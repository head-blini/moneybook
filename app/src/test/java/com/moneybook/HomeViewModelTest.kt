package com.moneybook

import com.moneybook.feature.home.HomeViewModel
import com.moneybook.feature.home.formatKrw
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test fun homeExposesExpectedStaticPreview() {
        val state = HomeViewModel().uiState.value
        assertEquals(2_431_500L, state.totalSpending)
        assertEquals(1_080_000L, state.sharedSpending)
        assertEquals(1_500_000L, state.sharedBudget)
        assertEquals(listOf("쿠팡", "스타벅스", "이마트"), state.recentTransactions.map { it.merchant })
    }

    @Test fun wonFormattingPreservesIntegerPrecision() {
        assertEquals("₩54,900", formatKrw(54_900L))
        assertEquals("₩9,223,372,036,854,775,807", formatKrw(Long.MAX_VALUE))
    }
}
