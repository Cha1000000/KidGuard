package ru.homelab.kidguard.platform.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Контракт обратного отсчёта замка перерыва: владелец отсчёта — deadline (endsAtElapsedMs),
 * остаток каждый раз вычисляется заново, а не декрементится локально.
 *
 * Округление — ceil: последние 999 мс ещё считаются целой секундой («0:01»), ровно в deadline
 * и после него — «0:00», и никогда не бывает отрицательных значений.
 */
class CountdownCalculatorTest {

    @Test
    fun `ровная секунда до deadline не округляется вверх`() {
        assertEquals(60, CountdownCalculator.secondsLeft(endsAtElapsedMs = 61_000L, nowElapsedMs = 1_000L))
    }

    @Test
    fun `одна миллисекунда после секундной границы даёт следующую секунду`() {
        // Остаток 60 с + 1 мс — ceil даёт 61, а не 60: неполная секунда считается целой.
        assertEquals(61, CountdownCalculator.secondsLeft(endsAtElapsedMs = 60_001L, nowElapsedMs = 0L))
    }

    @Test
    fun `последняя неполная секунда ещё считается секундой`() {
        assertEquals(1, CountdownCalculator.secondsLeft(endsAtElapsedMs = 1_999L, nowElapsedMs = 1_000L))
    }

    @Test
    fun `deadline наступил ровно сейчас — ноль`() {
        assertEquals(0, CountdownCalculator.secondsLeft(endsAtElapsedMs = 5_000L, nowElapsedMs = 5_000L))
    }

    @Test
    fun `deadline в прошлом — ноль без отрицательных значений`() {
        assertEquals(0, CountdownCalculator.secondsLeft(endsAtElapsedMs = 5_000L, nowElapsedMs = 9_000L))
    }

    @Test
    fun `большой пропущенный интервал вычитает реальные секунды`() {
        // Экран был погашен на 2 минуты: локальный декремент «замерз» бы, а от deadline остаток честный.
        assertEquals(180, CountdownCalculator.secondsLeft(endsAtElapsedMs = 300_000L, nowElapsedMs = 120_000L))
    }

    @Test
    fun `за миллисекунду до deadline ещё не ноль`() {
        assertEquals(1, CountdownCalculator.secondsLeft(endsAtElapsedMs = 5_000L, nowElapsedMs = 4_999L))
    }
}
