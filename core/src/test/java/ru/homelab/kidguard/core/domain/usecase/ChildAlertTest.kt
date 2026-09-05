package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.domain.model.DeviceHealth
import java.time.Instant

/**
 * Когда родителя стоит потревожить уведомлением. Ошибка в обе стороны портит фичу целиком:
 * промолчим — родитель не узнает, что контроля нет; повторимся каждые 15 минут — уведомления
 * отключат, и он не узнает тем более.
 */
class ChildAlertTest {

    private val now: Instant = Instant.parse("2026-08-07T12:00:00Z")
    private val healthy = DeviceHealth(
        accessibility = true, overlay = true, deviceAdmin = true, vpn = true, batteryOptimization = true
    )

    private fun child(
        health: DeviceHealth? = healthy,
        lastSeenAt: Instant? = now.minusSeconds(60),
        paired: Boolean = true
    ) = Child(id = 1, name = "Олег", avatar = 0, paired = paired, lastSeenAt = lastSeenAt, health = health)

    @Test
    fun `всё в порядке - молчим`() {
        assertNull(childAlert(previous = child(), current = child(), now = now))
    }

    @Test
    fun `слетело accessibility - тревожим и говорим что именно`() {
        val broken = child(health = healthy.copy(accessibility = false))
        val alert = childAlert(previous = child(), current = broken, now = now)
        assertEquals(listOf(DevicePermission.ACCESSIBILITY), alert?.brokenPermissions)
        assertEquals("Олег", alert?.childName)
        assertEquals(false, alert?.silent)
    }

    @Test
    fun `сломалось другое разрешение - тоже тревожим`() {
        val broken = child(health = healthy.copy(vpn = false))
        assertEquals(
            listOf(DevicePermission.VPN),
            childAlert(previous = child(), current = broken, now = now)?.brokenPermissions
        )
    }

    @Test
    fun `та же поломка на следующей проверке - не повторяемся`() {
        val broken = child(health = healthy.copy(accessibility = false))
        assertNull(childAlert(previous = broken, current = broken, now = now))
    }

    @Test
    fun `сломалось ещё одно разрешение - тревожим снова`() {
        val first = child(health = healthy.copy(accessibility = false))
        val worse = child(health = healthy.copy(accessibility = false, overlay = false))
        val alert = childAlert(previous = first, current = worse, now = now)
        assertEquals(
            listOf(DevicePermission.ACCESSIBILITY, DevicePermission.OVERLAY),
            alert?.brokenPermissions
        )
    }

    @Test
    fun `устройство молчит дольше порога - тревожим отдельным поводом`() {
        // Именно этот случай ловит «приложение остановили или снесли»: флаги здоровья при этом
        // остаются последними известными, то есть выглядят здоровыми.
        val silent = child(lastSeenAt = now.minus(Child.STALE_AFTER).minusSeconds(60))
        val alert = childAlert(previous = child(), current = silent, now = now)
        assertTrue(alert?.silent == true)
        assertTrue(alert?.brokenPermissions.orEmpty().isEmpty())
    }

    @Test
    fun `короткий разрыв связи не повод для тревоги`() {
        // 20 минут — меньше порога в 40: один пропущенный heartbeat бывает от сети, а не от
        // остановленного приложения. Ночной случай сюда больше не относится: с порогом 40 минут
        // выключенный телефон тревогу даёт, и подавляет её isQuietHours у родителя.
        val quiet = child(lastSeenAt = now.minusSeconds(20 * 60))
        assertNull(childAlert(previous = child(), current = quiet, now = now))
    }

    @Test
    fun `первая проверка после входа - сообщаем о том что уже сломано`() {
        val broken = child(health = healthy.copy(accessibility = false))
        assertEquals(
            listOf(DevicePermission.ACCESSIBILITY),
            childAlert(previous = null, current = broken, now = now)?.brokenPermissions
        )
    }

    @Test
    fun `непривязанный ребёнок не повод тревожить`() {
        val notPaired = child(health = null, lastSeenAt = null, paired = false)
        assertNull(childAlert(previous = null, current = notPaired, now = now))
    }
}
