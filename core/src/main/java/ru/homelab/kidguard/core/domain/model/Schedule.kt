package ru.homelab.kidguard.core.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** Минут в сутках — окна расписания задаются смещением от полуночи. */
const val MINUTES_PER_DAY: Int = 24 * 60

/**
 * Окно блокировки внутри суток, в минутах от полуночи.
 *
 * Начало включительно, конец исключительно: 08:00–14:00 блокирует до 13:59 и отпускает ровно
 * в 14:00. Если [endMinute] меньше [startMinute] — окно переходит **через полночь** (21:00–07:00
 * = с 21:00 сегодня до 07:00 завтра). Равенство границ трактуется как **пустое** окно: суточная
 * блокировка расписанием не задаётся (для этого есть дневной лимит 0 минут).
 */
data class TimeWindow(val startMinute: Int, val endMinute: Int) {

    /** Границы совпадают — окно ничего не блокирует. */
    val isEmpty: Boolean get() = startMinute == endMinute

    /** Окно переходит на следующие сутки (напр. 21:00–07:00). */
    val crossesMidnight: Boolean get() = endMinute < startMinute

    /** Время окончания окна как время суток — для текста «до 07:00». */
    val endsAt: LocalTime get() = LocalTime.of(endMinute / 60 % 24, endMinute % 60)

    /**
     * Идёт ли окно, начавшееся в ЭТИ ЖЕ сутки, в момент [minuteOfDay]. Для окна через полночь
     * покрывает только «хвост» до полуночи; кусок после полуночи проверяется отдельно —
     * см. [ScheduleRules.activeWindowAt].
     */
    fun containsSameDay(minuteOfDay: Int): Boolean = when {
        isEmpty -> false
        crossesMidnight -> minuteOfDay >= startMinute
        else -> minuteOfDay in startMinute until endMinute
    }
}

/**
 * Расписание блокировки: своё окно на каждый день недели плюс общий тумблер.
 *
 * Ключ карты — день **начала** окна: «Понедельник 21:00–07:00» означает ночь с понедельника на
 * вторник. День без записи — в этот день расписание не действует.
 */
data class ScheduleRules(
    val windowsByDay: Map<DayOfWeek, TimeWindow>,
    val enabled: Boolean
) {

    /** Окно на конкретный день недели, либо null, если родитель его не задавал. */
    fun windowFor(day: DayOfWeek): TimeWindow? = windowsByDay[day]

    /**
     * Окно, которое идёт прямо сейчас, либо null.
     *
     * Проверяем два кандидата: окно сегодняшнего дня и окно **вчерашнего** дня, если оно
     * перешло через полночь и его хвост ещё не закончился. Без второй проверки ночная
     * блокировка отпускала бы ровно в 00:00.
     */
    fun activeWindowAt(now: LocalDateTime): TimeWindow? {
        if (!enabled) return null
        val minuteOfDay = now.toMinuteOfDay()

        windowsByDay[now.dayOfWeek]?.let { window ->
            if (window.containsSameDay(minuteOfDay)) return window
        }
        windowsByDay[now.dayOfWeek.minus(1)]?.let { window ->
            if (!window.isEmpty && window.crossesMidnight && minuteOfDay < window.endMinute) return window
        }
        return null
    }

    /**
     * Через сколько минут начнётся ближайшее окно — для предупреждения ребёнка заранее.
     * Возвращает null, если расписание выключено, окно уже идёт или ближайшего окна нет
     * (смотрим сегодняшний и завтрашний день — дальше предупреждать бессмысленно).
     */
    fun minutesUntilStart(now: LocalDateTime): Int? {
        if (!enabled || activeWindowAt(now) != null) return null
        val minuteOfDay = now.toMinuteOfDay()

        windowsByDay[now.dayOfWeek]
            ?.takeIf { !it.isEmpty && it.startMinute > minuteOfDay }
            ?.let { return it.startMinute - minuteOfDay }
        windowsByDay[now.dayOfWeek.plus(1)]
            ?.takeIf { !it.isEmpty }
            ?.let { return (MINUTES_PER_DAY - minuteOfDay) + it.startMinute }
        return null
    }

    companion object {
        val EMPTY = ScheduleRules(emptyMap(), enabled = false)
    }
}

/** Тип расписания — различает две независимые настройки в хранилище и синхронизации. */
enum class ScheduleKind { STUDY, SLEEP }

/** Что расписание диктует прямо сейчас. */
sealed interface ScheduleState {

    /** Ни одно расписание не действует. */
    data object Inactive : ScheduleState

    /** Идёт «Время учёбы» — мягкая блокировка, как при исчерпанном дневном лимите. */
    data class Study(val endsAt: LocalTime) : ScheduleState

    /** Идёт «Время сна» — полная блокировка несмахиваемым замком, снимается только PIN. */
    data class Sleep(val endsAt: LocalTime) : ScheduleState
}

/**
 * Состояние расписаний в момент [now]. **Сон бьёт учёбу**: если окна пересеклись (родитель задал
 * учёбу до 22:00 и сон с 21:00), действует более строгое правило.
 */
fun scheduleStateAt(
    now: LocalDateTime,
    study: ScheduleRules,
    sleep: ScheduleRules
): ScheduleState {
    sleep.activeWindowAt(now)?.let { return ScheduleState.Sleep(it.endsAt) }
    study.activeWindowAt(now)?.let { return ScheduleState.Study(it.endsAt) }
    return ScheduleState.Inactive
}

private fun LocalDateTime.toMinuteOfDay(): Int = hour * 60 + minute
