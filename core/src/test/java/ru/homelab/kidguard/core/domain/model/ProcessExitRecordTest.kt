package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessExitRecordTest {

    @Test
    fun `обновление приложения родителю не показываем`() {
        // После обновления APK контроль поднимается сам — уведомлять о каждой установке значит
        // приучить родителя не читать плашку здоровья.
        assertFalse(ProcessExitKind.PACKAGE_UPDATED.worthReporting)
    }

    @Test
    fun `неизвестную и прочую причину не показываем`() {
        assertFalse(ProcessExitKind.UNKNOWN.worthReporting)
        assertFalse(ProcessExitKind.OTHER.worthReporting)
    }

    @Test
    fun `все способы остановки пользователем показываем`() {
        // Ради этого различения и городился разбор subReason: три разных события лечатся
        // по-разному, а reason у них одинаковый.
        assertTrue(ProcessExitKind.FORCE_STOP.worthReporting)
        assertTrue(ProcessExitKind.TASK_MANAGER_STOP.worthReporting)
        assertTrue(ProcessExitKind.REMOVE_TASK.worthReporting)
    }

    @Test
    fun `системные и аварийные причины показываем`() {
        assertTrue(ProcessExitKind.CRASH.worthReporting)
        assertTrue(ProcessExitKind.ANR.worthReporting)
        assertTrue(ProcessExitKind.LOW_MEMORY.worthReporting)
        assertTrue(ProcessExitKind.FREEZER.worthReporting)
    }

    @Test
    fun `очистка в одно касание распознаётся по описанию прошивки`() {
        // Реальная строка с телефона ребёнка (TECNO KL6, 05.09.2026): тем же нажатием выгрузило
        // ещё два приложения. Это и есть основной способ, которым там умирал контроль.
        val kind = userRequestedExitKind("remove task: due to 2099, cleanType:oneKeyClean")
        assertEquals(ProcessExitKind.TASK_MANAGER_STOP, kind)
    }

    @Test
    fun `свайп одной карточки отличается от очистки всех`() {
        assertEquals(ProcessExitKind.REMOVE_TASK, userRequestedExitKind("remove task: due to 1002"))
    }

    @Test
    fun `незнакомую формулировку сводим к общей остановке`() {
        // subReason недоступен, поэтому на чужой прошивке разобрать точнее нечем — и врать о
        // способе остановки хуже, чем сказать общее «приложение остановили».
        assertEquals(
            ProcessExitKind.FORCE_STOP,
            userRequestedExitKind("fully stop ru.homelab.kidguard/0 by user request")
        )
    }
}
