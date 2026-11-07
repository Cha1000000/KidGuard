package ru.homelab.kidguard.platform.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт setup-grace звонка (план, этап 6, дефект D16):
 * grace существует только после реального действия звонка и ограничен по времени.
 */
class CallSetupGraceTest {

    private val graceMs = 20_000L

    @Test
    fun `звонок не начинался — grace неактивен сразу после boot`() {
        assertFalse(CallSetupGrace.isActive(callStartedAtMs = null, nowMs = 0L, graceMs = graceMs))
    }

    @Test
    fun `звонок не начинался — grace неактивен и через пять секунд после boot`() {
        assertFalse(CallSetupGrace.isActive(callStartedAtMs = null, nowMs = 5_000L, graceMs = graceMs))
    }

    @Test
    fun `звонок не начинался — grace неактивен в конце первых двадцати секунд`() {
        assertFalse(
            CallSetupGrace.isActive(callStartedAtMs = null, nowMs = 19_999L, graceMs = graceMs)
        )
    }

    @Test
    fun `сразу после тапа по контакту grace активен`() {
        assertTrue(
            CallSetupGrace.isActive(callStartedAtMs = 1_000L, nowMs = 1_000L, graceMs = graceMs)
        )
    }

    @Test
    fun `grace активен до последней миллисекунды окна`() {
        assertTrue(
            CallSetupGrace.isActive(callStartedAtMs = 1_000L, nowMs = 20_999L, graceMs = graceMs)
        )
    }

    @Test
    fun `отменённый вызов завершает grace по истечении окна`() {
        assertFalse(
            CallSetupGrace.isActive(callStartedAtMs = 1_000L, nowMs = 21_000L, graceMs = graceMs)
        )
    }

    @Test
    fun `через минуту после старта звонка grace уже неактивен`() {
        assertFalse(
            CallSetupGrace.isActive(callStartedAtMs = 1_000L, nowMs = 61_000L, graceMs = graceMs)
        )
    }

    @Test
    fun `момент тапа может быть нулевым — grace работает и от нуля`() {
        // `0L` — валидный момент elapsed-тапа (после boot счётчик мал); «0 = звонка не было» —
        // форма дефекта D16, она не должна вернуться.
        assertTrue(CallSetupGrace.isActive(callStartedAtMs = 0L, nowMs = 1_000L, graceMs = graceMs))
    }

    @Test
    fun `отрицательная дельта (elapsed пошёл назад) — grace активен`() {
        // elapsedRealtime в пределах загрузки назад не идёт; если дельта всё же отрицательная,
        // безопаснее держать замок скрытым, чем вернуть его поверх набора.
        assertTrue(
            CallSetupGrace.isActive(callStartedAtMs = 5_000L, nowMs = 4_000L, graceMs = graceMs)
        )
    }
}
