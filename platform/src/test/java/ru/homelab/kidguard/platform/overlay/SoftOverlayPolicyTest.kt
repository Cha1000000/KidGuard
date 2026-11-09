package ru.homelab.kidguard.platform.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.BlockCause

/**
 * Правило снятия мягкой плашки. Главная защита — «лаунчер + причина жива»: после `sendHome`
 * решение становится false, но плашку снимать нельзя, иначе ребёнок не прочитает сообщение.
 *
 * Причины сравниваются ПО ПРЕДМЕТАМ: личный лимит несёт имя пакета, поэтому лимит одного
 * приложения не может «погасить» причину по лимиту другого.
 */
class SoftOverlayPolicyTest {

    private val allowed = setOf("com.launcher", "ru.homelab.kidguard")

    private fun hide(
        shownCauses: Set<BlockCause> = setOf(BlockCause.StudyTime),
        currentShownCauses: Set<BlockCause> = setOf(BlockCause.StudyTime),
        activeCauses: Set<BlockCause> = emptySet(),
        activePackage: String? = "com.launcher",
        lastPackage: String? = "com.app"
    ) = shouldHideSoftOverlay(
        shownCauses = shownCauses,
        currentShownCauses = currentShownCauses,
        activeCauses = activeCauses,
        activePackage = activePackage,
        lastPackage = lastPackage,
        alwaysAllowed = allowed
    )

    @Test
    fun `причина исчезла - плашку снимаем`() {
        assertTrue(hide(shownCauses = setOf(BlockCause.StudyTime), currentShownCauses = emptySet()))
    }

    @Test
    fun `причина жива, ребёнок на лаунчере - плашку оставляем`() {
        assertFalse(
            hide(
                shownCauses = setOf(BlockCause.StudyTime),
                currentShownCauses = setOf(BlockCause.StudyTime),
                activePackage = "com.launcher"
            )
        )
    }

    @Test
    fun `причина жива, ребёнок в другом разрешённом приложении - плашку снимаем`() {
        // Лаунчер и сам KidGuard — исключение: туда мы уводим ребёнка САМИ.
        assertTrue(
            hide(
                shownCauses = setOf(BlockCause.StudyTime),
                currentShownCauses = setOf(BlockCause.StudyTime),
                activeCauses = emptySet(),
                activePackage = "com.whitelisted"
            )
        )
    }

    @Test
    fun `причина жива, ребёнок в самом KidGuard - плашку оставляем`() {
        assertFalse(
            hide(
                shownCauses = setOf(BlockCause.StudyTime),
                currentShownCauses = setOf(BlockCause.StudyTime),
                activeCauses = emptySet(),
                activePackage = "ru.homelab.kidguard"
            )
        )
    }

    @Test
    fun `причина жива, но ребёнок в другом приложении с блокировкой - плашку оставляем`() {
        // Активный пакет сам заблокирован: плашка всё ещё нужна.
        assertFalse(
            hide(
                shownCauses = setOf(BlockCause.StudyTime),
                currentShownCauses = setOf(BlockCause.StudyTime),
                activeCauses = setOf(BlockCause.DailyLimitExpired),
                activePackage = "com.other"
            )
        )
    }

    @Test
    fun `личный лимит другого пакета не гасит личный лимит показанного`() {
        // Регресс 09.11.2026: лимиты текущего пакета (лаунчер без лимитов) не должны были
        // снимать плашку, показанную по лимиту конкретного приложения. Поэтому личный лимит
        // показанного пакета сверяется с лимитом ЭТОГО пакета, а не текущего.
        assertFalse(
            hide(
                shownCauses = setOf(BlockCause.PersonalLimitExpired("com.app")),
                currentShownCauses = setOf(BlockCause.PersonalLimitExpired("com.app"))
            )
        )
    }

    @Test
    fun `причина показа жива, но активный пакет заблокирован иначе - плашку оставляем`() {
        // Активный пакет закрыт по другой причине: блокировка реальна, плашка нужна.
        assertFalse(
            hide(
                shownCauses = setOf(BlockCause.StudyTime),
                currentShownCauses = setOf(BlockCause.StudyTime),
                activeCauses = setOf(BlockCause.DailyLimitExpired),
                activePackage = "com.other"
            )
        )
    }

    @Test
    fun `личный лимит активного пакета не гасит личный лимит показанного`() {
        // Показанная плашка — по лимиту com.app; активный пакет com.other закрыт по своему личту.
        // Блокировка активного пакета реальна, значит плашка остаётся.
        assertFalse(
            hide(
                shownCauses = setOf(BlockCause.PersonalLimitExpired("com.app")),
                currentShownCauses = setOf(BlockCause.PersonalLimitExpired("com.app")),
                activeCauses = setOf(BlockCause.PersonalLimitExpired("com.other")),
                activePackage = "com.other"
            )
        )
    }

    @Test
    fun `личный лимит показанного пакета восстановлен - плашку снимаем`() {
        assertTrue(
            hide(
                shownCauses = setOf(BlockCause.PersonalLimitExpired("com.app")),
                currentShownCauses = emptySet()
            )
        )
    }

    @Test
    fun `личный лимит показанного пакета и дневной живы - плашку оставляем`() {
        assertFalse(
            hide(
                shownCauses = setOf(
                    BlockCause.PersonalLimitExpired("com.app"),
                    BlockCause.DailyLimitExpired
                ),
                currentShownCauses = setOf(BlockCause.DailyLimitExpired)
            )
        )
    }

    @Test
    fun `запрет родителя снят - плашку снимаем`() {
        assertTrue(
            hide(
                shownCauses = setOf(BlockCause.BlockedByParent),
                currentShownCauses = emptySet()
            )
        )
    }

    @Test
    fun `запрет родителя ещё в силе - плашку оставляем`() {
        assertFalse(
            hide(
                shownCauses = setOf(BlockCause.BlockedByParent),
                currentShownCauses = setOf(BlockCause.BlockedByParent)
            )
        )
    }

    @Test
    fun `неизвестный активный пакет при живой причине - не скрываем`() {
        assertFalse(
            hide(
                shownCauses = setOf(BlockCause.StudyTime),
                currentShownCauses = setOf(BlockCause.StudyTime),
                activePackage = null
            )
        )
    }

    @Test
    fun `показана пустая причина - нечего скрывать, но и нечего показывать`() {
        assertFalse(hide(shownCauses = emptySet(), currentShownCauses = emptySet()))
    }
}
