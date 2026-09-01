package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.model.dayBudgetMinutes
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PenaltyRepository
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.core.domain.repository.todayFlow
import java.time.LocalDate
import javax.inject.Inject

/**
 * Наблюдает за состоянием дневного лимита: сравнивает накопленное реальное экранное время за
 * сегодня с лимитом на текущий день недели (плюс выданное на сегодня «Дополнительное время» —
 * веха 3Б). Дату берёт из [CurrentDateProvider] (с анти-отмоткой), поэтому перевод времени назад
 * не сбрасывает накопленное.
 *
 * Дата приходит потоком ([todayFlow]), а не берётся один раз при подписке: сервис живёт неделями,
 * и после полуночи иначе продолжал бы читать вчерашний расход — вчерашнее «время вышло» держало бы
 * блокировки весь новый день.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLimitStateUseCase @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val penaltyRepository: PenaltyRepository,
    private val currentDateProvider: CurrentDateProvider
) {

    operator fun invoke(): Flow<LimitState> = currentDateProvider.todayFlow().flatMapLatest { today ->
        combine(
            policyRepository.dailyLimits,
            usageRepository.screenTimeSeconds(today),
            bonusRepository.phoneBonusMinutes(today),
            penaltyRepository.phonePenalty(today)
        ) { limits, usedSeconds, bonusMinutes, penalty ->
            calculate(limits, today, usedSeconds, bonusMinutes, penalty?.minutes ?: 0)
        }
    }

    private fun calculate(
        limits: DailyLimits,
        today: LocalDate,
        usedSeconds: Int,
        bonusMinutes: Int,
        penaltyMinutes: Int
    ): LimitState {
        val limitMinutes = limits.limitFor(today.dayOfWeek) ?: return LimitState.NoLimit
        val usedMinutes = usedSeconds / 60
        val minutesLeft = dayBudgetMinutes(limitMinutes, bonusMinutes, penaltyMinutes) - usedMinutes
        return if (minutesLeft <= 0) LimitState.Expired else LimitState.Remaining(minutesLeft)
    }
}
