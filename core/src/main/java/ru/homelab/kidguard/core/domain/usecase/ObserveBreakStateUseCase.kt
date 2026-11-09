package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakRules
import ru.homelab.kidguard.core.domain.model.BreakSession
import ru.homelab.kidguard.core.domain.model.BreakState
import ru.homelab.kidguard.core.domain.model.ContinuousUsageSession
import ru.homelab.kidguard.core.domain.model.HoursWindow
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.model.WARNING_LEAD_SECONDS
import ru.homelab.kidguard.core.domain.model.breaksApplyToday
import ru.homelab.kidguard.core.domain.repository.BreakClock
import ru.homelab.kidguard.core.domain.repository.BreakRuntimeRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.StickinessSource
import ru.homelab.kidguard.core.domain.repository.todayFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Единый владелец устойчивого состояния перерыва и usage-цикла. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBreakStateUseCase internal constructor(
    private val policyRepository: PolicyRepository,
    private val scheduleStates: Flow<ScheduleState>,
    private val stickinessSource: StickinessSource,
    private val currentDateProvider: CurrentDateProvider,
    private val breakRuntimeRepository: BreakRuntimeRepository,
    private val breakClock: BreakClock
) {

    @Inject
    constructor(
        policyRepository: PolicyRepository,
        observeScheduleStateUseCase: ObserveScheduleStateUseCase,
        stickinessSource: StickinessSource,
        currentDateProvider: CurrentDateProvider,
        breakRuntimeRepository: BreakRuntimeRepository,
        breakClock: BreakClock
    ) : this(
        policyRepository = policyRepository,
        scheduleStates = observeScheduleStateUseCase(),
        stickinessSource = stickinessSource,
        currentDateProvider = currentDateProvider,
        breakRuntimeRepository = breakRuntimeRepository,
        breakClock = breakClock
    )

    operator fun invoke(): Flow<BreakState> = flow {
        var activeSession = restoreSession()
        var previousRules: SignificantRules? = null
        var previousApplicability: Boolean? = null

        suspend fun evaluate(input: BreakInput): BreakState {
            val rules = input.rules
            val significantRules = SignificantRules.from(rules)
            val applicable = rules.isConfigured && breaksApplyToday(input.dayLimitMinutes)
            val rulesChanged = previousRules != null && previousRules != significantRules
            var usageReset = false

            val current = activeSession
            if (current != null && (!applicable || rulesChanged || current.mode != rules.mode)) {
                breakRuntimeRepository.cancelSession(current.id)
                stickinessSource.reset()
                activeSession = null
                usageReset = true
            }

            val uncompleted = activeSession
            if (uncompleted != null && breakClock.elapsedRealtimeMs() >= uncompleted.endsAtElapsedMs) {
                breakRuntimeRepository.completeBreakAndResetUsage(uncompleted.id)
                stickinessSource.reset()
                activeSession = null
                usageReset = true
            }

            // Старт учёбы/сна завершает перерыв и сбрасывает его остаток: после расписания
            // замок перерыва не должен всплывать (решение 2026-11-08).
            val scheduled = activeSession
            if (scheduled != null && input.scheduleActive) {
                breakRuntimeRepository.completeBreakAndResetUsage(scheduled.id)
                stickinessSource.reset()
                activeSession = null
                usageReset = true
            }

            // Смерть процесса/reboot: сессия могла начаться до расписания, а завершение не
            // успеть сохраниться. Прошедшая часть, пересекшая расписание, тоже не воскресает.
            val tainted = activeSession
            if (tainted != null && sessionOverlapsSchedule(tainted, input)) {
                breakRuntimeRepository.completeBreakAndResetUsage(tainted.id)
                stickinessSource.reset()
                activeSession = null
                usageReset = true
            }

            fun finish(state: BreakState): BreakState {
                previousRules = significantRules
                previousApplicability = applicable
                stickinessSource.pause(activeSession != null || input.scheduleActive)
                return state
            }

            activeSession?.let { session ->
                return finish(session.toActiveState(rules.message))
            }

            if (!usageReset && (rulesChanged || (!applicable && previousApplicability != false))) {
                stickinessSource.reset()
                usageReset = true
            }
            if (!applicable || usageReset || rulesChanged) return finish(BreakState.Idle)

            val state = when (rules.mode) {
                BreakMode.INTERVAL -> intervalState(rules, input.usage)
                BreakMode.HOURS -> hoursState(rules, input.scheduleActive, input.studyRules, input.sleepRules)
            }
            if (state is BreakState.Active) {
                activeSession = breakRuntimeRepository.loadActiveSession()
            }
            return finish(state)
        }

        // Правила расписания нужны для проверки прошедшей части HOURS-окна; combine на 5
        // потоков уже занят, поэтому сначала собираем политику, затем добавляем состояния.
        val policyInputs = combine(
            policyRepository.breakRules,
            policyRepository.dailyLimits,
            policyRepository.studySchedule,
            policyRepository.sleepSchedule,
            currentDateProvider.todayFlow()
        ) { rules, limits, study, sleep, today ->
            PolicyRules(
                rules = rules,
                dayLimitMinutes = limits.limitFor(today.dayOfWeek),
                study = study,
                sleep = sleep
            )
        }
        val inputs = combine(
            policyInputs,
            scheduleStates,
            stickinessSource.usageSession
        ) { policies, schedule, usage ->
            BreakInput(
                rules = policies.rules,
                dayLimitMinutes = policies.dayLimitMinutes,
                scheduleActive = schedule !is ScheduleState.Inactive,
                usage = usage,
                studyRules = policies.study,
                sleepRules = policies.sleep
            )
        }
        emitAll(
            combine(inputs, ticker()) { input, _ -> input }
                .map { evaluate(it) }
                .distinctUntilChanged()
        )
    }

    private suspend fun restoreSession(): BreakSession? {
        val saved = breakRuntimeRepository.loadActiveSession() ?: return null
        val elapsedMs = breakClock.elapsedRealtimeMs()
        val bootCount = breakClock.bootCount()
        if (saved.bootCount != null && saved.bootCount == bootCount) {
            if (elapsedMs < saved.endsAtElapsedMs) return saved
            breakRuntimeRepository.completeBreakAndResetUsage(saved.id)
            stickinessSource.reset()
            return null
        }

        val remainingMs = saved.endsAtWallMs - breakClock.wallTimeMillis()
        if (remainingMs <= 0) {
            breakRuntimeRepository.completeBreakAndResetUsage(saved.id)
            stickinessSource.reset()
            return null
        }
        return saved.copy(
            startedAtElapsedMs = elapsedMs,
            endsAtElapsedMs = elapsedMs + remainingMs,
            bootCount = bootCount
        ).also { breakRuntimeRepository.saveSession(it) }
    }

    private suspend fun intervalState(
        rules: BreakRules,
        usage: ContinuousUsageSession?
    ): BreakState {
        if (usage == null) return BreakState.Idle
        val thresholdMs = rules.intervalMinutes * MILLIS_PER_MINUTE
        if (usage.accumulatedMs >= thresholdMs) {
            return startSession(
                mode = BreakMode.INTERVAL,
                durationMs = rules.durationMinutes * MILLIS_PER_MINUTE,
                windowKey = null,
                message = rules.message
            )
        }
        return if (usage.accumulatedMs >= thresholdMs - WARNING_LEAD_SECONDS * MILLIS_PER_SECOND) {
            BreakState.Warning("interval:${usage.cycleId}")
        } else {
            BreakState.Idle
        }
    }

    private suspend fun hoursState(
        rules: BreakRules,
        scheduleActive: Boolean,
        studyRules: ScheduleRules,
        sleepRules: ScheduleRules
    ): BreakState {
        // Пока идёт расписание, новую сессию не создаём (решение 2026-11-08).
        if (scheduleActive) return BreakState.Idle
        val now = breakClock.localDateTime()
        val activeWindow = rules.activeHoursWindow(now)
        if (activeWindow != null) {
            // Прошедшая часть окна пересекла учёбу/сон — остаток после расписания не показываем.
            if (
                studyRules.overlaps(activeWindow.start, now) ||
                sleepRules.overlaps(activeWindow.start, now)
            ) {
                return BreakState.Idle
            }
            val remainingMs = Duration.between(now, activeWindow.end).toMillis().coerceAtLeast(0)
            if (remainingMs > 0) {
                return startSession(
                    mode = BreakMode.HOURS,
                    durationMs = remainingMs,
                    windowKey = activeWindow.stableKey(),
                    message = rules.message
                )
            }
        }
        val nextWindow = rules.mergedHoursWindows(now).firstOrNull { it.start.isAfter(now) }
        return if (
            nextWindow != null &&
            Duration.between(now, nextWindow.start).seconds <= WARNING_LEAD_SECONDS
        ) {
            BreakState.Warning("hours:${nextWindow.stableKey()}")
        } else {
            BreakState.Idle
        }
    }

    /** Пересекла ли прошедшая часть сессии (до [breakClock.wallTimeMillis] или до её deadline) расписание. */
    private fun sessionOverlapsSchedule(session: BreakSession, input: BreakInput): Boolean {
        val endWall = minOf(breakClock.wallTimeMillis(), session.endsAtWallMs)
        if (endWall <= session.startedAtWallMs) return false
        val start = session.startedAtWallMs.toLocalDateTime()
        val end = endWall.toLocalDateTime()
        return input.studyRules.overlaps(start, end) || input.sleepRules.overlaps(start, end)
    }

    private fun Long.toLocalDateTime(): LocalDateTime =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private suspend fun startSession(
        mode: BreakMode,
        durationMs: Long,
        windowKey: String?,
        message: String
    ): BreakState {
        val elapsedMs = breakClock.elapsedRealtimeMs()
        val wallMs = breakClock.wallTimeMillis()
        val session = BreakSession(
            id = UUID.randomUUID().toString(),
            mode = mode,
            startedAtElapsedMs = elapsedMs,
            endsAtElapsedMs = elapsedMs + durationMs,
            startedAtWallMs = wallMs,
            endsAtWallMs = wallMs + durationMs,
            bootCount = breakClock.bootCount(),
            windowKey = windowKey
        )
        breakRuntimeRepository.saveSession(session)
        return session.toActiveState(message)
    }

    private fun BreakSession.toActiveState(message: String): BreakState.Active {
        val remainingMs = (endsAtElapsedMs - breakClock.elapsedRealtimeMs()).coerceAtLeast(0)
        return BreakState.Active(
            secondsLeft = ((remainingMs + 999) / 1000).toInt(),
            message = message,
            sessionId = id,
            endsAtElapsedMs = endsAtElapsedMs
        )
    }

    private fun HoursWindow.stableKey(): String = "$start|$end"

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    private data class PolicyRules(
        val rules: BreakRules,
        val dayLimitMinutes: Int?,
        val study: ScheduleRules,
        val sleep: ScheduleRules
    )

    private data class BreakInput(
        val rules: BreakRules,
        val dayLimitMinutes: Int?,
        val scheduleActive: Boolean,
        val usage: ContinuousUsageSession?,
        val studyRules: ScheduleRules,
        val sleepRules: ScheduleRules
    )

    private data class SignificantRules(
        val enabled: Boolean,
        val mode: BreakMode,
        val intervalMinutes: Int,
        val durationMinutes: Int,
        val hours: Set<Int>
    ) {
        companion object {
            fun from(rules: BreakRules) = SignificantRules(
                enabled = rules.enabled,
                mode = rules.mode,
                intervalMinutes = rules.intervalMinutes,
                durationMinutes = rules.durationMinutes,
                hours = rules.hours
            )
        }
    }

    private companion object {
        const val TICK_MS = 15_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
