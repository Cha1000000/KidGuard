package ru.homelab.kidguard.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Накопленное за день время, расходующее дневной лимит. date — ISO-строка LocalDate (YYYY-MM-DD).
 *
 * Два счётчика вместо одного (Room v12): [seconds] растёт, пока дневной бюджет не исчерпан, и
 * потому никогда его не превышает; всё, что ребёнок накрутил после блокировки (оверлей мягкой
 * блокировки смахивается), уходит в [overrunSeconds]. Иначе выданный бонус сперва гасил бы этот
 * перерасход вместо того, чтобы дать время с момента выдачи.
 */
@Entity(tableName = "screen_time")
data class ScreenTimeEntity(
    @PrimaryKey val date: String,
    val seconds: Int,
    // defaultValue обязателен: без него Room создаёт колонку NOT NULL без DEFAULT, и
    // INSERT-ы, перечисляющие только date/seconds, падали бы на свежей установке.
    @ColumnInfo(defaultValue = "0") val overrunSeconds: Int = 0
)
