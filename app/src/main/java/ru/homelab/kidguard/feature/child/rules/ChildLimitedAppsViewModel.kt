package ru.homelab.kidguard.feature.child.rules

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.core.domain.repository.todayFlow
import javax.inject.Inject

/** Приложение с личным дневным лимитом на детском экране «Лимиты» (только просмотр). */
data class ChildLimitedAppUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    /** Дневной лимит с учётом выданного на сегодня бонуса (минут). */
    val limitMinutes: Int,
    /** Потрачено этим приложением сегодня (минут). */
    val spentMinutes: Int,
    /** Остаток; <= 0 — лимит исчерпан. */
    val leftMinutes: Int
)

/**
 * ViewModel детского экрана «Лимиты»: список приложений с личным дневным лимитом и остатком
 * на сегодня. Только просмотр — менять лимиты может только родитель.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChildLimitedAppsViewModel @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val childLocalAppsProvider: ChildLocalAppsProvider
) : ViewModel() {

    /** null — ещё грузится; пустой список — лимитов не задано. */
    val apps: StateFlow<List<ChildLimitedAppUi>?> = flow<List<ChildLimitedAppUi>?> {
        // Имена/иконки читаем один раз при входе на экран — пакеты в политике меняются редко.
        val knownApps = childLocalAppsProvider.loadByPackage()
        emitAll(
            currentDateProvider.todayFlow().flatMapLatest { today ->
                combine(
                    policyRepository.appLimits,
                    usageRepository.appScreenTimeByPackage(today),
                    bonusRepository.appBonusMinutes(today)
                ) { limits, usedSeconds, bonus ->
                    limits.entries.map { (packageName, limitMinutesRaw) ->
                        // Формула та же, что в TodayViewModel.computeRules — итоговый остаток
                        // должен совпадать с превью на экране «Сегодня».
                        val limitMinutes = limitMinutesRaw + (bonus[packageName] ?: 0)
                        val spentMinutes = (usedSeconds[packageName] ?: 0) / 60
                        val app = knownApps[packageName]
                        ChildLimitedAppUi(
                            packageName = packageName,
                            label = app?.label ?: packageName,
                            icon = app?.icon,
                            limitMinutes = limitMinutes,
                            spentMinutes = spentMinutes,
                            leftMinutes = limitMinutes - spentMinutes
                        )
                    }.sortedBy { it.label.lowercase() }
                }
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
