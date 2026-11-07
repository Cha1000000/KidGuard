package ru.homelab.kidguard.platform.vpn.dns

import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/**
 * Отправка задачи в пул форвардинга DNS с корректным завершением.
 *
 * При остановке прокси [DnsProxyLoop.stop] выключает пул (`shutdownNow`), но поток чтения может
 * успеть разобрать и отправить ещё один пакет. `ThreadPoolExecutor` в этом состоянии бросает
 * [RejectedExecutionException] прямо в потоке чтения, и процесс падает (`FATAL EXCEPTION:
 * KidGuardDnsProxy` при reboot/выключении VPN). Это штатное завершение, а не сбой: ответа на
 * пакет не будет, клиент повторит запрос.
 */
internal fun ExecutorService.executeOrIgnore(task: Runnable) {
    try {
        execute(task)
    } catch (e: RejectedExecutionException) {
        Timber.tag("DnsProxyLoop").d("DNS-пул уже остановлен — пакет пропущен")
    }
}
