package ru.homelab.kidguard.data.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.homelab.kidguard.data.network.DailyUsageBlockDto
import ru.homelab.kidguard.data.network.DailyUsageUnblockDto
import ru.homelab.kidguard.data.network.PolicyDocumentDto

/**
 * Снимок сравнения политики обязан меняться от ЛЮБОЙ правки документа — иначе правка не уедет на
 * сервер. 15.09.2026 из сравнения выпадала разблокировка дня: поля перечислялись вручную.
 */
class PolicyCanonicalJsonTest {

    private val json = Json

    private val base = PolicyDocumentDto(
        dailyLimits = mapOf("MONDAY" to 180),
        dailyUsageBlock = DailyUsageBlockDto("2026-09-15", 1_000L)
    )

    @Test
    fun `разблокировка дня меняет снимок`() {
        val unblocked = base.copy(dailyUsageUnblock = DailyUsageUnblockDto("2026-09-15", 2_000L, true))
        assertNotEquals(canonicalPolicyJson(json, base), canonicalPolicyJson(json, unblocked))
    }

    @Test
    fun `перестановка элементов снимок не меняет`() {
        val a = base.copy(whitelist = listOf("b", "a"), dailyLimits = mapOf("TUESDAY" to 60, "MONDAY" to 180))
        val b = base.copy(whitelist = listOf("a", "b"), dailyLimits = mapOf("MONDAY" to 180, "TUESDAY" to 60))
        assertEquals(canonicalPolicyJson(json, a), canonicalPolicyJson(json, b))
    }

    @Test
    fun `уже упорядоченный документ сериализуется целиком, без потерь`() {
        // Главная защита от повторения: если какое-то поле документа выпадет из сравнения, снимок
        // уже нормализованного документа разойдётся с его обычной сериализацией.
        val full = base.copy(
            whitelist = listOf("a", "b"),
            pinHash = "h",
            pinSalt = "s",
            blockGoogleSearch = true,
            studyScheduleEnabled = true,
            sleepScheduleEnabled = true,
            dailyUsageUnblock = DailyUsageUnblockDto("2026-09-15", 2_000L, false)
        )
        assertEquals(json.encodeToString(PolicyDocumentDto.serializer(), full), canonicalPolicyJson(json, full))
    }
}
