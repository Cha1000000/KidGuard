package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Кто на переднем плане, когда на экране одновременно приложение и оболочка.
 *
 * Тест держит именно тот случай, который год ломал учёт: поверх игры открыта шторка уведомлений,
 * и «активным приложением» становилась она.
 */
class ForegroundWindowTest {

    private fun window(
        id: Int,
        kind: WindowKind,
        layer: Int,
        packageName: String? = "pkg$id"
    ) = WindowSnapshot(id = id, kind = kind, layer = layer, packageName = packageName)

    @Test
    fun `шторка поверх игры не отбирает передний план`() {
        val windows = listOf(
            window(1, WindowKind.APPLICATION, layer = 10, packageName = "com.roblox.client"),
            window(2, WindowKind.SYSTEM, layer = 100, packageName = "com.android.systemui")
        )
        assertEquals("com.roblox.client", resolveForegroundPackage(windows))
    }

    @Test
    fun `клавиатура поверх приложения не отбирает передний план`() {
        val windows = listOf(
            window(1, WindowKind.APPLICATION, layer = 10, packageName = "com.transsion.smartmessage"),
            window(2, WindowKind.INPUT_METHOD, layer = 50, packageName = "ru.yandex.androidkeyboard")
        )
        assertEquals("com.transsion.smartmessage", resolveForegroundPackage(windows))
    }

    @Test
    fun `наш блокирующий оверлей не считается приложением`() {
        val windows = listOf(
            window(1, WindowKind.APPLICATION, layer = 10, packageName = "com.roblox.client"),
            window(2, WindowKind.OVERLAY, layer = 200, packageName = "ru.homelab.kidguard")
        )
        assertEquals("com.roblox.client", resolveForegroundPackage(windows))
    }

    @Test
    fun `из двух приложений берём верхнее по слою`() {
        val windows = listOf(
            window(1, WindowKind.APPLICATION, layer = 10, packageName = "com.transsion.hilauncher"),
            window(2, WindowKind.APPLICATION, layer = 20, packageName = "com.roblox.client")
        )
        assertEquals("com.roblox.client", resolveForegroundPackage(windows))
    }

    @Test
    fun `порядок в списке не влияет на выбор`() {
        val windows = listOf(
            window(2, WindowKind.APPLICATION, layer = 20, packageName = "com.roblox.client"),
            window(1, WindowKind.APPLICATION, layer = 10, packageName = "com.transsion.hilauncher")
        )
        assertEquals("com.roblox.client", resolveForegroundPackage(windows))
    }

    @Test
    fun `окно с ещё не доехавшим пакетом пропускаем в пользу нижнего приложения`() {
        val windows = listOf(
            window(1, WindowKind.APPLICATION, layer = 10, packageName = "com.roblox.client"),
            window(2, WindowKind.APPLICATION, layer = 30, packageName = null)
        )
        assertEquals("com.roblox.client", resolveForegroundPackage(windows))
    }

    @Test
    fun `пустое имя пакета считается неизвестным`() {
        val windows = listOf(
            window(1, WindowKind.APPLICATION, layer = 10, packageName = "com.roblox.client"),
            window(2, WindowKind.APPLICATION, layer = 30, packageName = "  ")
        )
        assertEquals("com.roblox.client", resolveForegroundPackage(windows))
    }

    @Test
    fun `на экране блокировки прикладных окон нет`() {
        val windows = listOf(
            window(1, WindowKind.SYSTEM, layer = 100, packageName = "com.android.systemui"),
            window(2, WindowKind.SYSTEM, layer = 110, packageName = "com.transsion.aod")
        )
        assertNull(resolveForegroundPackage(windows))
    }

    @Test
    fun `пустой список окон — переднего плана нет`() {
        assertNull(resolveForegroundPackage(emptyList()))
    }
}
