package ru.homelab.kidguard.core.domain.model

import java.time.Duration
import java.time.LocalDateTime

/** Как выбираются моменты перерывов: через интервал залипания или в назначенные часы. */
enum class BreakMode { INTERVAL, HOURS }

/**
 * Абсолютное окно перерыва режима HOURS: [start] включительно, [end] исключительно.
 * В отличие от [TimeWindow] расписания, границы — конкретные моменты с датой, поэтому окно
 * свободно переходит через полночь, а остаток считается с точностью до секунды.
 */
data class HoursWindow(val start: LocalDateTime, val end: LocalDateTime)

/**
 * Настройки принудительных перерывов. Ноль в [intervalMinutes]/[durationMinutes] и пустые [hours]
 * означают «родитель ещё не задал» — заготовленных значений у фичи нет намеренно.
 */
data class BreakRules(
    val enabled: Boolean,
    val mode: BreakMode,
    val intervalMinutes: Int,
    val hours: Set<Int>,
    val durationMinutes: Int,
    val message: String
) {

    /** Родитель задал всё необходимое и включил перерывы. */
    val isConfigured: Boolean
        get() = enabled && durationMinutes > 0 && when (mode) {
            BreakMode.INTERVAL -> intervalMinutes > 0
            BreakMode.HOURS -> hours.isNotEmpty()
        }

    /**
     * Окно перерыва режима HOURS, внутри которого мы сейчас находимся (или null).
     *
     * Окна строятся на вчера/сегодня/завтра и перед выбором объединяются: пересекающиеся
     * и вплотную примыкающие окна (15:00–15:10 и 15:10–15:20) сливаются в одно непрерывное
     * с максимальным концом — иначе замок показывал бы то один остаток, то другой, а на стыке
     * возникал бы разрыв. Вчерашние окна нужны, потому что окно 23:58 + 15 минут живёт после
     * полуночи; завтрашние — чтобы предупреждение работало и перед окном за полночь.
     */
    fun activeHoursWindow(now: LocalDateTime): HoursWindow? {
        if (!isConfigured || mode != BreakMode.HOURS) return null
        return mergedHoursWindows(now).firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
    }

    /**
     * Объединённые окна HOURS вокруг [now] (вчера/сегодня/завтра), отсортированные по началу.
     * Служебная: нужна и для активного окна, и для ближайшего предупреждения.
     */
    internal fun mergedHoursWindows(now: LocalDateTime): List<HoursWindow> {
        val duration = Duration.ofMinutes(durationMinutes.toLong())
        val today = now.toLocalDate()
        val raw = listOf(today.minusDays(1), today, today.plusDays(1)).flatMap { date ->
            hours.map { minuteOfDay ->
                val start = date.atStartOfDay().plusMinutes(minuteOfDay.toLong())
                HoursWindow(start, start.plus(duration))
            }
        }.sortedBy { it.start }
        val merged = mutableListOf<HoursWindow>()
        for (window in raw) {
            val last = merged.lastOrNull()
            if (last != null && !window.start.isAfter(last.end)) {
                if (window.end.isAfter(last.end)) {
                    merged[merged.lastIndex] = HoursWindow(last.start, window.end)
                }
            } else {
                merged.add(window)
            }
        }
        return merged
    }

    companion object {
        val EMPTY = BreakRules(
            enabled = false,
            mode = BreakMode.INTERVAL,
            intervalMinutes = 0,
            hours = emptySet(),
            durationMinutes = 0,
            message = ""
        )
    }
}

/** Текущее состояние перерывов на детском устройстве. */
sealed interface BreakState {
    data object Idle : BreakState
    /** Скоро перерыв — повод показать плашку; ключ стабилен внутри usage-цикла или окна HOURS. */
    data class Warning(val warningKey: String = "") : BreakState
    /**
     * Идёт перерыв; [secondsLeft] — производная проекция, а владельцем countdown служит
     * [endsAtElapsedMs]. [sessionId] связывает отображение с устойчивой [BreakSession].
     */
    data class Active(
        val secondsLeft: Int,
        val message: String = "",
        val sessionId: String = "",
        val endsAtElapsedMs: Long = 0L
    ) : BreakState
}

/**
 * Действуют ли перерывы в день с таким лимитом. Перерывы нужны там, где ребёнок не ограничен
 * жёстко: лимит вовсе не задан или больше [BREAKS_LIMIT_THRESHOLD_MINUTES]. Бонусы не учитываются —
 * порог описывает намерение родителя, а бонус это разовая поблажка.
 */
fun breaksApplyToday(dayLimitMinutes: Int?): Boolean =
    dayLimitMinutes == null || dayLimitMinutes > BREAKS_LIMIT_THRESHOLD_MINUTES

const val BREAKS_LIMIT_THRESHOLD_MINUTES = 180

/**
 * Чистая проекция перерывов «здесь и сейчас» без сохранения сессии. Устойчивым жизненным циклом
 * в приложении управляет ObserveBreakStateUseCase (сессия с абсолютным deadline, план
 * «исправление перерывов»); эта функция проверяет базовые правила отдельно и на runtime-отсчёт
 * не претендует.
 *
 * В режиме INTERVAL проекция описывается счётчиком залипания: перерыв занимает отрезок
 * [порог; порог + длительность). Это модель самой проекции, а не живого перерыва — реальный
 * перерыв НЕ зависит от экрана и заканчивается по deadline (`BreakSession.endsAtElapsedMs`).
 */
fun breakStateAt(
    rules: BreakRules,
    dayLimitMinutes: Int?,
    scheduleActive: Boolean,
    stickySeconds: Int,
    now: LocalDateTime
): BreakState {
    // Любое окно расписания (учёба или сон) отменяет перерыв: телефон и так ограничен.
    if (scheduleActive) return BreakState.Idle
    if (!rules.isConfigured) return BreakState.Idle
    if (!breaksApplyToday(dayLimitMinutes)) return BreakState.Idle

    return when (rules.mode) {
        BreakMode.INTERVAL -> {
            val threshold = rules.intervalMinutes * 60
            val breakEnd = threshold + rules.durationMinutes * 60
            when {
                stickySeconds >= breakEnd -> BreakState.Idle
                stickySeconds >= threshold ->
                    BreakState.Active(breakEnd - stickySeconds, rules.message)
                stickySeconds >= threshold - WARNING_LEAD_SECONDS -> BreakState.Warning()
                else -> BreakState.Idle
            }
        }
        BreakMode.HOURS -> {
            val windows = rules.mergedHoursWindows(now)
            val active = windows.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
            if (active != null) {
                return BreakState.Active(ceilSecondsBetween(now, active.end), rules.message)
            }
            val next = windows.firstOrNull { it.start.isAfter(now) }
            when {
                next != null && Duration.between(now, next.start).seconds <= WARNING_LEAD_SECONDS ->
                    BreakState.Warning()
                else -> BreakState.Idle
            }
        }
    }
}

/** Остаток от [now] до [end] в секундах с округлением вверх: последние 500 мс — это 1 секунда, а не 0. */
private fun ceilSecondsBetween(now: LocalDateTime, end: LocalDateTime): Int {
    val millis = Duration.between(now, end).toMillis().coerceAtLeast(0)
    return ((millis + 999) / 1000).toInt()
}

const val WARNING_LEAD_SECONDS = 5 * 60
