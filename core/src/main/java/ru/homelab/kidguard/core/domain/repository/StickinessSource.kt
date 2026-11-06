package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.ContinuousUsageSession

/**
 * Непрерывное «залипание» — сколько времени подряд ребёнок смотрит в экран. Реализация живёт в
 * `:platform` (нужны PowerManager/KeyguardManager), домен видит только этот порт: так правило
 * перерывов остаётся чистым и тестируется без Robolectric.
 */
interface StickinessSource {

    /** Полный usage-цикл: координатору нужны миллисекунды и стабильный cycleId для warningKey. */
    val usageSession: Flow<ContinuousUsageSession?>

    /** Сбросить счётчик — вызывается, когда перерыв доиграл свой таймер. */
    fun reset()

    /**
     * Заморозить накопление на активном перерыве или в окне расписания («Время учёбы» / «Время сна»).
     * Паузу с выключенным экраном при этом продолжаем считать — иначе ночной сброс не сработал бы.
     */
    fun pause(paused: Boolean)
}
