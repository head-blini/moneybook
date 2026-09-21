package com.moneybook.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.Household
import com.moneybook.domain.repository.AuthRepository
import com.moneybook.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppUiState {
    data object Loading : AppUiState
    data object ConfigurationRequired : AppUiState
    data object Unauthenticated : AppUiState
    data object HouseholdRequired : AppUiState
    data class Ready(val household: Household) : AppUiState
    data class Error(val message: String) : AppUiState
}

internal enum class AppRoute { LOGIN, HOUSEHOLD_SETUP, HOME }

internal fun resolveAppRoute(authenticated: Boolean, hasHousehold: Boolean): AppRoute = when {
    !authenticated -> AppRoute.LOGIN
    !hasHousehold -> AppRoute.HOUSEHOLD_SETUP
    else -> AppRoute.HOME
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = AppUiState.Loading
            if (!authRepository.isConfigured) {
                mutableState.value = AppUiState.ConfigurationRequired
                return@launch
            }
            if (!authRepository.hasSession()) {
                mutableState.value = AppUiState.Unauthenticated
                return@launch
            }
            mutableState.value = when (val result = householdRepository.currentHousehold()) {
                is AppResult.Success -> result.value?.let(AppUiState::Ready) ?: AppUiState.HouseholdRequired
                is AppResult.Error -> AppUiState.Error(result.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            mutableState.value = AppUiState.Unauthenticated
        }
    }
}
