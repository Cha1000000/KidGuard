package ru.homelab.kidguard.feature.child.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.todayFlow
import java.time.LocalDate
import ru.homelab.kidguard.core.domain.repository.UsageRepository
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
    /** Всё экранное время за сегодня (не только то, что расходует лимит) — карточка «Сегодня». */
    val usedMinutes: Int,
    val alwaysAllowed: RuleGroup,
    val limited: LimitedGroup,
    val blocked: RuleGroup
)

/**
 * Состояние экрана «Сегодня» (Фаза 4 UI-аудита). Раньше экран умел только «грузится» (null
 * uiState) и «готово» — при сбое любого исходного потока (сеть, БД) пользователь видел вечный
 * спиннер без объяснений. Теперь есть явное [Error].
 */
sealed interface TodayScreenState {
    data object Loading : TodayScreenState
    data object Error : TodayScreenState
    data class Content(val ui: TodayUiState) : TodayScreenState
}

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
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val installedAppsSource: InstalledAppsSource
) : ViewModel() {

    private data class TimeAndBonus(val state: TodayTimeState, val bonusMinutes: Int, val usedMinutes: Int)

    private data class RulesData(
        val alwaysAllowed: RuleGroup,
        val limited: LimitedGroup,
        val blocked: RuleGroup
    )

    val uiState: StateFlow<TodayScreenState> = flow<TodayScreenState> {
        // Пакет → человекочитаемое имя. Если PackageManager недоступен — покажем имена пакетов.
        val labels: Map<String, String> = runCatching {
            installedAppsSource.launchableApps().associate { it.packageName to it.label }
        }.getOrDefault(emptyMap())
        // Дата — потоком: экран может остаться открытым через полночь (телефон на зарядке рядом
        // с кроватью), и тогда остаток дня показывался бы за вчера.
        emitAll(
            currentDateProvider.todayFlow()
                .flatMapLatest { today -> uiStateFor(today, labels) }
                .map { TodayScreenState.Content(it) }
        )
    }.catch { emit(TodayScreenState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayScreenState.Loading)

    private fun uiStateFor(today: LocalDate, labels: Map<String, String>): Flow<TodayUiState> = run {

        val timeFlow = combine(
            policyRepository.dailyLimits,
            usageRepository.screenTimeSeconds(today),
            usageRepository.appScreenTimeByPackage(today),
            bonusRepository.phoneBonusMinutes(today)
        ) { limits, limitedSeconds, appSeconds, bonusMinutes ->
            // Кольцо остатка считаем по времени, расходующему лимит, а карточку «Сегодня» —
            // по всему экранному времени: она ведёт на экран статистики, где показано всё.
            computeTime(limits, today, limitedSeconds, appSeconds.values.sum(), bonusMinutes)
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
                usedMinutes = time.usedMinutes,
                alwaysAllowed = rules.alwaysAllowed,
                limited = rules.limited,
                blocked = rules.blocked
            )
        }
        combined
    }

    /** Ребёнок выбрал свой аватар (веха 4.1.5) — сохраняется локально, на сервер не уходит. */
    fun chooseAvatar(index: Int) {
        viewModelScope.launch { authRepository.setChildLocalAvatar(index) }
    }

    /** Сброс к аватару, который выбрал родитель (серверному). */
    fun resetAvatar() {
        viewModelScope.launch { authRepository.clearChildLocalAvatar() }
    }

    private fun computeTime(
        limits: DailyLimits,
        today: LocalDate,
        limitedSeconds: Int,
        allScreenSeconds: Int,
        bonusMinutes: Int
    ): TimeAndBonus {
        val limitedMinutes = limitedSeconds / 60
        val screenMinutes = allScreenSeconds / 60
        val limitMinutes = limits.limitFor(today.dayOfWeek)
            ?: return TimeAndBonus(TodayTimeState.NoLimit, bonusMinutes = 0, usedMinutes = screenMinutes)
        // Бонус на сегодня прибавляется к бюджету дня — как в ObserveLimitStateUseCase.
        val totalMinutes = limitMinutes + bonusMinutes
        val minutesLeft = totalMinutes - limitedMinutes
        val state = if (minutesLeft <= 0) {
            TodayTimeState.Expired(totalMinutes)
        } else {
            TodayTimeState.Remaining(minutesLeft, totalMinutes)
        }
        return TimeAndBonus(state, bonusMinutes, usedMinutes = screenMinutes)
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
