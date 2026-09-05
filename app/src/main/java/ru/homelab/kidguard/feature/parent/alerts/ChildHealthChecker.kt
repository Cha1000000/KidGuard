package ru.homelab.kidguard.feature.parent.alerts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.homelab.kidguard.core.domain.repository.ChildAlertStore
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.usecase.childAlert
import ru.homelab.kidguard.core.domain.usecase.isQuietHours
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Спрашивает у сервера состояние детских устройств и уведомляет родителя о поломках контроля.
 *
 * Вынесено из [ChildHealthWorker], потому что вызывающих двое: фоновый воркер (приложение
 * закрыто) и WS-событие (приложение открыто — тогда родитель узнаёт сразу, без ожидания
 * следующего запуска воркера).
 */
@Singleton
class ChildHealthChecker @Inject constructor(
    private val childRepository: ChildRepository,
    private val alertStore: ChildAlertStore,
    private val notifier: ParentAlertNotifier
) {

    /**
     * Проверки приходят из трёх мест и могут совпасть по времени: периодическая задача
     * WorkManager выполняется сразу при постановке — то есть ровно в момент входа родителя, когда
     * стартует и разовая проверка. Без замка обе успевали прочитать один и тот же снимок и
     * показать по уведомлению об одной поломке.
     */
    private val mutex = Mutex()

    /** @return удалось ли получить состояние детей; `false` — сеть или сервер недоступны. */
    suspend fun check(now: Instant): Boolean = mutex.withLock {
        // Ночью не тревожим и снимок НЕ обновляем: иначе утреннее сравнение «было в порядке →
        // сломалось» не сработает, и ночная поломка останется незамеченной совсем.
        if (isQuietHours(now)) {
            Timber.tag(TAG).d("Ночь — тревоги отложены до утра")
            return true
        }
        val children = childRepository.listChildren().getOrElse { return false }
        // Снимок читаем один раз: внутри цикла это было бы чтение DataStore на каждого ребёнка.
        val previous = alertStore.previous()
        children.forEach { child ->
            val alert = childAlert(previous = previous[child.id], current = child, now = now)
                ?: return@forEach
            Timber.tag(TAG).w(
                "Контроль у %s сломан: %s",
                alert.childName,
                if (alert.silent) "устройство молчит" else alert.brokenPermissions.toString()
            )
            notifier.show(alert)
        }
        alertStore.save(children)
        return true
    }

    private companion object {
        const val TAG = "KidGuardParentAlert"
    }
}
