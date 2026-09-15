package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Что родитель видит о блокировке дня. Маркеров три, и порядок их нажатия разный — цена ошибки в
 * том, что родитель видит «заблокировано», когда у ребёнка уже есть время, или наоборот.
 */
class DayBlockStateTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val block = DailyUsageBlock(today, issuedAt = 1_000L)
    private val appliedAt = Instant.parse("2026-09-15T11:32:00Z")
    private val applied = AppliedDayBlock(today, issuedAt = 1_000L, appliedAt = appliedAt, minutesLeftBefore = 80)

    private fun state(
        block: DailyUsageBlock? = this.block,
        reset: DailyUsageReset? = null,
        unblock: DailyUsageUnblock? = null,
        applied: AppliedDayBlock? = null
    ) = dayBlockState(today, block, reset, unblock, applied)

    @Test
    fun `без блокировки ничего не показываем`() {
        assertEquals(DayBlockState.NotBlocked, state(block = null))
    }

    @Test
    fun `вчерашняя блокировка сегодня не действует`() {
        assertEquals(DayBlockState.NotBlocked, state(block = DailyUsageBlock(today.minusDays(1), 1_000L)))
    }

    @Test
    fun `нажали, телефон ещё не подтвердил - ожидание`() {
        assertEquals(DayBlockState.Pending(1_000L), state())
    }

    @Test
    fun `телефон подтвердил - заблокировано с остатком`() {
        assertEquals(DayBlockState.Confirmed(1_000L, appliedAt, 80), state(applied = applied))
    }

    @Test
    fun `подтверждение предыдущей блокировки новую не подтверждает`() {
        // Разблокировали и заблокировали снова: отчёт телефона ещё про прошлую блокировку.
        val newerBlock = DailyUsageBlock(today, issuedAt = 5_000L)
        assertEquals(DayBlockState.Pending(5_000L), state(block = newerBlock, applied = applied))
    }

    @Test
    fun `вчерашнее подтверждение не засчитывается`() {
        assertEquals(DayBlockState.Pending(1_000L), state(applied = applied.copy(date = today.minusDays(1))))
    }

    @Test
    fun `сброс после блокировки снимает её`() {
        assertEquals(DayBlockState.NotBlocked, state(reset = DailyUsageReset(today, 2_000L), applied = applied))
    }

    @Test
    fun `сброс до блокировки её не снимает`() {
        assertEquals(DayBlockState.Pending(1_000L), state(reset = DailyUsageReset(today, 500L)))
    }

    @Test
    fun `разблокировка кнопкой снимает блокировку`() {
        val unblock = DailyUsageUnblock(today, 2_000L, restoreRemaining = true)
        assertEquals(DayBlockState.NotBlocked, state(unblock = unblock, applied = applied))
    }

    @Test
    fun `бонус во время блокировки снимает её`() {
        val byBonus = DailyUsageUnblock(today, 2_000L, restoreRemaining = false)
        assertEquals(DayBlockState.NotBlocked, state(unblock = byBonus))
    }

    @Test
    fun `повторная блокировка после разблокировки снова действует`() {
        val unblock = DailyUsageUnblock(today, 2_000L, restoreRemaining = true)
        val again = DailyUsageBlock(today, 3_000L)
        assertTrue(isDayBlockActive(today, again, null, unblock))
        assertFalse(isDayBlockActive(today, block, null, unblock))
    }
}
