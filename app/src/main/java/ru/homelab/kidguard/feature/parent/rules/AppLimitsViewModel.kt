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
    val icon: ImageBitmap?,
    /** Личный дневной лимит (минут), либо null, если не задан. */
    val limitMinutes: Int?,
    /** Израсходовано этим приложением сегодня (минут). */
    val spentMinutes: Int,
    /** Выданное приложению на сегодня «Дополнительное время» (минут, суммарно за день). */
    val bonusMinutes: Int,
    /** Сколько из выданного уже израсходовано (минут). Тратится только при исчерпанном общем лимите. */
    val bonusSpentMinutes: Int,
    val isSystem: Boolean,
    val isRisky: Boolean
) {
    /** Остаток выданного времени; ноль — либо не выдавали, либо уже израсходовано. */
    val bonusMinutesLeft: Int get() = (bonusMinutes - bonusSpentMinutes).coerceAtLeast(0)

    /** Приложение сейчас открыто выданным временем поверх общего лимита. */
    val hasActiveBonus: Boolean get() = bonusMinutesLeft > 0
}

@HiltViewModel
class AppLimitsViewModel @Inject constructor(
    private val childAppsProvider: ChildAppsProvider,
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider
) : ViewModel() {

    private val childApps = flow {
        emit(withContext(Dispatchers.Default) { childAppsProvider.loadActiveChildApps() })
    }

    /** `null` — список с сервера ещё грузится; пустой — устройство ребёнка его не прислало. */
    val apps: StateFlow<List<AppLimitUi>?> = flow {
        val today = currentDateProvider.today()
        val combined = combine(
            childApps,
            policyRepository.appLimits,
            usageRepository.appScreenTimeByPackage(today),
            bonusRepository.appBonusMinutes(today),
            usageRepository.appBonusSpentByPackage(today)
        ) { apps, limits, usedByPackage, bonusByPackage, bonusSpentByPackage ->
            apps
                .map { app ->
                    AppLimitUi(
                        packageName = app.packageName,
                        label = app.label,
                        icon = app.icon,
                        limitMinutes = limits[app.packageName],
                        spentMinutes = (usedByPackage[app.packageName] ?: 0) / 60,
                        bonusMinutes = bonusByPackage[app.packageName] ?: 0,
                        bonusSpentMinutes = (bonusSpentByPackage[app.packageName] ?: 0) / 60,
                        isSystem = app.isSystem,
                        isRisky = app.isRisky
                    )
                }
                // Приложения с заданным лимитом — вверх; внутри групп сохраняем алфавит.
                .sortedWith(compareBy({ it.limitMinutes == null }, { it.label.lowercase() }))
        }
        emitAll(combined)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAppLimit(packageName: String, minutes: Int?) {
        viewModelScope.launch { policyRepository.setAppLimit(packageName, minutes) }
    }

    /** Добавить приложению дополнительное время на сегодня (суммируется). */
    fun addAppBonus(packageName: String, minutes: Int) {
        viewModelScope.launch { bonusRepository.addBonus(currentDateProvider.today(), packageName, minutes) }
    }

    /**
     * Отменить дополнительное время приложения на сегодня.
     *
     * Расход сбрасывается вместе с выдачей: иначе он пережил бы отмену и погасил бы следующую
     * выдачу мгновенно. На детском устройстве то же самое делает применение политики при
     * синхронизации (`SyncRepositoryImpl.resetBonusSpentWhereReduced`) — здесь мы приводим в
     * порядок родительскую копию, по которой рисуется экран.
     */
    fun clearAppBonus(packageName: String) {
        viewModelScope.launch {
            val today = currentDateProvider.today()
            bonusRepository.clearBonus(today, packageName)
            usageRepository.resetAppBonusSpent(today, packageName)
        }
    }
}
