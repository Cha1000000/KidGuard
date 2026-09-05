package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Логика watchdog (веха 6): когда родителю показывать «контроль сломан».
 * Ключевое требование — НЕ шуметь: ночной простой телефона не должен выглядеть поломкой.
 */
class ChildHealthTest {

    private val now: Instant = Instant.parse("2026-07-16T12:00:00Z")

    private val healthy = DeviceHealth(
        accessibility = true,
        overlay = true,
        deviceAdmin = true,
        vpn = true,
        batteryOptimization = true
    )

    private fun child(
        paired: Boolean = true,
        lastSeenAt: Instant? = now,
        health: DeviceHealth? = healthy
    ) = Child(id = 1, name = "Alina", avatar = 0, paired = paired, lastSeenAt = lastSeenAt, health = health)

    @Test
    fun `всё выдано и связь свежая — контроль в порядке`() {
        assertFalse(child().isControlBroken(now))
    }

    @Test
    fun `отвалившееся разрешение — поломка сразу, порог не нужен`() {
        // Реальный случай 16.07.2026: accessibility выключен, время не считается,
        // а детский экран показывает «2 ч осталось». Связь при этом свежая.
        val broken = child(health = healthy.copy(accessibility = false))
        assertTrue(broken.isControlBroken(now))
    }

    @Test
    fun `любое из пяти разрешений делает контроль сломанным`() {
        val variants = listOf(
            healthy.copy(accessibility = false),
            healthy.copy(overlay = false),
            healthy.copy(deviceAdmin = false),
            healthy.copy(vpn = false),
            healthy.copy(batteryOptimization = false)
        )
        variants.forEach { assertTrue(child(health = it).isControlBroken(now)) }
    }

    @Test
    fun `короткий разрыв связи не тревога`() {
        // Один пропущенный heartbeat — это метро, лифт или переключение сети, а не поломка.
        val blip = child(lastSeenAt = now.minus(Duration.ofMinutes(20)))
        assertFalse(blip.isControlBroken(now))
    }

    @Test
    fun `ночной простой ловится не порогом, а тишиной у родителя`() {
        // Порог снижен с 12 часов до 40 минут (инциденты 31.08 и 01.09: приложение останавливают
        // вручную, и ждать полсуток нельзя). Поэтому выключенный на ночь телефон формально
        // считается сломанным — от ночной лжи защищает isQuietHours на стороне родителя, который
        // в это время не показывает уведомлений и не трогает снимок состояния.
        val night = child(lastSeenAt = now.minus(Duration.ofHours(9)))
        assertTrue(night.isControlBroken(now))
    }

    @Test
    fun `молчание дольше порога — поломка, даже если последний отчёт был здоровым`() {
        // Так проявляется убитый сервис: доложить о себе он уже не может, поэтому
        // последний известный health остаётся «здоровым» — судим по молчанию.
        val silent = child(lastSeenAt = now.minus(Child.STALE_AFTER).minusSeconds(1))
        assertTrue(silent.isControlBroken(now))
    }

    @Test
    fun `ровно на пороге ещё не тревога`() {
        assertFalse(child(lastSeenAt = now.minus(Child.STALE_AFTER)).isControlBroken(now))
    }

    @Test
    fun `непривязанный ребёнок не считается сломанным`() {
        // Устройство ещё не привязано — heartbeat слать некому, это не поломка.
        assertFalse(child(paired = false, lastSeenAt = null, health = null).isControlBroken(now))
    }

    @Test
    fun `привязан но отчётов ещё не было — не тревожим`() {
        // Только что привязали: первый heartbeat ещё не дошёл. Тревожить рано.
        assertFalse(child(lastSeenAt = null, health = null).isControlBroken(now))
    }

    @Test
    fun `brokenPermissions — порядок как в мастере разрешений, чинить сверху вниз`() {
        val all = DeviceHealth(
            accessibility = false, overlay = false,
            deviceAdmin = false, vpn = false, batteryOptimization = false
        )
        assertEquals(
            listOf(
                DevicePermission.ACCESSIBILITY,
                DevicePermission.OVERLAY,
                DevicePermission.DEVICE_ADMIN,
                DevicePermission.BATTERY_OPTIMIZATION,
                DevicePermission.VPN
            ),
            all.brokenPermissions()
        )
    }

    @Test
    fun `brokenPermissions — здоровый даёт пустой список, уведомления не считаются поломкой`() {
        // NOTIFICATIONS в DeviceHealth нет вовсе: без уведомления контроль работает.
        assertTrue(healthy.brokenPermissions().isEmpty())
    }

    @Test
    fun `сломано несколько — перечисляем все, а не только первое`() {
        val two = healthy.copy(accessibility = false, vpn = false)
        assertEquals(
            listOf(DevicePermission.ACCESSIBILITY, DevicePermission.VPN),
            two.brokenPermissions()
        )
    }
}
