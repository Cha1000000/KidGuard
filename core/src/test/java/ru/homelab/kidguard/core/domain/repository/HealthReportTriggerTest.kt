package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthReportTriggerTest {

    @Test
    fun `requestNow доставляет сигнал активному подписчику`() = runTest {
        val trigger = HealthReportTrigger()
        var received = 0

        val job = launch { trigger.requests.collect { received++ } }
        runCurrent() // дать collect подписаться до эмита

        trigger.requestNow()
        runCurrent()

        assertEquals(1, received)
        job.cancel()
    }

    @Test
    fun `быстрые повторные вызовы схлопываются в подряд идущие эмиты`() = runTest {
        val trigger = HealthReportTrigger()
        var received = 0

        val job = launch { trigger.requests.collect { received++ } }
        runCurrent()

        // Реальный сценарий: onServiceConnected и следом refresh() в мастере разрешений почти
        // одновременно просят heartbeat — подписчик (childSyncLoop) должен получить хотя бы один
        // сигнал, а не упасть/потеряться.
        repeat(3) { trigger.requestNow() }
        runCurrent()

        assertEquals(true, received >= 1)
        job.cancel()
    }
}
