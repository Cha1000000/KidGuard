package ru.homelab.kidguard.platform.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.BreakState
import ru.homelab.kidguard.core.domain.model.ScheduleState
import java.time.LocalTime

/**
 * Контракт выбора полноэкранного замка: чистая функция от состояний расписания, перерыва,
 * звонка и PIN-флагов. Приоритеты (из FullScreenLockController):
 *
 * 1. звонок или setup-grace — замка нет;
 * 2. «Время сна» бьёт активный перерыв;
 * 3. активный перерыв;
 * 4. снятие PIN-ом до гашения экрана скрывает соответствующий замок.
 *
 * Сам перерыв под расписанием продолжается — reducer выбирает только UI.
 */
class FullScreenLockReducerTest {

    private val sleep = ScheduleState.Sleep(endsAt = LocalTime.of(7, 0))
    private val study = ScheduleState.Study(endsAt = LocalTime.of(14, 0))
    private val breakSession = BreakState.Active(
        secondsLeft = 240,
        message = "Разомнись",
        sessionId = "session-1",
        endsAtElapsedMs = 600_000L
    )

    private fun select(
        scheduleState: ScheduleState = ScheduleState.Inactive,
        breakState: BreakState = BreakState.Idle,
        callActive: Boolean = false,
        callGraceActive: Boolean = false,
        sleepUnlocked: Boolean = false,
        breakUnlocked: Boolean = false
    ) = FullScreenLockReducer.select(
        scheduleState = scheduleState,
        breakState = breakState,
        callActive = callActive,
        callGraceActive = callGraceActive,
        sleepUnlocked = sleepUnlocked,
        breakUnlocked = breakUnlocked
    )

    @Test
    fun `сон бьёт активный перерыв — показываем ночной замок`() {
        assertEquals(
            FullScreenLockSelection.Night,
            select(scheduleState = sleep, breakState = breakSession)
        )
    }

    @Test
    fun `сон без перерыва — ночной замок`() {
        assertEquals(FullScreenLockSelection.Night, select(scheduleState = sleep))
    }

    @Test
    fun `переход sleep в break — ночной замок сменяется замком перерыва`() {
        val duringSleep = select(scheduleState = sleep, breakState = breakSession)
        val afterSleep = select(scheduleState = ScheduleState.Inactive, breakState = breakSession)
        assertEquals(FullScreenLockSelection.Night, duringSleep)
        assertEquals(FullScreenLockSelection.Break("session-1"), afterSleep)
    }

    @Test
    fun `переход break в sleep — замок перерыва сменяется ночным`() {
        val duringBreak = select(breakState = breakSession)
        val sleepStarted = select(scheduleState = sleep, breakState = breakSession)
        assertEquals(FullScreenLockSelection.Break("session-1"), duringBreak)
        assertEquals(FullScreenLockSelection.Night, sleepStarted)
    }

    @Test
    fun `переход break в study и обратно — замок перерыва уходит и возвращается`() {
        val duringBreak = select(breakState = breakSession)
        val duringStudy = select(scheduleState = study, breakState = breakSession)
        val backToBreak = select(scheduleState = ScheduleState.Inactive, breakState = breakSession)
        assertEquals(FullScreenLockSelection.Break("session-1"), duringBreak)
        assertEquals(FullScreenLockSelection.Hidden, duringStudy)
        assertEquals(FullScreenLockSelection.Break("session-1"), backToBreak)
    }

    @Test
    fun `время учёбы само по себе полноэкранного замка не даёт`() {
        assertEquals(FullScreenLockSelection.Hidden, select(scheduleState = study))
    }

    @Test
    fun `звонок скрывает любой замок`() {
        assertEquals(
            FullScreenLockSelection.Hidden,
            select(scheduleState = sleep, breakState = breakSession, callActive = true)
        )
    }

    @Test
    fun `setup-grace после начала звонка держит замок скрытым`() {
        assertEquals(
            FullScreenLockSelection.Hidden,
            select(scheduleState = sleep, callGraceActive = true)
        )
    }

    @Test
    fun `ночной замок снят PIN-ом до гашения экрана`() {
        assertEquals(
            FullScreenLockSelection.Hidden,
            select(scheduleState = sleep, sleepUnlocked = true)
        )
    }

    @Test
    fun `снятый PIN-ом ночной замок не открывает показ замка перерыва под ним`() {
        assertEquals(
            FullScreenLockSelection.Hidden,
            select(scheduleState = sleep, breakState = breakSession, sleepUnlocked = true)
        )
    }

    @Test
    fun `замок перерыва снят PIN-ом до гашения экрана`() {
        assertEquals(
            FullScreenLockSelection.Hidden,
            select(breakState = breakSession, breakUnlocked = true)
        )
    }

    @Test
    fun `выбор несёт sessionId активной сессии для lockKey`() {
        val other = breakSession.copy(sessionId = "session-2")
        assertEquals(FullScreenLockSelection.Break("session-2"), select(breakState = other))
    }

    @Test
    fun `ничего не действует — замка нет`() {
        assertEquals(FullScreenLockSelection.Hidden, select())
    }
}
