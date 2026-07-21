package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakState
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.model.breakStateAt
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.StickinessSource
import java.time.DayOfWeek
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Наблюдает состояние принудительных перерывов «здесь и сейчас».
 *
 * Своей логики не содержит — только собирает входы для чистой функции `breakStateAt`
 * (см. `BreakRules.kt`): правила перерывов, дневной лимит на сегодня (решает, применяются ли
 * перерывы вообще), состояние расписания ([ObserveScheduleStateUseCase] — учёба и сон одним
 * потоком, любое из них отменяет перерыв), счётчик залипания ([StickinessSource]) и собственный
 * тик — режим INTERVAL меняется от хода времени, а не только от событий в приложении.
 *
 * Отдельно следит за сменой значимых настроек перерыва (режим/интервал/длительность): родитель
 * может выставить интервал 30 минут, когда ребёнок уже залип на 50, — без сброса счётчика замок
 * упал бы мгновенно, без предупреждения. Самую первую загрузку настроек сбросом не считаем — это
 * не смена, а старт.
 */
class ObserveBreakStateUseCase @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val observeScheduleStateUseCase: ObserveScheduleStateUseCase,
    private val stickinessSource: StickinessSource,
    private val currentDateProvider: CurrentDateProvider
) {

    operator fun invoke(): Flow<BreakState> = flow {
        val today = currentDateProvider.today()
        coroutineScope {
            launch { resetOnRuleChange() }
            emitAll(observeState(today.dayOfWeek))
        }
    }

    private fun observeState(today: DayOfWeek): Flow<BreakState> = combine(
        policyRepository.breakRules,
        policyRepository.dailyLimits,
        observeScheduleStateUseCase(),
        stickinessSource.stickySeconds,
        ticker()
    ) { rules, dailyLimits, scheduleState, stickySeconds, now ->
        breakStateAt(
            rules = rules,
            dayLimitMinutes = dailyLimits.limitFor(today),
            scheduleActive = scheduleState !is ScheduleState.Inactive,
            stickySeconds = stickySeconds,
            nowMinuteOfDay = now.toMinuteOfDay()
        )
    }.distinctUntilChanged()

    /** На каждую значимую смену настроек (кроме самой первой загрузки) сбрасывает счётчик залипания. */
    private suspend fun resetOnRuleChange() {
        policyRepository.breakRules
            .map { BreakTiming(it.mode, it.intervalMinutes, it.durationMinutes) }
            .distinctUntilChanged()
            .drop(1)
            .collect { stickinessSource.reset() }
    }

    private fun ticker(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MS)
        }
    }

    /** «Значимая» часть [ru.homelab.kidguard.core.domain.model.BreakRules] — смена любого поля тут требует сброса счётчика. */
    private data class BreakTiming(val mode: BreakMode, val interval: Int, val duration: Int)

    private fun LocalDateTime.toMinuteOfDay(): Int = hour * 60 + minute

    private companion object {
        const val TICK_MS = 15_000L
    }
}
