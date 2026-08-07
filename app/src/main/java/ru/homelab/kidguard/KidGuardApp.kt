package ru.homelab.kidguard

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Точка входа приложения. Инициализирует Hilt (граф зависимостей) и логирование Timber.
 *
 * Реализует [Configuration.Provider], чтобы WorkManager умел создавать воркеры с внедрёнными
 * зависимостями (`ChildHealthWorker` ходит в репозиторий детей).
 */
@HiltAndroidApp
class KidGuardApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
