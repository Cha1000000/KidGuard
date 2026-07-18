package ru.homelab.kidguard.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.homelab.kidguard.data.db.KidGuardDatabase
import ru.homelab.kidguard.data.db.MIGRATION_1_2
import ru.homelab.kidguard.data.db.MIGRATION_2_3
import ru.homelab.kidguard.data.db.MIGRATION_3_4
import ru.homelab.kidguard.data.db.MIGRATION_4_5
import ru.homelab.kidguard.data.db.MIGRATION_5_6
import ru.homelab.kidguard.data.db.MIGRATION_6_7
import ru.homelab.kidguard.data.db.dao.BonusDao
import ru.homelab.kidguard.data.db.dao.PolicyDao
import ru.homelab.kidguard.data.db.dao.UsageDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KidGuardDatabase =
        Room.databaseBuilder(context, KidGuardDatabase::class.java, "kidguard.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    fun providePolicyDao(database: KidGuardDatabase): PolicyDao = database.policyDao()

    @Provides
    fun provideUsageDao(database: KidGuardDatabase): UsageDao = database.usageDao()

    @Provides
    fun provideBonusDao(database: KidGuardDatabase): BonusDao = database.bonusDao()
}
