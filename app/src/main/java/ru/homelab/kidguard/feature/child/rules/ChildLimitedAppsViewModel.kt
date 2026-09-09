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
import ru.homelab.kidguard.core.domain.usecase.bonusMinutesLeft
import javax.inject.Inject

/**
 * Приложение на детском экране «Лимиты» (только просмотр).
 *
 * Попадает сюда по одной из двух причин, и они независимы: у приложения есть личный дневной лимит
 * ([limitMinutes] не null) либо родитель выдал ему дополнительное время поверх общего лимита
 * ([bonusLeftMinutes] > 0). Второе ребёнку важно видеть не меньше первого: это единственное, чем
 * он может пользоваться, когда время на телефон закончилось.
 */
data class ChildLimitedAppUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    /** Дневной лимит с учётом выданного на сегодня бонуса (минут); null — своего лимита нет. */
    val limitMinutes: Int?,
    /** Потрачено этим приложением сегодня (минут). */
    val spentMinutes: Int,
    /** Остаток личного лимита; <= 0 — исчерпан. Осмыслен только при заданном [limitMinutes]. */
    val leftMinutes: Int,
    /** Остаток выданного родителем дополнительного времени (минут); 0 — не выдавали или потрачено. */
    val bonusLeftMinutes: Int = 0
) {
    /** Приложение сейчас открыто дополнительным временем поверх исчерпанного общего лимита. */
    val hasBonusAccess: Boolean get() = bonusLeftMinutes > 0
}

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
                    bonusRepository.appBonusMinutes(today),
                    usageRepository.appBonusSpentByPackage(today)
                ) { limits, usedSeconds, bonus, bonusSpent ->
                    // Показываем и приложения без своего лимита, которым выдали дополнительное
                    // время: для ребёнка это единственное, что открыто, когда общее время вышло.
                    val bonusPackages = bonus.keys.filter { pkg ->
                        pkg.isNotEmpty() &&
                            bonusMinutesLeft(bonus[pkg] ?: 0, bonusSpent[pkg] ?: 0) > 0
                    }
                    (limits.keys + bonusPackages).map { packageName ->
                        val limitMinutesRaw = limits[packageName]
                        // Формула та же, что в TodayViewModel.computeRules — итоговый остаток
                        // должен совпадать с превью на экране «Сегодня».
                        val limitMinutes = limitMinutesRaw?.plus(bonus[packageName] ?: 0)
                        val spentMinutes = (usedSeconds[packageName] ?: 0) / 60
                        val app = knownApps[packageName]
                        ChildLimitedAppUi(
                            packageName = packageName,
                            label = app?.label ?: packageName,
                            icon = app?.icon,
                            limitMinutes = limitMinutes,
                            spentMinutes = spentMinutes,
                            leftMinutes = (limitMinutes ?: 0) - spentMinutes,
                            bonusLeftMinutes = bonusMinutesLeft(
                                bonus[packageName] ?: 0,
                                bonusSpent[packageName] ?: 0
                            )
                        )
                    }.sortedBy { it.label.lowercase() }
                }
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
