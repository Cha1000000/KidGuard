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
 * Определяет, куда вести пользователя при запуске: в мастер первичной настройки (роль ещё не
 * выбрана), в экран входа (роль выбрана, но сохранённой рабочей сессии нет — не входили или
 * истёк JWT — веха 4), либо сразу в граф родителя/ребёнка.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val startState: StateFlow<AppStartState> = combine(
        settingsRepository.setupCompleted,
        settingsRepository.role,
        authRepository.hasValidSession
    ) { completed, role, hasValidSession ->
        val route = when {
            !completed || role == null -> Destinations.ONBOARDING
            !hasValidSession -> Destinations.LOGIN
            role == Role.PARENT -> Destinations.PARENT
            else -> Destinations.CHILD
        }
        AppStartState.Ready(route)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppStartState.Loading
    )
}
