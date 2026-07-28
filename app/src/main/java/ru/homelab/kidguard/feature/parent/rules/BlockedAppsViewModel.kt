package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import javax.inject.Inject

/** Приложение в списке настройки запрета (веха 4.1.2). */
data class BlockedAppUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val blocked: Boolean,
    val isSystem: Boolean,
    val isRisky: Boolean
)

@HiltViewModel
class BlockedAppsViewModel @Inject constructor(
    private val childAppsProvider: ChildAppsProvider,
    private val policyRepository: PolicyRepository
) : ViewModel() {

    private val childApps = flow {
        emit(withContext(Dispatchers.Default) { childAppsProvider.loadActiveChildApps() })
    }

    /** `null` — список с сервера ещё грузится; пустой — устройство ребёнка его не прислало. */
    val apps: StateFlow<List<BlockedAppUi>?> =
        combine(childApps, policyRepository.blockedApps) { apps, blockedApps ->
            apps
                .map { app ->
                    BlockedAppUi(
                        packageName = app.packageName,
                        label = app.label,
                        icon = app.icon,
                        blocked = app.packageName in blockedApps,
                        isSystem = app.isSystem,
                        isRisky = app.isRisky
                    )
                }
                // Запрещённые приложения — вверх; внутри групп сохраняем алфавит (как в лимитах).
                .sortedWith(compareBy({ !it.blocked }, { it.label.lowercase() }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { policyRepository.setBlocked(packageName, blocked) }
    }
}
