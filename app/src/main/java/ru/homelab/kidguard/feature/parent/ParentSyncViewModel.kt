package ru.homelab.kidguard.feature.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import ru.homelab.kidguard.feature.parent.alerts.ChildHealthChecker
import ru.homelab.kidguard.feature.parent.alerts.ParentAlertScheduler
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * Держит петлю синхронизации политики, пока открыт родительский режим (веха 4.3):
 * pull при входе (правки второго родителя) + push локальных правок с дебаунсом.
 * Живёт в viewModelScope — умирает вместе с родительским экраном, что и требуется.
 *
 * Здесь же включается наблюдение за здоровьем детских устройств: фоновая проверка (переживает
 * закрытие приложения) и немедленная — по сигналу с сервера, пока родитель в приложении.
 */
@HiltViewModel
class ParentSyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val childHealthChecker: ChildHealthChecker,
    alertScheduler: ParentAlertScheduler
) : ViewModel() {

    init {
        viewModelScope.launch { syncRepository.parentSyncLoop() }
        // Фоновая проверка ставится один раз и живёт дальше сама — в том числе когда приложение
        // закрыто. Именно она доносит до родителя, что контроль у ребёнка упал.
        alertScheduler.schedule()
        // Разовая проверка при входе: за время, пока приложение было закрыто, могло случиться
        // что угодно, а ждать до следующего запуска воркера незачем.
        viewModelScope.launch {
            runCatching { childHealthChecker.check(Instant.now()) }
                .onFailure { Timber.w(it, "Проверка здоровья при входе не удалась") }
        }
        observeHealthSignals()
    }

    /**
     * Сервер сообщает, что детское устройство прислало отчёт с ухудшением — проверяем сразу, не
     * дожидаясь очередного запуска фонового воркера (до 15 минут).
     */
    private fun observeHealthSignals() {
        viewModelScope.launch {
            syncRepository.childHealthChanged.collect {
                runCatching { childHealthChecker.check(Instant.now()) }
                    .onFailure { Timber.w(it, "Проверка здоровья по сигналу сервера не удалась") }
            }
        }
    }
}
