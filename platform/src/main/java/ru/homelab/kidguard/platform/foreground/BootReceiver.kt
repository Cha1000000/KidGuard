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
 * Поднимает foreground-сервис контроля — но только на детском устройстве (роль CHILD), на
 * родительском телефоне он не нужен.
 *
 * Два повода, и оба означают одно: процесс только что стартовал с нуля, а сервис сам себя не
 * запустит.
 * 1. `BOOT_COMPLETED` — телефон перезагрузили.
 * 2. `MY_PACKAGE_REPLACED` — приложение обновили. Без этого после КАЖДОГО обновления (а из
 *    магазина они прилетают сами) контроль лежал бы, пока ребёнок не откроет приложение руками:
 *    единственное другое место, где стартует сервис, — детский экран. Наблюдалось вживую
 *    31.08.2026 при обновлении сборки на телефоне Олега.
 *
 * Оба броадкаста — protected: подделать их чужое приложение не может. Для обоих Android разрешает
 * поднимать foreground-сервис из фона (иначе Android 12+ отдал бы `ForegroundServiceStartNotAllowed`).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

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

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
