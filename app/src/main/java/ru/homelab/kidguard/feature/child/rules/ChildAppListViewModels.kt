package ru.homelab.kidguard.feature.child.rules

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import javax.inject.Inject

/** Строка простого списка приложений на детских экранах «Запрещено» / «Доступно». */
data class ChildAppUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/** ViewModel детского экрана «Запрещено» (только просмотр). Источник — [PolicyRepository.blockedApps]. */
@HiltViewModel
class ChildBlockedAppsViewModel @Inject constructor(
    policyRepository: PolicyRepository,
    childLocalAppsProvider: ChildLocalAppsProvider
) : ViewModel() {

    /** null — грузится. Источник — PolicyRepository.blockedApps. */
    val apps: StateFlow<List<ChildAppUi>?> =
        childAppListFlow(policyRepository.blockedApps, childLocalAppsProvider)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** ViewModel детского экрана «Доступно» (только просмотр). Источник — [PolicyRepository.whitelist]. */
@HiltViewModel
class ChildAllowedAppsViewModel @Inject constructor(
    policyRepository: PolicyRepository,
    childLocalAppsProvider: ChildLocalAppsProvider
) : ViewModel() {

    /** null — грузится. Источник — PolicyRepository.whitelist. */
    val apps: StateFlow<List<ChildAppUi>?> =
        childAppListFlow(policyRepository.whitelist, childLocalAppsProvider)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Общая логика обоих простых списков: имена/иконки читаем один раз при входе на экран (пакеты
 * в политике меняются редко), сами пакеты — реактивно из [packages]. Дата не участвует — в
 * отличие от лимитов и статистики, простой список «запрещено/доступно» от неё не зависит.
 */
private fun childAppListFlow(
    packages: Flow<Set<String>>,
    provider: ChildLocalAppsProvider
): Flow<List<ChildAppUi>> = flow<List<ChildAppUi>> {
    val known = provider.loadByPackage()
    emitAll(
        packages.map { set ->
            set.map { packageName ->
                val app = known[packageName]
                ChildAppUi(
                    packageName = packageName,
                    label = app?.label ?: packageName,
                    icon = app?.icon
                )
            }.sortedBy { it.label.lowercase() }
        }
    )
}
