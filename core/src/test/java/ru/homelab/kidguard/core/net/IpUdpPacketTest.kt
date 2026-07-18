package ru.homelab.kidguard.core.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Сборка/разбор IPv4+UDP пакетов для локального VPN-DNS-туннеля: мы сами собираем UDP-ответ
 * и заворачиваем его обратно в IP, поэтому обе стороны (parse и build) должны сходиться.
 */
class IpUdpPacketTest {

    private val srcIp = byteArrayOf(10, 0, 0, 2)
    private val dstIp = byteArrayOf(8, 8, 8, 8)
    private val payload = byteArrayOf(1, 2, 3, 4, 5)

    @Test
    fun `round-trip — build затем parse возвращают исходные ip, порты и payload`() {
        val packet = IpUdpPacket.buildIpv4Udp(srcIp, 5353, dstIp, 53, payload)
        val parsed = IpUdpPacket.parse(packet)
        requireNotNull(parsed)

        assertArrayEquals(srcIp, parsed.srcIp)
        assertArrayEquals(dstIp, parsed.dstIp)
        assertEquals(5353, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(payload, parsed.udpPayload)
    }

    @Test
    fun `IP-заголовок собран с корректной контрольной суммой`() {
        val packet = IpUdpPacket.buildIpv4Udp(srcIp, 5353, dstIp, 53, payload)
        // IHL = нижний ниббл первого байта * 4.
        val ihl = (packet[0].toInt() and 0x0F) * 4
        var sum = 0
        var i = 0
        while (i < ihl) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        // Пересуммировав весь заголовок (включая поле checksum), должны получить 0xFFFF.
        assertEquals(0xFFFF, sum)
    }

    @Test
    fun `parse на IPv6-пакете возвращает null`() {
        val ipv6 = ByteArray(40).also { it[0] = 0x60 }
        assertNull(IpUdpPacket.parse(ipv6))
    }

    @Test
    fun `parse на не-UDP протоколе возвращает null`() {
        val packet = IpUdpPacket.buildIpv4Udp(srcIp, 5353, dstIp, 53, payload)
        packet[9] = 6 // TCP вместо UDP (17)
        assertNull(IpUdpPacket.parse(packet))
    }

    @Test
    fun `parse на слишком коротком пакете возвращает null`() {
        assertNull(IpUdpPacket.parse(ByteArray(10)))
    }
}
