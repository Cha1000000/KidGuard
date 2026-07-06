package ru.homelab.kidguard.platform.warning

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Показывает предупреждение «осталось N минут», когда до истечения дневного лимита остаётся не
 * больше [WARNING_THRESHOLD_MINUTES] минут. Уведомление обновляется по мере убывания минут и
 * снимается, когда предупреждать больше не нужно. Запускается foreground-сервисом.
 */
@Singleton
class WarningController @Inject constructor(
    private val observeLimitStateUseCase: ObserveLimitStateUseCase,
    private val warningNotifier: WarningNotifier
) {

    suspend fun run() {
        Timber.tag(TAG).d("Контроллер предупреждений запущен")
        observeLimitStateUseCase()
            .map { state -> (state as? LimitState.Remaining)?.minutesLeft }
            .distinctUntilChanged()
            .collect { minutesLeft ->
                if (minutesLeft != null && minutesLeft in 1..WARNING_THRESHOLD_MINUTES) {
                    warningNotifier.show(minutesLeft)
                } else {
                    warningNotifier.clear()
                }
            }
    }

    private companion object {
        const val TAG = "KidGuardWarning"
        const val WARNING_THRESHOLD_MINUTES = 5
    }
}
