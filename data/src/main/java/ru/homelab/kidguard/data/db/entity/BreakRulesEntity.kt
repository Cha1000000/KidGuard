package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Настройки принудительных перерывов — single-row таблица (`id = 0`), по образцу [PolicyFlagsEntity]
 * и [PinEntity]. Отсутствие строки означает «родитель ничего не задал» (репозиторий отдаёт
 * `BreakRules.EMPTY`). Часы режима HOURS живут отдельной таблицей [BreakHourEntity] — их может
 * быть произвольное количество.
 */
@Entity(tableName = "break_rules")
data class BreakRulesEntity(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean,
    val mode: String,
    val intervalMinutes: Int,
    val durationMinutes: Int,
    val message: String
)
