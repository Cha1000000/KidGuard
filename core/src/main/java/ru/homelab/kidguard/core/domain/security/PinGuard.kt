package ru.homelab.kidguard.core.domain.security

import kotlinx.coroutines.flow.first
import ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource
import ru.homelab.kidguard.core.domain.repository.PinAttemptsStore
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственная точка проверки родительского PIN (веха 6). Её зовут и экран разрешений детского
 * меню, и PIN-оверлей системных экранов (6.2) — так логика попыток существует в одном
 * экземпляре и не может разъехаться между двумя дверьми.
 *
 * Защита от перебора — лестница: 5 попыток, затем блок 60 с, ещё 5 — 120 с, дальше 600 с
 * потолком. Полный перебор 10000 комбинаций становится многодневным.
 *
 * Момент разблокировки живёт ТОЛЬКО в памяти и считается по [ElapsedTimeSource]:
 * * системные часы не годятся — ребёнок переведёт время вперёд и снимет блок;
 * * на диск класть нельзя — после перезагрузки `elapsedRealtime` обнуляется, сохранённый момент
 *   окажется «в будущем», и устройство запрётся навсегда.
 *
 * Цена решения: перезагрузка снимает таймер. Но счётчик неудач переживает её, поэтому ребёнок
 * быстро упирается в потолок 600 с (записано в `docs/known-limits-and-bypasses.md`).
 */
@Singleton
class PinGuard @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val attemptsStore: PinAttemptsStore,
    private val elapsedTimeSource: ElapsedTimeSource
) {

    private var blockedUntilElapsedMs: Long = 0L

    suspend fun verify(pin: String): PinVerifyResult {
        val protection = policyRepository.pinProtection.first() ?: return PinVerifyResult.NoPinSet

        val now = elapsedTimeSource.elapsedRealtimeMs()
        if (now < blockedUntilElapsedMs) {
            return PinVerifyResult.Blocked(secondsLeft = secondsLeft(now))
        }

        if (PinHasher.verify(pin, protection.salt, protection.hash)) {
            attemptsStore.reset()
            blockedUntilElapsedMs = 0L
            return PinVerifyResult.Success
        }

        val failures = attemptsStore.increment()
        val attemptInSeries = failures % ATTEMPTS_PER_SERIES
        if (attemptInSeries != 0) {
            return PinVerifyResult.Wrong(attemptsLeft = ATTEMPTS_PER_SERIES - attemptInSeries)
        }

        val blockMs = blockMsForSeries(failures / ATTEMPTS_PER_SERIES)
        blockedUntilElapsedMs = now + blockMs
        return PinVerifyResult.Blocked(secondsLeft = (blockMs / 1000).toInt())
    }

    /** Округляем вверх: показывать «0 с» и не пускать выглядело бы поломкой. */
    private fun secondsLeft(now: Long): Int {
        val leftMs = blockedUntilElapsedMs - now
        return ((leftMs + 999) / 1000).toInt()
    }

    private fun blockMsForSeries(series: Int): Long = when (series) {
        1 -> FIRST_BLOCK_MS
        2 -> SECOND_BLOCK_MS
        else -> MAX_BLOCK_MS
    }

    private companion object {
        const val ATTEMPTS_PER_SERIES = 5
        const val FIRST_BLOCK_MS = 60_000L
        const val SECOND_BLOCK_MS = 120_000L
        const val MAX_BLOCK_MS = 600_000L
    }
}
