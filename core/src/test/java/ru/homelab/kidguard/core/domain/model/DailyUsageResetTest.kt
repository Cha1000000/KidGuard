package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyUsageResetTest {
    private val today = LocalDate.of(2026, 7, 28)

    @Test fun `null маркер — не применяем`() {
        assertEquals(false, shouldApplyReset(null, today, 0L))
    }

    @Test fun `маркер за вчера — не применяем`() {
        val marker = DailyUsageReset(today.minusDays(1), issuedAt = 100L)
        assertEquals(false, shouldApplyReset(marker, today, 0L))
    }

    @Test fun `маркер сегодня, но не новее применённого — не применяем`() {
        val marker = DailyUsageReset(today, issuedAt = 100L)
        assertEquals(false, shouldApplyReset(marker, today, lastAppliedAt = 100L))
    }

    @Test fun `маркер сегодня и новее применённого — применяем`() {
        val marker = DailyUsageReset(today, issuedAt = 101L)
        assertEquals(true, shouldApplyReset(marker, today, lastAppliedAt = 100L))
    }
}
