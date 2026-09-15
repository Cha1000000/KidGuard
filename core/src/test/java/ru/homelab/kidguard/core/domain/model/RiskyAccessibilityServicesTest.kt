package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskyAccessibilityServicesTest {

    private val own = "ru.homelab.kidguard"
    private val kidguard = "ru.homelab.kidguard/ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService"
    private val hiosMenu =
        "com.android.systemui.accessibility.accessibilitymenu/com.android.systemui.accessibility.accessibilitymenu.AccessibilityMenuService"
    private val suiteMenu =
        "com.google.android.marvin.talkback/com.google.android.accessibility.accessibilitymenu.AccessibilityMenuService"
    private val talkback = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"

    @Test
    fun `меню спец возможностей опасно в обоих вариантах`() {
        assertTrue(RiskyAccessibilityServices.isRisky(hiosMenu))
        assertTrue(RiskyAccessibilityServices.isRisky(suiteMenu))
        assertTrue(RiskyAccessibilityServices.isRisky("com.android.systemui.accessibility.accessibilitymenu/.AccessibilityMenuService"))
    }

    @Test
    fun `TalkBack и сам KidGuard опасными не считаются`() {
        assertFalse(RiskyAccessibilityServices.isRisky(talkback))
        assertFalse(RiskyAccessibilityServices.isRisky(kidguard))
        assertFalse(RiskyAccessibilityServices.isRisky(""))
    }

    @Test
    fun `посторонние службы - всё кроме KidGuard`() {
        assertEquals(listOf(hiosMenu, talkback), RiskyAccessibilityServices.foreignIn("$kidguard:$hiosMenu:$talkback", own))
        assertEquals(emptyList<String>(), RiskyAccessibilityServices.foreignIn(kidguard, own))
        assertEquals(emptyList<String>(), RiskyAccessibilityServices.foreignIn(null, own))
    }

    @Test
    fun `вычищение убирает только опасное и сохраняет KidGuard и TalkBack в порядке`() {
        assertEquals("$kidguard:$talkback", RiskyAccessibilityServices.withoutRisky("$hiosMenu:$kidguard:$suiteMenu:$talkback"))
    }

    @Test
    fun `если опасного нет - в настройки не пишем`() {
        assertNull(RiskyAccessibilityServices.withoutRisky("$kidguard:$talkback"))
        assertNull(RiskyAccessibilityServices.withoutRisky(null))
        assertNull(RiskyAccessibilityServices.withoutRisky(""))
    }

    @Test
    fun `опасная служба делает отчёт нездоровым, прочая - нет`() {
        val base = DeviceHealth(accessibility = true, overlay = true, deviceAdmin = true, vpn = true, batteryOptimization = true)
        assertTrue(base.copy(foreignAccessibilityServices = listOf(talkback)).isHealthy)
        assertFalse(base.copy(foreignAccessibilityServices = listOf(hiosMenu)).isHealthy)
        assertEquals(listOf(hiosMenu), base.copy(foreignAccessibilityServices = listOf(talkback, hiosMenu)).riskyAccessibilityServices())
    }
}
