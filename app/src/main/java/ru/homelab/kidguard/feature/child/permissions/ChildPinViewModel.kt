package ru.homelab.kidguard.feature.child.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.security.PinVerifyResult
import javax.inject.Inject

/** Состояние экрана ввода PIN. */
data class ChildPinUiState(
    val entered: String = "",
    val attemptsLeft: Int? = null,
    val blockedSecondsLeft: Int = 0,
    val unlocked: Boolean = false
) {
    val isError: Boolean get() = attemptsLeft != null
    val isBlocked: Boolean get() = blockedSecondsLeft > 0
}

@HiltViewModel
class ChildPinViewModel @Inject constructor(
    private val pinGuard: PinGuard
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildPinUiState())
    val uiState: StateFlow<ChildPinUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun onDigit(digit: Int) {
        val state = _uiState.value
        if (state.isBlocked || state.entered.length >= PIN_LENGTH) return
        val entered = state.entered + digit
        _uiState.update { it.copy(entered = entered, attemptsLeft = null) }
        if (entered.length == PIN_LENGTH) verify(entered)
    }

    fun onBackspace() {
        val state = _uiState.value
        if (state.isBlocked || state.entered.isEmpty()) return
        _uiState.update { it.copy(entered = it.entered.dropLast(1), attemptsLeft = null) }
    }

    private fun verify(pin: String) {
        viewModelScope.launch {
            when (val result = pinGuard.verify(pin)) {
                is PinVerifyResult.Success -> _uiState.update { it.copy(unlocked = true) }
                is PinVerifyResult.NoPinSet -> _uiState.update { it.copy(unlocked = true) }
                is PinVerifyResult.Wrong -> _uiState.update {
                    it.copy(entered = "", attemptsLeft = result.attemptsLeft)
                }
                is PinVerifyResult.Blocked -> startCountdown(result.secondsLeft)
            }
        }
    }

    /** Живой отсчёт: цифра на экране должна убывать, иначе выглядит как зависание. */
    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        _uiState.update { it.copy(entered = "", attemptsLeft = null, blockedSecondsLeft = seconds) }
        countdownJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                delay(1000L)
                left--
                _uiState.update { it.copy(blockedSecondsLeft = left) }
            }
        }
    }

    private companion object {
        const val PIN_LENGTH = 4
    }
}
