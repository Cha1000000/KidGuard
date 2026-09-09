package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.BonusGrant
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import java.time.LocalDate

/**
 * Набор приложений, открытых поверх исчерпанного общего лимита выданным родителем временем.
 * Фейки здесь изменяемые: проверяем не снимок, а переходы — выдали, израсходовали, выдали снова.
 */
class ObserveBonusPassesUseCaseTest {

    private val today = LocalDate.of(2026, 9, 9)
    private val bonuses = FakeBonusRepository()
    private val usage = FakeUsageRepository()
    private val useCase = ObserveBonusPassesUseCase(
        bonusRepository = bonuses,
        usageRepository = usage,
        currentDateProvider = object : CurrentDateProvider {
            override suspend fun today(): LocalDate = today
        }
    )

    @Test
    fun `без выданного времени пропусков нет`() = runTest {
        assertEquals(emptySet<String>(), useCase().first())
    }

    @Test
    fun `выданное и не потраченное время даёт пропуск`() = runTest {
        bonuses.addBonus(today, "com.messenger", 15)
        assertEquals(setOf("com.messenger"), useCase().first())
    }

    @Test
    fun `израсходованный пропуск исчезает`() = runTest {
        bonuses.addBonus(today, "com.messenger", 15)
        usage.addAppBonusSpentTime(today, "com.messenger", 15 * 60)
        assertEquals(emptySet<String>(), useCase().first())
    }

    @Test
    fun `повторная выдача возвращает пропуск - минуты суммируются`() = runTest {
        bonuses.addBonus(today, "com.messenger", 15)
        usage.addAppBonusSpentTime(today, "com.messenger", 15 * 60)
        bonuses.addBonus(today, "com.messenger", 15)
        assertEquals(setOf("com.messenger"), useCase().first())
    }

    @Test
    fun `отмена бонуса гасит пропуск`() = runTest {
        bonuses.addBonus(today, "com.messenger", 15)
        bonuses.clearBonus(today, "com.messenger")
        assertEquals(emptySet<String>(), useCase().first())
    }

    @Test
    fun `бонус телефона пропуском приложения не является`() = runTest {
        // Маркер "" — бонус на весь телефон: он снимает общую блокировку, а не открывает
        // отдельное приложение, и в набор пропусков попадать не должен.
        bonuses.addBonus(today, null, 30)
        assertEquals(emptySet<String>(), useCase().first())
    }

    @Test
    fun `пропуска считаются независимо по приложениям`() = runTest {
        bonuses.addBonus(today, "com.messenger", 15)
        bonuses.addBonus(today, "com.game", 15)
        usage.addAppBonusSpentTime(today, "com.game", 15 * 60)
        assertEquals(setOf("com.messenger"), useCase().first())
    }

    @Test
    fun `вчерашние выдачи сегодня не действуют`() = runTest {
        bonuses.addBonus(today.minusDays(1), "com.messenger", 15)
        assertEquals(emptySet<String>(), useCase().first())
    }

    private class FakeBonusRepository : BonusRepository {
        private val grants = MutableStateFlow<Map<Pair<LocalDate, String>, Int>>(emptyMap())

        override fun phoneBonusMinutes(date: LocalDate): Flow<Int> =
            grants.map { it[date to ""] ?: 0 }

        override fun appBonusMinutes(date: LocalDate): Flow<Map<String, Int>> = grants.map { all ->
            all.filterKeys { it.first == date && it.second.isNotEmpty() }
                .mapKeys { (key, _) -> key.second }
        }

        override suspend fun addBonus(date: LocalDate, packageName: String?, minutes: Int) {
            val key = date to (packageName ?: "")
            grants.value = grants.value + (key to ((grants.value[key] ?: 0) + minutes))
        }

        override suspend fun clearBonus(date: LocalDate, packageName: String?) {
            grants.value = grants.value - (date to (packageName ?: ""))
        }

        override fun observeAll(): Flow<List<BonusGrant>> = grants.map { all ->
            all.map { (key, minutes) -> BonusGrant(key.first, key.second, minutes) }
        }

        override suspend fun replaceAll(grants: List<BonusGrant>) = Unit
        override suspend fun deleteOlderThan(date: LocalDate) = Unit
    }

    private class FakeUsageRepository : UsageRepository {
        private val bonusSpent = MutableStateFlow<Map<Pair<LocalDate, String>, Int>>(emptyMap())

        override fun appBonusSpentByPackage(date: LocalDate): Flow<Map<String, Int>> =
            bonusSpent.map { all ->
                all.filterKeys { it.first == date }.mapKeys { (key, _) -> key.second }
            }

        override suspend fun addAppBonusSpentTime(date: LocalDate, packageName: String, seconds: Int) {
            val key = date to packageName
            bonusSpent.value = bonusSpent.value + (key to ((bonusSpent.value[key] ?: 0) + seconds))
        }

        override suspend fun resetAppBonusSpent(date: LocalDate, packageName: String?) {
            bonusSpent.value = bonusSpent.value.filterKeys {
                it.first != date || (packageName != null && it.second != packageName)
            }
        }

        override fun screenTimeSeconds(date: LocalDate): Flow<Int> = MutableStateFlow(0)
        override suspend fun addScreenTime(date: LocalDate, seconds: Int) = Unit
        override suspend fun setScreenTime(date: LocalDate, seconds: Int) = Unit
        override fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int> = MutableStateFlow(0)
        override fun appScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
        override suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int) = Unit
        override fun overrunSeconds(date: LocalDate): Flow<Int> = MutableStateFlow(0)
        override suspend fun addOverrunTime(date: LocalDate, seconds: Int) = Unit
        override fun appOverrunByPackage(date: LocalDate): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
        override fun appTotalScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
        override suspend fun addAppOverrunTime(date: LocalDate, packageName: String, seconds: Int) = Unit
        override suspend fun resetScreenTime(date: LocalDate) = Unit
        override suspend fun resetAppScreenTime(date: LocalDate) = Unit
    }
}
