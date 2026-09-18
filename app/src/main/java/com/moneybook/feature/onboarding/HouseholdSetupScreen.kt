package com.moneybook.feature.onboarding

import androidx.compose.runtime.Composable
import com.moneybook.core.ui.PlaceholderScreen

// PHASE_1_DEV: replace temporary onboarding with real session/household state in Phase 2.
@Composable
fun HouseholdSetupScreen(onContinue: () -> Unit) {
    PlaceholderScreen("우리 가계부 만들기", "개발용 임시 화면 · 가구를 생성하거나 가입하지 않습니다.", "screen_HouseholdSetup", actionLabel = "개발용 다음", onAction = onContinue)
}
