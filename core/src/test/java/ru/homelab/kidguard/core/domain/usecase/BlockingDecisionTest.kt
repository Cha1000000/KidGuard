package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Матрица приоритетов вехи 4.1.2: alwaysAllowed > запрет > пер-app лимит > белый список >
 * общий лимит.
 */
class BlockingDecisionTest {

    private val allowed = setOf("ru.homelab.kidguard", "com.android.launcher")

    private fun block(
        pkg: String? = "com.game.app",
        limit: LimitState = LimitState.NoLimit,
        appLimit: LimitState = LimitState.NoLimit,
        whitelist: Set<String> = emptySet(),
        blockedApps: Set<String> = emptySet()
    ) = shouldBlock(pkg, limit, appLimit, whitelist, allowed, blockedApps)

    // --- Приоритет 1: alwaysAllowed ---

    @Test
    fun `само KidGuard и лаунчер - не блокируем даже при исчерпанных лимитах`() {
        assertFalse(block(pkg = "ru.homelab.kidguard", limit = LimitState.Expired, appLimit = LimitState.Expired))
        assertFalse(block(pkg = "com.android.launcher", limit = LimitState.Expired, appLimit = LimitState.Expired))
    }

    @Test
    fun `alwaysAllowed сильнее запрета - не блокируем, даже если пакет в blockedApps`() {
        assertFalse(block(pkg = "ru.homelab.kidguard", blockedApps = setOf("ru.homelab.kidguard")))
    }

    // --- Приоритет 2: запрет (blockedApps) ---

    @Test
    fun `приложение запрещено - блокируем при любых лимитах и белом списке`() {
        assertTrue(
            block(
                blockedApps = setOf("com.game.app"),
                limit = LimitState.NoLimit,
                appLimit = LimitState.NoLimit,
                whitelist = setOf("com.game.app")
            )
        )
        assertTrue(
            block(
                blockedApps = setOf("com.game.app"),
                limit = LimitState.Remaining(30),
                appLimit = LimitState.Remaining(30)
            )
        )
    }

    @Test
    fun `запрет бьёт белый список - блокируем, даже если приложение тоже в whitelist`() {
        assertTrue(
            block(
                pkg = "com.android.dialer",
                blockedApps = setOf("com.android.dialer"),
                whitelist = setOf("com.android.dialer")
            )
        )
    }

    @Test
    fun `приложение не в blockedApps - запрет не влияет`() {
        assertFalse(block(blockedApps = setOf("com.other.app")))
    }

    // --- Приоритет 3: личный (пер-app) лимит ---

    @Test
    fun `личный лимит исчерпан - блокируем, даже если общий не исчерпан`() {
        assertTrue(block(appLimit = LimitState.Expired, limit = LimitState.Remaining(30)))
        assertTrue(block(appLimit = LimitState.Expired, limit = LimitState.NoLimit))
    }

    @Test
    fun `личный лимит исчерпан - блокируем даже приложение из белого списка`() {
        assertTrue(
            block(appLimit = LimitState.Expired, whitelist = setOf("com.game.app"))
        )
    }

    @Test
    fun `личный лимит ещё не исчерпан - не блокируем (общий в норме)`() {
        assertFalse(block(appLimit = LimitState.Remaining(10), limit = LimitState.Remaining(30)))
    }

    // --- Приоритет 4: белый список ---

    @Test
    fun `белый список - не блокируем даже при исчерпанном общем лимите`() {
        assertFalse(
            block(pkg = "com.android.dialer", limit = LimitState.Expired, whitelist = setOf("com.android.dialer"))
        )
    }

    // --- Приоритет 5: общий дневной лимит ---

    @Test
    fun `общий лимит исчерпан и приложение не разрешено - блокируем`() {
        assertTrue(block(limit = LimitState.Expired))
    }

    @Test
    fun `общий лимит исчерпан, но у приложения свой лимит ещё не исчерпан - всё равно блокируем`() {
        // Личный Remaining не даёт послабления от общего Expired: Remaining — не белый список.
        assertTrue(block(limit = LimitState.Expired, appLimit = LimitState.Remaining(15)))
    }

    // --- Приоритет 6: всё в норме ---

    @Test
    fun `время ещё есть - не блокируем`() {
        assertFalse(block(limit = LimitState.Remaining(10)))
    }

    @Test
    fun `лимиты не заданы - не блокируем`() {
        assertFalse(block())
    }

    @Test
    fun `нет активного приложения - не блокируем`() {
        assertFalse(block(pkg = null, limit = LimitState.Expired, appLimit = LimitState.Expired))
    }
}
