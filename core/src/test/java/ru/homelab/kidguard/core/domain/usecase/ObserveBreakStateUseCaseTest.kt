package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.homelab.kidguard.core.domain.FakePolicyRepository
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakRules
import ru.homelab.kidguard.core.domain.model.BreakSession
import ru.homelab.kidguard.core.domain.model.BreakState
import ru.homelab.kidguard.core.domain.model.ContinuousUsageSession
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.repository.BreakClock
import ru.homelab.kidguard.core.domain.repository.BreakRuntimeRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.StickinessSource
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveBreakStateUseCaseTest {

    @Test
    fun `два последовательных INTERVAL без гашения экрана и warningKey привязан к циклу`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        val firstWarningKey = "interval:cycle-0"

        fixture.stickiness.setUsage(25 * MINUTE)
        runCurrent()
        assertEquals(BreakState.Warning(firstWarningKey), states.last())
        clockTick(fixture, 15_000)
        assertEquals(BreakState.Warning(firstWarningKey), states.last())

        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val first = states.filterIsInstance<BreakState.Active>().last()
        assertEquals(fixture.clock.elapsedRealtimeMs() + 10 * MINUTE, first.endsAtElapsedMs)
        assertEquals(1, fixture.runtime.savedSessions.size)

        fixture.clock.advanceBy(10 * MINUTE)
        advanceTimeBy(10 * MINUTE)
        runCurrent()
        assertEquals(BreakState.Idle, states.last())
        assertEquals(listOf(first.sessionId), fixture.runtime.completedSessions)
        assertEquals(0L, fixture.stickiness.currentUsage()?.accumulatedMs)

        fixture.stickiness.setUsage(25 * MINUTE)
        runCurrent()
        val secondWarning = states.last() as BreakState.Warning
        assertNotEquals(firstWarningKey, secondWarning.warningKey)

        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val second = states.filterIsInstance<BreakState.Active>().last()
        assertNotEquals(first.sessionId, second.sessionId)
        assertEquals(2, fixture.runtime.savedSessions.size)
    }

    @Test
    fun `Doze и screen off в середине сессии не продлевают deadline`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val active = states.last() as BreakState.Active

        fixture.clock.advanceBy(5 * MINUTE)
        advanceTimeBy(5 * MINUTE)
        runCurrent()
        assertEquals(active.copy(secondsLeft = 5 * 60), states.last())

        fixture.clock.advanceBy(5 * MINUTE)
        advanceTimeBy(5 * MINUTE)
        runCurrent()
        assertEquals(BreakState.Idle, states.last())
        assertEquals(listOf(active.sessionId), fixture.runtime.completedSessions)
    }

    @Test
    fun `hide и show восстанавливают проекцию без новой сессии`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val active = states.last() as BreakState.Active
        fixture.job.cancel()
        runCurrent()

        val restoredStates = mutableListOf<BreakState>()
        fixture.job = backgroundScope.launch { fixture.useCase().collect { restoredStates += it } }
        runCurrent()

        assertEquals(active, restoredStates.single())
        assertEquals(1, fixture.runtime.savedSessions.size)
    }

    @Test
    fun `PIN-разблокировка не меняет deadline`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val active = states.last() as BreakState.Active

        clockTick(fixture, MINUTE)

        val afterUnlock = states.last() as BreakState.Active
        assertEquals(active.sessionId, afterUnlock.sessionId)
        assertEquals(active.endsAtElapsedMs, afterUnlock.endsAtElapsedMs)
        assertEquals(1, fixture.runtime.savedSessions.size)
        assertTrue(fixture.runtime.cancelledSessions.isEmpty())
    }

    @Test
    fun `смерть процесса в той же загрузке восстанавливает тот же sessionId и deadline`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val active = states.last() as BreakState.Active
        fixture.job.cancel()
        runCurrent()

        val restored = mutableListOf<BreakState>()
        fixture.job = backgroundScope.launch { fixture.useCase().collect { restored += it } }
        runCurrent()

        assertEquals(active, restored.single())
        assertEquals(1, fixture.runtime.savedSessions.size)
    }

    @Test
    fun `reboot до deadline восстанавливает остаток по wall clock`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val active = states.last() as BreakState.Active
        val endsAtWallMs = fixture.runtime.activeSession?.endsAtWallMs
        fixture.job.cancel()
        runCurrent()
        fixture.clock.rebootAfter(4 * MINUTE)

        val restored = mutableListOf<BreakState>()
        fixture.job = backgroundScope.launch { fixture.useCase().collect { restored += it } }
        runCurrent()

        val restoredActive = restored.single() as BreakState.Active
        assertEquals(active.sessionId, restoredActive.sessionId)
        assertEquals(endsAtWallMs, fixture.runtime.activeSession?.endsAtWallMs)
        assertEquals(6 * 60, restoredActive.secondsLeft)
        assertEquals(fixture.clock.elapsedRealtimeMs() + 6 * MINUTE, restoredActive.endsAtElapsedMs)
        assertEquals(fixture.clock.boot, fixture.runtime.activeSession?.bootCount)
    }

    @Test
    fun `reboot после deadline завершает сессию`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val active = states.last() as BreakState.Active
        fixture.job.cancel()
        runCurrent()
        fixture.clock.rebootAfter(11 * MINUTE)

        val restored = mutableListOf<BreakState>()
        fixture.job = backgroundScope.launch { fixture.useCase().collect { restored += it } }
        runCurrent()

        assertEquals(BreakState.Idle, restored.single())
        assertNull(fixture.runtime.activeSession)
        assertEquals(listOf(active.sessionId), fixture.runtime.completedSessions)
    }

    @Test
    fun `расписание скрывает перерыв на UI но не продлевает сессию`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val active = states.last() as BreakState.Active

        fixture.schedule.value = ScheduleState.Study(LocalTime.NOON)
        runCurrent()
        assertEquals(active, states.last())
        assertEquals(active.endsAtElapsedMs, fixture.runtime.activeSession?.endsAtElapsedMs)
        assertTrue(fixture.stickiness.pauseValues.last())

        fixture.clock.advanceBy(10 * MINUTE)
        advanceTimeBy(10 * MINUTE)
        runCurrent()
        assertEquals(BreakState.Idle, states.last())
        assertEquals(listOf(active.sessionId), fixture.runtime.completedSessions)
    }

    @Test
    fun `отключение и неприменимый день отменяют сессию`() = runTest {
        val fixture = Fixture(rules = intervalRules())
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val first = states.last() as BreakState.Active

        fixture.policies.breakRules.value = intervalRules(enabled = false)
        runCurrent()
        assertEquals(BreakState.Idle, states.last())
        assertEquals(listOf(first.sessionId), fixture.runtime.cancelledSessions)
        assertNull(fixture.runtime.activeSession)

        fixture.policies.breakRules.value = intervalRules()
        runCurrent()
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val second = states.filterIsInstance<BreakState.Active>().last()
        fixture.policies.dailyLimits.value = DailyLimits(
            mapOf(fixture.clock.localDateTime().dayOfWeek to 120)
        )
        runCurrent()

        assertEquals(BreakState.Idle, states.last())
        assertEquals(listOf(first.sessionId, second.sessionId), fixture.runtime.cancelledSessions)
        assertNull(fixture.runtime.activeSession)
    }

    @Test
    fun `включение после старого накопления не блокирует мгновенно`() = runTest {
        val fixture = Fixture(
            rules = intervalRules(enabled = false),
            initialUsage = usage(2 * 60 * MINUTE)
        )
        val states = observe(fixture)

        assertEquals(BreakState.Idle, states.single())
        assertEquals(0L, fixture.stickiness.currentUsage()?.accumulatedMs)

        fixture.policies.breakRules.value = intervalRules()
        runCurrent()
        assertEquals(BreakState.Idle, states.last())
        assertTrue(states.filterIsInstance<BreakState.Active>().isEmpty())
    }

    @Test
    fun `изменение message сохраняет сессию и обновляет текст`() = runTest {
        val fixture = Fixture(rules = intervalRules(message = "Старое"))
        val states = observe(fixture)
        fixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val active = states.last() as BreakState.Active

        fixture.policies.breakRules.value = intervalRules(message = "Новое")
        runCurrent()

        val updated = states.last() as BreakState.Active
        assertEquals("Новое", updated.message)
        assertEquals(active.sessionId, updated.sessionId)
        assertEquals(active.endsAtElapsedMs, updated.endsAtElapsedMs)
        assertTrue(fixture.runtime.cancelledSessions.isEmpty())
    }

    @Test
    fun `изменение timing или hours отменяет сессию`() = runTest {
        val intervalFixture = Fixture(rules = intervalRules())
        val intervalStates = observe(intervalFixture)
        intervalFixture.stickiness.setUsage(30 * MINUTE)
        runCurrent()
        val intervalActive = intervalStates.last() as BreakState.Active

        intervalFixture.policies.breakRules.value = intervalRules(intervalMinutes = 31)
        runCurrent()
        assertEquals(BreakState.Idle, intervalStates.last())
        assertEquals(listOf(intervalActive.sessionId), intervalFixture.runtime.cancelledSessions)

        intervalFixture.job.cancel()
        runCurrent()
        val hoursFixture = Fixture(
            clock = FakeClock(now = LocalDateTime.of(2026, 11, 4, 15, 5)),
            rules = hoursRules()
        )
        val hoursStates = observe(hoursFixture)
        val hoursActive = hoursStates.last() as BreakState.Active

        hoursFixture.policies.breakRules.value = hoursRules(hours = setOf(16 * 60))
        runCurrent()
        assertEquals(BreakState.Idle, hoursStates.last())
        assertEquals(listOf(hoursActive.sessionId), hoursFixture.runtime.cancelledSessions)
    }

    @Test
    fun `перекрывающиеся HOURS и полночь используют один непрерывный deadline`() = runTest {
        val fixture = Fixture(
            clock = FakeClock(now = LocalDateTime.of(2026, 11, 4, 23, 53)),
            rules = hoursRules(hours = setOf(23 * 60 + 58, 23 * 60 + 59), durationMinutes = 15)
        )
        val states = observe(fixture)
        val warning = states.single() as BreakState.Warning

        fixture.clock.advanceBy(12 * MINUTE)
        advanceTimeBy(12 * MINUTE)
        runCurrent()

        val active = states.filterIsInstance<BreakState.Active>().last()
        val session = fixture.runtime.activeSession
        assertEquals(9 * 60, active.secondsLeft)
        assertEquals("hours:${session?.windowKey}", warning.warningKey)
        assertEquals(1, fixture.runtime.savedSessions.size)

        clockTick(fixture, 15_000)
        assertEquals(active.sessionId, (states.last() as BreakState.Active).sessionId)
        assertEquals(active.endsAtElapsedMs, (states.last() as BreakState.Active).endsAtElapsedMs)
        assertEquals(1, fixture.runtime.savedSessions.size)
    }

    private suspend fun TestScope.observe(fixture: Fixture): MutableList<BreakState> {
        val states = mutableListOf<BreakState>()
        fixture.job = backgroundScope.launch { fixture.useCase().collect { states += it } }
        runCurrent()
        return states
    }

    private suspend fun TestScope.clockTick(fixture: Fixture, millis: Long) {
        // Часы и виртуальный тикер двигаются вместе; использование при этом не растёт само.
        fixture.clock.advanceBy(millis)
        advanceTimeBy(millis)
        runCurrent()
    }

    private class Fixture(
        val clock: FakeClock = FakeClock(),
        rules: BreakRules,
        initialUsage: ContinuousUsageSession? = usage(0),
        val runtime: FakeBreakRuntimeRepository = FakeBreakRuntimeRepository()
    ) {
        val policies = FakePolicyRepository(rules)
        val schedule = MutableStateFlow<ScheduleState>(ScheduleState.Inactive)
        val stickiness = FakeStickinessSource(clock, initialUsage)
        lateinit var job: Job

        fun useCase() = ObserveBreakStateUseCase(
            policyRepository = policies,
            scheduleStates = schedule,
            stickinessSource = stickiness,
            currentDateProvider = clock,
            breakRuntimeRepository = runtime,
            breakClock = clock
        ).invoke()
    }

    private class FakeClock(
        var now: LocalDateTime = LocalDateTime.of(2026, 11, 4, 10, 0),
        var elapsedMs: Long = 1_000_000L,
        var wallMs: Long = 1_800_000_000_000L,
        var boot: Int = 7
    ) : BreakClock, CurrentDateProvider {
        override fun elapsedRealtimeMs(): Long = elapsedMs
        override fun wallTimeMillis(): Long = wallMs
        override fun localDateTime(): LocalDateTime = now
        override fun bootCount(): Int = boot
        override suspend fun today() = now.toLocalDate()

        fun advanceBy(millis: Long) {
            elapsedMs += millis
            wallMs += millis
            now = now.plusNanos(millis * 1_000_000)
        }

        fun rebootAfter(millis: Long) {
            wallMs += millis
            now = now.plusNanos(millis * 1_000_000)
            elapsedMs = 50_000L
            boot++
        }
    }

    private class FakeStickinessSource(
        private val clock: FakeClock,
        initialUsage: ContinuousUsageSession?
    ) : StickinessSource {
        private val usageState = MutableStateFlow(initialUsage)
        private val secondsState = MutableStateFlow(((initialUsage?.accumulatedMs ?: 0) / 1000).toInt())
        private var nextCycle = 1
        val pauseValues = mutableListOf<Boolean>()

        override val usageSession: Flow<ContinuousUsageSession?> = usageState
        override val stickySeconds: Flow<Int> = secondsState

        override fun reset() {
            setUsage(0, "cycle-${nextCycle++}")
        }

        override fun pause(paused: Boolean) {
            pauseValues += paused
        }

        fun setUsage(accumulatedMs: Long, cycleId: String? = null) {
            val current = usageState.value
            usageState.value = ContinuousUsageSession(
                cycleId = cycleId ?: current?.cycleId ?: "cycle-0",
                accumulatedMs = accumulatedMs,
                idleSinceElapsedMs = null,
                lastObservedElapsedMs = clock.elapsedRealtimeMs()
            )
            secondsState.value = (accumulatedMs / 1000).toInt()
        }

        fun currentUsage(): ContinuousUsageSession? = usageState.value
    }

    private class FakeBreakRuntimeRepository : BreakRuntimeRepository {
        var activeSession: BreakSession? = null
        val savedSessions = mutableListOf<BreakSession>()
        val cancelledSessions = mutableListOf<String>()
        val completedSessions = mutableListOf<String>()
        private var usageSnapshot: ContinuousUsageSession? = null

        override suspend fun loadActiveSession(): BreakSession? = activeSession

        override suspend fun saveSession(session: BreakSession) {
            activeSession = session
            savedSessions += session
        }

        override suspend fun cancelSession(sessionId: String) {
            if (activeSession?.id == sessionId) activeSession = null
            cancelledSessions += sessionId
        }

        override suspend fun completeBreakAndResetUsage(sessionId: String) {
            if (activeSession?.id != sessionId) return
            activeSession = null
            usageSnapshot = null
            completedSessions += sessionId
        }

        override suspend fun loadUsageSnapshot(): ContinuousUsageSession? = usageSnapshot

        override suspend fun saveUsageSnapshot(snapshot: ContinuousUsageSession) {
            usageSnapshot = snapshot
        }

        override suspend fun clearUsageSnapshot() {
            usageSnapshot = null
        }
    }

    private class FakePolicyRepository(initialRules: BreakRules) : PolicyRepository by FakePolicyRepository() {
        override val breakRules = MutableStateFlow(initialRules)
        override val dailyLimits = MutableStateFlow(DailyLimits.EMPTY)
    }

    private companion object {
        const val MINUTE = 60_000L

        fun intervalRules(
            enabled: Boolean = true,
            intervalMinutes: Int = 30,
            durationMinutes: Int = 10,
            message: String = ""
        ) = BreakRules(enabled, BreakMode.INTERVAL, intervalMinutes, emptySet(), durationMinutes, message)

        fun hoursRules(
            hours: Set<Int> = setOf(15 * 60),
            durationMinutes: Int = 10
        ) = BreakRules(true, BreakMode.HOURS, 0, hours, durationMinutes, "")

        fun usage(accumulatedMs: Long, cycleId: String = "cycle-0") = ContinuousUsageSession(
            cycleId = cycleId,
            accumulatedMs = accumulatedMs,
            idleSinceElapsedMs = null,
            lastObservedElapsedMs = 0
        )
    }
}
