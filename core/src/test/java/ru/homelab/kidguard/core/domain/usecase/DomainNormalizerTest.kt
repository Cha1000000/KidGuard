package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Приводим то, что родитель вбил в поле ввода (домен или полный URL), к чистому хосту. */
class DomainNormalizerTest {

    @Test
    fun `полный URL с протоколом, путём и query — остаётся только хост`() {
        assertEquals("vk.com", DomainNormalizer.normalize("HTTPS://VK.com/feed?x=1"))
    }

    @Test
    fun `порт отбрасывается`() {
        assertEquals("m.vk.com", DomainNormalizer.normalize("m.vk.com:443"))
    }

    @Test
    fun `пробелы по краям и завершающая точка убираются`() {
        assertEquals("vk.com", DomainNormalizer.normalize("  vk.com.  "))
    }

    @Test
    fun `пустая строка — null`() {
        assertNull(DomainNormalizer.normalize(""))
        assertNull(DomainNormalizer.normalize("   "))
    }

    @Test
    fun `строка без точки — null`() {
        assertNull(DomainNormalizer.normalize("no-dot"))
    }

    @Test
    fun `строка с недопустимым пробелом внутри — null`() {
        assertNull(DomainNormalizer.normalize("bad space.com"))
    }
}
