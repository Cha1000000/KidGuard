package ru.homelab.kidguard.feature.parent.alerts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.homelab.kidguard.core.domain.repository.AlertSettingsRepository
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
    private val alertSettings: AlertSettingsRepository,
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
        // Флаги каналов — тоже один раз и до цикла, по той же причине.
        val pushFlags = alertSettings.pushFlags()
        children.forEach { child ->
            val alert = childAlert(previous = previous[child.id], current = child, now = now)
                ?: return@forEach
            Timber.tag(TAG).w(
                "Контроль у %s сломан: %s",
                alert.childName,
                if (alert.silent) "устройство молчит" else alert.brokenPermissions.toString()
            )
            // Родитель мог отписаться от шторки по этому ребёнку (экран «Оповещения»). Фильтр
            // стоит здесь, а не на сервере: WS-событие лишь будит клиента «сходи проверь», и одна
            // эта проверка накрывает сразу все три пути пробуждения — воркер, WS и вход в
            // приложение.
            //
            // Сравнение именно с `false`: отсутствие ребёнка в карте означает «неизвестно» (сервер
            // старый, кэш пуст) и трактуется как «показывать». Молчать можно только тогда, когда
            // родитель ЯВНО этого попросил.
            if (pushFlags[child.id] == false) {
                Timber.tag(TAG).d("Уведомления о %s выключены родителем — не показываю", alert.childName)
                return@forEach
            }
            notifier.show(alert)
        }
        // Снимок сохраняем для ВСЕХ детей, включая тех, чьи уведомления выключены: иначе, включив
        // канал обратно, родитель получил бы тревогу о поломке, случившейся при выключенном
        // канале, как о свежей.
        alertStore.save(children)
        return true
    }

    private companion object {
        const val TAG = "KidGuardParentAlert"
    }
}
