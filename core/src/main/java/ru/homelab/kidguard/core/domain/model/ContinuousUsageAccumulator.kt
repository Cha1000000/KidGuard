package ru.homelab.kidguard.core.domain.model

import java.util.UUID

/**
 * Чистый накопитель непрерывного использования (план «исправление перерывов», этап 3).
 * Заменяет константное `+15 секунд на тик` фактическими дельтами монотонных elapsed-часов:
 * задержанный Doze-тик, событие экрана между тиками и восстановление после смерти процесса
 * считаются одним и тем же путём.
 *
 * Сам класс платформу не знает: часы (`SystemClock.elapsedRealtime`), состояние экрана и
 * персистентность подаёт вызывающий (в приложении — `StickinessTracker`), поэтому вся логика
 * проверяется JVM-тестами без Robolectric.
 *
 * Осознанные допущения плана: время полностью мёртвого процесса задним числом как экранное
 * не реконструируется (накопление при этом сохраняется), а reboot определяется по elapsed,
 * пошедшему назад, — цикл загрузку не переживает.
 */
class ContinuousUsageAccumulator(
    private val cycleIdGenerator: () -> String = { UUID.randomUUID().toString() }
) {

    /** Текущий цикл использования; null — накопление очищено (перерывы выключены/неприменимы). */
    var state: ContinuousUsageSession? = null
        private set

    /**
     * Восстановить снимок после смерти процесса. [screenActive] — состояние экрана сейчас.
     * Мёртвое время не досчитывается: накопление сохраняется, отсчёт продолжается от
     * [nowElapsedMs]. Пауза, начавшаяся до смерти, продолжается по реальному времени.
     */
    fun restore(snapshot: ContinuousUsageSession?, nowElapsedMs: Long, screenActive: Boolean) {
        state = when {
            snapshot == null -> null
            snapshot.lastObservedElapsedMs > nowElapsedMs -> null // elapsed назад — другая загрузка
            else -> snapshot.copy(
                lastObservedElapsedMs = nowElapsedMs,
                idleSinceElapsedMs = when {
                    screenActive -> null // пауза кончилась не позже текущего момента
                    else -> snapshot.idleSinceElapsedMs ?: nowElapsedMs
                }
            )
        }
    }

    /**
     * Зафиксировать наблюдение — тик или системное событие экрана.
     *
     * [paused] — накопление заморожено расписанием (паузу без экрана при этом считаем: ночью
     * именно она сбрасывает цикл); [countingAllowed] — перерывы INTERVAL применимы сейчас
     * (включены, заданы, день подходит); false очищает цикл, чтобы включение после долгого
     * использования не роняло замок мгновенно (D11). [resetAfterIdleMs] — пауза, засчитываемая
     * за состоявшийся перерыв (длительность перерыва).
     */
    fun observe(
        nowElapsedMs: Long,
        screenActive: Boolean,
        paused: Boolean,
        countingAllowed: Boolean,
        resetAfterIdleMs: Long
    ) {
        if (!countingAllowed) {
            state = null
            return
        }
        val current = state ?: run {
            state = newCycle(nowElapsedMs, screenActive)
            return
        }
        if (nowElapsedMs < current.lastObservedElapsedMs) {
            // Reboot между наблюдениями без восстановления: старый цикл недействителен.
            state = newCycle(nowElapsedMs, screenActive)
            return
        }
        val idleSince = current.idleSinceElapsedMs
        if (idleSince == null) {
            // Экран был активен: дельта — использование (если не заморожено расписанием).
            state = current.copy(
                accumulatedMs = if (paused) current.accumulatedMs
                else current.accumulatedMs + (nowElapsedMs - current.lastObservedElapsedMs),
                idleSinceElapsedMs = if (screenActive) null else nowElapsedMs,
                lastObservedElapsedMs = nowElapsedMs
            )
        } else {
            if (screenActive) {
                state = current.copy(idleSinceElapsedMs = null, lastObservedElapsedMs = nowElapsedMs)
            } else if (
                current.accumulatedMs > 0 &&
                resetAfterIdleMs > 0 &&
                nowElapsedMs - idleSince >= resetAfterIdleMs
            ) {
                // Пауза длиной с перерыв = состоявшийся отдых: новый цикл. На пустом цикле
                // не сбрасываем — иначе вечный покой плодил бы новые cycleId без смысла.
                state = newCycle(nowElapsedMs, screenActive)
            } else {
                state = current.copy(lastObservedElapsedMs = nowElapsedMs)
            }
        }
    }

    /** Внешний сброс (смена значимых правил, завершение перерыва): новый цикл с нуля. */
    fun reset(nowElapsedMs: Long, screenActive: Boolean) {
        state = newCycle(nowElapsedMs, screenActive)
    }

    /** Очистить цикл (перерывы выключены/неприменимы): следующее включение начнёт с нуля. */
    fun clear() {
        state = null
    }

    private fun newCycle(nowElapsedMs: Long, screenActive: Boolean) = ContinuousUsageSession(
        cycleId = cycleIdGenerator(),
        accumulatedMs = 0,
        idleSinceElapsedMs = if (screenActive) null else nowElapsedMs,
        lastObservedElapsedMs = nowElapsedMs
    )
}
