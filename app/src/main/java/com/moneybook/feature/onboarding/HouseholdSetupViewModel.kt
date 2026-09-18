package com.moneybook.feature.onboarding

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

data class HouseholdSetupUiState(val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class HouseholdSetupViewModel @Inject constructor(private val repository: HouseholdRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HouseholdSetupUiState())
    val state: StateFlow<HouseholdSetupUiState> = mutableState.asStateFlow()

    fun create(name: String, onSuccess: () -> Unit) = submit({ repository.createHousehold(name) }, onSuccess)
    fun join(code: String, onSuccess: () -> Unit) = submit({ repository.joinHousehold(code) }, onSuccess)

    private fun submit(request: suspend () -> AppResult<*>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            mutableState.value = HouseholdSetupUiState(loading = true)
            when (val result = request()) {
                is AppResult.Success -> {
                    mutableState.value = HouseholdSetupUiState()
                    onSuccess()
                }
                is AppResult.Error -> mutableState.value = HouseholdSetupUiState(error = result.message)
            }
        }
    }
}
