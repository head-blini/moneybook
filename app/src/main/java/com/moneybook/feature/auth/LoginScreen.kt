package com.moneybook.feature.auth

import androidx.compose.runtime.Composable
import com.moneybook.core.ui.PlaceholderScreen

// PHASE_1_DEV: replace temporary onboarding with real session/household state in Phase 2.
@Composable
fun LoginScreen(onContinue: () -> Unit) {
    PlaceholderScreen("로그인", "개발용 임시 화면 · 실제 인증 없이 다음 단계로 이동합니다.", "screen_Login", actionLabel = "개발용 시작", onAction = onContinue)
}
