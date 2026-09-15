package ru.homelab.kidguard.feature.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.DailyBudgetState
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.DailyUsageBlock
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.DayBlockState
import ru.homelab.kidguard.core.domain.model.PenaltyGrant
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.model.dailyBudgetState
import ru.homelab.kidguard.core.domain.model.dayBlockState
import ru.homelab.kidguard.core.domain.model.isDayBlockActive
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PenaltyRepository
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import ru.homelab.kidguard.core.domain.repository.todayFlow
import ru.homelab.kidguard.core.domain.usecase.ObserveScheduleStateUseCase
import ru.homelab.kidguard.feature.parent.ChildUsageProvider
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Состояние блока «Штраф» на экране дневного лимита.
 *
 * Штрафовать можно только тогда, когда есть что снимать, поэтому у блока три несводимых
 * состояния, а не флаг «показывать/нет»: пока расход ребёнка едет с сервера, на его месте
 * стоит скелетон ([Loading]) — иначе максимум штрафа был бы неверным, а экран прыгал бы.
 */
sealed interface PenaltyUiState {

    /** Расход ребёнка ещё грузится — рисуем скелетон в габаритах готового блока. */
    data object Loading : PenaltyUiState

    /**
     * Снимать нечего и отменять нечего: лимита на сегодня нет, он исчерпан, родитель
     * заблокировал день или действует «Время учёбы»/«Время сна» — и штрафа при этом не
     * назначено. Блок не показываем совсем.
     */
    data object Unavailable : PenaltyUiState

    /**
     * Блок показываем. [remainingMinutes] — сколько времени у ребёнка осталось: это и есть
     * потолок штрафа; ноль означает «снимать больше нечего», и кнопки назначения прячутся.
     * [penalty] — уже назначенный на сегодня штраф (null — ещё не штрафовали).
     *
     * Ноль остатка при назначенном штрафе — не то же самое, что [Unavailable]: родитель мог
     * сгоряча снять всё время, и кнопка «Отменить» обязана остаться доступной.
     */
    data class Available(
        val remainingMinutes: Int,
        val penalty: PenaltyGrant?
    ) : PenaltyUiState
}

/**
 * Статус блокировки дня для экрана: само состояние и имя ребёнка для текстов плашки и диалогов.
 * Имя может быть неизвестно (список детей ещё не загружен или нет сети) — тексты тогда нейтральные.
 */
data class DayBlockUi(
    val state: DayBlockState = DayBlockState.NotBlocked,
    val childName: String? = null
)

