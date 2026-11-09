package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.FakePolicyRepository
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.model.TimeWindow
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.ScheduleClock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Контракт общего состояния расписания: граница окна переключается на точной минуте, смена
 * политики применяется сразу, все потребители получают один поток и один тикер.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveScheduleStateUseCaseTest {

    @Test
    fun `граница минуты переключает состояние точно`() = runTest {
        val clock = FakeScheduleClock()
        val policy = FakePolicy()
        policy.study.value = schedule(21 * 60 + 58, 22 * 60)
        val source = ScheduleStateSource.forTest(policy, clock, backgroundScope)
        val states = mutableListOf<ScheduleState>()
        backgroundScope.launch {
            ObserveScheduleStateUseCase(source)().collect { states += it }
        }
        runCurrent()
        assertEquals(listOf<ScheduleState>(ScheduleState.Inactive), states)

        // 30 секунд до 21:58:00 — ровно граница окна, без 15–30-секундной задержки.
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(ScheduleState.Study(LocalTime.of(22, 0)), states.last())
        assertEquals(2, states.size)
    }

    @Test
    fun `смена политики применяется сразу, без ожидания тика`() = runTest {
        val clock = FakeScheduleClock(now = LocalDateTime.of(2026, 11, 7, 21, 57, 0))
        val policy = FakePolicy()
        val source = ScheduleStateSource.forTest(policy, clock, backgroundScope)
        val states = mutableListOf<ScheduleState>()
        backgroundScope.launch {
            ObserveScheduleStateUseCase(source)().collect { states += it }
        }
        runCurrent()
        assertEquals(ScheduleState.Inactive, states.last())

        policy.study.value = schedule(21 * 60, 22 * 60)
        runCurrent()
        assertEquals(ScheduleState.Study(LocalTime.of(22, 0)), states.last())
        // Политика пересчитана на последнем известном "сейчас" — нового тика не ждём.
        assertEquals(1, clock.awaitCalls)
    }

    @Test
    fun `два подписчика получают один поток и один тикер`() = runTest {
        val clock = FakeScheduleClock()
        val policy = FakePolicy()
        policy.study.value = schedule(21 * 60 + 58, 22 * 60)
        val source = ScheduleStateSource.forTest(policy, clock, backgroundScope)
        val first = mutableListOf<ScheduleState>()
        val second = mutableListOf<ScheduleState>()
        backgroundScope.launch { ObserveScheduleStateUseCase(source)().collect { first += it } }
        backgroundScope.launch { ObserveScheduleStateUseCase(source)().collect { second += it } }
        runCurrent()

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(first, second)
        // Один тикер на процесс: первый await + следующий await на новой минуте, а не по одному на подписчика.
        assertEquals(2, clock.awaitCalls)
    }

    @Test
    fun `поздний подписчик получает актуальное состояние из replay`() = runTest {
        val clock = FakeScheduleClock()
        val policy = FakePolicy()
        policy.study.value = schedule(21 * 60 + 58, 22 * 60)
        val source = ScheduleStateSource.forTest(policy, clock, backgroundScope)
        val first = mutableListOf<ScheduleState>()
        backgroundScope.launch { ObserveScheduleStateUseCase(source)().collect { first += it } }
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(ScheduleState.Study(LocalTime.of(22, 0)), first.last())

        val late = mutableListOf<ScheduleState>()
        backgroundScope.launch { ObserveScheduleStateUseCase(source)().collect { late += it } }
        runCurrent()
        assertEquals(ScheduleState.Study(LocalTime.of(22, 0)), late.single())
    }

    private fun schedule(startMinute: Int, endMinute: Int) = ScheduleRules(
        DayOfWeek.entries.associateWith { TimeWindow(startMinute, endMinute) },
        enabled = true
    )

    private class FakePolicy(
        val study: MutableStateFlow<ScheduleRules> = MutableStateFlow(ScheduleRules.EMPTY),
        val sleep: MutableStateFlow<ScheduleRules> = MutableStateFlow(ScheduleRules.EMPTY)
    ) : PolicyRepository by FakePolicyRepository() {
        override val studySchedule: Flow<ScheduleRules> = study
        override val sleepSchedule: Flow<ScheduleRules> = sleep
    }

    private class FakeScheduleClock(
        var now: LocalDateTime = LocalDateTime.of(2026, 11, 7, 21, 57, 30)
    ) : ScheduleClock {

        var awaitCalls: Int = 0
            private set

        override fun now(): LocalDateTime = now

        override suspend fun awaitNextMinute() {
            awaitCalls++
            val elapsedInMinute = now.second * 1_000L + now.nano / 1_000_000L
            val waitMs = 60_000L - elapsedInMinute
            val wait = if (waitMs <= 0L) 60_000L else waitMs
            delay(wait)
            now = now.plusNanos(wait * 1_000_000L)
        }
    }
}
