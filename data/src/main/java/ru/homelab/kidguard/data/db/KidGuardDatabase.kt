package ru.homelab.kidguard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.homelab.kidguard.data.db.dao.BonusDao
import ru.homelab.kidguard.data.db.dao.PenaltyDao
import ru.homelab.kidguard.data.db.dao.PolicyDao
import ru.homelab.kidguard.data.db.dao.UsageDao
import ru.homelab.kidguard.data.db.entity.AppLimitEntity
import ru.homelab.kidguard.data.db.entity.AppScreenTimeEntity
import ru.homelab.kidguard.data.db.entity.BlockedAppEntity
import ru.homelab.kidguard.data.db.entity.BlockedSiteEntity
import ru.homelab.kidguard.data.db.entity.BonusGrantEntity
import ru.homelab.kidguard.data.db.entity.BreakHourEntity
import ru.homelab.kidguard.data.db.entity.BreakRulesEntity
import ru.homelab.kidguard.data.db.entity.DayLimitEntity
import ru.homelab.kidguard.data.db.entity.EmergencyContactEntity
import ru.homelab.kidguard.data.db.entity.PenaltyGrantEntity
import ru.homelab.kidguard.data.db.entity.PinEntity
import ru.homelab.kidguard.data.db.entity.PolicyFlagsEntity
import ru.homelab.kidguard.data.db.entity.ScheduleWindowEntity
import ru.homelab.kidguard.data.db.entity.ScreenTimeEntity
import ru.homelab.kidguard.data.db.entity.WhitelistedAppEntity

@Database(
    entities = [
        DayLimitEntity::class,
        WhitelistedAppEntity::class,
        ScreenTimeEntity::class,
        AppLimitEntity::class,
        AppScreenTimeEntity::class,
        BonusGrantEntity::class,
        PenaltyGrantEntity::class,
        BlockedAppEntity::class,
        PinEntity::class,
        BlockedSiteEntity::class,
        PolicyFlagsEntity::class,
        ScheduleWindowEntity::class,
        EmergencyContactEntity::class,
        BreakRulesEntity::class,
        BreakHourEntity::class
    ],
    // Экспорт схемы включён: после релиза на устройствах будут реальные БД,
    // и без сохранённых JSON-схем миграции нельзя проверить через MigrationTestHelper.
    version = 13,
    exportSchema = true
)
abstract class KidGuardDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao
    abstract fun usageDao(): UsageDao
    abstract fun bonusDao(): BonusDao
    abstract fun penaltyDao(): PenaltyDao
}
