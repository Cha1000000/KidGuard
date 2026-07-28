package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Сайт (домен) из списка запрещённых родителем (веха 4.1.2) — как и приложения, но со своим вкл/выкл. */
@Entity(tableName = "blocked_site")
data class BlockedSiteEntity(
    @PrimaryKey val domain: String,
    val enabled: Boolean
)
