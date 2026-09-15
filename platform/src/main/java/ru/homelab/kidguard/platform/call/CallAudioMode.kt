package ru.homelab.kidguard.platform.call

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Идёт ли звонок — по режиму аудио системы, без `READ_PHONE_STATE`.
 *
 * Режим аудио — единственный надёжный признак: окно звонилки, открывшееся ПОД нашим оверлеем,
 * сервис доступности не видит (система не шлёт о нём событие), а пакет звонилки на прошивках разный.
 *
 * @param includeRinging считать звонком и входящий вызов, который ещё звонит (`MODE_RINGTONE`).
 *   Ночному замку это не нужно — он скрывается на время разговора; PIN над списком последних
 *   должен уходить уже на звонок, иначе ребёнок не сможет ответить.
 */
fun Context.callAudioModeFlow(includeRinging: Boolean): Flow<Boolean> = callbackFlow {
    val audioManager = getSystemService(AudioManager::class.java)
    fun active(): Boolean = audioManager?.mode.let {
        it == AudioManager.MODE_IN_CALL ||
            it == AudioManager.MODE_IN_COMMUNICATION ||
            (includeRinging && it == AudioManager.MODE_RINGTONE)
    }
    trySend(active())
    val listener = AudioManager.OnModeChangedListener { trySend(active()) }
    audioManager?.addOnModeChangedListener(mainExecutor, listener)
    awaitClose { audioManager?.removeOnModeChangedListener(listener) }
}.distinctUntilChanged()
