package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsContentTest {

    @Test
    fun `id элементов обзора HiOS собираются с пакетом лаунчера`() {
        val ids = recentsViewIdsFor("com.transsion.hilauncher")
        assertTrue("com.transsion.hilauncher:id/recent_overview_panel" in ids)
        assertTrue("com.transsion.hilauncher:id/ts_btn_recents_clear" in ids)
    }

    @Test
    fun `id элементов обзора Quickstep собираются с пакетом лаунчера`() {
        val ids = recentsViewIdsFor("com.google.android.apps.nexuslauncher")
        assertEquals(
            listOf("overview_panel", "clear_all").map { "com.google.android.apps.nexuslauncher:id/$it" },
            ids.filter { it.endsWith("/overview_panel") || it.endsWith("/clear_all") }
        )
    }

    @Test
    fun `обзор признаётся только по двум проверкам подряд`() {
        assertTrue(recentsConfirmedByContent(previousCheckSawRecents = true, currentCheckSawRecents = true))
        assertFalse(recentsConfirmedByContent(previousCheckSawRecents = false, currentCheckSawRecents = true))
        assertFalse(recentsConfirmedByContent(previousCheckSawRecents = true, currentCheckSawRecents = false))
    }
}
