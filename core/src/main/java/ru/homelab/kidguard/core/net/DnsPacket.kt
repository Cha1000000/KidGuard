package ru.homelab.kidguard.core.net

/**
 * Минимальный разбор/сборка DNS-сообщений без поддержки сжатия имён (0xC0-указателей) —
 * для наших целей (исходящие запросы от Android-клиентов) этого достаточно: клиент шлёт
 * QNAME полными метками, сжатие появляется только в ответах серверов.
 */
object DnsPacket {

    private const val HEADER_SIZE = 12
    private const val MAX_NAME_LENGTH = 255

    /** Разбирает QNAME из секции вопроса DNS-сообщения. null — если пакет некорректен. */
    fun parseQName(dns: ByteArray): String? {
        if (dns.size < HEADER_SIZE) return null
        val labels = StringBuilder()
        var offset = HEADER_SIZE
        var totalLength = 0
        while (true) {
            if (offset >= dns.size) return null
            val len = dns[offset].toInt() and 0xFF
            offset++
            if (len == 0) break
            // Сжатие (два старших бита указателя установлены) не поддерживаем.
            if (len and 0xC0 != 0) return null
            if (offset + len > dns.size) return null
            if (labels.isNotEmpty()) {
                labels.append('.')
                totalLength++
            }
            for (i in 0 until len) {
                labels.append((dns[offset + i].toInt() and 0xFF).toChar())
            }
            totalLength += len
            if (totalLength > MAX_NAME_LENGTH) return null
            offset += len
        }
        if (labels.isEmpty()) return null
        return labels.toString().lowercase()
    }

    /** Offset конца секции вопроса (после нулевого байта QNAME + QTYPE(2) + QCLASS(2)). */
    fun questionEndOffset(dns: ByteArray): Int? {
        if (dns.size < HEADER_SIZE) return null
        var offset = HEADER_SIZE
        while (true) {
            if (offset >= dns.size) return null
            val len = dns[offset].toInt() and 0xFF
            offset++
            if (len == 0) break
            if (len and 0xC0 != 0) return null
            if (offset + len > dns.size) return null
            offset += len
        }
        val end = offset + 4 // QTYPE + QCLASS
        if (end > dns.size) return null
        return end
    }

    /**
     * Ответ на запрос: копия query до конца секции вопроса, с выставленными флагами
     * QR=1/RA=1 и заданным RCODE; ANCOUNT/NSCOUNT/ARCOUNT обнулены, QDCOUNT сохранён.
     */
    fun buildResponse(query: ByteArray, rcode: Int): ByteArray? {
        val end = questionEndOffset(query) ?: return null
        val out = query.copyOfRange(0, end)
        out[2] = (query[2].toInt() or 0x80).toByte()               // QR=1, opcode/RD сохранены
        out[3] = (0x80 or (rcode and 0x0F)).toByte()                // RA=1 + RCODE
        // ANCOUNT/NSCOUNT/ARCOUNT = 0 (offsets 6,8,10 по 2 байта). QDCOUNT (offset 4) не трогаем.
        out[6] = 0; out[7] = 0
        out[8] = 0; out[9] = 0
        out[10] = 0; out[11] = 0
        return out
    }
}
