package ru.homelab.kidguard.core.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор и сборка сырых DNS-сообщений (UDP payload), без сжатия имён — так устроены обычные
 * запросы от Android-клиента, которые видит наш локальный VPN-DNS-фильтр.
 */
class DnsPacketTest {

    /** Запрос "vk.com", QDCOUNT=1, ID=0x1234, стандартные флаги RD=1. */
    private fun vkComQuery(): ByteArray {
        val header = byteArrayOf(
            0x12, 0x34,             // ID
            0x01, 0x00,             // flags: RD=1
            0x00, 0x01,             // QDCOUNT=1
            0x00, 0x00,             // ANCOUNT=0
            0x00, 0x00,             // NSCOUNT=0
            0x00, 0x00              // ARCOUNT=0
        )
        val qname = byteArrayOf(
            0x02, 'v'.code.toByte(), 'k'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00
        )
        val qtypeQclass = byteArrayOf(0x00, 0x01, 0x00, 0x01)
        return header + qname + qtypeQclass
    }

    @Test
    fun `parseQName достаёт vk-com из запроса`() {
        assertEquals("vk.com", DnsPacket.parseQName(vkComQuery()))
    }

    @Test
    fun `questionEndOffset указывает сразу за QTYPE-QCLASS`() {
        val query = vkComQuery()
        // 12 (header) + 8 (qname: 1+2+1+3+1) + 4 (qtype+qclass) = 24
        assertEquals(24, DnsPacket.questionEndOffset(query))
        assertEquals(query.size, DnsPacket.questionEndOffset(query))
    }

    @Test
    fun `parseQName на слишком короткой строке — null`() {
        assertNull(DnsPacket.parseQName(ByteArray(5)))
    }

    @Test
    fun `parseQName при выходе метки за границы массива — null`() {
        val header = ByteArray(12)
        val broken = header + byteArrayOf(0x05, 'a'.code.toByte()) // длина метки 5, а байт только 1
        assertNull(DnsPacket.parseQName(broken))
    }

    @Test
    fun `buildResponse для NXDOMAIN — QR и RCODE выставлены, счётчики ответа нулевые`() {
        val query = vkComQuery()
        val response = DnsPacket.buildResponse(query, rcode = 3)
        requireNotNull(response)

        // QR=1: старший бит третьего байта.
        assertTrue((response[2].toInt() and 0x80) != 0)
        // RCODE в младших 4 битах четвёртого байта.
        assertEquals(3, response[3].toInt() and 0x0F)
        // RA=1 выставлен.
        assertTrue((response[3].toInt() and 0x80) != 0)

        // ANCOUNT/NSCOUNT/ARCOUNT == 0.
        assertEquals(0, ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF))
        assertEquals(0, ((response[8].toInt() and 0xFF) shl 8) or (response[9].toInt() and 0xFF))
        assertEquals(0, ((response[10].toInt() and 0xFF) shl 8) or (response[11].toInt() and 0xFF))

        // Секция вопроса совпадает с запросом.
        val questionEnd = DnsPacket.questionEndOffset(query)!!
        assertArrayEquals(query.copyOfRange(12, questionEnd), response.copyOfRange(12, questionEnd))
        assertEquals(questionEnd, response.size)
    }

    @Test
    fun `buildResponse на некорректном запросе — null`() {
        assertNull(DnsPacket.buildResponse(ByteArray(5), rcode = 3))
    }
}
