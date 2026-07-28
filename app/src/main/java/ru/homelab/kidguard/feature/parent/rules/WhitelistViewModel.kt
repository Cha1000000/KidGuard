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

/** Приложение в списке настройки белого списка. */
data class WhitelistAppUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val whitelisted: Boolean,
    val isSystem: Boolean,
    val isRisky: Boolean
)

@HiltViewModel
class WhitelistViewModel @Inject constructor(
    private val childAppsProvider: ChildAppsProvider,
    private val policyRepository: PolicyRepository
) : ViewModel() {

    private val childApps = flow {
        emit(withContext(Dispatchers.Default) { childAppsProvider.loadActiveChildApps() })
    }

    /** `null` — список с сервера ещё грузится; пустой — устройство ребёнка его не прислало. */
    val apps: StateFlow<List<WhitelistAppUi>?> =
        combine(childApps, policyRepository.whitelist) { apps, whitelist ->
            apps.map { app ->
                WhitelistAppUi(
                    packageName = app.packageName,
                    label = app.label,
                    icon = app.icon,
                    whitelisted = app.packageName in whitelist,
                    isSystem = app.isSystem,
                    isRisky = app.isRisky
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        viewModelScope.launch { policyRepository.setWhitelisted(packageName, whitelisted) }
    }
}
