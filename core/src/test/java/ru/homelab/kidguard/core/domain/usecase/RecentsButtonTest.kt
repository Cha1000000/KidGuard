package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsButtonTest {

    private val shell = setOf("com.android.systemui")

    @Test
    fun `нажатие кнопки обзора на HiOS распознаётся`() =
        assertTrue(isRecentsButtonClick("com.android.systemui", shell, "Просмотр"))

    @Test
    fun `английская подпись распознаётся`() =
        assertTrue(isRecentsButtonClick("com.android.systemui", shell, "Recent apps"))

    @Test
    fun `кнопка с тем же названием в приложении игнорируется`() =
        assertFalse(isRecentsButtonClick("ru.vk.store", shell, "Обзор"))

    @Test
    fun `другая кнопка оболочки игнорируется`() =
        assertFalse(isRecentsButtonClick("com.android.systemui", shell, "Домой"))

    @Test
    fun `пустая подпись игнорируется`() {
        assertFalse(isRecentsButtonClick("com.android.systemui", shell, null))
        assertFalse(isRecentsButtonClick("com.android.systemui", shell, "   "))
    }
}
