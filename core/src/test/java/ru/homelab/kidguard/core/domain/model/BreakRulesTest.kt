package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakRulesTest {

    private fun rules(
        enabled: Boolean = true,
        mode: BreakMode = BreakMode.INTERVAL,
        intervalMinutes: Int = 45,
        hours: Set<Int> = emptySet(),
        durationMinutes: Int = 10
    ) = BreakRules(enabled, mode, intervalMinutes, hours, durationMinutes, message = "")

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
        assertEquals(TimeWindow(900, 915), r.activeHoursWindow(nowMinuteOfDay = 907))
    }

    @Test
    fun `активное окно часов - до начала окна null`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 15)
        assertNull(r.activeHoursWindow(nowMinuteOfDay = 899))
    }

    @Test
    fun `активное окно часов - конец окна не включается`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 15)
        assertNull(r.activeHoursWindow(nowMinuteOfDay = 915))
    }

    @Test
    fun `минут до ближайшего часа`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900, 1080))
        assertEquals(5, r.minutesUntilNextHour(nowMinuteOfDay = 895))
    }

    @Test
    fun `минут до ближайшего часа - после последнего часа null`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900))
        assertNull(r.minutesUntilNextHour(nowMinuteOfDay = 901))
    }

    @Test
    fun `выключенное расписание не даёт окна`() {
        val r = rules(enabled = false, mode = BreakMode.HOURS, hours = setOf(900))
        assertNull(r.activeHoursWindow(nowMinuteOfDay = 905))
    }

    @Test
    fun `окно расписания бьёт перерыв`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = true,
            stickySeconds = 31 * 60, nowMinuteOfDay = 600)
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `в день с малым лимитом перерывов нет`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = 120, scheduleActive = false,
            stickySeconds = 40 * 60, nowMinuteOfDay = 600)
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `перерыв активен с корректным остатком`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 31 * 60, nowMinuteOfDay = 600)
        assertEquals(BreakState.Active(9 * 60), state)
    }

    @Test
    fun `после перерыва снова Idle`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 40 * 60, nowMinuteOfDay = 600)
        assertEquals(BreakState.Idle, state)
    }

    @Test
    fun `за пять минут до перерыва - предупреждение`() {
        val r = rules(intervalMinutes = 30, durationMinutes = 10)
        val state = breakStateAt(r, dayLimitMinutes = null, scheduleActive = false,
            stickySeconds = 26 * 60, nowMinuteOfDay = 600)
        assertEquals(BreakState.Warning, state)
    }

    @Test
    fun `окно под полночь обрывается в полночь`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(1438), durationMinutes = 15)
        // Известное ограничение: окно не переносится на следующие сутки.
        assertNull(r.activeHoursWindow(nowMinuteOfDay = 5))
    }
}
