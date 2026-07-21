package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Контакт для экстренного звонка с ночного замка. Ключ — номер: один и тот же номер дважды в
 * списке смысла не имеет, а имя к нему родитель может переписать (upsert).
 */
@Entity(tableName = "emergency_contact")
data class EmergencyContactEntity(
    @PrimaryKey val phone: String,
    val name: String
)
