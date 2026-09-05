package ru.homelab.kidguard.core.domain.model

import java.time.Instant

/**
 * Почему в прошлый раз умер процесс детского приложения.
 *
 * Зачем эта модель вообще появилась: на HiOS кольцевой буфер logcat всего 256 КБ и userspace-логи
 * сторонних приложений режутся — через десяток минут улик не остаётся, и после каждой смерти
 * контроля мы гадали. Система же хранит причины сама (`ActivityManager.getHistoricalProcessExitReasons`,
 * до 16 записей, переживает перезагрузку) и отдаёт их своему пакету без каких-либо разрешений.
 *
 * Первый же съём на телефоне ребёнка 05.09.2026 показал, что все шесть смертей за пять дней —
 * [TASK_MANAGER_STOP], а не вендорский киллер и не нехватка памяти, как мы предполагали.
 */
data class ProcessExitRecord(
    val at: Instant,
    val kind: ProcessExitKind,
    /** Текст системы (`fully stop … by user request` и подобное) — для диагностики. */
    val description: String,
    /** Сырые коды: пригодятся, если встретится причина, которой ещё нет в [ProcessExitKind]. */
    val rawReason: Int,
    val rawSubreason: Int
)

/**
 * Причина смерти в терминах, понятных родителю.
 *
 * Три способа «пользователь остановил» разделены намеренно — они означают совершенно разное и
 * лечатся по-разному. Различить их можно только по subreason: `reason` у всех трёх одинаковый
 * (`REASON_USER_REQUESTED`), и по нему одному вывод сделать нельзя.
 */
enum class ProcessExitKind {

    /** Кнопка «Остановить» в настройках приложения (`SUBREASON_FORCE_STOP`). */
    FORCE_STOP,

    /**
     * Остановка из диспетчера задач — «очистить всё» в списке последних приложений
     * (`SUBREASON_STOP_APP`). Именно так убивают контроль на телефоне ребёнка: и сам ребёнок
     * привычным жестом, и вендорский «бустер» HiOS. Лечится замком карточки в recents.
     */
    TASK_MANAGER_STOP,

    /**
     * Свайп одной карточки из списка последних (`SUBREASON_REMOVE_TASK`). Наш foreground-сервис
     * это переживает — проверено на телефоне ребёнка 18.07.2026.
     */
    REMOVE_TASK,

    /** Обновление APK. Штатная смерть, тревожить родителя незачем. */
    PACKAGE_UPDATED,

    /** Приложение упало само (`REASON_CRASH`, `REASON_CRASH_NATIVE`) — это наш баг. */
    CRASH,

    /** Не отвечало (`REASON_ANR`). */
    ANR,

    /** Система выгрузила из-за нехватки памяти. */
    LOW_MEMORY,

    /** Убит «замораживателем» процессов — вендорское поведение поверх AOSP. */
    FREEZER,

    /** Причина известна системе, но нам не интересна по отдельности. */
    OTHER,

    /** Причины нет или она незнакома этой версии приложения. */
    UNKNOWN;

    /**
     * Стоит ли говорить об этом родителю. Обновление APK и штатный выход — шум: контроль после них
     * поднимается сам, и уведомление о каждой установке только приучит родителя их игнорировать.
     */
    val worthReporting: Boolean
        get() = this != PACKAGE_UPDATED && this != UNKNOWN && this != OTHER
}

/**
 * Как именно пользователь остановил приложение — по тексту, которым это описала прошивка.
 *
 * Зачем эвристика по строке: у всех трёх способов один и тот же системный `reason`, а различающий
 * их `subReason` помечен `@hide`, и достать его рефлексией не даёт сама система (проверено на
 * TECNO KL6: `hiddenapi … api=blocked … denied`). Публичный `description` при этом достаточно
 * подробен: HiOS помечает очистку «в одно касание» как `cleanType:oneKeyClean`, а свайп одной
 * карточки — как `remove task`.
 *
 * Незнакомую формулировку сводим к [ProcessExitKind.FORCE_STOP]: «приложение остановили» верно для
 * любого из трёх случаев, теряется лишь подсказка, чем именно.
 */
fun userRequestedExitKind(description: String): ProcessExitKind {
    val text = description.lowercase()
    return when {
        text.contains(ONE_KEY_CLEAN_MARKER) -> ProcessExitKind.TASK_MANAGER_STOP
        text.contains(REMOVE_TASK_MARKER) -> ProcessExitKind.REMOVE_TASK
        else -> ProcessExitKind.FORCE_STOP
    }
}

/** Метка очистки «в одно касание» в описании от HiOS: `cleanType:oneKeyClean`. */
private const val ONE_KEY_CLEAN_MARKER = "onekeyclean"

/** Свайп одной карточки: описание начинается с `remove task`. */
private const val REMOVE_TASK_MARKER = "remove task"
