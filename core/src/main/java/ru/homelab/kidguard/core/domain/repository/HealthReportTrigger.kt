package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Событийный триггер немедленной отправки heartbeat (watchdog, веха 6).
 *
 * Проблема, найденная на реальном телефоне: watchdog узнаёт о смене состояния разрешений только
 * раз в 15 минут (интервал [ru.homelab.kidguard.data.sync.SyncRepositoryImpl] `childSyncLoop`).
 * Родитель переустановил приложение, восстановил accessibility — но красная плашка «Не работает»
 * висела ещё ~10 минут, пока не пришёл следующий тик. Та же задержка бьёт и в обратную сторону:
 * о реальной поломке родитель узнаёт с опозданием до 15 минут.
 *
 * Источники события (accessibility-сервис в :platform, мастер разрешений в :app) дёргают
 * [requestNow], петля синхронизации в :data на это подписана и шлёт heartbeat немедленно —
 * в ДОПОЛНЕНИЕ к штатному 15-минутному циклу (его никто не убирает, он остаётся страховкой).
 *
 * Живёт в :core, а не в :data или :platform: по правилам модулей `:platform` и `:data` друг
 * друга не видят и зависят только от `:core`, а канал нужен обеим сторонам одновременно.
 *
 * Конкретный класс без интерфейса: реализация — чистый SharedFlow без побочных эффектов,
 * заводить абстракцию не над чем. `extraBufferCapacity = 1` + `DROP_OLDEST` — сигнал не
 * теряется, если подписчик на секунду отстал, но и не копится очередью: несколько быстрых
 * подряд вызовов `requestNow()` (например, `onServiceConnected` и следом `refresh()` в мастере)
 * схлопываются в один heartbeat.
 */
@Singleton
class HealthReportTrigger @Inject constructor() {

    private val _requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Поток сигналов «пора отправить heartbeat немедленно». */
    val requests: Flow<Unit> = _requests.asSharedFlow()

    /** Запросить немедленную отправку heartbeat (не блокирует и не бросает исключений). */
    fun requestNow() {
        _requests.tryEmit(Unit)
    }
}
