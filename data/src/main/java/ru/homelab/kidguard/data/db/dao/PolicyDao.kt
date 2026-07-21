package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.data.db.entity.AppLimitEntity
import ru.homelab.kidguard.data.db.entity.BlockedAppEntity
import ru.homelab.kidguard.data.db.entity.BlockedSiteEntity
import ru.homelab.kidguard.data.db.entity.BreakHourEntity
import ru.homelab.kidguard.data.db.entity.BreakRulesEntity
import ru.homelab.kidguard.data.db.entity.DayLimitEntity
import ru.homelab.kidguard.data.db.entity.EmergencyContactEntity
import ru.homelab.kidguard.data.db.entity.PinEntity
import ru.homelab.kidguard.data.db.entity.PolicyFlagsEntity
import ru.homelab.kidguard.data.db.entity.ScheduleWindowEntity
import ru.homelab.kidguard.data.db.entity.WhitelistedAppEntity

@Dao
interface PolicyDao {

    @Query("SELECT * FROM day_limit")
    fun dayLimits(): Flow<List<DayLimitEntity>>

    @Upsert
    suspend fun upsertDayLimit(entity: DayLimitEntity)

    @Query("DELETE FROM day_limit WHERE dayOfWeek = :dayOfWeek")
    suspend fun deleteDayLimit(dayOfWeek: Int)

    @Query("SELECT * FROM app_limits")
    fun appLimits(): Flow<List<AppLimitEntity>>

