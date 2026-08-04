package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Правило «что не блокируется дневным лимитом, то его и не тратит». Тесты держат его в паре с
 * [shouldBlock]: если однажды поменяется матрица приоритетов, расхождение должно всплыть здесь.
 */
class CountsTowardsDailyLimitTest {

    private val launcher = "com.android.launcher"
    private val ownApp = "ru.homelab.kidguard"
    private val alwaysAllowed = setOf(launcher, ownApp)

    private fun counts(pkg: String, whitelist: Set<String> = emptySet()) =
        countsTowardsDailyLimit(pkg, whitelist, alwaysAllowed)

    @Test
    fun `обычное приложение расходует лимит`() {
        assertTrue(counts("com.game.app"))
    }

    @Test
    fun `приложение из Всегда доступных лимит не расходует`() {
        // Тот самый случай: час разговора с бабушкой не должен съедать час игрового времени.
        assertFalse(counts("com.android.dialer", whitelist = setOf("com.android.dialer")))
    }

    @Test
    fun `домашний экран лимит не расходует`() {
        assertFalse(counts(launcher))
    }

    @Test
    fun `само KidGuard лимит не расходует`() {
        assertFalse(counts(ownApp))
    }

    @Test
    fun `запрещённое приложение расходует лимит`() {
        // Запрет — отдельное правило (приоритет 2 в shouldBlock), к общему лимиту отношения нет:
        // время в нём должно оставаться видимым и в расходе.
        assertTrue(counts("com.blocked.app"))
    }

    @Test
    fun `не блокируемое дневным лимитом не расходует его - согласовано с shouldBlock`() {
        val whitelist = setOf("com.android.dialer")
        val packages = listOf("com.game.app", "com.android.dialer", launcher, ownApp)
        packages.forEach { pkg ->
            val blockedByDailyLimit = shouldBlock(
                activePackage = pkg,
                limitState = LimitState.Expired,
                appLimitState = LimitState.NoLimit,
                whitelist = whitelist,
                alwaysAllowed = alwaysAllowed,
                blockedApps = emptySet()
            )
            assertEqualsForPackage(pkg, blockedByDailyLimit, counts(pkg, whitelist))
        }
    }

    private fun assertEqualsForPackage(pkg: String, blocked: Boolean, counts: Boolean) {
        if (blocked != counts) {
            throw AssertionError(
                "Пакет $pkg: блокируется дневным лимитом = $blocked, расходует лимит = $counts"
            )
        }
    }
}
