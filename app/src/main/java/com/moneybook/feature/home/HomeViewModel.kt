package com.moneybook.feature.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecentTransactionPreview(val merchant: String, val amount: Long, val detail: String)
data class HomeUiState(
    val month: String = "9월",
    val totalSpending: Long = 2_431_500L,
    val sharedSpending: Long = 1_080_000L,
    val sharedBudget: Long = 1_500_000L,
    val recentTransactions: List<RecentTransactionPreview> = listOf(
        RecentTransactionPreview("쿠팡", 54_900L, "나 · 공동 · 육아"),
        RecentTransactionPreview("스타벅스", 6_500L, "나 · 개인 · 카페"),
        RecentTransactionPreview("이마트", 87_200L, "상대 · 공동 · 식비"),
    ),
)

// PHASE_1_DEV: static display fixtures only; no budget or transaction business logic.
@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableState.asStateFlow()
}
