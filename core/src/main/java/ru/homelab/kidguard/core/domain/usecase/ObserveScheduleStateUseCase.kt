package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.ScheduleState
import javax.inject.Inject

/**
 * Какое расписание действует прямо сейчас: «Время сна» (бьёт всё), «Время учёбы» или ничего.
 *
 * Сам расчёт и тик живут в singleton [ScheduleStateSource]: один поток на процесс, точная граница
 * минуты. Use case остаётся тонкой точкой входа, чтобы вызывающие не зависели от источника.
 */
class ObserveScheduleStateUseCase @Inject constructor(
    private val source: ScheduleStateSource
) {

    operator fun invoke(): Flow<ScheduleState> = source.state
}
