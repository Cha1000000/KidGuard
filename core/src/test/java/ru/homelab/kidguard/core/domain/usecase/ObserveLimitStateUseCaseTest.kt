package ru.homelab.kidguard.core.domain.usecase

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.FakePolicyRepository
import ru.homelab.kidguard.core.domain.model.BonusGrant
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.UsageRepository

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

    @Test
    fun `лимит исчерпан, но выдан бонус - снова Remaining`() = runTest {
        // Лимит 30 мин, использовано 30 мин (Expired), бонус +15 -> осталось 15.
        val limits = DailyLimits(mapOf(DayOfWeek.MONDAY to 30))
        val state = useCase(limits = limits, usedSeconds = 1800, bonusMinutes = 15).invoke().first()
        assertEquals(LimitState.Remaining(15), state)
    }

    @Test
    fun `бонус суммирован из нескольких выдач - учитывается целиком`() = runTest {
        // Лимит 30 мин, использовано 45 мин (перерасход 15), бонус 15+15=30 -> осталось 15.
        val limits = DailyLimits(mapOf(DayOfWeek.MONDAY to 30))
        val state = useCase(limits = limits, usedSeconds = 2700, bonusMinutes = 30).invoke().first()
        assertEquals(LimitState.Remaining(15), state)
    }

    @Test
    fun `бонус выдан, но лимит на день не задан - всё равно NoLimit`() = runTest {
        val state = useCase(limits = DailyLimits.EMPTY, usedSeconds = 3600, bonusMinutes = 30)
            .invoke().first()
        assertEquals(LimitState.NoLimit, state)
    }

    @Test
    fun `бонус недостаточен - остаётся Expired`() = runTest {
        // Лимит 30 мин, использовано 60 мин (перерасход 30), бонус всего 10 -> всё ещё исчерпан.
        val limits = DailyLimits(mapOf(DayOfWeek.MONDAY to 30))
        val state = useCase(limits = limits, usedSeconds = 3600, bonusMinutes = 10).invoke().first()
        assertEquals(LimitState.Expired, state)
    }

    /**
     * Регрессия с обкатки 22.07.2026: у ребёнка весь день не было интернета при нетронутом лимите.
     * Причина — дата бралась один раз при подписке, и живущий сутками сервис после полуночи
     * продолжал читать ВЧЕРАШНИЙ расход: вчерашнее «время вышло» держало блокировки весь новый
     * день, пока приложение не перезапустят.
     */
    @Test
    fun `после полуночи лимит считается по новому дню, а не по вчерашнему`() = runTest {
        val limits = DailyLimits(mapOf(DayOfWeek.MONDAY to 30, DayOfWeek.TUESDAY to 60))
        val dateProvider = MutableDateProvider(monday)
        // Понедельник: 30 мин лимита израсходованы полностью.
        val usage = PerDateUsageRepository(
            mapOf(monday to 1800, monday.plusDays(1) to 0)
        )
        val useCase = ObserveLimitStateUseCase(
            policyRepository = FakePolicyRepository(limits),
            usageRepository = usage,
            bonusRepository = FakeBonusRepository(0),
            currentDateProvider = dateProvider
        )

        val seen = mutableListOf<LimitState>()
        val job = launch { useCase().collect { seen += it } }
        runCurrent()
        assertEquals(LimitState.Expired, seen.last())

        // Наступил вторник: расход обнулился, лимит дня — 60 минут.
        dateProvider.date = monday.plusDays(1)
        // Дата опрашивается тикером раз в минуту; в runTest время виртуальное, ждать не нужно.
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(LimitState.Remaining(60), seen.last())

        job.cancel()
    }

    private fun useCase(limits: DailyLimits, usedSeconds: Int, bonusMinutes: Int = 0) =
        ObserveLimitStateUseCase(
            policyRepository = FakePolicyRepository(limits),
            usageRepository = FakeUsageRepository(usedSeconds),
            bonusRepository = FakeBonusRepository(bonusMinutes),
            currentDateProvider = FakeDateProvider(monday)
        )

    /** Дата, которую тест двигает вручную, — имитация перехода через полночь. */
    private class MutableDateProvider(@Volatile var date: LocalDate) : CurrentDateProvider {
        override suspend fun today(): LocalDate = date
    }

    /** Расход хранится по дням: показывает, что после смены даты читается уже другой счётчик. */
    private class PerDateUsageRepository(private val byDate: Map<LocalDate, Int>) : UsageRepository {
        override fun screenTimeSeconds(date: LocalDate): Flow<Int> = flowOf(byDate[date] ?: 0)
        override suspend fun addScreenTime(date: LocalDate, seconds: Int) = Unit
        override suspend fun setScreenTime(date: LocalDate, seconds: Int) = Unit
        override fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int> = flowOf(0)
        override fun appScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>> = flowOf(emptyMap())
        override suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int) = Unit
        override suspend fun resetScreenTime(date: LocalDate) = Unit
        override suspend fun resetAppScreenTime(date: LocalDate) = Unit
    }

    private class FakeUsageRepository(private val seconds: Int) : UsageRepository {
        override fun screenTimeSeconds(date: LocalDate): Flow<Int> = flowOf(seconds)
        override suspend fun addScreenTime(date: LocalDate, seconds: Int) = Unit
        override suspend fun setScreenTime(date: LocalDate, seconds: Int) = Unit
        override fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int> = flowOf(0)
        override fun appScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>> = flowOf(emptyMap())
        override suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int) = Unit
        override suspend fun resetScreenTime(date: LocalDate) = Unit
        override suspend fun resetAppScreenTime(date: LocalDate) = Unit
    }

    private class FakeBonusRepository(private val phoneMinutes: Int) : BonusRepository {
        override fun phoneBonusMinutes(date: LocalDate): Flow<Int> = flowOf(phoneMinutes)
        override fun appBonusMinutes(date: LocalDate): Flow<Map<String, Int>> = flowOf(emptyMap())
        override suspend fun addBonus(date: LocalDate, packageName: String?, minutes: Int) = Unit
        override suspend fun clearBonus(date: LocalDate, packageName: String?) = Unit
        override fun observeAll(): Flow<List<BonusGrant>> = flowOf(emptyList())
        override suspend fun replaceAll(grants: List<BonusGrant>) = Unit
        override suspend fun deleteOlderThan(date: LocalDate) = Unit
    }

    private class FakeDateProvider(private val date: LocalDate) : CurrentDateProvider {
        override suspend fun today(): LocalDate = date
    }
}
