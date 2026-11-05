package ru.homelab.kidguard.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.homelab.kidguard.data.db.dao.BreakRulesWithHours
import ru.homelab.kidguard.data.db.entity.BreakRulesEntity

/**
 * Атомарность записи правил перерывов (план «исправление перерывов», этап 2, D18): наблюдатель
 * склеивает строку `break_rules` с таблицей `break_hour`, и пока запись идёт одной транзакцией,
 * он не должен увидеть промежуточную комбинацию вроде «режим HOURS + часов нет».
 *
 * Тест стрессовый: много чередующихся записей под живым коллектором. При раздельной записи
 * (две транзакции) промежуточное состояние почти гарантированно попадает в эмиссии.
 */
class PolicyDaoBreakRulesAtomicityTest {

    private lateinit var db: KidGuardDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, KidGuardDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun rulesEntity(mode: String) = BreakRulesEntity(
        enabled = true,
        mode = mode,
        intervalMinutes = if (mode == "INTERVAL") 45 else 0,
        durationMinutes = 10,
        message = "пауза"
    )

    @Test
    fun наблюдатель_не_видит_полуприменённые_правила() = runBlocking {
        val dao = db.policyDao()
        dao.replaceBreakRules(rulesEntity("INTERVAL"), emptyList())

        val emissions = mutableListOf<BreakRulesWithHours?>()
        val collector = launch {
            dao.breakRulesWithHours().collect { emissions.add(it) }
        }
        // Коллектор обязан успеть подписаться до серии записей, иначе тест не проверяет ничего.
        withTimeout(5_000) {
            while (emissions.isEmpty()) yield()
        }

        repeat(30) { i ->
            if (i % 2 == 0) {
                dao.replaceBreakRules(rulesEntity("HOURS"), listOf(900, 1080))
            } else {
                dao.replaceBreakRules(rulesEntity("INTERVAL"), emptyList())
            }
        }
        withTimeout(5_000) {
            while (emissions.size < 3) yield()
        }
        collector.cancelAndJoin()

        val inconsistent = emissions.filter { row ->
            val rules = row?.rules
            val hours = row?.hours.orEmpty().map { it.minuteOfDay }
            rules != null && (
                (rules.mode == "HOURS" && hours.isEmpty()) ||
                    (rules.mode == "INTERVAL" && hours.isNotEmpty())
                )
        }
        assertTrue(
            "наблюдатель увидел несогласованные комбинации: $inconsistent",
            inconsistent.isEmpty()
        )
        // Финальное состояние проверяем прямым чтением: эмиссии flow могут отставать
        // от последней записи на момент остановки коллектора, это не предмет теста.
        val final = dao.breakRulesWithHours().first()
        assertTrue(
            "после серии записей состояние обязано быть финальным (INTERVAL без часов)",
            final?.rules?.mode == "INTERVAL" && final.hours.isEmpty()
        )
    }
}
