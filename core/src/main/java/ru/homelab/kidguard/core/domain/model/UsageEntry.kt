package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate

/**
 * Запись серверной статистики ребёнка (веха 4.4): накопленные секунды за день.
 * `packageName = ""` — суммарное экранное время за день (маркер-тотал).
 */
data class UsageEntry(
    val date: LocalDate,
    val packageName: String,
    val seconds: Int
) {
    val isTotal: Boolean get() = packageName.isEmpty()
}
