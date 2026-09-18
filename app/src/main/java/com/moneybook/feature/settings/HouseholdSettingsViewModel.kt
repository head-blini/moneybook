package com.moneybook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneybook.core.result.AppResult
import com.moneybook.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HouseholdSettingsUiState(
    val loading: Boolean = false,
    val invitationCode: String? = null,
    val error: String? = null,
)

@HiltViewModel
class HouseholdSettingsViewModel @Inject constructor(
    private val repository: HouseholdRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HouseholdSettingsUiState())
    val state: StateFlow<HouseholdSettingsUiState> = mutableState.asStateFlow()

    fun createInvitation() {
        viewModelScope.launch {
            mutableState.value = HouseholdSettingsUiState(loading = true)
            mutableState.value = when (val result = repository.createInvitation()) {
                is AppResult.Success -> HouseholdSettingsUiState(invitationCode = result.value)
                is AppResult.Error -> HouseholdSettingsUiState(error = result.message)
            }
        }
    }
}
