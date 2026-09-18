package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Служебные окна запрещённых пакетов — игровая панель HiOS и подобные. */
class ServiceWindowBlockingTest {

    private val panel = "com.transsion.smartpanel"
    private val blocked = setOf(panel)
    private val protected = setOf("ru.homelab.kidguard", "com.transsion.hilauncher", "com.android.systemui")

    private fun close(
        pkg: String = panel,
        kind: WindowKind? = WindowKind.SYSTEM,
        area: Float? = 0.4f,
        blockedApps: Set<String> = blocked,
        sinceLast: Long = 10_000L
    ) = shouldCloseBlockedServiceWindow(pkg, kind, area, blockedApps, protected, sinceLast)

    @Test
    fun `игровая панель запрещённого пакета закрывается`() = assertTrue(close())

    @Test
    fun `окно неизвестного рода, но крупное, тоже закрывается`() = assertTrue(close(kind = null))

    @Test
    fun `пакет не запрещён — не трогаем`() = assertFalse(close(blockedApps = emptySet()))

    @Test
    fun `ручка у края экрана не закрывается`() = assertFalse(close(area = 0.004f))

    @Test
    fun `размер неизвестен — не трогаем`() = assertFalse(close(area = null))

    @Test
    fun `прикладное окно — дело обычной блокировки`() = assertFalse(close(kind = WindowKind.APPLICATION))

    @Test
    fun `клавиатуру запрещённого пакета не закрываем`() = assertFalse(close(kind = WindowKind.INPUT_METHOD))

    @Test
    fun `защищённый пакет не закрываем даже при запрете`() =
        assertFalse(close(pkg = "com.android.systemui", blockedApps = setOf("com.android.systemui")))

    @Test
    fun `повтор раньше паузы не закрывает`() = assertFalse(close(sinceLast = 300L))
}
