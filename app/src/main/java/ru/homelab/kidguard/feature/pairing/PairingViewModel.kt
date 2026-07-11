package ru.homelab.kidguard.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import javax.inject.Inject

const val PAIRING_CODE_LENGTH = 6

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Loading : PairingUiState
    data object Success : PairingUiState
    data object Error : PairingUiState
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    /** Принимает ввод, оставляя только цифры и не длиннее 6; сбрасывает прошлую ошибку. */
    fun onCodeChange(input: String) {
        _code.value = input.filter { it.isDigit() }.take(PAIRING_CODE_LENGTH)
        if (_uiState.value == PairingUiState.Error) {
            _uiState.value = PairingUiState.Idle
        }
    }

    fun submit() {
        if (_code.value.length != PAIRING_CODE_LENGTH || _uiState.value == PairingUiState.Loading) return
        _uiState.value = PairingUiState.Loading
        viewModelScope.launch {
            val result = authRepository.pairDeviceWithCode(_code.value)
            _uiState.value = result.fold(
                onSuccess = { PairingUiState.Success },
                onFailure = { PairingUiState.Error }
            )
        }
    }
}
