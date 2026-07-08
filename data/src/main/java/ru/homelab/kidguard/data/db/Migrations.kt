package ru.homelab.kidguard.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Явные миграции схемы Room. Destructive-миграции запрещены: данные ребёнка (накопленное
 * время, политика) при обновлении приложения теряться не должны.
 */

/** v1 → v2 (веха 3, шаг 3.1): таблица личных дневных лимитов приложений. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_limits` (" +
                "`packageName` TEXT NOT NULL, " +
                "`minutes` INTEGER NOT NULL, " +
                "PRIMARY KEY(`packageName`))"
        )
    }
}

/** v2 → v3 (веха 3, шаг 3.2): пер-app учёт экранного времени по дням. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_screen_time` (" +
                "`date` TEXT NOT NULL, " +
                "`packageName` TEXT NOT NULL, " +
                "`seconds` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`, `packageName`))"
        )
    }
}

/** v3 → v4 (веха 3Б, шаг 3Б.1): разовые бонусы (дополнительное время) на день. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bonus_grants` (" +
                "`date` TEXT NOT NULL, " +
                "`packageName` TEXT NOT NULL, " +
                "`minutes` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`, `packageName`))"
        )
    }
}
