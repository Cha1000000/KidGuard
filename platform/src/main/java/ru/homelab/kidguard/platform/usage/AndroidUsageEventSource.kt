package ru.homelab.kidguard.platform.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.domain.repository.UsageEventSource
import ru.homelab.kidguard.core.domain.usecase.RawUsageEvent
import ru.homelab.kidguard.core.domain.usecase.UsageEventKind
import ru.homelab.kidguard.platform.permissions.PermissionsManager
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UsageEventSource] поверх `UsageStatsManager`.
 *
 * Запрашиваем окно с запасом назад ([LOOKBEHIND_MS]): нам нужно не только то, что происходило
 * внутри провала, но и событие, которое его открыло. Иначе игра, которая шла ещё до убийства
 * контроля, не дала бы внутри окна ни одного `ACTIVITY_RESUMED` — и провал остался бы недосчитан
 * ровно в том случае, ради которого всё и затевалось.
 */
@Singleton
class AndroidUsageEventSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionsManager: PermissionsManager
) : UsageEventSource {

    override fun isAvailable(): Boolean =
        permissionsManager.isGranted(DevicePermission.USAGE_ACCESS)

    override fun events(from: Instant, to: Instant): List<RawUsageEvent> {
        if (!isAvailable()) {
            Timber.tag(TAG).d("Доступа к статистике использования нет — досчитывать нечем")
            return emptyList()
        }
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        return runCatching {
            val cursor = manager.queryEvents(from.toEpochMilli() - LOOKBEHIND_MS, to.toEpochMilli())
            buildList {
                val event = UsageEvents.Event()
                while (cursor.hasNextEvent()) {
                    cursor.getNextEvent(event)
                    kindOf(event.eventType)?.let { kind ->
                        add(
                            RawUsageEvent(
                                at = Instant.ofEpochMilli(event.timeStamp),
                                packageName = event.packageName.orEmpty(),
                                kind = kind
                            )
                        )
                    }
                }
            }
        }.onFailure { Timber.tag(TAG).w(it, "Не удалось прочитать статистику использования") }
            .getOrDefault(emptyList())
    }

    /**
     * Нас интересуют только смены переднего плана и погасший экран; всё остальное
     * (конфигурации, уведомления, режимы standby) для подсчёта времени шум.
     *
     * `KEYGUARD_SHOWN` учитываем наравне с выключением экрана: телефон в кармане с горящим
     * замком — это не использование.
     */
    private fun kindOf(eventType: Int): UsageEventKind? = when (eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.APP_FOREGROUND
        UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
            UsageEventKind.APP_BACKGROUND
        UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN ->
            UsageEventKind.SCREEN_OFF
        else -> null
    }

    private companion object {
        const val TAG = "KidGuardBackfill"

        /**
         * Насколько заглядываем назад за начало окна в поисках события, открывшего провал.
         * Шесть часов — компромисс: перекрывает любой реалистичный «убили вечером, открыли утром»
         * и при этом не заставляет систему поднимать сутки событий на каждый запуск.
         */
        const val LOOKBEHIND_MS = 6 * 60 * 60 * 1000L
    }
}
