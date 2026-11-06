package ru.homelab.kidguard.core.domain.model

/**
 * Уже начавшийся перерыв как самостоятельное устойчивое состояние (план «исправление перерывов»,
 * этапы 2–4). Переживает Doze, погасший экран, звонок, PIN-разблокировку и смерть процесса:
 * остаток всегда вычисляется как `max(0, endsAt - now)` и ничем не продлевается.
 *
 * Двойная шкала времени намеренна: в пределах одной загрузки опора — монотонные elapsed-часы
 * (перевод системных часов не влияет), после reboot elapsed-счётчик обнуляется, и остаток
 * восстанавливается через wall-clock deadline. Пара [bootCount] + elapsed позволяет отличить
 * «та же загрузка» от «после перезагрузки».
 */
data class BreakSession(
    /** Устойчивый идентификатор сессии: завершение и отмена идемпотентны именно по нему. */
    val id: String,
    val mode: BreakMode,
    val startedAtElapsedMs: Long,
    val endsAtElapsedMs: Long,
    val startedAtWallMs: Long,
    val endsAtWallMs: Long,
    /** Системный BOOT_COUNT на момент старта; null — прошивка его не отдаёт, fallback по wall-clock. */
    val bootCount: Int?,
    /**
     * Ключ объединённого окна HOURS, к которому относится сессия (диагностика/трассировка);
     * у INTERVAL null. Дедупликацию после рестарта обеспечивает восстановление ACTIVE-сессии,
     * а не этот ключ.
     */
    val windowKey: String?
)

/**
 * Снимок накопления непрерывного использования (план, этап 3). Хранится, чтобы смерть процесса
 * не обнуляла уже накопленное «залипание»; единица — миллисекунды elapsed-часов.
 *
 * @param cycleId идентификатор usage-цикла: к нему привязывается предупреждение INTERVAL, чтобы
 *   плашка не показывалась повторно внутри одного цикла.
 * @param accumulatedMs накопленные миллисекунды активности текущего цикла.
 * @param idleSinceElapsedMs начало текущей неактивности (экран погашен/заблокирован); null — экран
 *   активен и накопление идёт.
 * @param lastObservedElapsedMs момент последнего измерения: дельта к текущим часам и есть
 *   фактически прошедшее время, а не константа тика.
 */
data class ContinuousUsageSession(
    val cycleId: String,
    val accumulatedMs: Long,
    val idleSinceElapsedMs: Long?,
    val lastObservedElapsedMs: Long
)
