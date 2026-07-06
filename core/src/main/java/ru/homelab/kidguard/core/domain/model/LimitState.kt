package ru.homelab.kidguard.core.domain.model

/**
 * Текущее состояние дневного лимита экранного времени.
 */
sealed interface LimitState {

    /** На сегодня лимит не задан — ограничения нет. */
    data object NoLimit : LimitState

    /** Лимит есть, время ещё осталось. */
    data class Remaining(val minutesLeft: Int) : LimitState

    /** Дневной лимит исчерпан — «время вышло». */
    data object Expired : LimitState
}
