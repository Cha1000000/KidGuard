package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Формула «бюджет = лимит + бонус» уже разъезжалась однажды: родительская «Статистика» считала
 * бюджет по голому лимиту, пока enforcement учитывал бонус, и родитель видел «исчерпан» при
 * незаблокированном телефоне. Тесты фиксируют формулу в одном месте.
 */
class DailyBudgetTest {

    @Test
    fun `лимит не задан - NoLimit независимо от расхода`() {
        val state = dailyBudgetState(limitMinutes = null, bonusMinutes = 60, usedMinutes = 300)
        assertEquals(DailyBudgetState.NoLimit, state)
    }

    @Test
    fun `бонус прибавляется к бюджету и увеличивает остаток`() {
        // Лимит 180, бонус 60 -> бюджет 240; израсходовано 173 -> осталось 67.
        val state = dailyBudgetState(limitMinutes = 180, bonusMinutes = 60, usedMinutes = 173)
        assertEquals(DailyBudgetState.Remaining(budgetMinutes = 240, usedMinutes = 173, leftMinutes = 67), state)
    }

    @Test
    fun `без бонуса бюджет равен лимиту`() {
        val state = dailyBudgetState(limitMinutes = 180, bonusMinutes = 0, usedMinutes = 100)
        assertEquals(DailyBudgetState.Remaining(budgetMinutes = 180, usedMinutes = 100, leftMinutes = 80), state)
    }

    @Test
    fun `расход больше бюджета - Overrun с разницей`() {
        // Случай из жизни: лимит 3 ч, бонус 1 ч, на экране 4 ч 53 мин -> 53 мин сверх бюджета.
        val state = dailyBudgetState(limitMinutes = 180, bonusMinutes = 60, usedMinutes = 293)
        assertEquals(DailyBudgetState.Overrun(budgetMinutes = 240, usedMinutes = 293, overMinutes = 53), state)
    }

    @Test
    fun `израсходовано ровно по бюджету - Overrun без перерасхода`() {
        // Та же граница, что в ObserveLimitStateUseCase: остаток 0 уже означает блокировку.
        val state = dailyBudgetState(limitMinutes = 180, bonusMinutes = 60, usedMinutes = 240)
        assertEquals(DailyBudgetState.Overrun(budgetMinutes = 240, usedMinutes = 240, overMinutes = 0), state)
    }

    @Test
    fun `лимит ноль без бонуса - весь расход идёт в перерасход`() {
        val state = dailyBudgetState(limitMinutes = 0, bonusMinutes = 0, usedMinutes = 18)
        assertEquals(DailyBudgetState.Overrun(budgetMinutes = 0, usedMinutes = 18, overMinutes = 18), state)
    }

    @Test
    fun `лимит ноль с бонусом - бюджет равен бонусу`() {
        // День без доступа, но родитель выдал 30 минут разово.
        val state = dailyBudgetState(limitMinutes = 0, bonusMinutes = 30, usedMinutes = 10)
        assertEquals(DailyBudgetState.Remaining(budgetMinutes = 30, usedMinutes = 10, leftMinutes = 20), state)
    }
}
