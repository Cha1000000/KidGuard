package ru.homelab.kidguard.platform.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.shouldBlock
import ru.homelab.kidguard.platform.accessibility.ForegroundAppMonitor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Связывает активное приложение, состояние лимита и белый список: когда время вышло и приложение
 * не разрешено — показывает блокирующий оверлей и уводит на домашний экран. Запускается
 * foreground-сервисом.
 */
@Singleton
class BlockingController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val observeLimitStateUseCase: ObserveLimitStateUseCase,
    private val policyRepository: PolicyRepository,
    private val overlayManager: OverlayManager
) {

    // Всегда разрешены: само KidGuard и лаунчеры (домашний экран не блокируем).
    private val alwaysAllowed: Set<String> = buildSet {
        add(context.packageName)
        addAll(resolveLauncherPackages())
    }

    suspend fun run() {
        Timber.tag(TAG).d("Контроллер блокировки запущен")
        combine(
            foregroundAppMonitor.currentPackage,
            observeLimitStateUseCase(),
            policyRepository.whitelist
        ) { activePackage, limitState, whitelist ->
            shouldBlock(activePackage, limitState, whitelist, alwaysAllowed)
        }.distinctUntilChanged().collect { block ->
            if (block) {
                overlayManager.show()
                sendHome()
                Timber.tag(TAG).d("Блокировка активна")
            } else {
                overlayManager.hide()
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
