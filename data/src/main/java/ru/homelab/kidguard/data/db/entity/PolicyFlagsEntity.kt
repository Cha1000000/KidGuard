package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Скалярные флаги политики, не привязанные к конкретному домену/приложению (веха 4.1.2) —
 * single-row таблица (всегда `id = 0`), по образцу [PinEntity]. Отсутствие строки означает
 * «блокировка google-поиска выключена» (дефолт false).
 */
@Entity(tableName = "policy_flags")
data class PolicyFlagsEntity(
    @PrimaryKey val id: Int = 0,
    val blockGoogleSearch: Boolean
)
