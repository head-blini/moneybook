package com.moneybook.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun HouseholdSetupScreen(
    state: HouseholdSetupUiState,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().testTag("screen_HouseholdSetup").padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("가구 설정", style = MaterialTheme.typography.headlineLarge)
        Text("새 가구를 만들거나 받은 초대 코드로 참여하세요.")
        OutlinedTextField(name, { name = it }, label = { Text("가구 이름") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("household_name"))
        Button({ onCreate(name) }, enabled = !state.loading && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("household_create")) {
            Text("가구 만들기")
        }
        HorizontalDivider()
        OutlinedTextField(code, { code = it.uppercase() }, label = { Text("초대 코드") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("invitation_code"))
        Button({ onJoin(code) }, enabled = !state.loading && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("household_join")) {
            Text("초대 코드로 참여")
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }
    }
}
