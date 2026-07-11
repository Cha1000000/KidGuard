package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate

/**
 * Выданный бонус «Дополнительное время» за день (веха 4.6, синхронизация).
 * `packageName = ""` — бонус на весь телефон (тот же маркер, что в Room-таблице `bonus_grants`).
 */
data class BonusGrant(
    val date: LocalDate,
    val packageName: String,
    val minutes: Int
)
