package ru.homelab.kidguard.core.domain.usecase

/** Нормализует пользовательский ввод (домен или URL) в чистый хост; null — если невалидно. */
object DomainNormalizer {
    fun normalize(input: String): String? {
        var s = input.trim().lowercase()
        if (s.isEmpty()) return null
        if ("://" in s) s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore(':')
        s = s.trim().trim('.')
        if (s.isEmpty() || '.' !in s) return null
        if (s.any { it != '.' && it != '-' && !it.isLetterOrDigit() }) return null
        return s
    }
}
