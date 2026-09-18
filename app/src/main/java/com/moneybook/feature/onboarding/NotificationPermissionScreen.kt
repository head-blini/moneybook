package com.moneybook.feature.onboarding

import androidx.compose.runtime.Composable
import com.moneybook.core.ui.PlaceholderScreen

// PHASE_1_DEV: replace temporary onboarding with real session/household state in Phase 2.
@Composable
fun NotificationPermissionScreen(onContinue: () -> Unit) {
    PlaceholderScreen("결제 알림 연결", "개발용 임시 화면 · 알림 접근 권한을 요청하거나 결제 알림을 수집하지 않습니다.", "screen_NotificationPermission", actionLabel = "개발용 홈으로", onAction = onContinue)
}
