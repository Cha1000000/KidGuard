package ru.homelab.kidguard.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Накопленное реальное экранное время приложения за день (веха 3, Room v3).
 * date — ISO-строка LocalDate (YYYY-MM-DD), ключ составной: день + пакет.
 *
 * Разделено на два счётчика (Room v12) по той же причине, что и в `screen_time`: [seconds] не
 * превышает личный лимит приложения, время сверх него копится в [overrunSeconds]. Фактическое
 * время в приложении — их сумма.
 */
@Entity(tableName = "app_screen_time", primaryKeys = ["date", "packageName"])
data class AppScreenTimeEntity(
    val date: String,
    val packageName: String,
    val seconds: Int,
    /** См. комментарий в `ScreenTimeEntity`: DEFAULT нужен для INSERT-ов без этой колонки. */
    @ColumnInfo(defaultValue = "0") val overrunSeconds: Int = 0
)
