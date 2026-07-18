package ru.homelab.kidguard.core.domain.model

/**
 * Правила блокировки сайтов, исполняемые DNS-фильтром. blockedDomains — уже нормализованные
 * и ТОЛЬКО включённые (enabled) домены. Матч списка — по суффиксу; google-тумблер — точный.
 */
data class SiteBlockRules(
    val blockedDomains: Set<String>,
    val blockGoogleSearch: Boolean
) {
    fun isBlocked(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (blockGoogleSearch && (h == "google.com" || h == "www.google.com")) return true
        return blockedDomains.any { e -> h == e || h.endsWith(".$e") }
    }

    val isActive: Boolean get() = blockGoogleSearch || blockedDomains.isNotEmpty()

    companion object {
        val NONE = SiteBlockRules(emptySet(), false)
    }
}
