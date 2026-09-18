package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsCardMenuTest {

    @Test
    fun `пункт блокировки распознаётся`() {
        assertTrue(isRecentsLockMenuItem("Заблокировать"))
        assertTrue(isRecentsLockMenuItem("Закрепить приложение"))
        assertTrue(isRecentsLockMenuItem("Lock"))
    }

    @Test
    fun `обратный пункт не принимается за блокировку`() {
        assertFalse(isRecentsLockMenuItem("Разблокировать"))
        assertFalse(isRecentsLockMenuItem("Открепить"))
        assertTrue(isRecentsUnlockMenuItem("Разблокировать"))
    }

    @Test
    fun `чужие пункты меню не трогаем`() {
        listOf("Разделить экран", "Закрыть", "О приложении", "Мини-окно", null, "  ").forEach {
            assertFalse(isRecentsLockMenuItem(it))
            assertFalse(isRecentsUnlockMenuItem(it))
        }
    }
}
