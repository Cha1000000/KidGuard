package ru.homelab.kidguard.feature.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.DailyBudgetState
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.DailyUsageBlock
import ru.homelab.kidguard.core.domain.model.DailyUsageReset
import ru.homelab.kidguard.core.domain.model.PenaltyGrant
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.model.dailyBudgetState
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PenaltyRepository
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
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

@HiltViewModel
class DailyLimitViewModel @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val bonusRepository: BonusRepository,
    private val penaltyRepository: PenaltyRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val childUsageProvider: ChildUsageProvider,
    observeScheduleState: ObserveScheduleStateUseCase
) : ViewModel() {

    val dailyLimits: StateFlow<DailyLimits> = policyRepository.dailyLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyLimits.EMPTY)

    /** Активное «Дополнительное время» телефона на сегодня (минут). */
    val phoneBonusMinutes: StateFlow<Int> = flow {
        emitAll(bonusRepository.phoneBonusMinutes(currentDateProvider.today()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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

    val penaltyState: StateFlow<PenaltyUiState> = flow {
        val today = currentDateProvider.today()
        emitAll(
            combine(
                policyRepository.dailyLimits,
                bonusRepository.phoneBonusMinutes(today),
                penaltyRepository.phonePenalty(today),
                policyRepository.dailyUsageBlock,
                observeScheduleState()
            ) { limits, bonus, penalty, block, schedule ->
                RawInputs(limits, bonus, penalty, block, schedule)
            }.combine(policyRepository.dailyUsageReset) { raw, reset ->
                PenaltyInputs(
                    limits = raw.limits,
                    bonusMinutes = raw.bonusMinutes,
                    penalty = raw.penalty,
                    blocked = isBlockedToday(today, raw.block, reset),
                    schedule = raw.schedule
                )
            }.combine(usedMinutes) { inputs, used ->
                penaltyState(today, inputs, used)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PenaltyUiState.Loading)

    private val _refreshingUsage = MutableStateFlow(false)

    /** Подтянуть свежий расход ребёнка (вход на экран, возврат из фона). */
    fun refreshUsage() {
        if (_refreshingUsage.value) return
        _refreshingUsage.value = true
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
        viewModelScope.launch { bonusRepository.addBonus(currentDateProvider.today(), null, minutes) }
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

    /** Заблокировать доступное на сегодня время: ставим маркер блокировки с меткой времени нажатия. */
    fun blockToday() {
        viewModelScope.launch {
            policyRepository.setDailyUsageBlock(currentDateProvider.today(), System.currentTimeMillis())
        }
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

    /**
     * Действует ли сейчас принудительная блокировка дня.
     *
     * Мало проверить, что маркер сегодняшний: «Сбросить сегодняшний лимит» блокировку снимает
     * (на детском устройстве расход обнуляется поверх выставленного блокировкой), но сам маркер
     * из политики никуда не девается. Поэтому сравниваем метки времени: блокировка жива, только
     * если она новее сброса — иначе после сброса штрафовать было бы нельзя, хотя время у
     * ребёнка уже есть.
     */
    private fun isBlockedToday(
        today: LocalDate,
        block: DailyUsageBlock?,
        reset: DailyUsageReset?
    ): Boolean {
        if (block == null || block.date != today) return false
        val resetToday = reset?.takeIf { it.date == today } ?: return true
        return block.issuedAt > resetToday.issuedAt
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
