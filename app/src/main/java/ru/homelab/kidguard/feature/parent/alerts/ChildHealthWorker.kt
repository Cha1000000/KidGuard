package ru.homelab.kidguard.feature.parent.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.Instant

/**
 * Фоновая проверка: жив ли контроль на телефонах детей. Основа уведомлений родителю без FCM —
 * приложение просыпается само (в том числе закрытое и после перезагрузки), спрашивает сервер и,
 * если контроль упал, показывает уведомление.
 *
 * Планировщик — [ParentAlertScheduler]. Минимальный период у периодических задач WorkManager —
 * 15 минут, поэтому это «почти push»: мгновенную доставку при открытом приложении даёт WS-событие,
 * а воркер ловит случай, когда родитель приложение закрыл.
 */
@HiltWorker
class ChildHealthWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val childHealthChecker: ChildHealthChecker
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Сеть могла отвалиться — не повод считать, что с ребёнком беда: просто повторим позже.
        return if (childHealthChecker.check(Instant.now())) {
            Result.success()
        } else {
            Timber.tag("KidGuardParentAlert").d("Проверка не удалась, повторим на следующем запуске")
            Result.retry()
        }
    }
}
