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

/**
 * Почему вход не удался. Причины разведены, потому что пользователю нужны разные подсказки:
 * при [NO_GOOGLE_ACCOUNT] надо идти в настройки телефона, а при [SERVER] — проверить интернет.
 * Текст выбирает UI: ViewModel про строковые ресурсы не знает.
 */
enum class SignInFailure {

    /** На устройстве нет ни одного Google-аккаунта. */
    NO_GOOGLE_ACCOUNT,

    /** Google не выдал токен: нет сети, сбой Сервисов Google Play, проблема конфигурации OAuth. */
    GOOGLE_UNAVAILABLE,

    /** Токен получен, но обменять его на сессию нашего сервера не вышло. */
    SERVER
}

sealed interface SignInUiState {
    data object Idle : SignInUiState
    data object Loading : SignInUiState
    data object Success : SignInUiState
    data class Error(val failure: SignInFailure) : SignInUiState
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
                onFailure = { SignInUiState.Error(SignInFailure.SERVER) }
            )
        }
    }

    /**
     * Google не отдал токен — до нашего сервера дело не дошло. Раньше этот случай нигде не
     * фиксировался, и экран молча оставался в исходном состоянии.
     */
    fun onGoogleSignInFailed(failure: SignInFailure) {
        _uiState.value = SignInUiState.Error(failure)
    }

    /** Сбросить состояние ошибки перед повторной попыткой. */
    fun resetError() {
        if (_uiState.value is SignInUiState.Error) {
            _uiState.value = SignInUiState.Idle
        }
    }
}
