package ru.homelab.kidguard.feature.child.rules

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.core.domain.repository.todayFlow
import java.time.LocalDate
import javax.inject.Inject

/** Строка «по приложениям · сегодня» на детском экране статистики. */
data class ChildStatsAppUi(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val minutes: Int,
    /** Доля этого приложения в суммарном времени за день (0f..1f) — ширина полоски. */
    val share: Float
)

/** Состояние детского экрана статистики за сегодня. */
data class ChildTodayStatsUiState(
    /** Всё экранное время за сегодня (сумма по приложениям). */
    val totalMinutes: Int,
    /** Часть [totalMinutes], расходующая дневной лимит (без «Всегда доступных» и лаунчера). */
    val limitedMinutes: Int,
    /** Дневной лимит с учётом бонуса; null — на сегодня лимита нет. */
    val limitMinutes: Int?,
    /** По убыванию времени. */
    val apps: List<ChildStatsAppUi>
) {
    /** Время в приложениях, которые лимит не закрывает. */
    val outsideLimitMinutes: Int get() = (totalMinutes - limitedMinutes).coerceAtLeast(0)
}

/**
 * ViewModel детского экрана «Сегодня» (статистика) — урезанная версия родительской «Статистики»:
 * только сегодняшний день, только локальные данные устройства ребёнка (без сети, без
 * ChildRepository.getChildUsage — тот тянет данные с сервера для родительского экрана).
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChildTodayStatsViewModel @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val childLocalAppsProvider: ChildLocalAppsProvider
) : ViewModel() {

    /** null — грузится. */
    val state: StateFlow<ChildTodayStatsUiState?> = flow<ChildTodayStatsUiState?> {
        emitAll(currentDateProvider.todayFlow().flatMapLatest { today -> statsFor(today) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun statsFor(today: LocalDate): Flow<ChildTodayStatsUiState> = flow<ChildTodayStatsUiState> {
        // Набор использованных пакетов известен только по факту чтения appScreenTimeByPackage —
        // берём его первый снимок и грузим имена/иконки один раз по нему (extraPackages
        // обязателен, иначе системные приложения без launcher-иконки останутся без названия).
        val firstUsedPackages = usageRepository.appScreenTimeByPackage(today).first().keys
        val knownApps = childLocalAppsProvider.loadByPackage(firstUsedPackages)

        emitAll(
            combine(
                policyRepository.dailyLimits,
                usageRepository.screenTimeSeconds(today),
                usageRepository.appScreenTimeByPackage(today),
                bonusRepository.phoneBonusMinutes(today)
            ) { limits, limitedSeconds, appSeconds, phoneBonus ->
                // Крупная цифра и доли — по всему экранному времени (сумма пер-app), а лимит
                // сравнивается только с той частью, которую он реально закрывает.
                val totalMinutes = appSeconds.values.sum() / 60
                val limitedMinutes = limitedSeconds / 60
                // Лимит + бонус — та же формула, что в TodayViewModel.computeTime, чтобы цифра
                // совпадала с кольцом на экране «Сегодня».
                val limitMinutes = limits.limitFor(today.dayOfWeek)?.plus(phoneBonus)
                val apps = appSeconds.entries
                    .mapNotNull { (packageName, seconds) ->
                        val minutes = seconds / 60
                        // Секунды есть, а минут ещё 0 — строку не показываем, чтобы не было «0 мин».
                        if (minutes <= 0) return@mapNotNull null
                        val app = knownApps[packageName]
                        ChildStatsAppUi(
                            packageName = packageName,
                            label = app?.label ?: packageName,
                            icon = app?.icon,
                            minutes = minutes,
                            share = if (totalMinutes > 0) minutes.toFloat() / totalMinutes else 0f
                        )
                    }
                    .sortedByDescending { it.minutes }
                ChildTodayStatsUiState(
                    totalMinutes = totalMinutes,
                    limitedMinutes = limitedMinutes,
                    limitMinutes = limitMinutes,
                    apps = apps
                )
            }
        )
    }
}
