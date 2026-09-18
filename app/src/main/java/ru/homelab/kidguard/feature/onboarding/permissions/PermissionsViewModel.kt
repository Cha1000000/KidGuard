package ru.homelab.kidguard.feature.onboarding.permissions

import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.homelab.kidguard.core.domain.model.DevicePermission
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.repository.HealthReportTrigger
import ru.homelab.kidguard.core.domain.repository.RecentsLockAutomation
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import ru.homelab.kidguard.R
import ru.homelab.kidguard.platform.permissions.PermissionsManager
import javax.inject.Inject

/**
 * Управляет мастером выдачи разрешений: держит актуальные статусы и отдаёт интенты для перехода
 * в системные экраны. Статусы обновляются каждый раз при возврате на экран.
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionsManager: PermissionsManager,
    private val healthReportTrigger: HealthReportTrigger,
    private val settingsRepository: SettingsRepository,
    private val recentsLockAutomation: RecentsLockAutomation
) : ViewModel() {

    private val _autoLockRunning = MutableStateFlow(false)

    /** Идёт ли автоматическое закрепление карточки — на это время кнопка блокируется. */
    val autoLockRunning: StateFlow<Boolean> = _autoLockRunning.asStateFlow()

    private val _autoLockMessage = MutableStateFlow<Int?>(null)

    /** Текст итога последней попытки закрепления (ресурс строки), либо null. */
    val autoLockMessage: StateFlow<Int?> = _autoLockMessage.asStateFlow()

    /**
     * Закрепить карточку автоматически: сервис откроет список последних и нажмёт пункт замка.
     * Успех (в том числе «уже закреплена») сразу проставляет отметку — родителю не надо жать ещё раз.
     */
    fun autoLockRecentsCard() {
        if (_autoLockRunning.value) return
        _autoLockRunning.value = true
        _autoLockMessage.value = null
        viewModelScope.launch {
            val result = recentsLockAutomation.request()
            if (result == RecentsLockAutomation.Result.Success || result == RecentsLockAutomation.Result.AlreadyLocked) {
                settingsRepository.setRecentsLockConfirmed(true)
                healthReportTrigger.requestNow()
            }
            _autoLockMessage.value = when (result) {
                RecentsLockAutomation.Result.Success -> R.string.recents_lock_auto_done
                RecentsLockAutomation.Result.AlreadyLocked -> R.string.recents_lock_auto_already
                RecentsLockAutomation.Result.Failed -> R.string.recents_lock_auto_failed
                RecentsLockAutomation.Result.NoService -> R.string.recents_lock_auto_no_service
            }
            _autoLockRunning.value = false
        }
    }

    /**
     * Подтвердил ли родитель закрепление карточки KidGuard в списке последних.
     *
     * Статус не проверяется программно — состояние закрепления система наружу не отдаёт. Это отметка
     * родителя: она уходит в отчёт о здоровье, чтобы в родительском приложении было видно, сделан ли шаг.
     */
    val recentsLockConfirmed: StateFlow<Boolean> = settingsRepository.recentsLockConfirmed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setRecentsLockConfirmed(confirmed: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRecentsLockConfirmed(confirmed)
            healthReportTrigger.requestNow()
        }
    }

    private val _statuses = MutableStateFlow(emptyStatuses())
    val statuses: StateFlow<Map<DevicePermission, Boolean>> = _statuses.asStateFlow()

    init {
        refresh()
    }

    /**
     * Перепроверить статусы всех разрешений (вызывать при возврате на экран, в т.ч. из системных
     * настроек). Помимо accessibility (её ловит `onServiceConnected`), тут выдаются overlay/battery/
     * notification/vpn — сигналим watchdog'у не ждать 15-минутный тик и для них тоже.
     */
    fun refresh() {
        _statuses.value = DevicePermission.entries.associateWith(permissionsManager::isGranted)
        healthReportTrigger.requestNow()
    }

    /** Интент для выдачи конкретного разрешения (или null, если не требуется). */
    fun grantIntent(permission: DevicePermission): Intent? =
        permissionsManager.grantIntent(permission)

    /**
     * Интент в вендорный менеджер автозапуска (HiOS/MIUI/EMUI…) с фолбэком на «О приложении».
     * Не [DevicePermission] — статус автозапуска программно не проверяется ни у одного вендора,
     * поэтому это карточка-инструкция, а не шаг со статусом.
     */
    fun autostartIntent(): Intent = permissionsManager.autostartIntent()

    private fun emptyStatuses(): Map<DevicePermission, Boolean> =
        DevicePermission.entries.associateWith { false }
}
