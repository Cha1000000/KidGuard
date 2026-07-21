package ru.homelab.kidguard.core.domain.model

/** Как выбираются моменты перерывов: через интервал залипания или в назначенные часы. */
enum class BreakMode { INTERVAL, HOURS }

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
     * Часы перебираем по возрастанию, а не в порядке множества: если родитель задал два часа
     * ближе, чем длительность перерыва (15:00 и 15:05 при десятиминутном перерыве), их окна
     * пересекаются — и без сортировки замок показывал бы то один остаток, то другой.
     */
    fun activeHoursWindow(nowMinuteOfDay: Int): TimeWindow? {
        if (!isConfigured || mode != BreakMode.HOURS) return null
        val start = hours.sorted().firstOrNull { start ->
            nowMinuteOfDay >= start && nowMinuteOfDay < start + durationMinutes
        } ?: return null
        return TimeWindow(start, start + durationMinutes)
    }

    /** Сколько минут до ближайшего часа перерыва сегодня (или null, если сегодня их больше нет). */
    fun minutesUntilNextHour(nowMinuteOfDay: Int): Int? {
        if (!isConfigured || mode != BreakMode.HOURS) return null
        val next = hours.filter { it > nowMinuteOfDay }.minOrNull() ?: return null
        return next - nowMinuteOfDay
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
    /** Скоро перерыв — повод показать плашку. */
    data object Warning : BreakState
    /** Идёт перерыв; [secondsLeft] питает обратный отсчёт на замке. */
    data class Active(val secondsLeft: Int) : BreakState
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
 * Состояние перерывов «здесь и сейчас».
 *
 * В режиме INTERVAL таймер перерыва идёт по тому же счётчику залипания: перерыв занимает отрезок
 * [порог; порог + длительность). Это не описка — счётчик копится только при включённом экране,
 * поэтому если ребёнок гасит экран посреди перерыва, перерыв «замирает». Отдых при этом всё равно
 * засчитывается: пауза длиной с перерыв обнуляет счётчик (см. StickinessTracker), и состояние
 * само возвращается в Idle.
 */
fun breakStateAt(
    rules: BreakRules,
    dayLimitMinutes: Int?,
    scheduleActive: Boolean,
    stickySeconds: Int,
    nowMinuteOfDay: Int
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
                stickySeconds >= threshold -> BreakState.Active(breakEnd - stickySeconds)
                stickySeconds >= threshold - WARNING_LEAD_SECONDS -> BreakState.Warning
                else -> BreakState.Idle
            }
        }
        BreakMode.HOURS -> {
            val window = rules.activeHoursWindow(nowMinuteOfDay)
            val minutesLeft = rules.minutesUntilNextHour(nowMinuteOfDay)
            when {
                window != null -> BreakState.Active((window.endMinute - nowMinuteOfDay) * 60)
                minutesLeft != null && minutesLeft <= WARNING_LEAD_SECONDS / 60 -> BreakState.Warning
                else -> BreakState.Idle
            }
        }
    }
}

const val WARNING_LEAD_SECONDS = 5 * 60
