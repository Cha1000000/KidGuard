package ru.homelab.kidguard.core.domain.repository

import ru.homelab.kidguard.core.domain.model.ProcessExitRecord

/**
 * История смертей процесса детского приложения. Реализация — в :platform, поверх
 * `ActivityManager.getHistoricalProcessExitReasons`.
 */
interface ProcessExitReader {

    /**
     * Последние смерти, свежие первыми. Пустой список — система ничего не помнит (первый запуск
     * после установки) либо запись недоступна.
     *
     * @param limit сколько записей запросить; система хранит не больше 16 на процесс.
     */
    fun recent(limit: Int = DEFAULT_LIMIT): List<ProcessExitRecord>

    companion object {
        const val DEFAULT_LIMIT = 8
    }
}
