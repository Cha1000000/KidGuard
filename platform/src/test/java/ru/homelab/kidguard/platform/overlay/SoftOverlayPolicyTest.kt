package ru.homelab.kidguard.platform.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило снятия мягкой плашки. Главная защита — «лаунчер + причина жива»: после `sendHome`
 * решение становится false, но плашку снимать нельзя, иначе ребёнок не прочитает сообщение.
 */
class SoftOverlayPolicyTest {

    private val allowed = setOf("com.launcher", "ru.homelab.kidguard")

    private fun hide(
        lastReason: BlockReason = BlockReason.STUDY_TIME,
        lastPackage: String? = "com.app",
        activePackage: String? = "com.launcher",
        studyTimeActive: Boolean = true,
        dailyLimitExpired: Boolean = false,
        personalLimitExpired: Boolean = false,
        blockedApps: Set<String> = emptySet()
    ) = shouldHideSoftOverlay(
        lastReason = lastReason,
        lastPackage = lastPackage,
        activePackage = activePackage,
        studyTimeActive = studyTimeActive,
        dailyLimitExpired = dailyLimitExpired,
        personalLimitExpired = personalLimitExpired,
        blockedApps = blockedApps,
        alwaysAllowed = allowed
    )

    @Test
    fun `учёба кончилась - плашку снимаем сразу`() {
        assertTrue(hide(studyTimeActive = false))
    }

    @Test
    fun `учёба идёт, ребёнок на лаунчере - плашку оставляем`() {
        assertFalse(hide(activePackage = "com.launcher", studyTimeActive = true))
    }

    @Test
    fun `учёба идёт, ребёнок открыл другое приложение - плашку снимаем`() {
        assertTrue(hide(activePackage = "com.whitelisted", studyTimeActive = true))
    }

    @Test
    fun `дневной лимит восстановлен - плашку снимаем`() {
        assertTrue(
            hide(lastReason = BlockReason.LIMIT_EXPIRED, dailyLimitExpired = false, personalLimitExpired = false)
        )
    }

    @Test
    fun `дневной лимит ещё исчерпан - плашку оставляем`() {
        assertFalse(
            hide(lastReason = BlockReason.LIMIT_EXPIRED, activePackage = "com.launcher", dailyLimitExpired = true)
        )
    }

    @Test
    fun `личный лимит приложения ещё исчерпан - плашку оставляем`() {
        // Регресс: после sendHome активным становится лаунчер, у него личных лимитов нет.
        // Если смотреть на лимиты ТЕКУЩЕГО пакета, плашка снимается сразу после показа.
        assertFalse(
            hide(
                lastReason = BlockReason.LIMIT_EXPIRED,
                lastPackage = "com.app",
                activePackage = "com.launcher",
                dailyLimitExpired = false,
                personalLimitExpired = true
            )
        )
    }

    @Test
    fun `личный лимит восстановлен, дневной не исчерпан - плашку снимаем`() {
        assertTrue(
            hide(
                lastReason = BlockReason.LIMIT_EXPIRED,
                lastPackage = "com.app",
                activePackage = "com.launcher",
                dailyLimitExpired = false,
                personalLimitExpired = false
            )
        )
    }

    @Test
    fun `пакет разблокирован - плашку снимаем`() {
        assertTrue(
            hide(
                lastReason = BlockReason.BLOCKED_BY_PARENT,
                lastPackage = "com.blocked",
                blockedApps = emptySet()
            )
        )
    }

    @Test
    fun `пакет всё ещё запрещён, лаунчер - плашку оставляем`() {
        assertFalse(
            hide(
                lastReason = BlockReason.BLOCKED_BY_PARENT,
                lastPackage = "com.blocked",
                activePackage = "com.launcher",
                blockedApps = setOf("com.blocked")
            )
        )
    }

    @Test
    fun `неизвестный активный пакет при живой причине - не скрываем`() {
        assertFalse(hide(activePackage = null, studyTimeActive = true))
    }
}