/**
 * «Сегодня» во всех потоках экрана приходит из [todayFlow], а не берётся один раз при подписке:
 * экран может провисеть открытым через полночь, и тогда блокировка, штраф и бонус считались бы по
 * вчерашней дате. Тот же класс бага, что нашли на обкатке 22.07.2026 (см. `todayFlow`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyLimitViewModel @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val bonusRepository: BonusRepository,
    private val penaltyRepository: PenaltyRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val childUsageProvider: ChildUsageProvider,
    private val childRepository: ChildRepository,
    private val syncRepository: SyncRepository,
    observeScheduleState: ObserveScheduleStateUseCase
) : ViewModel() {

    /**
     * Активный ребёнок вместе с отчётом его телефона: из отчёта берётся подтверждение блокировки.
     * Грузится при входе на экран и перезапрашивается по WS-сигналу об изменении отчёта — так
     * плашка «ждём телефон» сменяется на «заблокировано» без перезахода.
     */
    private val activeChild = MutableStateFlow<Child?>(null)

    init {
        viewModelScope.launch {
            syncRepository.childHealthChanged.collect { childId ->
                if (childId == syncRepository.activeChildId.first()) loadActiveChild()
            }
        }
    }

    /** Состояние блокировки дня: маркеры общей политики плюс подтверждение с телефона ребёнка. */
    val dayBlock: StateFlow<DayBlockUi> = currentDateProvider.todayFlow().flatMapLatest { today ->
        combine(
            policyRepository.dailyUsageBlock,
            policyRepository.dailyUsageReset,
            policyRepository.dailyUsageUnblock,
            activeChild
        ) { block, reset, unblock, child ->
            DayBlockUi(
                state = dayBlockState(today, block, reset, unblock, child?.health?.dayBlock),
                childName = child?.name
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayBlockUi())

    val dailyLimits: StateFlow<DailyLimits> = policyRepository.dailyLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyLimits.EMPTY)

    /** Активное «Дополнительное время» телефона на сегодня (минут). */
    val phoneBonusMinutes: StateFlow<Int> = currentDateProvider.todayFlow()
        .flatMapLatest { bonusRepository.phoneBonusMinutes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Израсходованное ребёнком сегодня время.
     *
     * Приезжает с сервера, а не из локальной БД: на родительском устройстве расхода нет, его
     * присылает детское. Обновляется при входе на экран и уточняется локально после сброса
     * расхода — гонять сеть на каждое нажатие незачем.
     */
    private val usedMinutes = MutableStateFlow<UsageLoad>(UsageLoad.Loading)

    /**
     * Загрузка расхода: до ответа сервера — [Loading] (скелетон), при неудаче — [Failed].
     *
     * Неудача НЕ равна «расход нулевой»: без свежих данных нельзя ни ограничить штраф остатком,
     * ни решить, есть ли что снимать. Поэтому офлайн прячет блок, а не оставляет скелетон
     * крутиться вечно и не показывает кнопки с наугад взятым потолком.
     */
    private sealed interface UsageLoad {
        data object Loading : UsageLoad
        data object Failed : UsageLoad
        data class Loaded(val minutes: Int) : UsageLoad
    }

    val penaltyState: StateFlow<PenaltyUiState> = currentDateProvider.todayFlow().flatMapLatest { today ->
            combine(
                policyRepository.dailyLimits,
                bonusRepository.phoneBonusMinutes(today),
                penaltyRepository.phonePenalty(today),
                policyRepository.dailyUsageBlock,
                observeScheduleState()
            ) { limits, bonus, penalty, block, schedule ->
                RawInputs(limits, bonus, penalty, block, schedule)
            }.combine(
                combine(policyRepository.dailyUsageReset, policyRepository.dailyUsageUnblock) { r, u -> r to u }
            ) { raw, (reset, unblock) ->
                PenaltyInputs(
                    limits = raw.limits,
                    bonusMinutes = raw.bonusMinutes,
                    penalty = raw.penalty,
                    blocked = isDayBlockActive(today, raw.block, reset, unblock),
                    schedule = raw.schedule
                )
            }.combine(usedMinutes) { inputs, used ->
                penaltyState(today, inputs, used)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PenaltyUiState.Loading)

    private val _refreshingUsage = MutableStateFlow(false)

    /** Подтянуть свежий расход ребёнка (вход на экран, возврат из фона). */
    fun refreshUsage() {
        if (_refreshingUsage.value) return
        _refreshingUsage.value = true
        viewModelScope.launch { loadActiveChild() }
        viewModelScope.launch {
            val entries = childUsageProvider.loadActiveChildUsage(days = 1).getOrNull()
            usedMinutes.value = if (entries == null) {
                UsageLoad.Failed
            } else {
                val today = currentDateProvider.today()
                UsageLoad.Loaded((childUsageProvider.limitedSecondsByDate(entries)[today] ?: 0) / 60)
            }
            _refreshingUsage.value = false
        }
    }

    /** Сохранить лимит на день (minutes = null — без лимита). */
    fun setLimit(day: DayOfWeek, minutes: Int?) {
        viewModelScope.launch { policyRepository.setDailyLimit(day, minutes) }
    }

    /** Сохранить один и тот же лимит на все дни недели. */
    fun setLimitForAllDays(minutes: Int?) {
        viewModelScope.launch {
            DayOfWeek.entries.forEach { policyRepository.setDailyLimit(it, minutes) }
        }
    }

    /** Добавить телефону дополнительное время на сегодня (суммируется). */
    fun addPhoneBonus(minutes: Int) {
        viewModelScope.launch {
            val today = currentDateProvider.today()
            // Бонус во время блокировки снимает её (решение Володи 15.09.2026), но остаток до
            // блокировки не возвращает — время даёт сам бонус. Маркер нужен, потому что у бонуса
            // нет метки времени, и без него статус «заблокировано» остался бы висеть.
            if (dayBlock.value.state != DayBlockState.NotBlocked) {
                policyRepository.setDailyUsageUnblock(today, System.currentTimeMillis(), restoreRemaining = false)
            }
            bonusRepository.addBonus(today, null, minutes)
        }
    }

    /** Отменить дополнительное время телефона на сегодня. */
    fun clearPhoneBonus() {
        viewModelScope.launch { bonusRepository.clearBonus(currentDateProvider.today(), null) }
    }

    /**
     * Снять у ребёнка время. Больше остатка снять нельзя: обрезаем здесь, а не только ползунком
     * в UI, потому что остаток мог измениться, пока родитель тянул ползунок.
     */
    fun addPenalty(minutes: Int, comment: String) {
        val state = penaltyState.value as? PenaltyUiState.Available ?: return
        val applied = minutes.coerceIn(1, state.remainingMinutes)
        viewModelScope.launch {
            penaltyRepository.addPenalty(currentDateProvider.today(), null, applied, comment.trim())
        }
    }

    /** Переписать пояснение к уже назначенному штрафу, не трогая минуты. */
    fun setPenaltyComment(comment: String) {
        viewModelScope.launch {
            penaltyRepository.setComment(currentDateProvider.today(), null, comment.trim())
        }
    }

    /** Отменить назначенный на сегодня штраф целиком. */
    fun clearPenalty() {
        viewModelScope.launch { penaltyRepository.clearPenalty(currentDateProvider.today(), null) }
    }

    /** Сбросить израсходованное сегодня время: ставим маркер сброса с меткой времени нажатия. */
    fun resetTodayUsage() {
        viewModelScope.launch {
            policyRepository.setDailyUsageReset(currentDateProvider.today(), System.currentTimeMillis())
            // Расход обнулён — не ждём следующего захода на экран, чтобы блок штрафа сразу
            // показал верный остаток.
            usedMinutes.update { UsageLoad.Loaded(0) }
        }
    }

    /**
     * Снять блокировку дня и вернуть ребёнку время, которое оставалось у него до неё. Если
     * блокировка ещё не дошла до телефона, возвращать нечего — телефон применит обе команды разом
     * и итог будет «ничего не поменялось».
     */
    fun unblockToday() {
        viewModelScope.launch {
            policyRepository.setDailyUsageUnblock(
                currentDateProvider.today(),
                System.currentTimeMillis(),
                restoreRemaining = true
            )
        }
    }

    /** Заблокировать доступное на сегодня время: ставим маркер блокировки с меткой времени нажатия. */
    fun blockToday() {
        viewModelScope.launch {
            policyRepository.setDailyUsageBlock(currentDateProvider.today(), System.currentTimeMillis())
        }
    }

    private suspend fun loadActiveChild() {
        val activeId = syncRepository.activeChildId.first() ?: return
        // Нет сети — оставляем прежнее значение: плашка «ждём телефон» честнее, чем пропавший статус.
        val children = childRepository.listChildren().getOrNull() ?: return
        activeChild.value = children.firstOrNull { it.id == activeId }
    }

    private fun penaltyState(
        today: LocalDate,
        inputs: PenaltyInputs,
        usage: UsageLoad
    ): PenaltyUiState {
        val used = when (usage) {
            UsageLoad.Loading -> return PenaltyUiState.Loading
            // Расход не доехал (нет сети, сервер молчит) — блок прячем целиком, кроме случая с
            // уже назначенным штрафом: его отмена работает и офлайн, она локальная.
            UsageLoad.Failed -> return inputs.penalty
                ?.let { PenaltyUiState.Available(remainingMinutes = 0, penalty = it) }
                ?: PenaltyUiState.Unavailable

            is UsageLoad.Loaded -> usage.minutes
        }
        // Пока штраф назначен, блок остаётся на экране в любом случае: иначе снятое сгоряча
        // время нечем вернуть — кнопка «Отменить» живёт именно здесь.
        val applied = inputs.penalty

        // Блокировка родителем и расписание бьют независимо от остатка: снимать время у
        // ребёнка, который и так не может пользоваться телефоном, бессмысленно.
        if (inputs.blocked || inputs.schedule != ScheduleState.Inactive) {
            return applied?.let { PenaltyUiState.Available(remainingMinutes = 0, penalty = it) }
                ?: PenaltyUiState.Unavailable
        }

        // Тот же расчёт остатка, что показывает карточка на «Статистике».
        val budget = dailyBudgetState(
            limitMinutes = inputs.limits.limitFor(today.dayOfWeek),
            bonusMinutes = inputs.bonusMinutes,
            penaltyMinutes = applied?.minutes ?: 0,
            usedMinutes = used
        )
        return when (budget) {
            is DailyBudgetState.Remaining ->
                PenaltyUiState.Available(budget.leftMinutes, applied)

            // Лимита нет или он выбран до конца: снимать нечего, но отменить уже снятое можно.
            DailyBudgetState.NoLimit, is DailyBudgetState.Overrun ->
                applied?.let { PenaltyUiState.Available(remainingMinutes = 0, penalty = it) }
                    ?: PenaltyUiState.Unavailable
        }
    }

    /** Промежуточный кортеж: типизированный `combine` берёт максимум пять потоков. */
    private data class RawInputs(
        val limits: DailyLimits,
        val bonusMinutes: Int,
        val penalty: PenaltyGrant?,
        val block: DailyUsageBlock?,
        val schedule: ScheduleState
    )

    private data class PenaltyInputs(
        val limits: DailyLimits,
        val bonusMinutes: Int,
        val penalty: PenaltyGrant?,
        val blocked: Boolean,
        val schedule: ScheduleState
    )
}
