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
            // Пакет читаем один раз за тик: суммарное и пер-app время должны сойтись.
            val activePackage = foregroundAppMonitor.currentPackage.value
            if (isUserActive() && activePackage != null) {
                val today = currentDateProvider.today()
                usageRepository.addScreenTime(today, TICK_SECONDS)
                usageRepository.addAppScreenTime(today, activePackage, TICK_SECONDS)
                Timber.tag(TAG).d("Учтено +%d сек (всего и для %s)", TICK_SECONDS, activePackage)
            }
        }
    }

    /** Экраном реально пользуются: включён и разблокирован (активный пакет проверяется в цикле). */
    private fun isUserActive(): Boolean {
        val interactive = powerManager?.isInteractive == true
        val unlocked = keyguardManager?.isKeyguardLocked == false
        return interactive && unlocked
    }

    private companion object {
        const val TAG = "KidGuardTracker"
        const val TICK_SECONDS = 15
    }
}
