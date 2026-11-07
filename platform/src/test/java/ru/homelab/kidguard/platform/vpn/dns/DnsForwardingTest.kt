package ru.homelab.kidguard.platform.vpn.dns

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Контракт [executeOrIgnore]: отправка в остановленный пул не бросает исключение (регресс
 * `FATAL EXCEPTION: KidGuardDnsProxy` при reboot/выключении VPN), а в живой — выполняет задачу.
 */
class DnsForwardingTest {

    @Test
    fun `отправка в остановленный пул не бросает исключение`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.shutdownNow()

        // Раньше здесь летел RejectedExecutionException и валил поток чтения DNS-прокси.
        executor.executeOrIgnore { /* пакет теряется — клиент повторит запрос */ }
    }

    @Test
    fun `отправка в живой пул выполняет задачу`() {
        val executor = Executors.newSingleThreadExecutor()
        val done = CountDownLatch(1)
        try {
            executor.executeOrIgnore { done.countDown() }
            assertTrue(done.await(2, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }
}
