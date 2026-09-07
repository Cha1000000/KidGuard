package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEmailTest {

    @Test
    fun `обычные адреса проходят проверку`() {
        assertTrue(isValidAlertEmail("a@b.ru"))
        assertTrue(isValidAlertEmail("racer.kafa@gmail.com"))
        assertTrue(isValidAlertEmail("kid+alerts@sub.domain.co"))
        assertTrue(isValidAlertEmail("x_y-z@mail.example.org"))
    }

    @Test
    fun `мусор проверку не проходит`() {
        assertFalse(isValidAlertEmail(""))
        assertFalse(isValidAlertEmail("   "))
        assertFalse(isValidAlertEmail("без-собаки.ru"))
        assertFalse(isValidAlertEmail("две@собаки@mail.ru"))
        assertFalse(isValidAlertEmail("нет-точки@домен"))
        assertFalse(isValidAlertEmail("про бел@mail.ru"))
    }

    @Test
    fun `пробелы по краям не мешают - их обрежут при сохранении`() {
        assertTrue(isValidAlertEmail("  parent@example.com  "))
    }

    @Test
    fun `нормализация обрезает пробелы`() {
        assertEquals("parent@example.com", normalizeAlertEmail("  parent@example.com "))
    }

    @Test
    fun `пустое поле означает возврат на адрес аккаунта`() {
        assertEquals(null, normalizeAlertEmail(""))
        assertEquals(null, normalizeAlertEmail("    "))
    }
}
