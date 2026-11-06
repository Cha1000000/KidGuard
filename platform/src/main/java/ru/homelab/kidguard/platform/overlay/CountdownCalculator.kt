package ru.homelab.kidguard.platform.overlay

/**
 * Остаток обратного отсчёта замка перерыва. Владелец отсчёта — deadline (`endsAtElapsedMs`
 * доменной сессии): каждый тик вычисляет остаток заново от текущего времени, а не декрементит
 * локальную переменную. Поэтому погашенный экран, уход в звонок или переподключение сервиса не
 * искажают показания — после возвращения отсчёт честно догоняет реальное время.
 *
 * Округление — ceil: последние 999 мс ещё показываются как секунда, ровно в deadline и позже —
 * ноль («0:00»), отрицательных значений не бывает.
 */
object CountdownCalculator {

    /** Секунд до [endsAtElapsedMs] от момента [nowElapsedMs] (обе — в шкале elapsedRealtime). */
    fun secondsLeft(endsAtElapsedMs: Long, nowElapsedMs: Long): Int {
        val remainingMs = (endsAtElapsedMs - nowElapsedMs).coerceAtLeast(0L)
        val seconds = remainingMs / 1_000L + if (remainingMs % 1_000L != 0L) 1L else 0L
        return seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
