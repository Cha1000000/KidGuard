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
    val icon: ImageBitmap,
    val whitelisted: Boolean
)

@HiltViewModel
class WhitelistViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val policyRepository: PolicyRepository
) : ViewModel() {

    private val installedApps = flow {
        emit(withContext(Dispatchers.Default) { installedAppsProvider.loadLaunchableApps() })
    }

    val apps: StateFlow<List<WhitelistAppUi>> =
        combine(installedApps, policyRepository.whitelist) { apps, whitelist ->
            apps.map { app ->
                WhitelistAppUi(app.packageName, app.label, app.icon, app.packageName in whitelist)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        viewModelScope.launch { policyRepository.setWhitelisted(packageName, whitelisted) }
    }
}
