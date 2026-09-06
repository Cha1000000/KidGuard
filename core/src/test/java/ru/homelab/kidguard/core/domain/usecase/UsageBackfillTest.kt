package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Досчёт времени, пропущенного мёртвым контролем. Ошибка здесь стоит дорого в обе стороны: недобор
 * оставляет ребёнку выигрыш от убийства приложения, перебор — отнимает время ни за что.
 */
class UsageBackfillTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    /** Момент по московскому времени — так тесты читаются как реальные сутки ребёнка. */
    private fun at(date: String, time: String): Instant =
        LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant()

    private fun event(at: Instant, pkg: String, kind: UsageEventKind) = RawUsageEvent(at, pkg, kind)

    private fun minutesOf(interval: UsageInterval): Long = interval.duration.toMinutes()

    @Test
    fun `приложение открыли и закрыли внутри окна`() {
        val from = at("2026-09-06", "12:00")
        val to = at("2026-09-06", "14:00")
        val intervals = foldUsageEvents(
            listOf(
                event(at("2026-09-06", "12:10"), "com.roblox.client", UsageEventKind.APP_FOREGROUND),
                event(at("2026-09-06", "12:40"), "com.roblox.client", UsageEventKind.APP_BACKGROUND)
            ),
            from, to
        )
        assertEquals(1, intervals.size)
        assertEquals("com.roblox.client", intervals[0].packageName)
        assertEquals(30L, minutesOf(intervals[0]))
    }

    @Test
    fun `игра шла ещё до провала — считаем с начала окна`() {
        // Ровно боевой случай: контроль убили посреди игры, приложение уже было открыто.
        val from = at("2026-09-06", "11:55")
        val to = at("2026-09-06", "14:15")
        val intervals = foldUsageEvents(
            listOf(event(at("2026-09-06", "11:52"), "com.roblox.client", UsageEventKind.APP_FOREGROUND)),
            from, to
        )
        assertEquals(1, intervals.size)
        assertEquals(from, intervals[0].from)
        assertEquals(140L, minutesOf(intervals[0]))
    }

    @Test
    fun `открытое приложение закрывается концом окна`() {
        val from = at("2026-09-06", "12:00")
        val to = at("2026-09-06", "13:00")
        val intervals = foldUsageEvents(
            listOf(event(at("2026-09-06", "12:30"), "com.roblox.client", UsageEventKind.APP_FOREGROUND)),
            from, to
        )
        assertEquals(30L, minutesOf(intervals.single()))
        assertEquals(to, intervals.single().to)
    }

    @Test
    fun `погасший экран обрывает счёт`() {
        val from = at("2026-09-06", "12:00")
        val to = at("2026-09-06", "14:00")
        val intervals = foldUsageEvents(
            listOf(
                event(at("2026-09-06", "12:00"), "com.roblox.client", UsageEventKind.APP_FOREGROUND),
                event(at("2026-09-06", "12:20"), "", UsageEventKind.SCREEN_OFF)
            ),
            from, to
        )
        assertEquals(20L, minutesOf(intervals.single()))
    }

    @Test
    fun `переключение между приложениями закрывает предыдущее`() {
        val from = at("2026-09-06", "12:00")
        val to = at("2026-09-06", "13:00")
        val intervals = foldUsageEvents(
            listOf(
                event(at("2026-09-06", "12:00"), "com.roblox.client", UsageEventKind.APP_FOREGROUND),
                event(at("2026-09-06", "12:15"), "ru.oneme.app", UsageEventKind.APP_FOREGROUND),
                event(at("2026-09-06", "12:25"), "ru.oneme.app", UsageEventKind.APP_BACKGROUND)
            ),
            from, to
        )
        assertEquals(2, intervals.size)
        assertEquals("com.roblox.client" to 15L, intervals[0].packageName to minutesOf(intervals[0]))
        assertEquals("ru.oneme.app" to 10L, intervals[1].packageName to minutesOf(intervals[1]))
    }

    @Test
    fun `чужой уход в фон не обрывает текущее приложение`() {
        // Система шлёт APP_BACKGROUND и по приложению, которое уже сменилось другим.
        val from = at("2026-09-06", "12:00")
        val to = at("2026-09-06", "13:00")
        val intervals = foldUsageEvents(
            listOf(
                event(at("2026-09-06", "12:00"), "com.roblox.client", UsageEventKind.APP_FOREGROUND),
                event(at("2026-09-06", "12:05"), "ru.oneme.app", UsageEventKind.APP_BACKGROUND)
            ),
            from, to
        )
        assertEquals(60L, minutesOf(intervals.single()))
    }

    @Test
    fun `события после конца окна не учитываются`() {
        val from = at("2026-09-06", "12:00")
        val to = at("2026-09-06", "12:30")
        val intervals = foldUsageEvents(
            listOf(
                event(at("2026-09-06", "12:00"), "com.roblox.client", UsageEventKind.APP_FOREGROUND),
                event(at("2026-09-06", "13:00"), "com.roblox.client", UsageEventKind.APP_BACKGROUND)
            ),
            from, to
        )
        assertEquals(30L, minutesOf(intervals.single()))
    }

    @Test
    fun `пустое окно не даёт отрезков`() {
        val moment = at("2026-09-06", "12:00")
        assertTrue(foldUsageEvents(emptyList(), moment, moment).isEmpty())
        assertTrue(foldUsageEvents(emptyList(), moment, moment.minusSeconds(60)).isEmpty())
    }

    @Test
    fun `без событий вообще ничего не досчитываем`() {
        val intervals = foldUsageEvents(
            emptyList(),
            at("2026-09-06", "12:00"),
            at("2026-09-06", "14:00")
        )
        assertTrue(intervals.isEmpty())
    }

    @Test
    fun `отрезок через полночь делится по датам`() {
        val interval = UsageInterval(
            packageName = "com.roblox.client",
            from = at("2026-09-06", "23:40"),
            to = at("2026-09-07", "00:30")
        )
        val byDate = splitByDate(interval, zone)
        assertEquals(2, byDate.size)
        assertEquals(20 * 60, byDate[LocalDate.parse("2026-09-06")])
        assertEquals(30 * 60, byDate[LocalDate.parse("2026-09-07")])
    }

    @Test
    fun `отрезок внутри одних суток остаётся одной датой`() {
        val interval = UsageInterval(
            packageName = "com.roblox.client",
            from = at("2026-09-06", "12:00"),
            to = at("2026-09-06", "12:45")
        )
        assertEquals(mapOf(LocalDate.parse("2026-09-06") to 45 * 60), splitByDate(interval, zone))
    }

    @Test
    fun `секунды складываются по дате и пакету`() {
        val intervals = listOf(
            UsageInterval("com.roblox.client", at("2026-09-06", "12:00"), at("2026-09-06", "12:10")),
            UsageInterval("ru.oneme.app", at("2026-09-06", "12:10"), at("2026-09-06", "12:15")),
            UsageInterval("com.roblox.client", at("2026-09-06", "12:15"), at("2026-09-06", "12:20"))
        )
        val result = secondsByDateAndPackage(intervals, zone)
        val day = LocalDate.parse("2026-09-06")
        assertEquals(15 * 60, result[day to "com.roblox.client"])
        assertEquals(5 * 60, result[day to "ru.oneme.app"])
    }
}
