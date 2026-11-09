package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.ScheduleState
import javax.inject.Inject

/**
 * Какое расписание действует прямо сейчас: «Время сна» (бьёт всё), «Время учёбы» или ничего.
 *
 * Сам расчёт живёт в singleton [ScheduleStateSource], а время — в общей шкале
 * [ru.homelab.kidguard.core.domain.repository.ScheduleTimeline]: один поток на процесс, тик ровно на
 * границе минуты. Use case остаётся тонкой точкой входа, чтобы вызывающие не зависели от источника.
 */
class ObserveScheduleStateUseCase @Inject constructor(
    private val source: ScheduleStateSource
) {

    operator fun invoke(): Flow<ScheduleState> = source.state
}
