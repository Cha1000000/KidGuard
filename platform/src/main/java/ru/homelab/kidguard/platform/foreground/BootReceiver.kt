package ru.homelab.kidguard.platform.foreground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.Role
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * После перезагрузки устройства запускает foreground-сервис контроля — но только на детском
 * устройстве (роль CHILD). На родительском телефоне сервис не нужен.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (settingsRepository.role.first() == Role.CHILD) {
                    KidGuardForegroundService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
