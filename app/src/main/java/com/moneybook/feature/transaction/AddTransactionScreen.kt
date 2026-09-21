package com.moneybook.feature.transaction

import androidx.compose.runtime.Composable
import com.moneybook.core.ui.PlaceholderScreen

// Phase 1 placeholder retained until transaction persistence is implemented.
@Composable
fun AddTransactionScreen(onClose: () -> Unit) {
    PlaceholderScreen("거래 추가", "입력과 저장 기능은 다음 단계에서 제공됩니다.", "screen_AddTransaction", actionLabel = "닫기", onAction = onClose)
}
