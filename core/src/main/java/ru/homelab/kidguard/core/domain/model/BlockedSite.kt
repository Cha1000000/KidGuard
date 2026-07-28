package ru.homelab.kidguard.core.domain.model

/** Сайт из списка запрещённых родителем (веха 4.1.2, по образцу приложений) — домен + вкл/выкл. */
data class BlockedSite(val domain: String, val enabled: Boolean)
