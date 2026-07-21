package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Окна расписания: границы (начало включительно, конец исключительно), переход через полночь,
 * выключенное и пустое расписание, приоритет «сон бьёт учёбу».
 */
class ScheduleTest {

    // Понедельник 2026-07-20 — опорный день во всех проверках.
    private fun monday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 7, 20, hour, minute)
    private fun tuesday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 7, 21, hour, minute)

    private fun window(startHour: Int, endHour: Int) =
        TimeWindow(startMinute = startHour * 60, endMinute = endHour * 60)

    /** Расписание с одинаковым окном на все 7 дней. */
    private fun everyDay(window: TimeWindow, enabled: Boolean = true) =
        ScheduleRules(DayOfWeek.entries.associateWith { window }, enabled)

    // --- Обычное окно внутри суток (учёба 08:00–14:00) ---

    @Test
    fun `внутри окна - активно`() {
        val rules = everyDay(window(8, 14))
        assertEquals(window(8, 14), rules.activeWindowAt(monday(10, 30)))
    }

    @Test
    fun `начало окна включительно, конец исключительно`() {
        val rules = everyDay(window(8, 14))
        assertEquals(window(8, 14), rules.activeWindowAt(monday(8, 0)))
        assertEquals(window(8, 14), rules.activeWindowAt(monday(13, 59)))
        assertNull(rules.activeWindowAt(monday(14, 0)))
    }

    @Test
    fun `до начала и после конца - не активно`() {
        val rules = everyDay(window(8, 14))
        assertNull(rules.activeWindowAt(monday(7, 59)))
        assertNull(rules.activeWindowAt(monday(20, 0)))
    }

    @Test
    fun `день без окна - не активно`() {
        val rules = ScheduleRules(mapOf(DayOfWeek.TUESDAY to window(8, 14)), enabled = true)
        assertNull(rules.activeWindowAt(monday(10, 0)))
    }

    // --- Окно через полночь (сон 21:00–07:00) ---

    @Test
    fun `окно через полночь - активно вечером того же дня`() {
        val rules = everyDay(window(21, 7))
        assertEquals(window(21, 7), rules.activeWindowAt(monday(21, 0)))
        assertEquals(window(21, 7), rules.activeWindowAt(monday(23, 59)))
    }

    @Test
    fun `окно через полночь - активно ночью уже следующего дня`() {
        val rules = everyDay(window(21, 7))
        assertEquals(window(21, 7), rules.activeWindowAt(tuesday(0, 0)))
        assertEquals(window(21, 7), rules.activeWindowAt(tuesday(6, 59)))
    }

    @Test
    fun `окно через полночь - отпускает ровно в час окончания`() {
        val rules = everyDay(window(21, 7))
        assertNull(rules.activeWindowAt(tuesday(7, 0)))
        assertNull(rules.activeWindowAt(tuesday(12, 0)))
    }

    @Test
    fun `хвост после полуночи берётся из окна ВЧЕРАШНЕГО дня, а не сегодняшнего`() {
        // Сон задан только на понедельник. Ночь с понедельника на вторник — блокируем;
        // вечер вторника — уже нет, у вторника своего окна нет.
        val rules = ScheduleRules(mapOf(DayOfWeek.MONDAY to window(21, 7)), enabled = true)
        assertEquals(window(21, 7), rules.activeWindowAt(tuesday(3, 0)))
        assertNull(rules.activeWindowAt(tuesday(22, 0)))
    }

    // --- Выключенное и пустое ---

    @Test
    fun `выключенное расписание - никогда не активно`() {
        val rules = everyDay(window(8, 14), enabled = false)
        assertNull(rules.activeWindowAt(monday(10, 0)))
    }

    @Test
    fun `пустое окно (границы совпали) - никогда не активно`() {
        val rules = everyDay(TimeWindow(startMinute = 600, endMinute = 600))
        assertNull(rules.activeWindowAt(monday(10, 0)))
        assertNull(rules.activeWindowAt(monday(10, 1)))
    }

    @Test
    fun `расписание без окон - не активно`() {
        assertNull(ScheduleRules.EMPTY.activeWindowAt(monday(10, 0)))
    }

    // --- Свойства окна ---

    @Test
    fun `окно через полночь распознаётся и отдаёт время окончания`() {
        val night = window(21, 7)
        assertTrue(night.crossesMidnight)
        assertFalse(night.isEmpty)
        assertEquals(LocalTime.of(7, 0), night.endsAt)
    }

    @Test
    fun `окончание в полночь отдаётся как 00 00, а не 24 00`() {
        assertEquals(LocalTime.MIDNIGHT, TimeWindow(startMinute = 22 * 60, endMinute = 0).endsAt)
    }

    // --- Предупреждение заранее ---

    @Test
    fun `до начала окна считаем минуты`() {
        val rules = everyDay(window(21, 7))
        assertEquals(10, rules.minutesUntilStart(monday(20, 50)))
        assertEquals(1, rules.minutesUntilStart(monday(20, 59)))
    }

    @Test
    fun `окно уже идёт - предупреждать нечего`() {
        assertNull(everyDay(window(21, 7)).minutesUntilStart(monday(22, 0)))
    }

    @Test
    fun `после окончания окна считаем до завтрашнего`() {
        // Вторник 08:00, сон начинается в 21:00 того же дня — 13 часов.
        assertEquals(13 * 60, everyDay(window(21, 7)).minutesUntilStart(tuesday(8, 0)))
    }

    @Test
    fun `окно завтра, сегодня его нет - считаем через полночь`() {
        val rules = ScheduleRules(mapOf(DayOfWeek.TUESDAY to window(21, 7)), enabled = true)
        // Понедельник 23:00 → до вторника 21:00 остаётся 22 часа.
        assertEquals(22 * 60, rules.minutesUntilStart(monday(23, 0)))
    }

    @Test
    fun `выключенное расписание - не предупреждаем`() {
        assertNull(everyDay(window(21, 7), enabled = false).minutesUntilStart(monday(20, 50)))
    }

    @Test
    fun `ближайшего окна нет - null`() {
        val rules = ScheduleRules(mapOf(DayOfWeek.THURSDAY to window(21, 7)), enabled = true)
        assertNull(rules.minutesUntilStart(monday(10, 0)))
    }

    // --- Совмещение двух расписаний ---

    @Test
    fun `сон бьёт учёбу при пересечении окон`() {
        val study = everyDay(window(8, 22))
        val sleep = everyDay(window(21, 7))
        val state = scheduleStateAt(monday(21, 30), study, sleep)
        assertEquals(ScheduleState.Sleep(LocalTime.of(7, 0)), state)
    }

    @Test
    fun `идёт только учёба`() {
        val state = scheduleStateAt(monday(10, 0), everyDay(window(8, 14)), everyDay(window(21, 7)))
        assertEquals(ScheduleState.Study(LocalTime.of(14, 0)), state)
    }

    @Test
    fun `ни одно окно не идёт`() {
        val state = scheduleStateAt(monday(16, 0), everyDay(window(8, 14)), everyDay(window(21, 7)))
        assertEquals(ScheduleState.Inactive, state)
    }

    @Test
    fun `выключенный сон не мешает учёбе`() {
        val state = scheduleStateAt(
            monday(21, 30),
            study = everyDay(window(8, 22)),
            sleep = everyDay(window(21, 7), enabled = false)
        )
        assertEquals(ScheduleState.Study(LocalTime.of(22, 0)), state)
    }
}
