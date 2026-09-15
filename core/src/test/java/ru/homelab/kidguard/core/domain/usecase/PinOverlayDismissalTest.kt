package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Когда PIN-оверлей уходит сам. Для списка последних цена ошибки — остановленный контроль: так
 * 15.09.2026 на телефоне ребёнка уходило по нескольку часов в день.
 */
class PinOverlayDismissalTest {

    private val own = "ru.homelab.kidguard"
    private val launcher = setOf("com.transsion.hilauncher")
    private val settings = setOf("com.android.settings")

    private fun onEvent(recents: Boolean, pkg: String, hosts: Set<String>) =
        shouldDismissPinOverlayOnWindowEvent(recents, pkg, own, hosts)

    @Test
    fun `поворот из горизонтальной игры не снимает PIN обзора`() {
        assertFalse(onEvent(recents = true, pkg = "com.android.systemui", hosts = launcher))
    }

    @Test
    fun `событие самой игры не снимает PIN обзора`() {
        assertFalse(onEvent(recents = true, pkg = "com.supercell.brawlstars", hosts = launcher))
    }

    @Test
    fun `панель меню спец возможностей не снимает PIN обзора`() {
        assertFalse(onEvent(recents = true, pkg = "com.android.systemui", hosts = launcher))
    }

    @Test
    fun `события лаунчера и собственного оверлея PIN обзора не снимают`() {
        assertFalse(onEvent(recents = true, pkg = "com.transsion.hilauncher", hosts = launcher))
        assertFalse(onEvent(recents = true, pkg = own, hosts = launcher))
    }

    @Test
    fun `звонок снимает PIN обзора`() {
        assertTrue(shouldDismissRecentsPinOnCall(overlayForRecents = true, callActive = true))
    }

    @Test
    fun `без звонка PIN обзора остаётся`() {
        assertFalse(shouldDismissRecentsPinOnCall(overlayForRecents = true, callActive = false))
    }

    @Test
    fun `звонок не трогает PIN других экранов - у них своё правило`() {
        assertFalse(shouldDismissRecentsPinOnCall(overlayForRecents = false, callActive = true))
    }

    @Test
    fun `PIN настроек по-прежнему уходит при уходе с настроек`() {
        assertTrue(onEvent(recents = false, pkg = "com.transsion.hilauncher", hosts = settings))
    }

    @Test
    fun `PIN настроек остаётся на под-окнах самих настроек и на своём оверлее`() {
        assertFalse(onEvent(recents = false, pkg = "com.android.settings", hosts = settings))
        assertFalse(onEvent(recents = false, pkg = own, hosts = settings))
    }
}
