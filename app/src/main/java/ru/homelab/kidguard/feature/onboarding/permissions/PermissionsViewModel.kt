package ru.homelab.kidguard.feature.onboarding.permissions

import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.platform.permissions.PermissionsManager
import javax.inject.Inject

/**
 * Управляет мастером выдачи разрешений: держит актуальные статусы и отдаёт интенты для перехода
 * в системные экраны. Статусы обновляются каждый раз при возврате на экран.
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionsManager: PermissionsManager
) : ViewModel() {

    private val _statuses = MutableStateFlow(emptyStatuses())
    val statuses: StateFlow<Map<DevicePermission, Boolean>> = _statuses.asStateFlow()

    init {
        refresh()
    }

    /** Перепроверить статусы всех разрешений (вызывать при возврате на экран). */
    fun refresh() {
        _statuses.value = DevicePermission.entries.associateWith(permissionsManager::isGranted)
    }

    /** Интент для выдачи конкретного разрешения (или null, если не требуется). */
    fun grantIntent(permission: DevicePermission): Intent? =
        permissionsManager.grantIntent(permission)

    private fun emptyStatuses(): Map<DevicePermission, Boolean> =
        DevicePermission.entries.associateWith { false }
}
