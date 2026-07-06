package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.LimitState

class BlockingDecisionTest {

    private val allowed = setOf("ru.homelab.kidguard", "com.android.launcher")

    @Test
    fun `лимит исчерпан и приложение не разрешено - блокируем`() {
        assertTrue(
            shouldBlock("com.game.app", LimitState.Expired, whitelist = emptySet(), alwaysAllowed = allowed)
        )
    }

    @Test
    fun `время ещё есть - не блокируем`() {
        assertFalse(
            shouldBlock("com.game.app", LimitState.Remaining(10), whitelist = emptySet(), alwaysAllowed = allowed)
        )
    }

    @Test
    fun `приложение в белом списке - не блокируем даже при Expired`() {
        assertFalse(
            shouldBlock("com.android.dialer", LimitState.Expired, whitelist = setOf("com.android.dialer"), alwaysAllowed = allowed)
        )
    }

    @Test
    fun `само KidGuard и лаунчер - не блокируем`() {
        assertFalse(shouldBlock("ru.homelab.kidguard", LimitState.Expired, emptySet(), allowed))
        assertFalse(shouldBlock("com.android.launcher", LimitState.Expired, emptySet(), allowed))
    }

    @Test
    fun `нет активного приложения - не блокируем`() {
        assertFalse(shouldBlock(null, LimitState.Expired, emptySet(), allowed))
    }
}
