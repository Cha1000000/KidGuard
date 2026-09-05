package ru.homelab.kidguard.platform.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.core.domain.model.ProcessExitKind
import ru.homelab.kidguard.core.domain.model.ProcessExitRecord
import ru.homelab.kidguard.core.domain.model.userRequestedExitKind
import ru.homelab.kidguard.core.domain.repository.ProcessExitReader
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Читает системную историю смертей процесса.
 *
 * Разрешений не требует: `getHistoricalProcessExitReasons` без ограничений отдаёт данные о СВОЁМ
 * пакете (DUMP нужен только для чужого uid). История переживает перезагрузку — то есть даёт
 * пост-мортем даже там, где logcat давно вытеснен (на HiOS буфер 256 КБ, это десяток минут).
 */
@Singleton
class AndroidProcessExitReader @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ProcessExitReader {

    override fun recent(limit: Int): List<ProcessExitRecord> = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java)
            ?: return@runCatching emptyList()
        // pid = 0 — «любые процессы пакета», а не только текущий.
        manager.getHistoricalProcessExitReasons(context.packageName, 0, limit).map { it.toRecord() }
    }.onFailure {
        Timber.tag(TAG).w(it, "Не удалось прочитать историю смертей процесса")
    }.getOrDefault(emptyList())

    private fun ApplicationExitInfo.toRecord(): ProcessExitRecord {
        val text = description.orEmpty()
        return ProcessExitRecord(
            at = Instant.ofEpochMilli(timestamp),
            kind = exitKind(reason, text),
            description = text,
            rawReason = reason,
            // subReason недоступен (см. exitKind) — храним заглушку, чтобы модель осталась честной.
            rawSubreason = -1
        )
    }

    /**
     * `REASON_USER_REQUESTED` покрывает три разных события — кнопку «Остановить» в настройках,
     * очистку списка последних приложений и свайп одной карточки. Различает их только `subReason`,
     * а он `@hide`: попытка достать его рефлексией на боевом телефоне отвергается системой
     * (`hiddenapi: … api=blocked … denied`, проверено на TECNO KL6, Android 14).
     *
     * Зато `description` — публичный, и прошивка пишет туда достаточно: очистка «в одно касание»
     * помечается `cleanType:oneKeyClean`, свайп карточки — `remove task`. Именно `oneKeyClean`
     * и убивал контроль на телефоне ребёнка (05.09.2026, тем же нажатием выгрузило ещё два
     * приложения). Если формулировка на другой прошивке иная — честно падаем в общее
     * [ProcessExitKind.FORCE_STOP]: «приложение остановили» верно для любого из трёх случаев.
     */
    private fun exitKind(reason: Int, description: String): ProcessExitKind = when (reason) {
        ApplicationExitInfo.REASON_USER_REQUESTED -> userRequestedExitKind(description)

        ApplicationExitInfo.REASON_PACKAGE_UPDATED,
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> ProcessExitKind.PACKAGE_UPDATED

        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE -> ProcessExitKind.CRASH

        ApplicationExitInfo.REASON_ANR -> ProcessExitKind.ANR
        ApplicationExitInfo.REASON_LOW_MEMORY -> ProcessExitKind.LOW_MEMORY
        ApplicationExitInfo.REASON_FREEZER -> ProcessExitKind.FREEZER
        ApplicationExitInfo.REASON_USER_STOPPED -> ProcessExitKind.FORCE_STOP
        ApplicationExitInfo.REASON_EXIT_SELF -> ProcessExitKind.OTHER
        ApplicationExitInfo.REASON_UNKNOWN -> ProcessExitKind.UNKNOWN
        else -> ProcessExitKind.OTHER
    }

    private companion object {
        const val TAG = "KidGuardExitInfo"
    }
}
