package ru.homelab.kidguard.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import javax.inject.Inject

sealed interface SignInUiState {
    data object Idle : SignInUiState
    data object Loading : SignInUiState
    data object Success : SignInUiState
    data object Error : SignInUiState
}

/** Google-вход — только роль родителя (ребёнок привязывается pairing-кодом, см. PairingScreen). */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Idle)
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /** Обменивает полученный от Google ID-token на сессию сервера. */
    fun signIn(googleIdToken: String) {
        if (_uiState.value == SignInUiState.Loading) return
        _uiState.value = SignInUiState.Loading
        viewModelScope.launch {
            val result = authRepository.signInWithGoogleIdToken(googleIdToken)
            _uiState.value = result.fold(
                onSuccess = { SignInUiState.Success },
                onFailure = { SignInUiState.Error }
            )
        }
    }

    /** Сбросить состояние ошибки перед повторной попыткой. */
    fun resetError() {
        if (_uiState.value == SignInUiState.Error) {
            _uiState.value = SignInUiState.Idle
        }
    }
}
