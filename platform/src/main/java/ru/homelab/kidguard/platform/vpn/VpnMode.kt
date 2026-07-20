package ru.homelab.kidguard.platform.vpn

import ru.homelab.kidguard.core.domain.model.SiteBlockRules

/** Режим, в котором должен работать VpnService. */
sealed interface VpnMode {
    /** Блокировка/pass-through набором disallowed-пакетов (tun-blackhole). */
    data class Blackhole(val disallowed: Set<String>) : VpnMode
    /** DNS-фильтр по правилам запрета сайтов. */
    data class DnsFilter(val rules: SiteBlockRules) : VpnMode
}
