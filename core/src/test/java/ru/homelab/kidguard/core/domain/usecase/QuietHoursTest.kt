package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class QuietHoursTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun at(hour: Int, minute: Int = 0) =
        LocalDate.of(2026, 9, 5).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    @Test
    fun `днём тревожим`() {
        assertFalse(isQuietHours(at(8), zone))
        assertFalse(isQuietHours(at(15), zone))
        assertFalse(isQuietHours(at(22, 59), zone))
    }

    @Test
    fun `ночью молчим`() {
        assertTrue(isQuietHours(at(23), zone))
        // Окно переходит через полночь — самый вероятный источник ошибки в такой проверке.
        assertTrue(isQuietHours(at(0, 4), zone))
        assertTrue(isQuietHours(at(7, 59), zone))
    }
}
