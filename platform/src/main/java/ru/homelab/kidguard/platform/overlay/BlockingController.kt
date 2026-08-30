package ru.homelab.kidguard.platform.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.usecase.ObserveAppLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveScheduleStateUseCase
import ru.homelab.kidguard.core.domain.usecase.shouldBlock
import ru.homelab.kidguard.platform.R
import ru.homelab.kidguard.platform.accessibility.ForegroundAppMonitor
import ru.homelab.kidguard.platform.apps.AlwaysAllowedPackages
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
    private val overlayManager: OverlayManager,
    private val pinOverlayManager: PinOverlayManager,
    private val pinGuard: PinGuard,
    alwaysAllowedPackages: AlwaysAllowedPackages
) {

    // Всегда разрешены: само KidGuard и лаунчер (домашний экран не блокируем). То же множество
    // использует движок учёта, поэтому оно вынесено в общий компонент.
    private val alwaysAllowed: Set<String> = alwaysAllowedPackages.packages

    /**
     * Пакет, который родитель только что открыл своим PIN'ом (см. [requestPinBypass]). Пока
     * активный пакет совпадает с этим значением — блокировку не применяем, PIN второй раз не
     * спрашиваем. Сбрасывается, как только фокус реально уходит на другой пакет (см. `onEach`
     * в [run]) — обход не переживает выход из приложения, это НЕ разовое снятие лимита на весь
     * день, а точечный пропуск «пока родитель здесь и сейчас».
     */
    private val bypassedPackage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        Timber.tag(TAG).d("Контроллер блокировки запущен")
        // Личный лимит зависит от активного пакета, поэтому на каждую его смену пересобираем
        // подписку (flatMapLatest): наблюдаем usage+limit именно текущего приложения.
        foregroundAppMonitor.currentPackage
            .onEach { activePackage ->
                // Обход PIN'ом действует, только пока родитель не ушёл из открытого им пакета.
                // Реальный переход на другой пакет (currentPackage — StateFlow, эмитит только
                // изменения) стирает обход — следующий заход в тот же пакет снова потребует PIN.
                val bypassed = bypassedPackage.value
                if (bypassed != null && bypassed != activePackage) {
                    bypassedPackage.value = null
                }
            }
            .flatMapLatest { activePackage ->
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
                PolicyInputs(limitState, appLimitState, whitelist, blockedApps, scheduleState)
            }.combine(bypassedPackage) { inputs, bypassed ->
                // «Время учёбы» по смыслу равно исчерпанному дневному лимиту (см. shouldBlock) —
                // просто передаём признак дальше в чистую функцию, вся матрица приоритетов там.
                val studyTimeActive = inputs.scheduleState is ScheduleState.Study
                val bypassActive = activePackage != null && activePackage == bypassed
                val block = !bypassActive && shouldBlock(
                    activePackage, inputs.limitState, inputs.appLimitState, inputs.whitelist,
                    alwaysAllowed, inputs.blockedApps, studyTimeActive
                )
                // Причина для оверлея (в том же порядке приоритета, что и в shouldBlock):
                // 1. Пакет в blockedApps (и не alwaysAllowed) — запрет родителя бьёт всё остальное.
                // 2. Иначе, если идёт «Время учёбы» — оно и есть причина мягкой блокировки.
                // 3. Иначе — обычный исчерпанный дневной лимит.
                val reason = when {
                    activePackage != null && activePackage !in alwaysAllowed && activePackage in inputs.blockedApps ->
                        BlockReason.BLOCKED_BY_PARENT
                    studyTimeActive -> BlockReason.STUDY_TIME
                    else -> BlockReason.LIMIT_EXPIRED
                }
                // Время окончания для оверлея нужно только при STUDY_TIME — в остальных случаях
                // untilText остаётся null (общая формулировка без времени).
                val untilText = (inputs.scheduleState as? ScheduleState.Study)
                    ?.takeIf { reason == BlockReason.STUDY_TIME }
                    ?.endsAt
                    ?.format(TIME_FORMATTER)
                BlockDecision(block, reason, untilText, activePackage)
            }
        }.distinctUntilChanged().collect { decision ->
            // Скрытие оверлея сюда намеренно не добавляем: он уходит сам по таймеру внутри
            // OverlayManager. Если бы скрытие шло отсюда, уход на домашний экран ниже сразу же
            // «снял» бы блокировку — лаунчер всегда разрешён.
            if (decision.block) {
                val onPinRequested = decision.activePackage?.let { pkg ->
                    { requestPinBypass(pkg) }
                }
                overlayManager.show(decision.reason, decision.untilText, onPinRequested)
                sendHome()
                Timber.tag(TAG).d("Блокировка активна (причина=%s)", decision.reason)
            }
        }
    }

    /**
     * Родитель нажал «Открыть с PIN родителя» на блокирующем оверлее. Снимает ЛЮБУЮ причину
     * блокировки ([BlockReason] неважна) — PIN известен только родителю, значит верный ввод сам
     * по себе достаточное основание пропустить: не нужно сначала идти в своё приложение снимать
     * ограничение, а потом возвращать его обратно.
     */
    private fun requestPinBypass(packageName: String) {
        pinOverlayManager.show(
            verifyPin = { entered -> pinGuard.verify(entered) },
            onUnlocked = {
                bypassedPackage.value = packageName
                relaunch(packageName)
            },
            onCancel = {},
            subtitleRes = R.string.pin_overlay_bypass_subtitle
        )
    }

    /** Приложение уже отправлено на домашний экран ДО показа PIN — открываем его заново самим. */
    private fun relaunch(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Timber.tag(TAG).w("Нечем перезапустить %s после обхода PIN", packageName)
        }
    }

    private fun sendHome() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Итог одной пересборки решения — нужен и сам факт блокировки, и данные для оверлея/PIN. */
    private data class BlockDecision(
        val block: Boolean,
        val reason: BlockReason,
        val untilText: String?,
        val activePackage: String?
    )

    /** Промежуточный срез политики — до подмешивания обхода PIN'ом (см. [bypassedPackage]). */
    private data class PolicyInputs(
        val limitState: LimitState,
        val appLimitState: LimitState,
        val whitelist: Set<String>,
        val blockedApps: Set<String>,
        val scheduleState: ScheduleState
    )

    private companion object {
        const val TAG = "KidGuardBlocking"

        /** Формат времени окончания «Времени учёбы» на оверлее — «14:00». */
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
