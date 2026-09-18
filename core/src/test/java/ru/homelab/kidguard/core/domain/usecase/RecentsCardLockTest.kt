package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsCardLockTest {

    @Test
    fun `id значка замка собираются с пакетом лаунчера`() {
        val ids = recentsLockIconIdsFor("com.transsion.hilauncher")
        assertTrue("com.transsion.hilauncher:id/taskLockIconTop" in ids)
    }

    @Test
    fun `список покрывает несколько вариантов написания`() {
        assertTrue(recentsLockIconIdsFor("x").size >= 3)
    }
}
