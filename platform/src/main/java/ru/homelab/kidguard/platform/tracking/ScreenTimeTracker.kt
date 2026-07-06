package ru.homelab.kidguard.platform.tracking

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.platform.accessibility.ForegroundAppMonitor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Движок учёта **реального экранного времени**. Раз в [TICK_SECONDS] секунд прибавляет интервал
 * к накопленному за сегодня — но только когда ребёнок реально пользуется телефоном:
 * экран включён И разблокирован И есть активное приложение. Фоновая активность (музыка при
 * погашенном экране) время НЕ расходует.
 */
@Singleton
class ScreenTimeTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val usageRepository: UsageRepository,
    private val currentDateProvider: CurrentDateProvider
) {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    /** Основной цикл учёта. Запускается foreground-сервисом и живёт, пока сервис активен. */
    suspend fun run() {
        Timber.tag(TAG).d("Движок учёта экранного времени запущен")
        while (currentCoroutineContext().isActive) {
            delay(TICK_SECONDS * 1000L)
            if (isUserActive()) {
                usageRepository.addScreenTime(currentDateProvider.today(), TICK_SECONDS)
                Timber.tag(TAG).d("Учтено +%d сек (реальное экранное время)", TICK_SECONDS)
            }
        }
    }

    /** Ребёнок реально пользуется телефоном: экран включён, разблокирован, есть активное приложение. */
    private fun isUserActive(): Boolean {
        val interactive = powerManager?.isInteractive == true
        val unlocked = keyguardManager?.isKeyguardLocked == false
        val hasForegroundApp = foregroundAppMonitor.currentPackage.value != null
        return interactive && unlocked && hasForegroundApp
    }

    private companion object {
        const val TAG = "KidGuardTracker"
        const val TICK_SECONDS = 15
    }
}
