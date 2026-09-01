package ru.homelab.kidguard.feature.parent

import kotlinx.coroutines.flow.first
import ru.homelab.kidguard.core.domain.model.UsageEntry
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Расход экранного времени ребёнка с сервера — общий для родительских экранов «Статистика» и
 * «Дневной лимит».
 *
 * Вынесен отдельно из-за [limitedSecondsByDate]: свернуть присланные ребёнком записи в «сколько
 * израсходовано под лимитом» неочевидно (нужны и итог дня, и перерасход — см. KDoc метода), и
 * второй экран не должен повторять это своей копией. Сам бюджет и остаток из этих секунд считает
 * доменная `dailyBudgetState` — та же, что рисует карточку на «Статистике».
 */
@Singleton
class ChildUsageProvider @Inject constructor(
    private val childRepository: ChildRepository,
    private val syncRepository: SyncRepository
) {

    /**
     * Расход активного ребёнка за последние [days] дней. `null` — активного ребёнка нет
     * (родитель ещё никого не завёл): для вызывающего это не ошибка, а «показывать нечего».
     */
    suspend fun loadActiveChildUsage(days: Int): Result<List<UsageEntry>?> = runCatching {
        val children = childRepository.listChildren().getOrThrow()
        val activeId = syncRepository.activeChildId.first()
        // Тот же выбор, что на «Статистике»: сохранённый активный ребёнок, а если его нет
        // (первый запуск, ребёнка удалили) — первый из списка.
        val child = children.firstOrNull { it.id == activeId } ?: children.firstOrNull()
            ?: return@runCatching null
        childRepository.getChildUsage(child.id, days).getOrThrow()
    }

    /**
     * Секунды, израсходовавшие дневной лимит, по датам.
     *
     * Ребёнок присылает израсходованный бюджет и перерасход **разными** записями: бюджетный
     * счётчик намеренно перестаёт расти после блокировки, чтобы выданный следом бонус не гасил
     * накрученное сверх лимита время. Родителю нужна их сумма — именно от неё считается остаток
     * и перерасход относительно бюджета дня.
     */
    fun limitedSecondsByDate(entries: List<UsageEntry>): Map<LocalDate, Int> = entries
        .filter { it.isTotal || it.isOverrun }
        .groupBy { it.date }
        .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.seconds } }
}
