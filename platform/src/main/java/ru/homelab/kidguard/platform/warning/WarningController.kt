package ru.homelab.kidguard.platform.warning

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.ScheduleTimeline
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Показывает два независимых предупреждения, чтобы блокировка не была для ребёнка внезапной:
 * «осталось N минут» перед истечением дневного лимита и «через N минут — Время сна» перед
 * началом ночного расписания. Оба уведомления обновляются по мере убывания минут и снимаются,
 * когда предупреждать больше не нужно. Запускается foreground-сервисом.
 */
@Singleton
class WarningController @Inject constructor(
    private val observeLimitStateUseCase: ObserveLimitStateUseCase,
    private val policyRepository: PolicyRepository,
    private val timeline: ScheduleTimeline,
    private val warningNotifier: WarningNotifier
) {

    suspend fun run() = coroutineScope {
        Timber.tag(TAG).d("Контроллер предупреждений запущен")
        // Два независимых предупреждения не должны блокировать друг друга (у обоих свой
        // бесконечный collect), поэтому запускаем их параллельно в общем scope.
        launch { runLimitWarning() }
        launch { runSleepWarning() }
    }

    private suspend fun runLimitWarning() {
        observeLimitStateUseCase()
            .map { state -> (state as? LimitState.Remaining)?.minutesLeft }
            .distinctUntilChanged()
            .collect { minutesLeft ->
                if (minutesLeft != null && minutesLeft in 1..LIMIT_WARNING_THRESHOLD_MINUTES) {
                    warningNotifier.showLimitWarning(minutesLeft)
                } else {
                    warningNotifier.clearLimitWarning()
                }
            }
    }

    /**
     * «Время сна» — это расписание, а не событие приложения: minutesUntilStart меняется от хода
     * часов. Время берём из общей шкалы [ScheduleTimeline] — той же, что и у состояния расписания:
     * свой тик здесь означал бы, что предупреждение «через N минут» считалось от другого момента,
     * чем сам замок сна, и расходилось с ним на минуту.
     */
    private suspend fun runSleepWarning() {
        policyRepository.sleepSchedule
            .combine(timeline.ticks) { sleepSchedule, now -> sleepSchedule.minutesUntilStart(now) }
            .distinctUntilChanged()
            .collect { minutesLeft ->
                if (minutesLeft != null && minutesLeft in 0..SLEEP_WARNING_THRESHOLD_MINUTES) {
                    Timber.tag(TAG).d("Предупреждение о сне: осталось %d мин", minutesLeft)
                    warningNotifier.showSleepWarning(minutesLeft)
                } else {
                    warningNotifier.clearSleepWarning()
                }
            }
    }

    private companion object {
        const val TAG = "KidGuardWarning"
        const val LIMIT_WARNING_THRESHOLD_MINUTES = 5
        const val SLEEP_WARNING_THRESHOLD_MINUTES = 10
    }
}
