package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт доступности PIN-обхода soft-блока: без заданного PIN ссылки быть не должно, иначе
 * `NoPinSet` в `PinOverlayManager` превращается в обход любыми четырьмя цифрами.
 */
class PinBypassTest {

    @Test
    fun `без PIN ссылка запрещена даже при известном пакете`() {
        assertFalse(pinBypassAllowed(pinConfigured = false, activePackage = "com.example"))
    }

    @Test
    fun `без пакета ссылки нет даже при заданном PIN`() {
        assertFalse(pinBypassAllowed(pinConfigured = true, activePackage = null))
    }

    @Test
    fun `PIN есть и пакет известен - ссылка разрешена`() {
        assertTrue(pinBypassAllowed(pinConfigured = true, activePackage = "com.example"))
    }

    @Test
    fun `без PIN и без пакета - запрещено`() {
        assertFalse(pinBypassAllowed(pinConfigured = false, activePackage = null))
    }
}
