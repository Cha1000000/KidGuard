package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * Провайдер текущей даты с защитой от перевода времени назад (анти-отмотка): возвращаемая дата
 * никогда не «откатывается» назад относительно ранее виденной. Это не даёт ребёнку сбросить
 * дневной счётчик, переставив системную дату. Полноценная защита от обходов — веха 6.
 */
interface CurrentDateProvider {

    /** Текущий «день учёта» с учётом анти-отмотки (>= ранее виденной даты). */
    suspend fun today(): LocalDate
}

/**
 * Поток «дня учёта», эмитящий заново при смене суток.
 *
 * Зачем: детский foreground-сервис живёт неделями, и его контроллеры подписываются на состояние
 * лимита один раз. Если дату взять единожды при подписке, после полуночи поток продолжит читать
 * расход и лимит **вчерашнего** дня — вчерашний «лимит исчерпан» останется навсегда, и блокировки
 * (оверлей, VPN) будут держаться весь новый день, хотя счётчик уже обнулился. Проявляется только
 * на реальном устройстве, которое не перезапускают сутками (найдено на обкатке 22.07.2026).
 *
 * Тик раз в [DATE_TICK_MS]: смена суток отслеживается с точностью до минуты, чего с запасом
 * достаточно, а `distinctUntilChanged` гарантирует, что подписчики пересоберутся ровно один раз
 * за сутки.
 */
fun CurrentDateProvider.todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(today())
        delay(DATE_TICK_MS)
    }
}.distinctUntilChanged()

private const val DATE_TICK_MS = 60_000L
