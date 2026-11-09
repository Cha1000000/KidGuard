package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.model.scheduleStateAt
import ru.homelab.kidguard.core.di.ApplicationScope
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.ScheduleTimeline
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственный источник состояния расписания на процесс.
 *
 * Раньше каждый потребитель (`BlockingController`, `FullScreenLockController`,
 * `ObserveBreakStateUseCase`) подписывался на собственный холодный `ObserveScheduleStateUseCase()`
 * со своим тикером: фазы расходились, а граница окна могла отрабатывать с задержкой до 30 с.
 * Теперь время берётся из общей шкалы [ScheduleTimeline] — её тики идут по границам минут и
 * раздаются всем потребителям расписания из одного места (см. [SharedScheduleTimeline]).
 *
 * Состояние холодное до подписки не остаётся: `SharingStarted.Eagerly` запускает поток сразу
 * после создания синглтона, поэтому первый подписчик не ждёт следующего тика.
 */
@Singleton
class ScheduleStateSource @Inject constructor(
    policyRepository: PolicyRepository,
    timeline: ScheduleTimeline,
    @ApplicationScope private val scope: CoroutineScope
) {

    val state: SharedFlow<ScheduleState> = combine(
        policyRepository.studySchedule,
        policyRepository.sleepSchedule,
        timeline.ticks
    ) { study, sleep, now -> scheduleStateAt(now, study, sleep) }
        .distinctUntilChanged()
        .shareIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            replay = 1
        )
}
