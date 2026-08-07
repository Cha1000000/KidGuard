package ru.homelab.kidguard.platform.tracking

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveAppLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.UsageBucket
import ru.homelab.kidguard.core.domain.usecase.countsTowardsDailyLimit
import ru.homelab.kidguard.core.domain.usecase.usageTickTargets
import ru.homelab.kidguard.platform.accessibility.BlockingUiState
import ru.homelab.kidguard.platform.accessibility.ForegroundAppMonitor
import ru.homelab.kidguard.platform.apps.AlwaysAllowedPackages
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Движок учёта **реального экранного времени**. Раз в [TICK_SECONDS] секунд прибавляет интервал
 * к накопленному за сегодня — но только когда ребёнок реально пользуется телефоном:
 * экран включён И разблокирован И есть активное приложение. Фоновая активность (музыка при
 * погашенном экране) время НЕ расходует.
 *
 * Ведёт два разных счётчика:
 * - **пер-app** ([UsageRepository.addAppScreenTime]) — по всем приложениям без исключений, это
 *   фактическое экранное время для статистики;
 * - **общий** ([UsageRepository.addScreenTime]) — только по тем приложениям, которые дневной лимит
 *   реально закрывает. Приложения из родительского списка «Всегда доступные», домашний лаунчер и
 *   само KidGuard лимит не расходуют: `shouldBlock` их не блокирует, и было бы нечестно, если бы
 *   час разговора с бабушкой съедал час игрового времени.
 *
 * Каждый из них расщеплён на бюджетную и перерасходную половины ([usageTickTargets]): после того
 * как лимит исчерпан, время уходит в перерасход и бюджет больше не расходует. Иначе выданный
 * бонус сперва гасил бы время, накрученное после блокировки, вместо того чтобы дать ребёнку
 * время с момента выдачи.
 */
@Singleton
class ScreenTimeTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val usageRepository: UsageRepository,
    private val policyRepository: PolicyRepository,
    private val alwaysAllowedPackages: AlwaysAllowedPackages,
    private val currentDateProvider: CurrentDateProvider,
    private val blockingUiState: BlockingUiState,
    // Те же use case'ы, что и у блокировки: «лимит исчерпан» в учёте и в enforcement обязаны
    // означать одно и то же — своя копия формулы здесь однажды уже разошлась бы с настоящей.
    private val observeLimitState: ObserveLimitStateUseCase,
    private val observeAppLimitState: ObserveAppLimitStateUseCase
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
                // Состояния лимитов читаем ДО записи: собственный тик сдвинул бы их, и время,
                // упирающееся в границу бюджета, ушло бы не в тот счётчик.
                val targets = usageTickTargets(
                    countsTowardsDailyLimit = countsTowardsLimit(activePackage),
                    dailyLimitState = observeLimitState().first(),
                    appLimitState = observeAppLimitState(activePackage).first()
                )
                when (targets.appBucket) {
                    UsageBucket.BUDGET -> usageRepository.addAppScreenTime(today, activePackage, TICK_SECONDS)
                    UsageBucket.OVERRUN -> usageRepository.addAppOverrunTime(today, activePackage, TICK_SECONDS)
                }
                when (targets.dailyBucket) {
                    UsageBucket.BUDGET -> usageRepository.addScreenTime(today, TICK_SECONDS)
                    UsageBucket.OVERRUN -> usageRepository.addOverrunTime(today, TICK_SECONDS)
                    null -> Unit // «Всегда доступные», лаунчер, само KidGuard — дневной лимит не трогают
                }
                Timber.tag(TAG).d(
                    "Учтено +%d сек: %s (дневной %s, личный %s)",
                    TICK_SECONDS, activePackage, targets.dailyBucket ?: "вне лимита", targets.appBucket
                )
            }
        }
    }

    /** Расходует ли активное приложение дневной лимит — правило живёт в :core рядом с shouldBlock. */
    private suspend fun countsTowardsLimit(packageName: String): Boolean =
        countsTowardsDailyLimit(
            packageName = packageName,
            whitelist = policyRepository.whitelist.first(),
            alwaysAllowed = alwaysAllowedPackages.packages
        )

    /**
     * Экраном реально пользуются: включён, разблокирован И не закрыт нашим блокирующим UI
     * (замок сна/перерыва, мягкий блок-оверлей, PIN-перехват настроек, предупреждение lockdown —
     * см. [BlockingUiState]). Активный пакет проверяется в цикле.
     */
    private fun isUserActive(): Boolean {
        val interactive = powerManager?.isInteractive == true
        val unlocked = keyguardManager?.isKeyguardLocked == false
        return interactive && unlocked && !blockingUiState.blockingVisible()
    }

    private companion object {
        const val TAG = "KidGuardTracker"
        const val TICK_SECONDS = 15
    }
}
