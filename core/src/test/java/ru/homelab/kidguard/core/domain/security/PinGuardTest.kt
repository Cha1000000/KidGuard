package ru.homelab.kidguard.core.domain.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.PinProtection
import ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource
import ru.homelab.kidguard.core.domain.repository.PinAttemptsStore
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.DayOfWeek

class PinGuardTest {

    private val salt = PinHasher.generateSalt()
    private val correctPin = "1234"
    private val wrongPin = "9999"

    private class FakeAttemptsStore(private var value: Int = 0) : PinAttemptsStore {
        override suspend fun totalFailures(): Int = value
        override suspend fun increment(): Int = ++value
        override suspend fun reset() { value = 0 }
    }

    private class FakeElapsed(var nowMs: Long = 0L) : ElapsedTimeSource {
        override fun elapsedRealtimeMs(): Long = nowMs
    }

    // Минимальный PolicyRepository: PinGuard читает только pinProtection, остальное не трогает.
    private fun policyWith(protection: PinProtection?): FakePolicy = FakePolicy(protection)

    private fun guard(
        protection: PinProtection? = PinProtection(PinHasher.hash(correctPin, salt), salt),
        store: PinAttemptsStore = FakeAttemptsStore(),
        elapsed: ElapsedTimeSource = FakeElapsed()
    ) = PinGuard(policyWith(protection), store, elapsed)

    @Test
    fun `верный PIN — Success и счётчик обнулён`() = runTest {
        val store = FakeAttemptsStore(value = 3)
        val result = guard(store = store).verify(correctPin)
        assertEquals(PinVerifyResult.Success, result)
        assertEquals(0, store.totalFailures())
    }

    @Test
    fun `PIN не задан — NoPinSet, не мешаем`() = runTest {
        assertEquals(PinVerifyResult.NoPinSet, guard(protection = null).verify(wrongPin))
    }

    @Test
    fun `неверный PIN — Wrong с убывающим остатком попыток`() = runTest {
        val g = guard()
        assertEquals(PinVerifyResult.Wrong(attemptsLeft = 4), g.verify(wrongPin))
        assertEquals(PinVerifyResult.Wrong(attemptsLeft = 3), g.verify(wrongPin))
        assertEquals(PinVerifyResult.Wrong(attemptsLeft = 2), g.verify(wrongPin))
        assertEquals(PinVerifyResult.Wrong(attemptsLeft = 1), g.verify(wrongPin))
    }

    @Test
    fun `5-я неудача — блок на 60 секунд`() = runTest {
        val g = guard()
        repeat(4) { g.verify(wrongPin) }
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 60), g.verify(wrongPin))
    }

    @Test
    fun `10-я неудача — блок на 120 секунд`() = runTest {
        val g = guard(store = FakeAttemptsStore(value = 9))
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 120), g.verify(wrongPin))
    }

    @Test
    fun `15-я неудача и дальше — потолок 600 секунд`() = runTest {
        val g15 = guard(store = FakeAttemptsStore(value = 14))
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 600), g15.verify(wrongPin))

        // 4-я серия — по-прежнему 600, лестница не растёт.
        val g20 = guard(store = FakeAttemptsStore(value = 19))
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 600), g20.verify(wrongPin))
    }

    @Test
    fun `во время блокировки верный PIN тоже Blocked, с остатком времени`() = runTest {
        val elapsed = FakeElapsed(nowMs = 0L)
        val g = guard(store = FakeAttemptsStore(value = 4), elapsed = elapsed)
        assertEquals(PinVerifyResult.Blocked(60), g.verify(wrongPin))

        elapsed.nowMs = 47_000L  // прошло 47 с из 60
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 13), g.verify(correctPin))
    }

    @Test
    fun `после истечения блокировки верный PIN снова работает`() = runTest {
        val elapsed = FakeElapsed(nowMs = 0L)
        val store = FakeAttemptsStore(value = 4)
        val g = guard(store = store, elapsed = elapsed)
        g.verify(wrongPin)              // → Blocked(60)

        elapsed.nowMs = 60_000L         // блок истёк ровно
        assertEquals(PinVerifyResult.Success, g.verify(correctPin))
        assertEquals(0, store.totalFailures())
    }

    @Test
    fun `перевод системных часов не влияет — считаем по elapsedRealtime`() = runTest {
        // ElapsedTimeSource монотонен и от системных часов не зависит: если он не сдвинулся,
        // блокировка держится, сколько бы ребёнок ни крутил часы в настройках.
        val elapsed = FakeElapsed(nowMs = 1_000L)
        val g = guard(store = FakeAttemptsStore(value = 4), elapsed = elapsed)
        g.verify(wrongPin)
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 60), g.verify(correctPin))
    }
}

private class FakePolicy(private val protection: PinProtection?) : PolicyRepository {

    override val pinProtection: Flow<PinProtection?> = flowOf(protection)

    private fun unused(): Nothing = error("PinGuard не должен обращаться к этому члену политики")

    override val dailyLimits: Flow<DailyLimits> get() = unused()
    override val whitelist: Flow<Set<String>> get() = unused()
    override val appLimits: Flow<Map<String, Int>> get() = unused()
    override val blockedApps: Flow<Set<String>> get() = unused()

    override suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?) = unused()
    override suspend fun setAppLimit(packageName: String, minutes: Int?) = unused()
    override suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) = unused()
    override suspend fun setBlocked(packageName: String, blocked: Boolean) = unused()
    override suspend fun setPin(hash: String, salt: String) = unused()
    override suspend fun clearPin() = unused()
    override suspend fun replaceAll(
        dailyLimits: Map<DayOfWeek, Int>,
        appLimits: Map<String, Int>,
        whitelist: Set<String>,
        blockedApps: Set<String>,
        pinHash: String?,
        pinSalt: String?
    ) = unused()
}