    @Upsert
    suspend fun upsertAppLimit(entity: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteAppLimit(packageName: String)

    @Query("SELECT * FROM whitelisted_app")
    fun whitelist(): Flow<List<WhitelistedAppEntity>>

    @Upsert
    suspend fun addToWhitelist(entity: WhitelistedAppEntity)

    @Query("DELETE FROM whitelisted_app WHERE packageName = :packageName")
    suspend fun removeFromWhitelist(packageName: String)

    @Query("SELECT * FROM blocked_app")
    fun blockedApps(): Flow<List<BlockedAppEntity>>

    @Upsert
    suspend fun addToBlocked(entity: BlockedAppEntity)

    @Query("DELETE FROM blocked_app WHERE packageName = :packageName")
    suspend fun removeFromBlocked(packageName: String)

    @Query("SELECT * FROM blocked_site")
    fun blockedSites(): Flow<List<BlockedSiteEntity>>

    @Upsert
    suspend fun upsertBlockedSite(entity: BlockedSiteEntity)

    @Query("DELETE FROM blocked_site WHERE domain = :domain")
    suspend fun removeBlockedSite(domain: String)

    @Query("UPDATE blocked_site SET enabled = :enabled WHERE domain = :domain")
    suspend fun setSiteEnabled(domain: String, enabled: Boolean)

    /** Скалярные флаги политики (веха 4.1.2) — single-row таблица, `id = 0`; отсутствие строки = дефолты. */
    @Query("SELECT * FROM policy_flags WHERE id = 0")
    fun policyFlags(): Flow<PolicyFlagsEntity?>

    @Upsert
    suspend fun upsertPolicyFlags(entity: PolicyFlagsEntity)

    /**
     * Флаги правятся точечными UPDATE, а не пересозданием строки: тумблеров здесь несколько
     * (google-поиск, два расписания), и upsert целой сущности затирал бы соседние значения.
     * [ensurePolicyFlagsRow] создаёт строку с дефолтами, если её ещё нет.
     */
    @Query(
        "INSERT OR IGNORE INTO policy_flags " +
            "(id, blockGoogleSearch, studyScheduleEnabled, sleepScheduleEnabled) VALUES (0, 0, 0, 0)"
    )
    suspend fun ensurePolicyFlagsRow()

    @Transaction
    suspend fun setBlockGoogleSearchFlag(enabled: Boolean) {
        ensurePolicyFlagsRow()
        updateBlockGoogleSearch(enabled)
    }

    @Transaction
    suspend fun setStudyScheduleEnabledFlag(enabled: Boolean) {
        ensurePolicyFlagsRow()
        updateStudyScheduleEnabled(enabled)
    }

    @Transaction
    suspend fun setSleepScheduleEnabledFlag(enabled: Boolean) {
        ensurePolicyFlagsRow()
        updateSleepScheduleEnabled(enabled)
    }

    @Query("UPDATE policy_flags SET blockGoogleSearch = :enabled WHERE id = 0")
    suspend fun updateBlockGoogleSearch(enabled: Boolean)

    @Query("UPDATE policy_flags SET studyScheduleEnabled = :enabled WHERE id = 0")
    suspend fun updateStudyScheduleEnabled(enabled: Boolean)

    @Query("UPDATE policy_flags SET sleepScheduleEnabled = :enabled WHERE id = 0")
    suspend fun updateSleepScheduleEnabled(enabled: Boolean)

    /** Окна расписаний обоих типов; фильтрацию по `kind` делает репозиторий. */
    @Query("SELECT * FROM schedule_window")
    fun scheduleWindows(): Flow<List<ScheduleWindowEntity>>

    @Upsert
    suspend fun upsertScheduleWindow(entity: ScheduleWindowEntity)

    @Query("DELETE FROM schedule_window WHERE kind = :kind AND dayOfWeek = :dayOfWeek")
    suspend fun deleteScheduleWindow(kind: String, dayOfWeek: Int)

    @Query("DELETE FROM schedule_window WHERE kind = :kind")
    suspend fun deleteScheduleWindows(kind: String)

    @Query("SELECT * FROM emergency_contact ORDER BY name")
    fun emergencyContacts(): Flow<List<EmergencyContactEntity>>

    @Upsert
    suspend fun upsertEmergencyContact(entity: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contact WHERE phone = :phone")
    suspend fun removeEmergencyContact(phone: String)

    /**
     * Правка контакта. Номер — первичный ключ, поэтому смена номера это не UPDATE, а удаление
     * старой строки плюс вставка новой; в транзакции, чтобы при сбое контакт не исчез совсем.
     * Смена только имени попадает под тот же путь: удалить и вставить строку с тем же ключом.
     */
    @Transaction
    suspend fun updateEmergencyContact(oldPhone: String, entity: EmergencyContactEntity) {
        removeEmergencyContact(oldPhone)
        upsertEmergencyContact(entity)
    }

    @Query("DELETE FROM day_limit")
    suspend fun deleteAllDayLimits()

    @Query("DELETE FROM app_limits")
    suspend fun deleteAllAppLimits()

    @Query("DELETE FROM whitelisted_app")
    suspend fun deleteAllWhitelist()

    @Query("DELETE FROM blocked_app")
    suspend fun deleteAllBlocked()

    @Query("DELETE FROM blocked_site")
    suspend fun deleteAllBlockedSites()

    @Query("DELETE FROM schedule_window")
    suspend fun deleteAllScheduleWindows()

    @Query("DELETE FROM emergency_contact")
    suspend fun deleteAllEmergencyContacts()

    /** Родительский PIN (веха 6.1) — single-row таблица, `id = 0`; null-строка означает «PIN не задан». */
    @Query("SELECT * FROM pin_protection WHERE id = 0")
    fun pin(): Flow<PinEntity?>

    @Upsert
    suspend fun upsertPin(entity: PinEntity)

    @Query("DELETE FROM pin_protection")
    suspend fun deletePin()

    /** Настройки перерывов (веха «принудительные перерывы») — single-row таблица, `id = 0`. */
    @Query("SELECT * FROM break_rules WHERE id = 0")
    fun breakRules(): Flow<BreakRulesEntity?>

    /** Часы перерыва режима HOURS, по возрастанию — так же, как читает `BreakRules.activeHoursWindow`. */
    @Query("SELECT minuteOfDay FROM break_hour ORDER BY minuteOfDay")
    fun breakHours(): Flow<List<Int>>

    /**
     * Настройки перерывов сохраняются целиком одним экраном (в отличие от `policy_flags`, где
     * несколько несвязанных тумблеров живут в одной строке) — здесь upsert не грабли, а корректный
     * способ: строка `break_rules` целиком принадлежит этой фиче.
     */
    @Upsert
    suspend fun upsertBreakRules(entity: BreakRulesEntity)

    @Query("DELETE FROM break_hour")
    suspend fun deleteAllBreakHours()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakHours(entities: List<BreakHourEntity>)

    /** Полная замена списка часов режима HOURS: удалить старые, вставить новые, одной транзакцией. */
    @Transaction
    suspend fun replaceBreakHours(minutes: Collection<Int>) {
        deleteAllBreakHours()
        insertBreakHours(minutes.map(::BreakHourEntity))
    }

    /**
     * Транзакционно заменяет ВСЮ политику разом (применение серверного документа — веха 4.3):
     * либо применяется целиком, либо не применяется вовсе — исполнители (блокировка/учёт)
     * не увидят промежуточного полупустого состояния.
     */
    @Transaction
    suspend fun replaceAllPolicy(entities: PolicyEntities) {
        deleteAllDayLimits()
        deleteAllAppLimits()
        deleteAllWhitelist()
        deleteAllBlocked()
        deleteAllBlockedSites()
        deleteAllScheduleWindows()
        deleteAllEmergencyContacts()
        deleteAllBreakHours()
        entities.dayLimits.forEach { upsertDayLimit(it) }
        entities.appLimits.forEach { upsertAppLimit(it) }
        entities.whitelist.forEach { addToWhitelist(it) }
        entities.blockedApps.forEach { addToBlocked(it) }
        entities.blockedSites.forEach { upsertBlockedSite(it) }
        entities.scheduleWindows.forEach { upsertScheduleWindow(it) }
        entities.emergencyContacts.forEach { upsertEmergencyContact(it) }
        insertBreakHours(entities.breakHours.map(::BreakHourEntity))
        upsertPolicyFlags(entities.flags)
        upsertBreakRules(entities.breakRules)
        entities.pin?.let { upsertPin(it) } ?: deletePin()
    }
}

/**
 * Полный набор строк политики для [PolicyDao.replaceAllPolicy]. Отдельный контейнер вместо
 * десятка параметров: список правил растёт с каждой фичей, а перепутанные местами аргументы
 * одинакового типа компилятор бы не поймал.
 */
data class PolicyEntities(
    val dayLimits: List<DayLimitEntity>,
    val appLimits: List<AppLimitEntity>,
    val whitelist: List<WhitelistedAppEntity>,
    val blockedApps: List<BlockedAppEntity>,
    val blockedSites: List<BlockedSiteEntity>,
    val scheduleWindows: List<ScheduleWindowEntity>,
    val emergencyContacts: List<EmergencyContactEntity>,
    val flags: PolicyFlagsEntity,
    val pin: PinEntity?,
    val breakRules: BreakRulesEntity,
    val breakHours: List<Int>
)
