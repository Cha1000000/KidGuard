package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт накопителя непрерывного использования (план «исправление перерывов», этап 3):
 * фактические дельты вместо константы тика, раздельный учёт active/idle, восстановление
 * после смерти процесса без потери накопления и без задним-числом реконструкции.
 */
class ContinuousUsageAccumulatorTest {

    private var cycleCounter = 0
    private fun accumulator() = ContinuousUsageAccumulator { "cycle-${++cycleCounter}" }

    private fun observed(
        acc: ContinuousUsageAccumulator,
        now: Long,
        active: Boolean = true,
        paused: Boolean = false,
        allowed: Boolean = true,
        thresholdMs: Long = 600_000
    ) {
        acc.observe(now, active, paused, allowed, thresholdMs)
    }

    @Test
    fun `тик прибавляет фактическую дельту, а не константу`() {
        val acc = accumulator()
        observed(acc, now = 0)
        observed(acc, now = 45_000)
        assertEquals(45_000L, acc.state?.accumulatedMs)
    }

    @Test
    fun `гашение экрана между тиками фиксируется событием - тёмное время не копится`() {
        val acc = accumulator()
        observed(acc, now = 0, active = true)
        observed(acc, now = 20_000, active = true)         // 20 с активности
        observed(acc, now = 20_000, active = false)        // экран погас (событие)
        observed(acc, now = 50_000, active = false)        // тик: экран темен, не копим
        observed(acc, now = 60_000, active = true)         // экран включён (событие)
        observed(acc, now = 75_000, active = true)         // тик: +15 с
        assertEquals(35_000L, acc.state?.accumulatedMs)
    }

    @Test
    fun `длинная пауза сбрасывает цикл по реальному времени`() {
        val acc = accumulator()
        observed(acc, now = 0, active = true)
        observed(acc, now = 100_000, active = true)
        val cycleBefore = acc.state?.cycleId
        observed(acc, now = 100_000, active = false)
        // Прошло 10 минут погашенного экрана при пороге 10 минут.
        observed(acc, now = 700_000, active = false)
        assertEquals(0L, acc.state?.accumulatedMs)
        assertNotEquals(cycleBefore, acc.state?.cycleId)
    }

    @Test
    fun `пауза короче порога цикл не сбрасывает`() {
        val acc = accumulator()
        observed(acc, now = 0, active = true)
        observed(acc, now = 100_000, active = true)
        val cycleBefore = acc.state?.cycleId
        observed(acc, now = 100_000, active = false)
        observed(acc, now = 400_000, active = false)       // 5 минут при пороге 10
        assertEquals(100_000L, acc.state?.accumulatedMs)
        assertEquals(cycleBefore, acc.state?.cycleId)
    }

    @Test
    fun `восстановление снимка не обнуляет накопление`() {
        val acc = accumulator()
        val saved = ContinuousUsageSession("saved-cycle", accumulatedMs = 100_000,
            idleSinceElapsedMs = null, lastObservedElapsedMs = 50_000)
        acc.restore(saved, nowElapsedMs = 80_000, screenActive = true)
        assertEquals(100_000L, acc.state?.accumulatedMs)
        observed(acc, now = 95_000)
        assertEquals(115_000L, acc.state?.accumulatedMs)
    }

    @Test
    fun `время мёртвого процесса не реконструируется задним числом как экранное`() {
        val acc = accumulator()
        val saved = ContinuousUsageSession("saved-cycle", accumulatedMs = 100_000,
            idleSinceElapsedMs = null, lastObservedElapsedMs = 50_000)
        // Процесс был мёртв 12 минут: накопление сохраняется, но не растёт задним числом.
        acc.restore(saved, nowElapsedMs = 800_000, screenActive = true)
        assertEquals(100_000L, acc.state?.accumulatedMs)
    }

    @Test
    fun `пауза в момент смерти процесса продолжается по реальному времени после восстановления`() {
        val acc = accumulator()
        val saved = ContinuousUsageSession("saved-cycle", accumulatedMs = 100_000,
            idleSinceElapsedMs = 100_000, lastObservedElapsedMs = 100_000)
        acc.restore(saved, nowElapsedMs = 800_000, screenActive = false)
        // С момента начала паузы прошло 700 секунд реального времени при пороге 600 — цикл сброшен.
        observed(acc, now = 800_000, active = false)
        assertEquals(0L, acc.state?.accumulatedMs)
    }

    @Test
    fun `elapsed пошедший назад означает reboot - новый цикл`() {
        val acc = accumulator()
        val saved = ContinuousUsageSession("saved-cycle", accumulatedMs = 300_000,
            idleSinceElapsedMs = null, lastObservedElapsedMs = 500_000)
        acc.restore(saved, nowElapsedMs = 60_000, screenActive = true)
        // Старый цикл недействителен; новый начинается с первого наблюдения после загрузки.
        assertNull(acc.state)
        observed(acc, now = 75_000)
        assertEquals(0L, acc.state?.accumulatedMs)
        assertNotEquals("saved-cycle", acc.state?.cycleId)
    }

    @Test
    fun `заморозка расписанием не копит использование, но не мешает сбросу паузой`() {
        val acc = accumulator()
        observed(acc, now = 0, active = true)
        observed(acc, now = 60_000, active = true)
        // Расписание: экран включён, но не копим.
        observed(acc, now = 120_000, active = true, paused = true)
        assertEquals(60_000L, acc.state?.accumulatedMs)
        // Ночь под расписанием: экран погас надолго — цикл всё равно сбрасывается.
        observed(acc, now = 120_000, active = false, paused = true)
        observed(acc, now = 720_000, active = false, paused = true)
        assertEquals(0L, acc.state?.accumulatedMs)
    }

    @Test
    fun `выключенные перерывы очищают цикл`() {
        val acc = accumulator()
        observed(acc, now = 0, active = true)
        observed(acc, now = 100_000, active = true)
        observed(acc, now = 115_000, active = true, allowed = false)
        assertNull(acc.state)
        // Повторное включение начинает с чистого цикла, а не со старого накопления (D11).
        observed(acc, now = 130_000, active = true, allowed = true)
        assertEquals(0L, acc.state?.accumulatedMs)
        observed(acc, now = 145_000, active = true, allowed = true)
        assertEquals(15_000L, acc.state?.accumulatedMs)
    }

    @Test
    fun `внешний сброс начинает новый цикл`() {
        val acc = accumulator()
        observed(acc, now = 0, active = true)
        observed(acc, now = 100_000, active = true)
        val cycleBefore = acc.state?.cycleId
        acc.reset(nowElapsedMs = 115_000, screenActive = true)
        assertEquals(0L, acc.state?.accumulatedMs)
        assertNotEquals(cycleBefore, acc.state?.cycleId)
        // И накопление продолжается от момента сброса, а не от старого lastObserved.
        observed(acc, now = 130_000, active = true)
        assertEquals(15_000L, acc.state?.accumulatedMs)
    }

    @Test
    fun `холостой сброс на пустом цикле не плодит новые cycleId`() {
        val acc = accumulator()
        observed(acc, now = 0, active = false)
        val cycle = acc.state?.cycleId
        // Пауза длиннее порога, но накапливать было нечего — цикл тот же.
        observed(acc, now = 700_000, active = false)
        assertEquals(cycle, acc.state?.cycleId)
    }

    @Test
    fun `накопление отдаётся в секундах через целочисленное деление`() {
        val acc = accumulator()
        observed(acc, now = 0, active = true)
        observed(acc, now = 1_500, active = true)
        assertTrue(acc.state != null)
        assertEquals(1_500L, acc.state?.accumulatedMs)
    }
}
