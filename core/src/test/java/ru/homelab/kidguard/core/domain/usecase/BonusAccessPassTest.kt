package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пропуск приложения сверх общего дневного лимита: родитель выдал приложению дополнительное
 * время, и оно тратится, только пока ребёнок в этом приложении сидит.
 */
class BonusAccessPassTest {

    @Test
    fun `бонуса нет - пропуска нет`() {
        assertFalse(hasBonusAccessPass(bonusMinutes = 0, bonusSpentSeconds = 0))
    }

    @Test
    fun `бонус выдан и не потрачен - пропуск активен`() {
        assertTrue(hasBonusAccessPass(bonusMinutes = 15, bonusSpentSeconds = 0))
    }

    @Test
    fun `бонус потрачен частично - пропуск ещё активен`() {
        assertTrue(hasBonusAccessPass(bonusMinutes = 15, bonusSpentSeconds = 14 * 60))
    }

    @Test
    fun `бонус потрачен ровно - пропуск гаснет`() {
        assertFalse(hasBonusAccessPass(bonusMinutes = 15, bonusSpentSeconds = 15 * 60))
    }

    @Test
    fun `неполная минута расхода пропуск не гасит`() {
        assertTrue(hasBonusAccessPass(bonusMinutes = 15, bonusSpentSeconds = 14 * 60 + 59))
    }

    @Test
    fun `повторная выдача продлевает пропуск - минуты суммируются`() {
        // Первые 15 минут израсходованы, пропуск погас.
        assertFalse(hasBonusAccessPass(bonusMinutes = 15, bonusSpentSeconds = 15 * 60))
        // Родитель выдал ещё 15: суммарно 30 против потраченных 15 — пропуск снова активен.
        assertTrue(hasBonusAccessPass(bonusMinutes = 30, bonusSpentSeconds = 15 * 60))
    }

    @Test
    fun `остаток окна считается в минутах и не уходит в минус`() {
        assertEquals(15, bonusMinutesLeft(bonusMinutes = 15, bonusSpentSeconds = 0))
        assertEquals(1, bonusMinutesLeft(bonusMinutes = 15, bonusSpentSeconds = 14 * 60))
        assertEquals(0, bonusMinutesLeft(bonusMinutes = 15, bonusSpentSeconds = 15 * 60))
        // Расход мог перевалить за выданное между тиками — остаток всё равно ноль, не минус.
        assertEquals(0, bonusMinutesLeft(bonusMinutes = 15, bonusSpentSeconds = 20 * 60))
    }
}
