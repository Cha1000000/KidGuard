package ru.homelab.kidguard.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Блокировка дня, которую телефон ребёнка **уже применил** — едет родителю в отчёте о состоянии
 * устройства ([DeviceHealth.dayBlock]).
 *
 * Именно через отчёт, а не отдельным запросом: сервер хранит отчёт непрозрачным JSON и сам
 * рассылает родителю сигнал при каждом его изменении, поэтому подтверждение доезжает за секунды и
 * без правок сервера.
 *
 * [minutesLeftBefore] посчитан на телефоне ребёнка в момент применения: он знает расход до секунды,
 * а статистика родителя отстаёт до 15 минут.
 */
data class AppliedDayBlock(
    val date: LocalDate,
    /** Метка блокировки, которую применили, — чтобы не спутать с более ранней. */
    val issuedAt: Long,
    val appliedAt: Instant,
    val minutesLeftBefore: Int
)

/** Что показывать родителю о блокировке дня. */
sealed interface DayBlockState {

    /** Блокировки сегодня нет — или её уже сняли сбросом, разблокировкой, бонусом. */
    data object NotBlocked : DayBlockState

    /**
     * Родитель заблокировал день, но телефон ребёнка ещё не подтвердил применение: выключен,
     * без сети или стоит старая сборка, не умеющая подтверждать.
     */
    data class Pending(val issuedAt: Long) : DayBlockState

    /** Телефон ребёнка применил блокировку в [appliedAt]; до неё оставалось [minutesLeftBefore]. */
    data class Confirmed(
        val issuedAt: Long,
        val appliedAt: Instant,
        val minutesLeftBefore: Int
    ) : DayBlockState
}

/**
 * Действует ли сейчас блокировка дня, по одним маркерам политики.
 *
 * Блокировку снимает любое более позднее действие родителя: сброс сегодняшнего лимита или
 * разблокировка (кнопкой либо выдачей бонуса). Маркеры сравниваются метками времени, потому что
 * в политике лежит по одному маркеру каждого типа и сам факт их наличия ничего не говорит.
 */
fun isDayBlockActive(
    today: LocalDate,
    block: DailyUsageBlock?,
    reset: DailyUsageReset?,
    unblock: DailyUsageUnblock?
): Boolean {
    if (block == null || block.date != today) return false
    val resetAt = reset?.takeIf { it.date == today }?.issuedAt ?: Long.MIN_VALUE
    val unblockAt = unblock?.takeIf { it.date == today }?.issuedAt ?: Long.MIN_VALUE
    return block.issuedAt > resetAt && block.issuedAt > unblockAt
}

/**
 * Состояние блокировки для экранов родителя: маркеры политики плюс подтверждение с телефона.
 *
 * Подтверждение засчитывается, только если оно про **ту же** блокировку (совпадает `issuedAt`) и за
 * сегодня. Иначе после повторной блокировки родитель сразу увидел бы «заблокировано» по отчёту о
 * предыдущей, хотя новая до телефона ещё не дошла.
 */
fun dayBlockState(
    today: LocalDate,
    block: DailyUsageBlock?,
    reset: DailyUsageReset?,
    unblock: DailyUsageUnblock?,
    applied: AppliedDayBlock?
): DayBlockState {
    if (!isDayBlockActive(today, block, reset, unblock)) return DayBlockState.NotBlocked
    val issuedAt = block!!.issuedAt
    val confirmed = applied?.takeIf { it.date == today && it.issuedAt == issuedAt }
        ?: return DayBlockState.Pending(issuedAt)
    return DayBlockState.Confirmed(issuedAt, confirmed.appliedAt, confirmed.minutesLeftBefore)
}
