package ru.homelab.kidguard.feature.child.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Состояние остатка времени на сегодня для детского экрана «Сегодня».
 * Разделено по видам, потому что макет рисует их принципиально по-разному:
 * кольцо прогресса для [Remaining] и карточки без прогрессбара для [Expired]/[NoLimit].
 */
sealed interface TodayTimeState {

    /** На сегодня дневной лимит не задан — «свободный день». */
    data object NoLimit : TodayTimeState

    /** Лимит есть, время осталось: кольцо [minutesLeft] из [totalMinutes] (лимит + бонус). */
    data class Remaining(val minutesLeft: Int, val totalMinutes: Int) : TodayTimeState

    /** Дневной лимит исчерпан — «время вышло»; [totalMinutes] = весь бюджет дня (лимит + бонус). */
    data class Expired(val totalMinutes: Int) : TodayTimeState
}

/** Группа правил для мини-списка «Мои правила»: количество и до трёх названий для превью. */
data class RuleGroup(
    val count: Int,
    val previewLabels: List<String>
)

/**
 * Группа «С лимитом»: сколько приложений под личным лимитом и остаток у первого из них
 * (по алфавиту) для строки превью. [firstMinutesLeft] == null — лимитированных приложений нет;
 * значение <= 0 означает, что личный лимит исчерпан.
 */
data class LimitedGroup(
    val count: Int,
    val firstLabel: String?,
    val firstMinutesLeft: Int?
)

/** Всё, что рисует детский экран «Сегодня». */
data class TodayUiState(
    val childName: String,
    val childAvatar: Int,
    val time: TodayTimeState,
    val bonusMinutes: Int,
    val alwaysAllowed: RuleGroup,
    val limited: LimitedGroup,
    val blocked: RuleGroup
)

/**
 * ViewModel детского экрана «Сегодня» (веха 4.1.3). Собирает в единый [TodayUiState]:
 * профиль привязанного ребёнка (имя/аватар), остаток дневного времени с учётом бонуса и
 * прозрачную для ребёнка сводку правил («всегда доступные», «с лимитом», «запрещённые»).
 *
 * Названия приложений берём с ЛОКАЛЬНОГО устройства ребёнка ([InstalledAppsSource]) — они у него
 * установлены, поэтому дополнительный запрос на сервер не нужен. Список читается один раз при
 * входе на экран (пакеты в политике меняются редко), а числа/остатки обновляются реактивно.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val installedAppsSource: InstalledAppsSource
) : ViewModel() {

    private data class TimeAndBonus(val state: TodayTimeState, val bonusMinutes: Int)

    private data class RulesData(
        val alwaysAllowed: RuleGroup,
        val limited: LimitedGroup,
        val blocked: RuleGroup
    )

    /** `null` — данные ещё собираются (первый кадр). */
    val uiState: StateFlow<TodayUiState?> = flow {
        val today = currentDateProvider.today()
        // Пакет → человекочитаемое имя. Если PackageManager недоступен — покажем имена пакетов.
        val labels: Map<String, String> = runCatching {
            installedAppsSource.launchableApps().associate { it.packageName to it.label }
        }.getOrDefault(emptyMap())

        val timeFlow = combine(
            policyRepository.dailyLimits,
            usageRepository.screenTimeSeconds(today),
            bonusRepository.phoneBonusMinutes(today)
        ) { limits, usedSeconds, bonusMinutes ->
            computeTime(limits, today, usedSeconds, bonusMinutes)
        }

        val rulesFlow = combine(
            policyRepository.whitelist,
            policyRepository.blockedApps,
            policyRepository.appLimits,
            usageRepository.appScreenTimeByPackage(today),
            bonusRepository.appBonusMinutes(today)
        ) { whitelist, blocked, appLimits, appUsedSeconds, appBonus ->
            computeRules(labels, whitelist, blocked, appLimits, appUsedSeconds, appBonus)
        }

        val combined = combine(
            authRepository.childProfile,
            timeFlow,
            rulesFlow
        ) { profile, time, rules ->
            TodayUiState(
                childName = profile?.name.orEmpty(),
                childAvatar = profile?.avatar ?: 0,
                time = time.state,
                bonusMinutes = time.bonusMinutes,
                alwaysAllowed = rules.alwaysAllowed,
                limited = rules.limited,
                blocked = rules.blocked
            )
        }
        emitAll(combined)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun computeTime(
        limits: DailyLimits,
        today: LocalDate,
        usedSeconds: Int,
        bonusMinutes: Int
    ): TimeAndBonus {
        val limitMinutes = limits.limitFor(today.dayOfWeek)
            ?: return TimeAndBonus(TodayTimeState.NoLimit, bonusMinutes = 0)
        // Бонус на сегодня прибавляется к бюджету дня — как в ObserveLimitStateUseCase.
        val totalMinutes = limitMinutes + bonusMinutes
        val minutesLeft = totalMinutes - usedSeconds / 60
        val state = if (minutesLeft <= 0) {
            TodayTimeState.Expired(totalMinutes)
        } else {
            TodayTimeState.Remaining(minutesLeft, totalMinutes)
        }
        return TimeAndBonus(state, bonusMinutes)
    }

    private fun computeRules(
        labels: Map<String, String>,
        whitelist: Set<String>,
        blocked: Set<String>,
        appLimits: Map<String, Int>,
        appUsedSeconds: Map<String, Int>,
        appBonus: Map<String, Int>
    ): RulesData {
        val limitedFirst = appLimits.keys
            .sortedBy { labelOf(labels, it).lowercase() }
            .firstOrNull()
        val limited = LimitedGroup(
            count = appLimits.size,
            firstLabel = limitedFirst?.let { labelOf(labels, it) },
            firstMinutesLeft = limitedFirst?.let { pkg ->
                (appLimits[pkg] ?: 0) + (appBonus[pkg] ?: 0) - (appUsedSeconds[pkg] ?: 0) / 60
            }
        )
        return RulesData(
            alwaysAllowed = groupOf(labels, whitelist),
            limited = limited,
            blocked = groupOf(labels, blocked)
        )
    }

    private fun groupOf(labels: Map<String, String>, packages: Set<String>): RuleGroup {
        val preview = packages
            .map { labelOf(labels, it) }
            .sortedBy { it.lowercase() }
            .take(PREVIEW_LIMIT)
        return RuleGroup(count = packages.size, previewLabels = preview)
    }

    private fun labelOf(labels: Map<String, String>, packageName: String): String =
        labels[packageName] ?: packageName

    private companion object {
        const val PREVIEW_LIMIT = 3
    }
}
