package com.moneybook.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneybook.core.result.AppResult
import com.moneybook.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = false, val message: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    fun signIn(email: String, password: String, onSuccess: () -> Unit) =
        submit({ repository.signIn(email, password) }, null, onSuccess)

    fun signUp(email: String, password: String, onSuccess: () -> Unit) {
        if (password.length < 6) {
            mutableState.value = AuthUiState(message = "비밀번호는 6자 이상 입력해 주세요.")
            return
        }
        submit(
            { repository.signUp(email, password) },
            "가입을 완료했습니다. 이메일 확인이 켜져 있다면 확인 후 로그인해 주세요.",
            onSuccess,
        )
    }

    private fun submit(
        request: suspend () -> AppResult<Unit>,
        successMessage: String?,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            mutableState.value = AuthUiState(loading = true)
            when (val result = request()) {
                is AppResult.Success -> {
                    mutableState.value = AuthUiState(message = successMessage)
                    onSuccess()
                }
                is AppResult.Error -> mutableState.value = AuthUiState(message = result.message)
            }
        }
    }
}
