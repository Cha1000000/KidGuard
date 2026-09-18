package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    @Test
    fun `находит по названию без учёта регистра`() =
        assertTrue(matchesAppQuery("Плавающие окна", "com.transsion.thunderback", "плавающие"))

    @Test
    fun `находит по имени пакета`() =
        assertTrue(matchesAppQuery("Плавающие окна", "com.transsion.thunderback", "thunderback"))

    @Test
    fun `находит по части пакета`() =
        assertTrue(matchesAppQuery("Смарт-панель", "com.transsion.smartpanel", "transsion.smart"))

    @Test
    fun `пустая строка и пробелы подходят всем`() {
        assertTrue(matchesAppQuery("Chrome", "com.android.chrome", ""))
        assertTrue(matchesAppQuery("Chrome", "com.android.chrome", "   "))
    }

    @Test
    fun `пробелы по краям не мешают`() =
        assertTrue(matchesAppQuery("Chrome", "com.android.chrome", " chrome "))

    @Test
    fun `не находит чужое`() =
        assertFalse(matchesAppQuery("Chrome", "com.android.chrome", "roblox"))
}
