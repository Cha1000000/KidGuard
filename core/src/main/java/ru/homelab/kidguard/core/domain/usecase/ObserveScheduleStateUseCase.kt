package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.model.scheduleStateAt
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Наблюдает, какое расписание действует прямо сейчас: «Время сна» (бьёт всё), «Время учёбы» или
 * ничего. Пересчитывается при правке расписаний родителем и по собственному тику.
 *
 * Тик нужен потому, что расписание меняет состояние само по себе, от хода часов, а не от событий
 * в приложении. [TICK_MS] в 30 секунд даёт запас точности к минутной сетке окон: граница окна
 * срабатывает не позже чем через полминуты после наступления. Тик дешёвый (сравнение двух чисел),
 * а `distinctUntilChanged` не даёт подписчикам лишних эмиссий между сменами состояния.
 */
class ObserveScheduleStateUseCase @Inject constructor(
    private val policyRepository: PolicyRepository
) {

    operator fun invoke(): Flow<ScheduleState> = combine(
        policyRepository.studySchedule,
        policyRepository.sleepSchedule,
        ticker()
    ) { study, sleep, now ->
        scheduleStateAt(now, study, sleep)
    }.distinctUntilChanged()

    private fun ticker(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MS)
        }
    }

    private companion object {
        const val TICK_MS = 30_000L
    }
}
