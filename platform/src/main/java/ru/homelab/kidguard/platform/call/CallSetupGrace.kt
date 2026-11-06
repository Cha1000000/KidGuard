package ru.homelab.kidguard.platform.call

/**
 * Окно «grace» между нажатием экстренного контакта и фактическим переключением аудио-режима
 * в `IN_CALL`: система поднимает вызов не мгновенно, и без этого окна тик контроллера вернул бы
 * замок поверх набора.
 *
 * Контракт намеренно узкий: grace отсчитывается ТОЛЬКО от реального действия звонка
 * ([callStartedAtMs] != null). Пока звонок не начинался, grace неактивен — иначе в первые
 * секунды после загрузки (`elapsedRealtime` мал) замок скрывался бы сам по себе (дефект D16).
 *
 * Чистая функция без Android — проверяется JVM-тестами.
 */
object CallSetupGrace {

    /**
     * @param callStartedAtMs момент старта звонка в elapsed-часах; `null` — звонка не было.
     * @param nowMs текущий elapsed-момент.
     * @param graceMs длительность окна.
     */
    fun isActive(callStartedAtMs: Long?, nowMs: Long, graceMs: Long): Boolean =
        callStartedAtMs != null && nowMs - callStartedAtMs < graceMs
}
