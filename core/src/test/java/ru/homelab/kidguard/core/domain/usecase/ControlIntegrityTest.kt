package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Реакция на пропажу accessibility. Цена ошибки в обе стороны заметная: рано запрём — ребёнок
 * останется с закрытым телефоном из-за случайной остановки приложения; не запрём вовсе — выключение
 * одного тумблера снимает все лимиты.
 */
class ControlIntegrityTest {

    private val grace = 300L // 5 минут

    private fun action(
        enabled: Boolean = false,
        canSelfRestore: Boolean = false,
        secondsSinceLost: Long? = null
    ) = controlIntegrityAction(enabled, canSelfRestore, secondsSinceLost, grace)

    @Test
    fun `разрешение на месте - не вмешиваемся`() {
        assertEquals(ControlIntegrityAction.NOTHING, action(enabled = true))
        // Даже если пропадало раньше: вернули — значит инцидент исчерпан.
        assertEquals(
            ControlIntegrityAction.NOTHING,
            action(enabled = true, secondsSinceLost = grace * 10)
        )
    }

    @Test
    fun `можем починить сами - чиним, не трогая ребёнка`() {
        assertEquals(ControlIntegrityAction.RESTORE, action(canSelfRestore = true))
        // Самовосстановление важнее отсрочки: замок незачем показывать, если через секунду
        // разрешение вернётся само.
        assertEquals(
            ControlIntegrityAction.RESTORE,
            action(canSelfRestore = true, secondsSinceLost = grace * 2)
        )
    }

    @Test
    fun `только что заметили пропажу - предупреждаем`() {
        assertEquals(ControlIntegrityAction.WARN, action(secondsSinceLost = null))
        assertEquals(ControlIntegrityAction.WARN, action(secondsSinceLost = 0))
    }

    @Test
    fun `отсрочка ещё идёт - предупреждаем`() {
        assertEquals(ControlIntegrityAction.WARN, action(secondsSinceLost = grace - 1))
    }

    @Test
    fun `отсрочка вышла - замок`() {
        assertEquals(ControlIntegrityAction.LOCK, action(secondsSinceLost = grace))
        assertEquals(ControlIntegrityAction.LOCK, action(secondsSinceLost = grace * 3))
    }

    @Test
    fun `нулевая отсрочка запирает сразу`() {
        assertEquals(
            ControlIntegrityAction.LOCK,
            controlIntegrityAction(
                accessibilityEnabled = false,
                canSelfRestore = false,
                secondsSinceLost = 0,
                graceSeconds = 0
            )
        )
    }
}
