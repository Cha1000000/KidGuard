package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyUsageBlockTest {
    private val today = LocalDate.of(2026, 7, 29)

    @Test fun `null маркер — не применяем`() {
        assertEquals(false, shouldApplyBlock(null, today, 0L))
    }

    @Test fun `маркер за вчера — не применяем`() {
        val marker = DailyUsageBlock(today.minusDays(1), issuedAt = 100L)
        assertEquals(false, shouldApplyBlock(marker, today, 0L))
    }

    @Test fun `маркер сегодня, но не новее применённого — не применяем`() {
        val marker = DailyUsageBlock(today, issuedAt = 100L)
        assertEquals(false, shouldApplyBlock(marker, today, lastAppliedAt = 100L))
    }

    @Test fun `маркер сегодня и новее применённого — применяем`() {
        val marker = DailyUsageBlock(today, issuedAt = 101L)
        assertEquals(true, shouldApplyBlock(marker, today, lastAppliedAt = 100L))
    }
}
