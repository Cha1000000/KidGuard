package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Приложение из белого списка «всегда доступных» (доступно даже после истечения лимита). */
@Entity(tableName = "whitelisted_app")
data class WhitelistedAppEntity(
    @PrimaryKey val packageName: String
)
