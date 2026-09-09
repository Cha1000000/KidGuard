package ru.homelab.kidguard.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Проверка миграций Room на настоящем SQLite устройства.
 *
 * Зачем инструментальный тест, а не обычный юнит: миграция — это выполненный движком SQL, и
 * ошибиться в ней можно так, что заметит только сам SQLite (несовместимый `ALTER TABLE`,
 * забытый `NOT NULL` без `DEFAULT`, разъехавшаяся с сущностью схема). Room вдобавок сверяет
 * результат миграции с ожидаемой схемой из `schemas/` и падает при малейшем расхождении —
 * ровно то, чего не даёт ручная проверка на эмуляторе.
 *
 * Цена ошибки здесь выше обычной: миграция выполняется на телефоне ребёнка поверх накопленной
 * статистики, и упавшая (или потерявшая данные) миграция — это потерянный учёт времени.
 *
 * Схемы сохраняются с 11-й версии — `exportSchema` включили на ней, более ранних JSON нет,
 * поэтому цепочка начинается с 11.
 */
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KidGuardDatabase::class.java
    )

    @Test
    fun миграция11в12_добавляет_счётчики_перерасхода() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL("INSERT INTO screen_time(date, seconds) VALUES('2026-09-01', 600)")
            db.execSQL(
                "INSERT INTO app_screen_time(date, packageName, seconds) " +
                    "VALUES('2026-09-01', 'com.example.app', 300)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)

        // Накопленное время обязано пережить миграцию: это и есть учёт, ради которого всё работает.
        assertEquals(600, db.querySingleInt("SELECT seconds FROM screen_time WHERE date='2026-09-01'"))
        assertEquals(
            300,
            db.querySingleInt(
                "SELECT seconds FROM app_screen_time WHERE date='2026-09-01' AND packageName='com.example.app'"
            )
        )
        db.close()
    }

    @Test
    fun миграция12в13_заводит_таблицу_штрафов() {
        helper.createDatabase(TEST_DB, 12).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        db.execSQL(
            "INSERT INTO penalty_grants(date, packageName, minutes, comment) " +
                "VALUES('2026-09-01', '', 15, 'за уроки')"
        )
        assertEquals(15, db.querySingleInt("SELECT minutes FROM penalty_grants WHERE date='2026-09-01'"))
        db.close()
    }

    @Test
    fun миграция13в14_добавляет_расход_дополнительного_времени_не_теряя_учёт() {
        helper.createDatabase(TEST_DB, 13).use { db ->
            db.execSQL(
                "INSERT INTO app_screen_time(date, packageName, seconds, overrunSeconds) " +
                    "VALUES('2026-09-09', 'ru.homebudget.finkeeper', 900, 120)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Оба прежних счётчика на месте — колонка добавляется, строки не пересоздаются.
        assertEquals(
            900,
            db.querySingleInt("SELECT seconds FROM app_screen_time WHERE packageName='ru.homebudget.finkeeper'")
        )
        assertEquals(
            120,
            db.querySingleInt(
                "SELECT overrunSeconds FROM app_screen_time WHERE packageName='ru.homebudget.finkeeper'"
            )
        )
        // Новый счётчик появляется нулём: у старых записей выданного времени не было.
        assertEquals(
            0,
            db.querySingleInt(
                "SELECT bonusSpentSeconds FROM app_screen_time WHERE packageName='ru.homebudget.finkeeper'"
            )
        )
        db.close()
    }

    @Test
    fun новая_запись_после_миграции13в14_пишется_без_указания_колонки() {
        // Проверяем именно DEFAULT: DAO вставляет строки, не перечисляя bonusSpentSeconds,
        // и без значения по умолчанию такой INSERT упал бы на NOT NULL.
        helper.createDatabase(TEST_DB, 13).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        db.execSQL(
            "INSERT INTO app_screen_time(date, packageName, seconds) VALUES('2026-09-09', 'com.new.app', 60)"
        )
        assertEquals(
            0,
            db.querySingleInt("SELECT bonusSpentSeconds FROM app_screen_time WHERE packageName='com.new.app'")
        )
        db.close()
    }

    @Test
    fun вся_цепочка_11_в_14_проходит_подряд() {
        // По одной миграции проверяет каждый тест выше; здесь важно, что они совместимы между
        // собой — телефон, пропустивший несколько обновлений, идёт именно этим путём.
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL("INSERT INTO screen_time(date, seconds) VALUES('2026-08-01', 1200)")
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 14, true,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
        )

        assertEquals(1200, db.querySingleInt("SELECT seconds FROM screen_time WHERE date='2026-08-01'"))
        assertTrue(db.hasTable("penalty_grants"))
        assertTrue(db.hasColumn("app_screen_time", "bonusSpentSeconds"))
        db.close()
    }

    private fun SupportSQLiteDatabase.querySingleInt(sql: String): Int = query(sql).use { cursor ->
        assertTrue("Запрос не вернул ни одной строки: $sql", cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun SupportSQLiteDatabase.hasTable(name: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'").use { it.moveToFirst() }

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        query("SELECT COUNT(*) FROM pragma_table_info('$table') WHERE name='$column'").use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) > 0
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
