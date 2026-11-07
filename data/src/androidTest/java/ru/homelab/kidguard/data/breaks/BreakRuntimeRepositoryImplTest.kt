package ru.homelab.kidguard.data.breaks

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakSession
import ru.homelab.kidguard.core.domain.model.ContinuousUsageSession
import ru.homelab.kidguard.data.db.KidGuardDatabase

/**
 * Контракт runtime-хранилища перерывов (план «исправление перерывов», этап 2). На настоящем
 * SQLite, а не моке DAO: смысл тестов — атомарность транзакций, которую мок не проверяет.
 */
class BreakRuntimeRepositoryImplTest {

    private lateinit var db: KidGuardDatabase
    private lateinit var repository: BreakRuntimeRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, KidGuardDatabase::class.java).build()
        repository = BreakRuntimeRepositoryImpl(db.breakRuntimeDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun session(
        id: String,
        mode: BreakMode = BreakMode.INTERVAL,
        endsAtElapsedMs: Long = 600_000,
        windowKey: String? = null
    ) = BreakSession(
        id = id,
        mode = mode,
        startedAtElapsedMs = 0,
        endsAtElapsedMs = endsAtElapsedMs,
        startedAtWallMs = 1_000_000,
        endsAtWallMs = 1_600_000,
        bootCount = 7,
        windowKey = windowKey
    )

    private fun usage(cycleId: String, accumulatedMs: Long) = ContinuousUsageSession(
        cycleId = cycleId,
        accumulatedMs = accumulatedMs,
        idleSinceElapsedMs = null,
        lastObservedElapsedMs = 5_000
    )

    @Test
    fun сохранённая_сессия_читается_активной() = runBlocking {
        repository.saveSession(session("s1", mode = BreakMode.HOURS, windowKey = "2026-11-05T15:00"))

        val loaded = repository.loadActiveSession()

        assertNotNull(loaded)
        assertEquals(session("s1", mode = BreakMode.HOURS, windowKey = "2026-11-05T15:00"), loaded)
    }

    @Test
    fun завершение_снимает_сессию_и_сбрасывает_usage_атомарно() = runBlocking {
        repository.saveSession(session("s1"))
        repository.saveUsageSnapshot(usage("cycle-1", accumulatedMs = 42_000))

        repository.completeBreakAndResetUsage("s1")

        assertNull(repository.loadActiveSession())
        assertNull("завершение перерыва обязано сбросить usage-счётчик", repository.loadUsageSnapshot())
    }

    @Test
    fun повторное_завершение_той_же_сессии_не_трогает_новый_usage_цикл() = runBlocking {
        repository.saveSession(session("s1"))
        repository.saveUsageSnapshot(usage("cycle-1", accumulatedMs = 42_000))
        repository.completeBreakAndResetUsage("s1")

        // Следующий цикл уже начался, и тут приходит запоздавшее повторное завершение старой сессии.
        repository.saveUsageSnapshot(usage("cycle-2", accumulatedMs = 5_000))
        repository.completeBreakAndResetUsage("s1")

        assertEquals(
            "повторное завершение старой сессии не должно сбрасывать новый цикл",
            usage("cycle-2", accumulatedMs = 5_000),
            repository.loadUsageSnapshot()
        )
    }

    @Test
    fun завершение_несуществующей_сессии_ничего_не_ломает() = runBlocking {
        repository.saveUsageSnapshot(usage("cycle-1", accumulatedMs = 10_000))

        repository.completeBreakAndResetUsage("no-such-session")

        assertEquals(usage("cycle-1", accumulatedMs = 10_000), repository.loadUsageSnapshot())
    }

    @Test
    fun отмена_снимает_сессию_не_трогая_usage() = runBlocking {
        repository.saveSession(session("s1"))
        repository.saveUsageSnapshot(usage("cycle-1", accumulatedMs = 42_000))

        repository.cancelSession("s1")

        assertNull(repository.loadActiveSession())
        assertEquals(
            "отмена (смена правил) — не отдых: накопление сохраняется",
            usage("cycle-1", accumulatedMs = 42_000),
            repository.loadUsageSnapshot()
        )
    }

    @Test
    fun отменённая_сессия_не_читается_активной() = runBlocking {
        repository.saveSession(session("s1"))
        repository.cancelSession("s1")
        repository.saveSession(session("s2", endsAtElapsedMs = 900_000))

        val loaded = repository.loadActiveSession()

        assertEquals("s2", loaded?.id)
        assertTrue("старая сессия не должна воскресать", loaded?.id != "s1")
    }
}
