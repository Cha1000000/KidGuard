package ru.homelab.kidguard.platform.vpn

import ru.homelab.kidguard.core.domain.model.SiteBlockRules

/** Режим, в котором должен работать VpnService. */
sealed interface VpnMode {
    /**
     * Блокировка/pass-through набором disallowed-пакетов (tun-blackhole).
     *
     * [limitReached] различает два внешне похожих случая: интернет реально отрезан (дневной лимит
     * исчерпан, в [disallowed] только разрешённые) или туннель поднят «вхолостую» и пропускает всех.
     * По набору пакетов это не отличить, а уведомлению нужно знать — иначе оно сообщает ребёнку
     * «время вышло» тогда, когда время не вышло.
     */
    data class Blackhole(val disallowed: Set<String>, val limitReached: Boolean = false) : VpnMode
    /** DNS-фильтр по правилам запрета сайтов. */
    data class DnsFilter(val rules: SiteBlockRules) : VpnMode
}
