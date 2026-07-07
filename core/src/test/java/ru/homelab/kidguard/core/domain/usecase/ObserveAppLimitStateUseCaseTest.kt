package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import java.time.DayOfWeek
import java.time.LocalDate

class ObserveAppLimitStateUseCaseTest {

    private val today = LocalDate.of(2026, 7, 6)
    private val pkg = "com.game.app"

    @Test
    fun `личный лимит не задан - NoLimit`() = runTest {
        val state = useCase(appLimits = emptyMap(), usedSeconds = 3600).invoke(pkg).first()
        assertEquals(LimitState.NoLimit, state)
    }

    @Test
    fun `лимит задан другому приложению - NoLimit`() = runTest {
        val state = useCase(appLimits = mapOf("com.other.app" to 30), usedSeconds = 3600).invoke(pkg).first()
        assertEquals(LimitState.NoLimit, state)
    }

    @Test
    fun `время осталось - Remaining с корректным остатком`() = runTest {
        // Лимит 60 мин, приложение израсходовало 20 мин (1200 сек) -> осталось 40.
        val state = useCase(appLimits = mapOf(pkg to 60), usedSeconds = 1200).invoke(pkg).first()
        assertEquals(LimitState.Remaining(40), state)
    }

    @Test
    fun `личный лимит исчерпан - Expired`() = runTest {
        val state = useCase(appLimits = mapOf(pkg to 30), usedSeconds = 1800).invoke(pkg).first()
        assertEquals(LimitState.Expired, state)
    }

    @Test
    fun `превышение личного лимита - Expired`() = runTest {
        val state = useCase(appLimits = mapOf(pkg to 30), usedSeconds = 5000).invoke(pkg).first()
        assertEquals(LimitState.Expired, state)
    }

    private fun useCase(appLimits: Map<String, Int>, usedSeconds: Int) = ObserveAppLimitStateUseCase(
        policyRepository = FakePolicyRepository(appLimits),
        usageRepository = FakeUsageRepository(usedSeconds),
        currentDateProvider = FakeDateProvider(today)
    )

    private class FakePolicyRepository(appLimitsMap: Map<String, Int>) : PolicyRepository {
        override val dailyLimits: Flow<DailyLimits> = flowOf(DailyLimits.EMPTY)
        override val whitelist: Flow<Set<String>> = flowOf(emptySet())
        override val appLimits: Flow<Map<String, Int>> = flowOf(appLimitsMap)
        override suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?) = Unit
        override suspend fun setAppLimit(packageName: String, minutes: Int?) = Unit
        override suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) = Unit
    }

    private class FakeUsageRepository(private val appSeconds: Int) : UsageRepository {
        override fun screenTimeSeconds(date: LocalDate): Flow<Int> = flowOf(0)
        override suspend fun addScreenTime(date: LocalDate, seconds: Int) = Unit
        override fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int> = flowOf(appSeconds)
        override suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int) = Unit
    }

    private class FakeDateProvider(private val date: LocalDate) : CurrentDateProvider {
        override suspend fun today(): LocalDate = date
    }
}
