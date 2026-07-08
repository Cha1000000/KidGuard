package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Наблюдает за состоянием дневного лимита: сравнивает накопленное реальное экранное время за
 * сегодня с лимитом на текущий день недели (плюс выданное на сегодня «Дополнительное время» —
 * веха 3Б). Дату берёт из [CurrentDateProvider] (с анти-отмоткой), поэтому перевод времени назад
 * не сбрасывает накопленное.
 */
class ObserveLimitStateUseCase @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider
) {

    operator fun invoke(): Flow<LimitState> = flow {
        val today = currentDateProvider.today()
        val stateFlow = combine(
            policyRepository.dailyLimits,
            usageRepository.screenTimeSeconds(today),
            bonusRepository.phoneBonusMinutes(today)
        ) { limits, usedSeconds, bonusMinutes ->
            calculate(limits, today, usedSeconds, bonusMinutes)
        }
        emitAll(stateFlow)
    }

    private fun calculate(
        limits: DailyLimits,
        today: LocalDate,
        usedSeconds: Int,
        bonusMinutes: Int
    ): LimitState {
        val limitMinutes = limits.limitFor(today.dayOfWeek) ?: return LimitState.NoLimit
        val usedMinutes = usedSeconds / 60
        val minutesLeft = (limitMinutes + bonusMinutes) - usedMinutes
        return if (minutesLeft <= 0) LimitState.Expired else LimitState.Remaining(minutesLeft)
    }
}
