package com.moneybook.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.moneybook.domain.model.Household

@Composable
fun SettingsScreen(
    household: Household,
    state: HouseholdSettingsUiState,
    onCreateInvitation: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag("screen_Settings").padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineLarge)
        Text(household.name, style = MaterialTheme.typography.titleLarge)
        Text("구성원 ${household.members.size}/2")
        household.members.forEach { member ->
            Text("${if (member.role == "OWNER") "소유자" else "구성원"} · ${member.userId.take(8)}…")
        }
        if (household.members.size < 2) {
            Button(onClick = onCreateInvitation, enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("create_invitation")) {
                Text(if (state.loading) "생성 중…" else "초대 코드 만들기")
            }
        }
        state.invitationCode?.let {
            Text("초대 코드", style = MaterialTheme.typography.labelLarge)
            Text(it, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("invitation_result"))
            Text("24시간 동안 한 번만 사용할 수 있습니다.")
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().testTag("refresh_household")) {
            Text("가구 정보 새로고침")
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("로그아웃")
        }
    }
}
