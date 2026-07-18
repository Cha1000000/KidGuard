package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Матчинг доменов для DNS-фильтра. Список блокирует домен и все поддомены (суффикс),
 * но не должен ловить "похожие" домены типа myvk.com. google-тумблер — отдельный точный флаг.
 */
class SiteBlockRulesTest {

    @Test
    fun `домен из списка блокирует сам себя и поддомены`() {
        val rules = SiteBlockRules(setOf("vk.com"), blockGoogleSearch = false)
        assertTrue(rules.isBlocked("vk.com"))
        assertTrue(rules.isBlocked("m.vk.com"))
        assertTrue(rules.isBlocked("a.b.vk.com"))
    }

    @Test
    fun `похожий домен по префиксу не блокируется — суффиксное совпадение, не substring`() {
        val rules = SiteBlockRules(setOf("vk.com"), blockGoogleSearch = false)
        assertFalse(rules.isBlocked("myvk.com"))
    }

    @Test
    fun `домен с блокируемым как поддоменом чужого домена не блокируется`() {
        val rules = SiteBlockRules(setOf("vk.com"), blockGoogleSearch = false)
        assertFalse(rules.isBlocked("vk.com.evil.com"))
    }

    @Test
    fun `google-тумблер блокирует google-com и www-google-com`() {
        val rules = SiteBlockRules(emptySet(), blockGoogleSearch = true)
        assertTrue(rules.isBlocked("google.com"))
        assertTrue(rules.isBlocked("www.google.com"))
    }

    @Test
    fun `google-тумблер не трогает mail и play google`() {
        val rules = SiteBlockRules(emptySet(), blockGoogleSearch = true)
        assertFalse(rules.isBlocked("mail.google.com"))
        assertFalse(rules.isBlocked("play.google.com"))
    }

    @Test
    fun `регистр и завершающая точка нормализуются`() {
        val rules = SiteBlockRules(setOf("vk.com"), blockGoogleSearch = false)
        assertTrue(rules.isBlocked("M.VK.COM."))
    }

    @Test
    fun `пустые правила ничего не блокируют`() {
        assertFalse(SiteBlockRules.NONE.isBlocked("vk.com"))
        assertFalse(SiteBlockRules.NONE.isBlocked("google.com"))
    }

    @Test
    fun `isActive отражает наличие хоть одного правила`() {
        assertFalse(SiteBlockRules.NONE.isActive)
        assertTrue(SiteBlockRules(setOf("vk.com"), false).isActive)
        assertTrue(SiteBlockRules(emptySet(), true).isActive)
    }
}
