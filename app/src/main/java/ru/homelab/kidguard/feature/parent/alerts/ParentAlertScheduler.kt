package ru.homelab.kidguard.feature.parent.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ставит и снимает фоновую проверку здоровья детских устройств.
 *
 * Период — 15 минут, это минимум для периодических задач WorkManager. Задача уникальная и
 * переживает перезагрузку телефона; сеть в ограничениях, потому что без неё проверять нечего.
 */
@Singleton
class ParentAlertScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Планирует проверку, если её ещё нет. `KEEP` (а не `UPDATE`) — иначе каждый вход родителя в
     * приложение сбрасывал бы отсчёт периода, и на активно используемом телефоне проверка не
     * запускалась бы вовсе.
     */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<ChildHealthWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Выход из аккаунта: чужих детей больше не проверяем. */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "child_health_check"
        const val PERIOD_MINUTES = 15L
    }
}
