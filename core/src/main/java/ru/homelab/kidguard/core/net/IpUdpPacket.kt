package ru.homelab.kidguard.core.net

/** Разбор и сборка IPv4+UDP пакетов для локального VPN-DNS-туннеля. */
object IpUdpPacket {

    private const val IP_HEADER_MIN_SIZE = 20
    private const val UDP_HEADER_SIZE = 8
    private const val PROTOCOL_UDP = 17

    data class Parsed(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val udpPayload: ByteArray
    )

    fun parse(packet: ByteArray): Parsed? {
        if (packet.size < IP_HEADER_MIN_SIZE) return null
        val version = (packet[0].toInt() and 0xFF) shr 4
        if (version != 4) return null // IPv6 и прочее пропускаем
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < IP_HEADER_MIN_SIZE || packet.size < ihl) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null
        if (packet.size < ihl + UDP_HEADER_SIZE) return null

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val udpOffset = ihl
        val srcPort = beUInt16(packet, udpOffset)
        val dstPort = beUInt16(packet, udpOffset + 2)
        val udpLength = beUInt16(packet, udpOffset + 4)
        if (udpLength < UDP_HEADER_SIZE) return null

        val payloadStart = udpOffset + UDP_HEADER_SIZE
        val payloadEnd = udpOffset + udpLength
        if (payloadEnd > packet.size || payloadStart > payloadEnd) return null

        return Parsed(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            udpPayload = packet.copyOfRange(payloadStart, payloadEnd)
        )
    }

    fun buildIpv4Udp(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = UDP_HEADER_SIZE + payload.size
        val totalLength = IP_HEADER_MIN_SIZE + udpLength
        val out = ByteArray(totalLength)

        // --- IPv4-заголовок ---
        out[0] = 0x45                                   // версия 4, IHL 5 (20 байт, без опций)
        out[1] = 0                                       // TOS
        writeBeUInt16(out, 2, totalLength)                // Total Length
        writeBeUInt16(out, 4, 0)                          // Identification
        writeBeUInt16(out, 6, 0)                          // Flags/Fragment offset
        out[8] = 64                                       // TTL
        out[9] = PROTOCOL_UDP.toByte()                    // Protocol = UDP
        writeBeUInt16(out, 10, 0)                         // Checksum — пока 0, посчитаем ниже
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        writeBeUInt16(out, 10, ipChecksum(out, 0, IP_HEADER_MIN_SIZE))

        // --- UDP-заголовок ---
        val udpOffset = IP_HEADER_MIN_SIZE
        writeBeUInt16(out, udpOffset, srcPort)
        writeBeUInt16(out, udpOffset + 2, dstPort)
        writeBeUInt16(out, udpOffset + 4, udpLength)
        writeBeUInt16(out, udpOffset + 6, 0)              // Checksum: 0 допустимо для IPv4

        System.arraycopy(payload, 0, out, udpOffset + UDP_HEADER_SIZE, payload.size)
        return out
    }

    private fun beUInt16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun writeBeUInt16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }

    /** Стандартная контрольная сумма IP-заголовка: сумма 16-битных слов, свёртка, инверсия. */
    private fun ipChecksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end) {
            sum += beUInt16(bytes, i)
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
