package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.BonusGrant
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.repository.BonusRepository
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

    @Test
    fun `личный лимит исчерпан, но выдан бонус приложению - снова Remaining`() = runTest {
        // Лимит 30 мин, использовано 30 мин (Expired), бонус +15 -> осталось 15.
        val state = useCase(appLimits = mapOf(pkg to 30), usedSeconds = 1800, appBonusMinutes = mapOf(pkg to 15))
            .invoke(pkg).first()
        assertEquals(LimitState.Remaining(15), state)
    }

    @Test
    fun `бонус выдан другому приложению - на это приложение не влияет`() = runTest {
        val state = useCase(
            appLimits = mapOf(pkg to 30),
            usedSeconds = 1800,
            appBonusMinutes = mapOf("com.other.app" to 100)
        ).invoke(pkg).first()
        assertEquals(LimitState.Expired, state)
    }

    @Test
    fun `бонус выдан, но личный лимит не задан - всё равно NoLimit`() = runTest {
        val state = useCase(appLimits = emptyMap(), usedSeconds = 3600, appBonusMinutes = mapOf(pkg to 30))
            .invoke(pkg).first()
        assertEquals(LimitState.NoLimit, state)
    }

    private fun useCase(
        appLimits: Map<String, Int>,
        usedSeconds: Int,
        appBonusMinutes: Map<String, Int> = emptyMap()
    ) = ObserveAppLimitStateUseCase(
        policyRepository = FakePolicyRepository(appLimits),
        usageRepository = FakeUsageRepository(usedSeconds),
        bonusRepository = FakeBonusRepository(appBonusMinutes),
        currentDateProvider = FakeDateProvider(today)
    )

    private class FakePolicyRepository(appLimitsMap: Map<String, Int>) : PolicyRepository {
        override val dailyLimits: Flow<DailyLimits> = flowOf(DailyLimits.EMPTY)
        override val whitelist: Flow<Set<String>> = flowOf(emptySet())
        override val appLimits: Flow<Map<String, Int>> = flowOf(appLimitsMap)
        override val blockedApps: Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?) = Unit
        override suspend fun setAppLimit(packageName: String, minutes: Int?) = Unit
        override suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) = Unit
        override suspend fun setBlocked(packageName: String, blocked: Boolean) = Unit
        override suspend fun replaceAll(
            dailyLimits: Map<DayOfWeek, Int>,
            appLimits: Map<String, Int>,
            whitelist: Set<String>,
            blockedApps: Set<String>
        ) = Unit
    }

    private class FakeUsageRepository(private val appSeconds: Int) : UsageRepository {
        override fun screenTimeSeconds(date: LocalDate): Flow<Int> = flowOf(0)
        override suspend fun addScreenTime(date: LocalDate, seconds: Int) = Unit
        override fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int> = flowOf(appSeconds)
        override fun appScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>> = flowOf(emptyMap())
        override suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int) = Unit
    }

    private class FakeBonusRepository(private val appMinutes: Map<String, Int>) : BonusRepository {
        override fun phoneBonusMinutes(date: LocalDate): Flow<Int> = flowOf(0)
        override fun appBonusMinutes(date: LocalDate): Flow<Map<String, Int>> = flowOf(appMinutes)
        override suspend fun addBonus(date: LocalDate, packageName: String?, minutes: Int) = Unit
        override suspend fun clearBonus(date: LocalDate, packageName: String?) = Unit
        override fun observeAll(): Flow<List<BonusGrant>> = flowOf(emptyList())
        override suspend fun replaceAll(grants: List<BonusGrant>) = Unit
    }

    private class FakeDateProvider(private val date: LocalDate) : CurrentDateProvider {
        override suspend fun today(): LocalDate = date
    }
}
