package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Приложение из списка запрещённых родителем (веха 4.1.2) — блокируется всегда, вне лимитов. */
@Entity(tableName = "blocked_app")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String
)
