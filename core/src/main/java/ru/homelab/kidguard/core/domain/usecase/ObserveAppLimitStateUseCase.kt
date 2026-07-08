package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import javax.inject.Inject

/**
 * Наблюдает за состоянием ЛИЧНОГО дневного лимита конкретного приложения (веха 3): сравнивает
 * накопленное этим приложением время за сегодня с его лимитом из политики (плюс выданное на
 * сегодня «Дополнительное время» для этого приложения — веха 3Б). Если личный лимит приложению
 * не задан — [LimitState.NoLimit]. Дата — из [CurrentDateProvider] (анти-отмотка).
 */
class ObserveAppLimitStateUseCase @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val usageRepository: UsageRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider
) {

    operator fun invoke(packageName: String): Flow<LimitState> = flow {
        val today = currentDateProvider.today()
        val stateFlow = combine(
            policyRepository.appLimits,
            usageRepository.appScreenTimeSeconds(today, packageName),
            bonusRepository.appBonusMinutes(today)
        ) { limits, usedSeconds, bonuses ->
            calculate(limits[packageName], usedSeconds, bonuses[packageName] ?: 0)
        }
        emitAll(stateFlow)
    }

    private fun calculate(limitMinutes: Int?, usedSeconds: Int, bonusMinutes: Int): LimitState {
        if (limitMinutes == null) return LimitState.NoLimit
        val minutesLeft = (limitMinutes + bonusMinutes) - usedSeconds / 60
        return if (minutesLeft <= 0) LimitState.Expired else LimitState.Remaining(minutesLeft)
    }
}
