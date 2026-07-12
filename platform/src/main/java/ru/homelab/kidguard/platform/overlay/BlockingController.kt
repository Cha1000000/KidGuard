package ru.homelab.kidguard.platform.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveAppLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.shouldBlock
import ru.homelab.kidguard.platform.accessibility.ForegroundAppMonitor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Связывает активное приложение, состояния лимитов (общего и личного пер-app) и белый список:
 * когда по матрице приоритетов приложение должно быть заблокировано — показывает оверлей и
 * уводит на домашний экран. Запускается foreground-сервисом.
 */
@Singleton
class BlockingController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val observeLimitStateUseCase: ObserveLimitStateUseCase,
    private val observeAppLimitStateUseCase: ObserveAppLimitStateUseCase,
    private val policyRepository: PolicyRepository,
    private val overlayManager: OverlayManager
) {

    // Всегда разрешены: само KidGuard и лаунчеры (домашний экран не блокируем).
    private val alwaysAllowed: Set<String> = buildSet {
        add(context.packageName)
        addAll(resolveLauncherPackages())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        Timber.tag(TAG).d("Контроллер блокировки запущен")
        // Личный лимит зависит от активного пакета, поэтому на каждую его смену пересобираем
        // подписку (flatMapLatest): наблюдаем usage+limit именно текущего приложения.
        foregroundAppMonitor.currentPackage.flatMapLatest { activePackage ->
            val appLimitStateFlow =
                if (activePackage != null) observeAppLimitStateUseCase(activePackage)
                else flowOf(LimitState.NoLimit)
            combine(
                observeLimitStateUseCase(),
                appLimitStateFlow,
                policyRepository.whitelist,
                policyRepository.blockedApps
            ) { limitState, appLimitState, whitelist, blockedApps ->
                val block = shouldBlock(activePackage, limitState, appLimitState, whitelist, alwaysAllowed, blockedApps)
                // Причина для оверлея: если пакет в blockedApps (и не alwaysAllowed), матрица
                // приоритетов гарантирует блокировку именно по запрету, независимо от лимитов —
                // отдельного дублирования логики shouldBlock не требуется.
                val reason = if (activePackage != null && activePackage !in alwaysAllowed && activePackage in blockedApps) {
                    BlockReason.BLOCKED_BY_PARENT
                } else {
                    BlockReason.LIMIT_EXPIRED
                }
                block to reason
            }
        }.distinctUntilChanged().collect { (block, reason) ->
            // Скрытие оверлея сюда намеренно не добавляем: он закрывается только свайпом
            // самого ребёнка (см. OverlayManager). Иначе уход на домашний экран ниже сразу же
            // «снял» бы блокировку — лаунчер всегда разрешён.
            if (block) {
                overlayManager.show(reason)
                sendHome()
                Timber.tag(TAG).d("Блокировка активна (причина=%s)", reason)
            }
        }
    }

    private fun sendHome() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun resolveLauncherPackages(): Set<String> {
        // Только текущий домашний лаунчер по умолчанию. queryIntentActivities(HOME) захватывает
        // и служебные HOME-активности (напр. Settings.FallbackHome), поэтому берём default.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val packageName = context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
        return setOfNotNull(packageName)
    }

    private companion object {
        const val TAG = "KidGuardBlocking"
    }
}
