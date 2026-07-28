package ru.homelab.kidguard.platform.vpn.dns

import android.os.ParcelFileDescriptor
import ru.homelab.kidguard.core.domain.model.SiteBlockRules
import ru.homelab.kidguard.core.net.DnsPacket
import ru.homelab.kidguard.core.net.IpUdpPacket
import ru.homelab.kidguard.platform.BuildConfig
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Читает DNS-режимный tun [tunFd] и фильтрует запросы по [rules]: заблокированные домены
 * получают локальный NXDOMAIN-ответ, остальные форвардятся на реальный upstream-DNS. Чтение
 * идёт в собственном потоке, форвардинг — асинхронно в пуле, чтобы round-trip до upstream не
 * тормозил чтение следующих пакетов из tun. [rules] можно поменять на лету (`@Volatile`) —
 * пересоздавать tun/loop для смены правил не нужно.
 */
class DnsProxyLoop(
    private val tunFd: ParcelFileDescriptor,
    @Volatile var rules: SiteBlockRules,
    private val protect: (DatagramSocket) -> Unit
) {

    private val running = AtomicBoolean(false)
    private val writeLock = Any()

    private var readerThread: Thread? = null
    private var inStream: FileInputStream? = null
    private var outStream: FileOutputStream? = null
    private var forwardExecutor: ExecutorService? = null

    /** Идемпотентен — повторный вызов на уже запущенном цикле ничего не делает. */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val fd = tunFd.fileDescriptor
        inStream = FileInputStream(fd)
        outStream = FileOutputStream(fd)
        forwardExecutor = Executors.newCachedThreadPool()
        readerThread = Thread(::readLoop, "KidGuardDnsProxy").apply { start() }
    }

    /** Идемпотентен — можно звать сколько угодно раз, в т.ч. если [start] не звался. */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        readerThread?.interrupt()
        runCatching { inStream?.close() }
        runCatching { outStream?.close() }
        forwardExecutor?.let { exec ->
            exec.shutdownNow()
            runCatching { exec.awaitTermination(1, TimeUnit.SECONDS) }
        }
        readerThread = null
        inStream = null
        outStream = null
        forwardExecutor = null
    }

    private fun readLoop() {
        val input = inStream ?: return
        val buffer = ByteArray(TUN_BUFFER_SIZE)
        while (running.get()) {
            val length = try {
                input.read(buffer)
            } catch (e: IOException) {
                if (running.get() && BuildConfig.DEBUG) {
                    Timber.tag(TAG).d(e, "Чтение из tun прервано")
                }
                break
            }
            if (length <= 0) continue
            handlePacket(buffer.copyOf(length))
        }
    }

    private fun handlePacket(packet: ByteArray) {
        val parsed = IpUdpPacket.parse(packet) ?: return
        if (parsed.dstPort != DNS_PORT) return // не-DNS в этот режим не маршрутизируется, но на всякий случай

        val host = DnsPacket.parseQName(parsed.udpPayload)
        if (host != null && rules.isBlocked(host)) {
            val response = DnsPacket.buildResponse(parsed.udpPayload, DNS_RCODE_NXDOMAIN) ?: return
            writeToTun(
                IpUdpPacket.buildIpv4Udp(
                    srcIp = parsed.dstIp,
                    srcPort = DNS_PORT,
                    dstIp = parsed.srcIp,
                    dstPort = parsed.srcPort,
                    payload = response
                )
            )
            if (BuildConfig.DEBUG) Timber.tag(TAG).d("Заблокирован host=%s", host)
        } else {
            forward(parsed)
        }
    }

    /** Round-trip до upstream-DNS — в пуле, чтобы не блокировать чтение следующих пакетов. */
    private fun forward(parsed: IpUdpPacket.Parsed) {
        val executor = forwardExecutor ?: return
        executor.execute {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = FORWARD_TIMEOUT_MS
                val upstream = InetAddress.getByName(UPSTREAM_DNS)
                socket.send(DatagramPacket(parsed.udpPayload, parsed.udpPayload.size, upstream, DNS_PORT))

                val answerBuffer = ByteArray(DNS_ANSWER_BUFFER_SIZE)
                val answerPacket = DatagramPacket(answerBuffer, answerBuffer.size)
                socket.receive(answerPacket)
                val answer = answerPacket.data.copyOf(answerPacket.length)

                writeToTun(
                    IpUdpPacket.buildIpv4Udp(
                        srcIp = parsed.dstIp,
                        srcPort = DNS_PORT,
                        dstIp = parsed.srcIp,
                        dstPort = parsed.srcPort,
                        payload = answer
                    )
                )
            } catch (e: IOException) {
                // Таймаут или сетевая ошибка апстрима — просто теряем ответ, клиент повторит запрос.
                if (BuildConfig.DEBUG) Timber.tag(TAG).d(e, "Форвардинг DNS не удался")
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    private fun writeToTun(data: ByteArray) {
        val output = outStream ?: return
        synchronized(writeLock) {
            try {
                output.write(data)
                output.flush()
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) Timber.tag(TAG).d(e, "Ошибка записи в tun")
            }
        }
    }

    companion object {
        private const val TAG = "KidGuardDnsProxy"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val DNS_PORT = 53
        private const val DNS_RCODE_NXDOMAIN = 3
        private const val TUN_BUFFER_SIZE = 32767
        private const val DNS_ANSWER_BUFFER_SIZE = 4096
        private const val FORWARD_TIMEOUT_MS = 5000
    }
}
