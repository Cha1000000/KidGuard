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






    // --- Characterization: текущее поведение, которое сохраняется (план, этап 0) ---













}
