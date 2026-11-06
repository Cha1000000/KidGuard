package ru.homelab.kidguard.platform.overlay

import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.security.PinVerifyResult

/** Ключ ночного замка: доменной сессии у сна нет, замок всегда один. */
const val LOCK_KEY_NIGHT: String = "NIGHT"

/** Ключ замка перерыва привязан к устойчивой доменной сессии: смена сессии = смена замка. */
fun breakLockKey(sessionId: String): String = "BREAK:$sessionId"

/**
 * Всё, что нужно полноэкранному замку: оформление, тексты, deadline отсчёта, контакты и callbacks.
 *
 * [lockKey] — идентичность замка для [FullScreenLockOverlayManager.showOrUpdate]: повтор с тем же
 * ключом обновляет содержимое на месте (не сбрасывая набранные цифры PIN), а другой ключ атомарно
 * заменяет окно вместе с callbacks.
 *
 * [countdownEndsAtElapsedMs] — deadline в шкале elapsedRealtime (владелец отсчёта — доменная
 * сессия), null у ночного замка: ему отсчёт не нужен.
 */
class FullScreenLockModel(
    val lockKey: String,
    val appearance: LockAppearance,
    val title: String,
    val subtitle: String,
    val countdownEndsAtElapsedMs: Long?,
    val contacts: List<EmergencyContact>,
    val verifyPin: suspend (String) -> PinVerifyResult,
    val onUnlocked: () -> Unit,
    val onCall: (EmergencyContact) -> Unit
)
