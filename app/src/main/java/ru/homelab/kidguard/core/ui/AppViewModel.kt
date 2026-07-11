package ru.homelab.kidguard.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.homelab.kidguard.core.domain.model.Role
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import ru.homelab.kidguard.core.ui.navigation.Destinations
import javax.inject.Inject

/** Стартовое состояние приложения: пока читаем настройки — Loading, затем целевой маршрут. */
sealed interface AppStartState {
    data object Loading : AppStartState
    data class Ready(val startRoute: String) : AppStartState
}

/**
 * Определяет, куда вести пользователя при запуске, по роли устройства и состоянию сессии:
 * - роль не выбрана → онбординг;
 * - родитель без валидной Google-сессии → вход через Google; иначе → родительский режим;
 * - ребёнок без привязки устройства → экран pairing-кода; иначе → детский режим.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val startState: StateFlow<AppStartState> = combine(
        settingsRepository.setupCompleted,
        settingsRepository.role,
        authRepository.hasValidSession,
        authRepository.hasPairedDevice
    ) { completed, role, hasValidSession, hasPairedDevice ->
        val route = when {
            !completed || role == null -> Destinations.ONBOARDING
            role == Role.PARENT -> if (hasValidSession) Destinations.PARENT else Destinations.LOGIN
            else -> if (hasPairedDevice) Destinations.CHILD else Destinations.PAIRING
        }
        AppStartState.Ready(route)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppStartState.Loading
    )
}
