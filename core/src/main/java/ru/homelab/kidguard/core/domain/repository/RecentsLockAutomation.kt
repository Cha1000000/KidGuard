package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Команда «закрепи карточку KidGuard в списке последних» accessibility-сервису и её итог.
 *
 * Зачем автоматика: закреплённая карточка — единственная защита от кнопки «Очистить всё», которая
 * работает всегда (PIN-замок система прячет на время поворота экрана). Родителю неудобно искать
 * скрытый жест в списке последних, а шаг обязательный.
 *
 * Почему только по кнопке родителя, а не сама: закрепление делается «руками» — сервис открывает
 * список последних, долгим нажатием вызывает меню карточки и жмёт пункт замка. На чужой оболочке
 * меню может выглядеть иначе, и трогать чужой интерфейс в фоне нельзя. По кнопке это осознанное
 * действие родителя, а при любой заминке сервис просто уходит на рабочий стол (см. [Result.Failed]).
 *
 * Живёт в :core по той же причине, что и [HealthReportTrigger]: канал нужен и `:app` (мастер), и
 * `:platform` (сервис), а друг друга эти модули не видят.
 */
@Singleton
class RecentsLockAutomation @Inject constructor() {

    /** Итог попытки. [AlreadyLocked] — карточка была закреплена до нас, это тоже успех. */
    enum class Result { Success, AlreadyLocked, Failed, NoService }

    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Запросы к сервису — на них подписан accessibility-сервис. */
    val requests: Flow<Unit> = _requests.asSharedFlow()

    /** Сервис сообщает итог сюда. */
    suspend fun report(result: Result) {
        _results.emit(result)
    }

    /**
     * Запросить закрепление и дождаться итога. [Result.NoService] — сервис не ответил за отведённое
     * время: скорее всего, accessibility-разрешение не выдано и команду некому исполнить.
     */
    suspend fun request(timeoutMillis: Long = DEFAULT_TIMEOUT_MS): Result {
        _requests.emit(Unit)
        return withTimeoutOrNull(timeoutMillis) { _results.first() } ?: Result.NoService
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 12_000L
    }
}
