package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ObserveLimitStateUseCaseTest {

    // Понедельник 2026-07-06 — фиксированная дата для предсказуемости дня недели.
    private val monday = LocalDate.of(2026, 7, 6)

    @Test
    fun `лимит на день не задан - NoLimit`() = runTest {
        val state = useCase(limits = DailyLimits.EMPTY, usedSeconds = 3600).invoke().first()
        assertEquals(LimitState.NoLimit, state)
    }

    @Test
    fun `время осталось - Remaining с корректным остатком`() = runTest {
        // Лимит 60 мин, использовано 20 мин (1200 сек) -> осталось 40.
        val limits = DailyLimits(mapOf(DayOfWeek.MONDAY to 60))
        val state = useCase(limits = limits, usedSeconds = 1200).invoke().first()
        assertEquals(LimitState.Remaining(40), state)
    }

    @Test
    fun `лимит исчерпан - Expired`() = runTest {
        // Лимит 30 мин, использовано 30 мин (1800 сек) -> время вышло.
        val limits = DailyLimits(mapOf(DayOfWeek.MONDAY to 30))
        val state = useCase(limits = limits, usedSeconds = 1800).invoke().first()
        assertEquals(LimitState.Expired, state)
    }

    @Test
    fun `превышение лимита - Expired`() = runTest {
        val limits = DailyLimits(mapOf(DayOfWeek.MONDAY to 30))
        val state = useCase(limits = limits, usedSeconds = 5000).invoke().first()
        assertEquals(LimitState.Expired, state)
    }

    private fun useCase(limits: DailyLimits, usedSeconds: Int) = ObserveLimitStateUseCase(
        policyRepository = FakePolicyRepository(limits),
        usageRepository = FakeUsageRepository(usedSeconds),
        currentDateProvider = FakeDateProvider(monday)
    )

    private class FakePolicyRepository(private val limits: DailyLimits) : PolicyRepository {
        override val dailyLimits: Flow<DailyLimits> = flowOf(limits)
        override val whitelist: Flow<Set<String>> = flowOf(emptySet())
        override val appLimits: Flow<Map<String, Int>> = flowOf(emptyMap())
        override suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?) = Unit
        override suspend fun setAppLimit(packageName: String, minutes: Int?) = Unit
        override suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) = Unit
    }

    private class FakeUsageRepository(private val seconds: Int) : UsageRepository {
        override fun screenTimeSeconds(date: LocalDate): Flow<Int> = flowOf(seconds)
        override suspend fun addScreenTime(date: LocalDate, seconds: Int) = Unit
        override fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int> = flowOf(0)
        override suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int) = Unit
    }

    private class FakeDateProvider(private val date: LocalDate) : CurrentDateProvider {
        override suspend fun today(): LocalDate = date
    }
}
