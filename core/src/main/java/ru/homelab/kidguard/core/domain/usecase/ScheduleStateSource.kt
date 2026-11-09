package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.model.scheduleStateAt
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.ScheduleClock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственный источник состояния расписания на процесс.
 *
 * Раньше каждый потребитель (`BlockingController`, `FullScreenLockController`,
 * `ObserveBreakStateUseCase`) подписывался на собственный холодный `ObserveScheduleStateUseCase()`
 * со своим тикером: фазы расходились, а граница окна могла отрабатывать с задержкой до 30 с.
 * Теперь один тикер на процесс, состояние кэшируется с `replay = 1`, а тик привязан к точной
 * границе минуты через [ScheduleClock].
 *
 * Состояние холодное до подписки не остаётся: `SharingStarted.Eagerly` запускает поток сразу
 * после создания синглтона, поэтому первый подписчик не ждёт следующего тика.
 */
@Singleton
class ScheduleStateSource private constructor(
    policyRepository: PolicyRepository,
    clock: ScheduleClock,
    scope: CoroutineScope
) {

    @Inject
    constructor(
        policyRepository: PolicyRepository,
        clock: ScheduleClock
    ) : this(
        policyRepository = policyRepository,
        clock = clock,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )

    val state: SharedFlow<ScheduleState> = combine(
        policyRepository.studySchedule,
        policyRepository.sleepSchedule,
        ticker(clock)
    ) { study, sleep, now -> scheduleStateAt(now, study, sleep) }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    internal companion object {

        /** Конструктор для JVM-тестов с тестовым scope; production создаёт собственный scope. */
        fun forTest(policyRepository: PolicyRepository, clock: ScheduleClock, scope: CoroutineScope) =
            ScheduleStateSource(policyRepository, clock, scope)

        private fun ticker(clock: ScheduleClock): Flow<LocalDateTime> = flow {
            while (true) {
                emit(clock.now())
                clock.awaitNextMinute()
            }
        }
    }
}
