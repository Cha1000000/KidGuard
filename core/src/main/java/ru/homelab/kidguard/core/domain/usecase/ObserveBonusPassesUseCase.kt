package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.core.domain.repository.todayFlow
import javax.inject.Inject

/**
 * Приложения, которым родитель выдал дополнительное время и оно ещё не израсходовано, — те, что
 * должны открываться поверх исчерпанного общего дневного лимита и ходить в сеть мимо blackhole-VPN.
 *
 * Отдельный use case, а не поле в состоянии лимита: пропуск нужен сразу двум потребителям с
 * разными вопросами — контроллеру блокировки («пускать ли текущее приложение») и резолверу VPN
 * («кому дать интернет», сразу списком).
 *
 * Дата приходит потоком ([todayFlow]) по той же причине, что и в [ObserveLimitStateUseCase]:
 * сервис живёт неделями, и после полуночи вчерашние выдачи иначе продолжали бы действовать.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBonusPassesUseCase @Inject constructor(
    private val bonusRepository: BonusRepository,
    private val usageRepository: UsageRepository,
    private val currentDateProvider: CurrentDateProvider
) {

    operator fun invoke(): Flow<Set<String>> =
        currentDateProvider.todayFlow().flatMapLatest { today ->
            combine(
                bonusRepository.appBonusMinutes(today),
                usageRepository.appBonusSpentByPackage(today)
            ) { bonuses, spent ->
                bonuses
                    .filterKeys { it.isNotEmpty() }
                    .filter { (pkg, minutes) -> hasBonusAccessPass(minutes, spent[pkg] ?: 0) }
                    .keys
            }
        }.distinctUntilChanged()
}
