package ru.homelab.kidguard

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Точка входа приложения. Инициализирует Hilt (граф зависимостей) и логирование Timber.
 */
@HiltAndroidApp
class KidGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
