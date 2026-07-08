package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import javax.inject.Inject

/** Приложение в списке настройки пер-app лимитов. */
data class AppLimitUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    /** Личный дневной лимит (минут), либо null, если не задан. */
    val limitMinutes: Int?,
    /** Израсходовано этим приложением сегодня (минут). */
    val spentMinutes: Int,
    /** Активное «Дополнительное время» приложения на сегодня (минут). */
    val bonusMinutes: Int
)

@HiltViewModel
class AppLimitsViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider
) : ViewModel() {

    private val installedApps = flow {
        emit(withContext(Dispatchers.Default) { installedAppsProvider.loadLaunchableApps() })
    }

    val apps: StateFlow<List<AppLimitUi>> = flow {
        val today = currentDateProvider.today()
        val combined = combine(
            installedApps,
            policyRepository.appLimits,
            usageRepository.appScreenTimeByPackage(today),
            bonusRepository.appBonusMinutes(today)
        ) { apps, limits, usedByPackage, bonusByPackage ->
            apps
                .map { app ->
                    AppLimitUi(
                        packageName = app.packageName,
                        label = app.label,
                        icon = app.icon,
                        limitMinutes = limits[app.packageName],
                        spentMinutes = (usedByPackage[app.packageName] ?: 0) / 60,
                        bonusMinutes = bonusByPackage[app.packageName] ?: 0
                    )
                }
                // Приложения с заданным лимитом — вверх; внутри групп сохраняем алфавит.
                .sortedWith(compareBy({ it.limitMinutes == null }, { it.label.lowercase() }))
        }
        emitAll(combined)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAppLimit(packageName: String, minutes: Int?) {
        viewModelScope.launch { policyRepository.setAppLimit(packageName, minutes) }
    }

    /** Добавить приложению дополнительное время на сегодня (суммируется). */
    fun addAppBonus(packageName: String, minutes: Int) {
        viewModelScope.launch { bonusRepository.addBonus(currentDateProvider.today(), packageName, minutes) }
    }

    /** Отменить дополнительное время приложения на сегодня. */
    fun clearAppBonus(packageName: String) {
        viewModelScope.launch { bonusRepository.clearBonus(currentDateProvider.today(), packageName) }
    }
}
