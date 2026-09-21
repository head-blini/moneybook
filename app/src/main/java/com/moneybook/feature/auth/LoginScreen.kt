package com.moneybook.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val enabled = !state.loading && email.isNotBlank() && password.isNotBlank()
    Column(
        Modifier.fillMaxSize().testTag("screen_Login").padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("MoneyBook", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("두 사람이 함께 쓰는 가계부")
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(email, { email = it }, label = { Text("이메일") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_email"))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("비밀번호") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("auth_password"))
        state.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button({ onSignIn(email, password) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("auth_sign_in")) {
            Text(if (state.loading) "처리 중…" else "로그인")
        }
        TextButton({ onSignUp(email, password) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("auth_sign_up")) {
            Text("새 계정 만들기")
        }
    }
}
