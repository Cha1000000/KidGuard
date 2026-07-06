package ru.homelab.kidguard.feature.parent.rules

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @param:ApplicationContext private val context: Context,
    private val policyRepository: PolicyRepository
) : ViewModel() {

    private val installedApps = flow {
        emit(withContext(Dispatchers.Default) { loadLaunchableApps() })
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

    private data class LoadedApp(val packageName: String, val label: String, val icon: ImageBitmap)

    private fun loadLaunchableApps(): List<LoadedApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.packageName }
            .map {
                LoadedApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm).toBitmap(width = ICON_PX, height = ICON_PX).asImageBitmap()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        const val ICON_PX = 96
    }
}
