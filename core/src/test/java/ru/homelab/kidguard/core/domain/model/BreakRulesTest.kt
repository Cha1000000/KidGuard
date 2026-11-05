package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BreakRulesTest {

    private fun rules(
        enabled: Boolean = true,
        mode: BreakMode = BreakMode.INTERVAL,
        intervalMinutes: Int = 45,
        hours: Set<Int> = emptySet(),
        durationMinutes: Int = 10
    ) = BreakRules(enabled, mode, intervalMinutes, hours, durationMinutes, message = "")

    /** Произвольный будний день; конкретная дата в чистых правилах значения не имеет. */
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0, nano: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 11, day, hour, minute, second, nano)

    @Test
    fun `перерывы действуют, когда лимит не задан`() {
        assertTrue(breaksApplyToday(dayLimitMinutes = null))
    }

    @Test
    fun `перерывы действуют, когда лимит больше трёх часов`() {
        assertTrue(breaksApplyToday(dayLimitMinutes = 181))
    }

    @Test
    fun `ровно три часа - перерывов нет`() {
        assertFalse(breaksApplyToday(dayLimitMinutes = 180))
    }

    @Test
    fun `малый лимит - перерывов нет`() {
        assertFalse(breaksApplyToday(dayLimitMinutes = 120))
    }

    @Test
    fun `не настроено, если выключено`() {
        assertFalse(rules(enabled = false).isConfigured)
    }

    @Test
    fun `не настроено, если длительность ноль`() {
        assertFalse(rules(durationMinutes = 0).isConfigured)
    }

    @Test
    fun `не настроено, если интервал ноль в режиме интервала`() {
        assertFalse(rules(intervalMinutes = 0).isConfigured)
    }

    @Test
    fun `не настроено, если часы пусты в режиме часов`() {
        assertFalse(rules(mode = BreakMode.HOURS, hours = emptySet()).isConfigured)
    }

    @Test
    fun `настроено в режиме часов, когда есть хотя бы один час`() {
        assertTrue(rules(mode = BreakMode.HOURS, hours = setOf(900)).isConfigured)
    }

    @Test
    fun `активное окно часов - внутри окна`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 15)
        assertEquals(
            HoursWindow(at(4, 15, 0), at(4, 15, 15)),
            r.activeHoursWindow(at(4, 15, 7))
        )
    }

    @Test
    fun `активное окно часов - до начала окна null`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 15)
        assertNull(r.activeHoursWindow(at(4, 14, 59, 59)))
    }

    @Test
    fun `активное окно часов - конец окна не включается`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 15)
        assertNull(r.activeHoursWindow(at(4, 15, 15)))
    }

    @Test
    fun `выключенное расписание не даёт окна`() {
        val r = rules(enabled = false, mode = BreakMode.HOURS, hours = setOf(900))
        assertNull(r.activeHoursWindow(at(4, 15, 5)))
    }

    @Test
    fun `окно расписания бьёт перерыв`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = true,
            stickySeconds = 31 * 60, now = at(4, 10, 0))
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `в день с малым лимитом перерывов нет`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = 120, scheduleActive = false,
            stickySeconds = 40 * 60, now = at(4, 10, 0))
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `перерыв активен с корректным остатком`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 31 * 60, now = at(4, 10, 0))
        assertEquals(BreakState.Active(9 * 60), state)
    }

    @Test
    fun `после перерыва снова Idle`() {
        // Закрепляет дефект D03: после конца перерыва цикл не начинается заново без длинной паузы.
        // Контракт изменится на этапе 4 по плану «KidGuard — исправление перерывов — план».
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 40 * 60, now = at(4, 10, 0))
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `за пять минут до перерыва - предупреждение`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 26 * 60, now = at(4, 10, 0))
        assertEquals(BreakState.Warning, state)
    }

    // --- Characterization: текущее поведение, которое сохраняется (план, этап 0) ---

    @Test
    fun `расписание отменяет активное окно часов`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = true,
            stickySeconds = 0, now = at(4, 15, 5))
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `расписание отменяет предупреждение часов`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = true,
            stickySeconds = 0, now = at(4, 14, 57))
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `в день с малым лимитом окно часов не показывается`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = 120, scheduleActive = false,
            stickySeconds = 0, now = at(4, 15, 5))
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `предупреждение начинается ровно за пять минут до порога интервала`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val atBoundary = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 25 * 60, now = at(4, 10, 0))
        assertEquals(BreakState.Warning, atBoundary)
        val beforeBoundary = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 25 * 60 - 1, now = at(4, 10, 0))
        assertEquals(BreakState.Idle, beforeBoundary)
    }

    // --- Новый контракт HOURS, см. план «KidGuard — исправление перерывов — план», этап 1 ---

    @Test
    fun `остаток считается с точностью до секунды - 59 секунд после начала окна`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 15, 0, 59))
        assertEquals(BreakState.Active(10 * 60 - 59), state)
    }

    @Test
    fun `последняя секунда окна не округляется до полной минуты`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 15, 9, 59, 500_000_000))
        assertEquals(BreakState.Active(1), state)
    }

    @Test
    fun `окно через полночь активно после полуночи`() {
        // Контракт изменён по плану этапа 1: раньше окно 23:58 + 15 минут обрывалось в 00:00.
        val r = rules(mode = BreakMode.HOURS, hours = setOf(1438), durationMinutes = 15)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(5, 0, 5))
        assertEquals(BreakState.Active(8 * 60), state)
    }

    @Test
    fun `окно через полночь заканчивается в свой абсолютный срок`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(1438), durationMinutes = 15)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(5, 0, 13))
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `пересекающиеся окна дают один общий конец`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900, 905), durationMinutes = 10)
        // Без объединения в 15:07 остаток качался бы между окнами (3 мин и 8 мин).
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 15, 7))
        assertEquals(BreakState.Active(8 * 60), state)
    }

    @Test
    fun `примыкающие окна не создают разрыв`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900, 910), durationMinutes = 10)
        // Окна 15:00–15:10 и 15:10–15:20 сливаются в одно 15:00–15:20: остаток считается до 15:20,
        // без объединения за полсекунды до стыка показалась бы 1 секунда вместо 601.
        val beforeSeam = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 15, 9, 59, 500_000_000))
        assertEquals(BreakState.Active(601), beforeSeam)
        val atSeam = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 15, 10))
        assertEquals(BreakState.Active(10 * 60), atSeam)
    }

    @Test
    fun `предупреждение за пять минут до окна - с точностью до секунды`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 10)
        val warning = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 14, 55, 30))
        assertEquals(BreakState.Warning, warning)
        val tooEarly = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 14, 54, 59))
        assertEquals(BreakState.Idle, tooEarly)
    }

    @Test
    fun `предупреждение показывается и перед окном за полночь`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(0), durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 23, 57))
        assertEquals(BreakState.Warning, state)
    }

    @Test
    fun `внутри уже начавшегося окна предупреждение задним числом не показывается`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 0, now = at(4, 15, 3))
        assertEquals(BreakState.Active(7 * 60), state)
    }
}
