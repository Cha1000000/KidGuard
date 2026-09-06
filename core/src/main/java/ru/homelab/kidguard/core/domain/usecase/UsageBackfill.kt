package ru.homelab.kidguard.core.domain.usecase

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Что случилось на экране — по данным СИСТЕМНОЙ статистики использования, а не нашего
 * accessibility-сервиса. Набор намеренно узкий: только то, из чего складывается ответ «каким
 * приложением пользовались в этот момент».
 */
enum class UsageEventKind {

    /** Приложение вышло на передний план. */
    APP_FOREGROUND,

    /** Приложение ушло с переднего плана. */
    APP_BACKGROUND,

    /** Экран погас или телефон заблокировали — пользоваться перестали. */
    SCREEN_OFF
}

/** Одно событие системной статистики. */
data class RawUsageEvent(
    val at: Instant,
    val packageName: String,
    val kind: UsageEventKind
)

/** Отрезок, в течение которого приложение было на переднем плане. */
data class UsageInterval(
    val packageName: String,
    val from: Instant,
    val to: Instant
) {
    val duration: Duration get() = Duration.between(from, to)
}

/**
 * Свёртка системных событий в отрезки «этим приложением пользовались».
 *
 * Зачем это вообще нужно. Пока контроль на телефоне ребёнка убит (force-stop через «Очистить
 * всё» и подобное), наш собственный учёт стоит — время не считается вообще, и убить приложение
 * ребёнку прямо выгодно: 06.09.2026 так достались 101 минута Roblox сверх лимита. Система
 * же продолжает вести статистику сама, и доступ к ней (`GET_USAGE_STATS`) переживает force-stop —
 * в отличие от accessibility, который слетает. Значит пропущенное время можно досчитать задним
 * числом при следующем запуске, и выигрыш от обхода исчезает — независимо от того, каким именно
 * способом контроль убили.
 *
 * Отрезки обрезаются окном [from]..[to]: досчитываем строго тот провал, который не покрыт обычным
 * учётом, иначе время задвоится.
 *
 * События вне окна тоже важны и должны передаваться: [APP_FOREGROUND][UsageEventKind.APP_FOREGROUND]
 * до начала окна означает, что приложение УЖЕ было открыто, когда провал начался, — такой отрезок
 * учитывается с [from]. Ровно так выглядит обычный случай: контроль убили посреди игры.
 */
fun foldUsageEvents(
    events: List<RawUsageEvent>,
    from: Instant,
    to: Instant
): List<UsageInterval> {
    if (!from.isBefore(to)) return emptyList()

    val intervals = mutableListOf<UsageInterval>()
    var openPackage: String? = null
    var openedAt: Instant = from

    fun close(at: Instant) {
        val packageName = openPackage ?: return
        val start = maxOf(openedAt, from)
        val end = minOf(at, to)
        if (start.isBefore(end)) {
            intervals += UsageInterval(packageName, start, end)
        }
        openPackage = null
    }

    for (event in events.sortedBy { it.at }) {
        if (event.at.isAfter(to)) break
        when (event.kind) {
            UsageEventKind.APP_FOREGROUND -> {
                close(event.at)
                openPackage = event.packageName
                openedAt = event.at
            }
            // Закрываем только своё: система шлёт «ушло в фон» и по приложениям, которые к этому
            // моменту уже сменились другим, — чужой BACKGROUND не должен обрывать чужой отрезок.
            UsageEventKind.APP_BACKGROUND -> if (openPackage == event.packageName) close(event.at)
            UsageEventKind.SCREEN_OFF -> close(event.at)
        }
    }
    close(to)
    return intervals
}

/**
 * Разбивка отрезка по календарным датам в поясе [zone] — счётчики у нас дневные, а провал
 * контроля запросто переживает полночь (телефон убили вечером, открыли утром).
 *
 * Возвращает секунды по датам; даты без времени не попадают.
 */
fun splitByDate(interval: UsageInterval, zone: ZoneId): Map<LocalDate, Int> {
    val result = linkedMapOf<LocalDate, Int>()
    var cursor = interval.from
    while (cursor.isBefore(interval.to)) {
        val date = cursor.atZone(zone).toLocalDate()
        val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant()
        val chunkEnd = minOf(nextMidnight, interval.to)
        val seconds = Duration.between(cursor, chunkEnd).seconds.toInt()
        if (seconds > 0) {
            result[date] = (result[date] ?: 0) + seconds
        }
        cursor = chunkEnd
    }
    return result
}

/**
 * Секунды по паре «дата + пакет» для набора отрезков — то, что остаётся записать в счётчики.
 */
fun secondsByDateAndPackage(
    intervals: List<UsageInterval>,
    zone: ZoneId
): Map<Pair<LocalDate, String>, Int> {
    val result = linkedMapOf<Pair<LocalDate, String>, Int>()
    for (interval in intervals) {
        for ((date, seconds) in splitByDate(interval, zone)) {
            val key = date to interval.packageName
            result[key] = (result[key] ?: 0) + seconds
        }
    }
    return result
}
