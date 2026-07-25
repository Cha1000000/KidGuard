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
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveAppLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveScheduleStateUseCase
import ru.homelab.kidguard.core.domain.usecase.shouldBlock
import ru.homelab.kidguard.platform.accessibility.ForegroundAppMonitor
import timber.log.Timber
import java.time.format.DateTimeFormatter
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
    private val observeScheduleStateUseCase: ObserveScheduleStateUseCase,
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
                policyRepository.blockedApps,
                observeScheduleStateUseCase()
            ) { limitState, appLimitState, whitelist, blockedApps, scheduleState ->
                // «Время учёбы» по смыслу равно исчерпанному дневному лимиту (см. shouldBlock) —
                // просто передаём признак дальше в чистую функцию, вся матрица приоритетов там.
                val studyTimeActive = scheduleState is ScheduleState.Study
                val block = shouldBlock(
                    activePackage, limitState, appLimitState, whitelist, alwaysAllowed, blockedApps, studyTimeActive
                )
                // Причина для оверлея (в том же порядке приоритета, что и в shouldBlock):
                // 1. Пакет в blockedApps (и не alwaysAllowed) — запрет родителя бьёт всё остальное.
                // 2. Иначе, если идёт «Время учёбы» — оно и есть причина мягкой блокировки.
                // 3. Иначе — обычный исчерпанный дневной лимит.
                val reason = when {
                    activePackage != null && activePackage !in alwaysAllowed && activePackage in blockedApps ->
                        BlockReason.BLOCKED_BY_PARENT
                    studyTimeActive -> BlockReason.STUDY_TIME
                    else -> BlockReason.LIMIT_EXPIRED
                }
                // Время окончания для оверлея нужно только при STUDY_TIME — в остальных случаях
                // untilText остаётся null (общая формулировка без времени).
                val untilText = (scheduleState as? ScheduleState.Study)
                    ?.takeIf { reason == BlockReason.STUDY_TIME }
                    ?.endsAt
                    ?.format(TIME_FORMATTER)
                Triple(block, reason, untilText)
            }
        }.distinctUntilChanged().collect { (block, reason, untilText) ->
            // Скрытие оверлея сюда намеренно не добавляем: он уходит сам по таймеру внутри
            // OverlayManager. Если бы скрытие шло отсюда, уход на домашний экран ниже сразу же
            // «снял» бы блокировку — лаунчер всегда разрешён.
            if (block) {
                overlayManager.show(reason, untilText)
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

        /** Формат времени окончания «Времени учёбы» на оверлее — «14:00». */
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
