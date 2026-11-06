package ru.homelab.kidguard.platform.schedule

import ru.homelab.kidguard.core.domain.model.BreakState
import ru.homelab.kidguard.core.domain.model.ScheduleState

/** Какой полноэкранный замок должен быть на экране прямо сейчас. */
sealed interface FullScreenLockSelection {

    /** Замка нет (или он скрыт звонком / снятием PIN-ом). */
    data object Hidden : FullScreenLockSelection

    /** Ночной замок «Времени сна». */
    data object Night : FullScreenLockSelection

    /** Замок перерыва; [sessionId] связывает замок с устойчивой доменной сессией. */
    data class Break(val sessionId: String) : FullScreenLockSelection
}

/**
 * Чистый выбор замка из состояний расписания, перерыва, звонка и PIN-флагов — вынесен из
 * [FullScreenLockController], чтобы приоритеты проверялись JVM-тестами без Android.
 *
 * Приоритеты (порядок важен):
 * 1. звонок или setup-grace — замка нет, иначе он накрыл бы вызов, который сам же и предложил;
 * 2. **сон бьёт перерыв**: активная сессия продолжается под расписанием, но на экране ночной замок;
 * 3. «Время учёбы» тоже скрывает замок перерыва (сессия продолжается фоново): мягкий блок учёбы
 *    рисует свой экран, два блокирующих UI одновременно быть не должно;
 * 4. активный перерыв;
 * 5. PIN-снятие до гашения экрана скрывает соответствующий замок (флаг передаёт вызывающий).
 */
object FullScreenLockReducer {

    fun select(
        scheduleState: ScheduleState,
        breakState: BreakState,
        callActive: Boolean,
        callGraceActive: Boolean,
        sleepUnlocked: Boolean,
        breakUnlocked: Boolean
    ): FullScreenLockSelection = when {
        callActive || callGraceActive -> FullScreenLockSelection.Hidden
        scheduleState is ScheduleState.Sleep ->
            if (sleepUnlocked) FullScreenLockSelection.Hidden else FullScreenLockSelection.Night
        scheduleState is ScheduleState.Study -> FullScreenLockSelection.Hidden
        breakState is BreakState.Active ->
            if (breakUnlocked) FullScreenLockSelection.Hidden else FullScreenLockSelection.Break(breakState.sessionId)
        else -> FullScreenLockSelection.Hidden
    }
}
