package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Чистая логика вехи 5.3: когда поднимать blackhole-VPN и какие пакеты должны его обходить.
 */
class VpnPolicyTest {

    // --- shouldBlockInternet ---

    @Test
    fun `общий лимит исчерпан - блокируем интернет`() {
        assertTrue(shouldBlockInternet(LimitState.Expired))
    }

    @Test
    fun `время ещё есть - интернет не блокируем`() {
        assertFalse(shouldBlockInternet(LimitState.Remaining(10)))
    }

    @Test
    fun `лимит не задан - интернет не блокируем`() {
        assertFalse(shouldBlockInternet(LimitState.NoLimit))
    }

    // --- vpnDisallowedPackages ---

    @Test
    fun `disallowed содержит собственный пакет, даже если белый список пуст`() {
        assertEquals(setOf("ru.homelab.kidguard"), vpnDisallowedPackages(emptySet(), "ru.homelab.kidguard"))
    }

    @Test
    fun `disallowed содержит все пакеты белого списка плюс собственный`() {
        val whitelist = setOf("com.android.dialer", "com.google.android.apps.messaging")
        val result = vpnDisallowedPackages(whitelist, "ru.homelab.kidguard")
        assertEquals(
            setOf("com.android.dialer", "com.google.android.apps.messaging", "ru.homelab.kidguard"),
            result
        )
    }

    @Test
    fun `собственный пакет уже в белом списке - дублей в наборе нет`() {
        val result = vpnDisallowedPackages(setOf("ru.homelab.kidguard"), "ru.homelab.kidguard")
        assertEquals(1, result.size)
    }

    // --- vpnDisallowedFor (веха 5.4) ---

    @Test
    fun `лимит исчерпан - disallowed это whitelist плюс собственный пакет`() {
        val whitelist = setOf("com.android.dialer")
        val allInstalled = setOf("com.android.dialer", "com.example.game", "com.example.social")
        val result = vpnDisallowedFor(LimitState.Expired, whitelist, allInstalled, "ru.homelab.kidguard")
        assertEquals(setOf("com.android.dialer", "ru.homelab.kidguard"), result)
    }

    @Test
    fun `время ещё есть - disallowed это все установленные плюс собственный пакет`() {
        val whitelist = setOf("com.android.dialer")
        val allInstalled = setOf("com.android.dialer", "com.example.game", "com.example.social")
        val result = vpnDisallowedFor(LimitState.Remaining(10), whitelist, allInstalled, "ru.homelab.kidguard")
        assertEquals(
            setOf("com.android.dialer", "com.example.game", "com.example.social", "ru.homelab.kidguard"),
            result
        )
    }

    @Test
    fun `лимит не задан - disallowed это все установленные плюс собственный пакет`() {
        val allInstalled = setOf("com.example.game")
        val result = vpnDisallowedFor(LimitState.NoLimit, emptySet(), allInstalled, "ru.homelab.kidguard")
        assertEquals(setOf("com.example.game", "ru.homelab.kidguard"), result)
    }
}
