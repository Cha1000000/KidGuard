package ru.homelab.kidguard.platform.overlay

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import ru.homelab.kidguard.core.domain.model.LimitState
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.usecase.ObserveAppLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveBonusPassesUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveScheduleStateUseCase
import ru.homelab.kidguard.core.domain.usecase.backgroundVisibleCandidates
import ru.homelab.kidguard.core.domain.usecase.confirmedAcrossChecks
import ru.homelab.kidguard.core.domain.usecase.shouldBlock
import ru.homelab.kidguard.core.domain.usecase.shouldRepeatBlock
import ru.homelab.kidguard.platform.R
import ru.homelab.kidguard.platform.accessibility.BlockingUiState
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
    private val observeBonusPassesUseCase: ObserveBonusPassesUseCase,
    private val observeScheduleStateUseCase: ObserveScheduleStateUseCase,
    private val policyRepository: PolicyRepository,
    private val overlayManager: OverlayManager,
    private val pinOverlayManager: PinOverlayManager,
    private val pinGuard: PinGuard,
    private val blockingUiState: BlockingUiState,
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
    suspend fun run() = coroutineScope {
        Timber.tag(TAG).d("Контроллер блокировки запущен")
        // Параллельно основному пути — проверка приложений, которые видны, но не активны (мини-окно,
        // разделённый экран). Основной путь смотрит только на активное окно.
        launch { watchVisibleWindows() }
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
            }.combine(observeBonusPassesUseCase()) { inputs, passes ->
                inputs to (activePackage != null && activePackage in passes)
            }.combine(bypassedPackage) { (inputs, hasPass), bypassed ->
                // «Время учёбы» по смыслу равно исчерпанному дневному лимиту (см. shouldBlock) —
                // просто передаём признак дальше в чистую функцию, вся матрица приоритетов там.
                val studyTimeActive = inputs.scheduleState is ScheduleState.Study
                val bypassActive = activePackage != null && activePackage == bypassed
                val block = !bypassActive && shouldBlock(
                    activePackage, inputs.limitState, inputs.appLimitState, inputs.whitelist,
                    alwaysAllowed, inputs.blockedApps, studyTimeActive, hasPass
                )
                describe(block, activePackage, inputs.blockedApps, inputs.scheduleState)
            }
        }.distinctUntilChanged().collectLatest { decision ->
            // Скрытие оверлея сюда намеренно не добавляем: он уходит сам по таймеру внутри
            // OverlayManager. Если бы скрытие шло отсюда, уход на домашний экран ниже сразу же
            // «снял» бы блокировку — лаунчер всегда разрешён.
            if (!decision.block) return@collectLatest
            enforce(decision)
            Timber.tag(TAG).d("Блокировка активна (причина=%s)", decision.reason)
            holdBlock(decision)
        }
    }

    /**
     * Держит блокировку, пока действует решение «блокировать»; новое решение отменяет цикл
     * (`collectLatest`).
     *
     * Раньше блокировка срабатывала один раз, на смене решения. Если переход «приложение → рабочий
     * стол → приложение» терялся — быстрые значения склеивались, или HiOS не присылал событие
     * рабочего стола, — решение повторялось тем же значением и отфильтровывалось, оверлей уходил сам
     * через несколько секунд, а приложение оставалось открытым (эмулятор, 17.09.2026). Когда
     * повторять — решает [shouldRepeatBlock]: только если приложение на экране и по монитору, и по
     * свежему чтению стека окон.
     */
    private suspend fun holdBlock(decision: BlockDecision) {
        val blockedPackage = decision.activePackage ?: return
        while (true) {
            delay(REPEAT_CHECK_MS)
            // Стек окон читает accessibility-сервис на главном потоке — там же живёт его кэш окон.
            val repeat = withContext(Dispatchers.Main) {
                shouldRepeatBlock(
                    blockedPackage = blockedPackage,
                    currentPackage = foregroundAppMonitor.currentPackage.value,
                    onScreenPackage = foregroundAppMonitor.probeOnScreenPackage(),
                    blockingUiVisible = blockingUiState.blockingVisible()
                )
            }
            if (repeat) {
                runCatching { enforce(decision) }
                    .onFailure { Timber.tag(TAG).e(it, "Повтор блокировки %s не удался", blockedPackage) }
                    .onSuccess { Timber.tag(TAG).d("Повтор блокировки: %s снова на экране (причина=%s)", blockedPackage, decision.reason) }
            }
        }
    }

    /**
     * Причина и текст для оверлея — в том же порядке приоритета, что и в [shouldBlock]:
     * 1. Пакет в blockedApps (и не alwaysAllowed) — запрет родителя бьёт всё остальное.
     * 2. Иначе, если идёт «Время учёбы» — оно и есть причина мягкой блокировки.
     * 3. Иначе — обычный исчерпанный дневной лимит.
     * Время окончания нужно только при STUDY_TIME — в остальных случаях формулировка без времени.
     */
    private fun describe(
        block: Boolean,
        packageName: String?,
        blockedApps: Set<String>,
        scheduleState: ScheduleState
    ): BlockDecision {
        val reason = when {
            packageName != null && packageName !in alwaysAllowed && packageName in blockedApps ->
                BlockReason.BLOCKED_BY_PARENT
            scheduleState is ScheduleState.Study -> BlockReason.STUDY_TIME
            else -> BlockReason.LIMIT_EXPIRED
        }
        val untilText = (scheduleState as? ScheduleState.Study)
            ?.takeIf { reason == BlockReason.STUDY_TIME }
            ?.endsAt
            ?.format(TIME_FORMATTER)
        return BlockDecision(block, reason, untilText, packageName)
    }

    /**
     * Приложения, которые видны на экране, но не активны: мини-окно поверх рабочего стола, вторая
     * половина разделённого экрана, «картинка в картинке».
     *
     * Основной путь решает по активному окну, и такое приложение для него невидимо. На телефоне Олега
     * 17.09.2026 игры в мини-окне HiOS шли во «Время учёбы» 44 минуты: пока активен рабочий стол,
     * блокировки не было вовсе. Раз в [VISIBLE_CHECK_MS] проверяем все видимые окна по тем же правилам
     * ([shouldBlock]) и блокируем подтверждённое двумя проверками подряд ([confirmedAcrossChecks]).
     *
     * «Домой» мини-окно не закрывает — поэтому оверлей возвращается после каждого своего ухода, пока
     * окно видно. Закрыть его — дело ребёнка; на это у него есть время, пока оверлей скрыт.
     */
    private suspend fun watchVisibleWindows() {
        val powerManager = context.getSystemService<PowerManager>()
        var previous = emptySet<String>()
        while (true) {
            delay(VISIBLE_CHECK_MS)
            // Ошибка одной проверки (чтение Room/DataStore, стек окон) не должна гасить ни этот цикл, ни
            // основной путь блокировки: они в одном coroutineScope, и исключение отменило бы оба насовсем.
            previous = try {
                checkVisibleWindows(powerManager, previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Проверка видимых окон не удалась — пробую на следующем шаге")
                emptySet()
            }
        }
    }

    /** Один шаг [watchVisibleWindows]; возвращает запрещённые видимые окна этого шага для подтверждения. */
    private suspend fun checkVisibleWindows(powerManager: PowerManager?, previous: Set<String>): Set<String> {
        // Оверлеи меняют состояние на главном потоке, и читать их флаги надо оттуда же — иначе проверка
        // может не увидеть только что открытый PIN родителя и заблокировать поверх него.
        val (uiVisible, visible) = withContext(Dispatchers.Main) {
            blockingUiState.blockingVisible() to foregroundAppMonitor.probeVisiblePackages()
        }
        if (powerManager?.isInteractive != true || uiVisible) return emptySet()
        val candidates = backgroundVisibleCandidates(
            visible = visible,
            activePackage = foregroundAppMonitor.currentPackage.value,
            alwaysAllowed = alwaysAllowed,
            bypassedPackage = bypassedPackage.value
        )
        val blocked = if (candidates.isEmpty()) emptySet() else blockedAmong(candidates)
        val confirmed = confirmedAcrossChecks(previous, blocked)
        val packageName = confirmed.minOrNull() ?: return blocked
        val decision = describe(
            block = true,
            packageName = packageName,
            blockedApps = policyRepository.blockedApps.first(),
            scheduleState = observeScheduleStateUseCase().first()
        )
        enforce(decision)
        Timber.tag(TAG).d("Блокировка видимого неактивного окна: %s (причина=%s)", packageName, decision.reason)
        return emptySet()
    }

    /** Какие из [candidates] сейчас заблокированы — по той же матрице, что и активное приложение. */
    private suspend fun blockedAmong(candidates: Set<String>): Set<String> {
        val limitState = observeLimitStateUseCase().first()
        val whitelist = policyRepository.whitelist.first()
        val blockedApps = policyRepository.blockedApps.first()
        val studyTimeActive = observeScheduleStateUseCase().first() is ScheduleState.Study
        val passes = observeBonusPassesUseCase().first()
        return candidates.filterTo(mutableSetOf()) { packageName ->
            shouldBlock(
                packageName, limitState, observeAppLimitStateUseCase(packageName).first(), whitelist,
                alwaysAllowed, blockedApps, studyTimeActive, packageName in passes
            )
        }
    }

    /** Показать оверлей и увести на рабочий стол. */
    private fun enforce(decision: BlockDecision) {
        val onPinRequested = decision.activePackage?.let { pkg ->
            { requestPinBypass(pkg) }
        }
        overlayManager.show(decision.reason, decision.untilText, onPinRequested)
        sendHome()
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

        /**
         * Шаг проверки «не вернулся ли ребёнок в заблокированное приложение». Больше, чем шаг сверки
         * монитора со стеком (1,5 с), не нужно: повтор всё равно подтверждается свежим чтением стека.
         */
        const val REPEAT_CHECK_MS = 1_000L

        /** Шаг проверки видимых неактивных окон. С подтверждением двумя проверками — блок за ~2 с. */
        const val VISIBLE_CHECK_MS = 1_000L
    }
}
