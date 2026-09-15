package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyUsageUnblockTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val block = DailyUsageBlock(today, issuedAt = 1_000L)

    private fun unblock(at: Long, date: LocalDate = today) = DailyUsageUnblock(date, at, restoreRemaining = true)

    @Test
    fun `разблокировка новее блокировки применяется`() {
        assertTrue(shouldApplyUnblock(unblock(2_000L), block, today, lastAppliedAt = 0L))
    }

    @Test
    fun `без маркера применять нечего`() {
        assertFalse(shouldApplyUnblock(null, block, today, lastAppliedAt = 0L))
    }

    @Test
    fun `вчерашняя разблокировка сегодня не действует`() {
        assertFalse(shouldApplyUnblock(unblock(2_000L, today.minusDays(1)), block, today, 0L))
    }

    @Test
    fun `уже применённая разблокировка повторно не применяется`() {
        assertFalse(shouldApplyUnblock(unblock(2_000L), block, today, lastAppliedAt = 2_000L))
    }

    @Test
    fun `старая разблокировка не снимает новую блокировку`() {
        // Заблокировал → разблокировал → снова заблокировал: в документе остаётся разблокировка
        // старше блокировки. Применить её значило бы молча снять свежую блокировку.
        val newerBlock = DailyUsageBlock(today, issuedAt = 3_000L)
        assertFalse(shouldApplyUnblock(unblock(2_000L), newerBlock, today, lastAppliedAt = 0L))
    }

    @Test
    fun `без сегодняшней блокировки разблокировать нечего`() {
        assertFalse(shouldApplyUnblock(unblock(2_000L), null, today, 0L))
        val yesterdayBlock = DailyUsageBlock(today.minusDays(1), issuedAt = 1_000L)
        assertFalse(shouldApplyUnblock(unblock(2_000L), yesterdayBlock, today, 0L))
    }
}
