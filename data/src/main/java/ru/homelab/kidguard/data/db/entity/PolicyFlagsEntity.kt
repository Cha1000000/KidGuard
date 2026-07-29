package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Скалярные флаги политики, не привязанные к конкретному домену/приложению (веха 4.1.2) —
 * single-row таблица (всегда `id = 0`), по образцу [PinEntity]. Отсутствие строки означает
 * «всё выключено» (дефолты false).
 *
 * Тумблеры расписаний живут здесь, а не в `schedule_window`: они относятся к расписанию целиком,
 * а не к отдельному дню, и родитель должен уметь выключить расписание, не стирая заданные часы.
 */
@Entity(tableName = "policy_flags")
data class PolicyFlagsEntity(
    @PrimaryKey val id: Int = 0,
    val blockGoogleSearch: Boolean,
    val studyScheduleEnabled: Boolean = false,
    val sleepScheduleEnabled: Boolean = false,
    /** Дата маркера сброса дневного лимита («ГГГГ-ММ-ДД»); null — сброса нет. */
    val dailyUsageResetDate: String? = null,
    /** Метка времени (epoch-ms) нажатия кнопки сброса; null — сброса нет. */
    val dailyUsageResetAt: Long? = null,
    /** Дата маркера блокировки на сегодня («ГГГГ-ММ-ДД»); null — блокировки нет. */
    val dailyUsageBlockDate: String? = null,
    /** Метка времени (epoch-ms) нажатия кнопки блокировки; null — блокировки нет. */
    val dailyUsageBlockAt: Long? = null
)
