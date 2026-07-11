package ru.homelab.kidguard.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.homelab.kidguard.core.domain.model.BonusGrant
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.data.auth.AuthLocalStore
import ru.homelab.kidguard.data.network.BonusEntryDto
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.PolicyApi
import ru.homelab.kidguard.data.network.PolicyDocumentDto
import ru.homelab.kidguard.data.network.PutPolicyRequest
import ru.homelab.kidguard.data.network.UsageApi
import ru.homelab.kidguard.data.network.UsageBatchRequest
import ru.homelab.kidguard.data.network.UsageEntryDto
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "kidguard_sync")

@Singleton
class SyncRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val policyApi: PolicyApi,
    private val childrenApi: ChildrenApi,
    private val usageApi: UsageApi,
    private val policyRepository: PolicyRepository,
    private val bonusRepository: BonusRepository,
    private val usageRepository: UsageRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val authLocalStore: AuthLocalStore,
    private val policySocket: PolicySocket
) : SyncRepository {

    private object Keys {
        /**
         * Канонизированный JSON последнего синхронизированного документа — защита от пинг-понга:
         * push уходит только когда локальная политика реально отличается от последней
         * синхронизированной; pull-apply тоже обновляет снапшот, иначе применение серверного
         * документа тут же триггерило бы обратный push того же содержимого.
         */
        val LAST_SYNCED_SNAPSHOT = stringPreferencesKey("last_synced_snapshot")
        val LAST_SYNCED_AT = stringPreferencesKey("last_synced_at")

        /** Выбранный родителем активный ребёнок (веха 4.5); null — выбор ещё не делался. */
        val ACTIVE_CHILD_ID = intPreferencesKey("active_child_id")
    }

    private val json = Json

    // --- Петли ---------------------------------------------------------------------------------

    @OptIn(FlowPreview::class)
    override suspend fun parentSyncLoop() = coroutineScope {
        // Разовый pull при входе: подхватить правки второго родителя (LWW — сервер прав).
        runCatching { pullAndApply(resolveParentChildId() ?: return@runCatching) }
            .onFailure { Timber.tag(TAG).w(it, "Стартовый pull родителя не удался") }

        // Push-канал: правка вторым родителем прилетает без перезахода (веха 4.6).
        launch {
            policySocket.events().collect { changedChildId ->
                runCatching {
                    if (changedChildId == activeChildId.first()) pullAndApply(changedChildId)
                }.onFailure { Timber.tag(TAG).w(it, "Pull по WS-сигналу не удался") }
            }
        }

        // Наблюдаем локальные правки (включая бонусы) и пушим с дебаунсом. combine эмитит и после
        // pull-apply, но pushIfChanged сравнит со снапшотом и промолчит.
        combine(
            policyRepository.dailyLimits,
            policyRepository.appLimits,
            policyRepository.whitelist,
            bonusRepository.observeAll()
        ) { _, _, _, _ -> Unit }
            .debounce(PUSH_DEBOUNCE_MS)
            .collect {
                runCatching {
                    val childId = resolveParentChildId() ?: return@collect
                    pushIfChanged(childId)
                }.onFailure { Timber.tag(TAG).w(it, "Push политики не удался (повторим при следующей правке)") }
            }
    }

    override val activeChildId: Flow<Int?> =
        context.syncDataStore.data.map { it[Keys.ACTIVE_CHILD_ID] }

    /**
     * Переключение активного ребёнка. Порядок важен: сперва тянем и применяем политику нового
     * ребёнка (обновляя снапшот — дебаунс-push после replaceAll сравнит и промолчит), и только
     * при успехе сохраняем выбор. Если pull упал — выбор не меняется, политика старого ребёнка
     * не может уехать новому.
     */
    override suspend fun switchActiveChild(childId: Int): Result<Unit> = runCatching {
        val response = policyApi.getPolicy(childId)
        // У нового ребёнка политики может ещё не быть — тогда локальный кэш очищается.
        val data = response.data ?: PolicyDocumentDto(
            dailyLimits = emptyMap(),
            appLimits = emptyMap(),
            whitelist = emptyList()
        )
        applyDocument(data)
        context.syncDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNCED_SNAPSHOT] = canonicalJson(data)
            if (response.updatedAt != null) {
                prefs[Keys.LAST_SYNCED_AT] = response.updatedAt
            } else {
                prefs.remove(Keys.LAST_SYNCED_AT)
            }
            prefs[Keys.ACTIVE_CHILD_ID] = childId
        }
        Timber.tag(TAG).d("Активный ребёнок переключён на %d", childId)
    }

    override suspend fun childSyncLoop() = coroutineScope {
        // Push-канал: политика/бонус применяются почти мгновенно (веха 4.6);
        // периодический pull ниже остаётся страховкой на случай долгого разрыва WS.
        launch {
            policySocket.events().collect { changedChildId ->
                runCatching {
                    if (changedChildId == authLocalStore.pairedChildId()) pullAndApply(changedChildId)
                }.onFailure { Timber.tag(TAG).w(it, "Pull по WS-сигналу не удался") }
            }
        }

        while (currentCoroutineContext().isActive) {
            val childId = authLocalStore.pairedChildId()
            if (childId != null) {
                runCatching { pullAndApply(childId) }
                    .onFailure { Timber.tag(TAG).w(it, "Pull политики не удался (повторим через интервал)") }
                runCatching { pushUsage(childId) }
                    .onFailure { Timber.tag(TAG).w(it, "Отправка статистики не удалась (повторим через интервал)") }
            }
            delay(CHILD_PULL_INTERVAL_MS)
        }
    }

    /**
     * Отправляет статистику за сегодня и вчера (вчера — дослать хвост дня после полуночи).
     * Значения АБСОЛЮТНЫЕ (накопленные за день из Room) — сервер перезаписывает, повтор безопасен.
     */
    private suspend fun pushUsage(childId: Int) {
        val today = currentDateProvider.today()
        val entries = buildList {
            for (date in listOf(today.minusDays(1), today)) {
                val total = usageRepository.screenTimeSeconds(date).first()
                if (total > 0) add(UsageEntryDto(date.toString(), packageName = "", seconds = total))
                usageRepository.appScreenTimeByPackage(date).first().forEach { (pkg, seconds) ->
                    if (seconds > 0) add(UsageEntryDto(date.toString(), packageName = pkg, seconds = seconds))
                }
            }
        }
        if (entries.isEmpty()) return
        usageApi.sendUsage(childId, UsageBatchRequest(entries))
        Timber.tag(TAG).d("Статистика отправлена (%d записей)", entries.size)
    }

    // --- Pull / Push ----------------------------------------------------------------------------

    /** Забирает серверный документ и применяет в Room, если он новее уже применённого. */
    private suspend fun pullAndApply(childId: Int) {
        val response = policyApi.getPolicy(childId)
        val data = response.data ?: return // политики на сервере ещё нет
        if (response.updatedAt != null && response.updatedAt == lastSyncedAt()) return // уже применяли

        applyDocument(data)
        saveSyncedState(canonicalJson(data), response.updatedAt)
        Timber.tag(TAG).d("Политика применена из сервера (updatedAt=%s)", response.updatedAt)
    }

    /** Целиком заменяет локальную политику (включая бонусы) содержимым серверного документа. */
    private suspend fun applyDocument(data: PolicyDocumentDto) {
        policyRepository.replaceAll(
            dailyLimits = data.dailyLimits.mapNotNull { (key, minutes) ->
                runCatching { DayOfWeek.valueOf(key) to minutes }.getOrNull()
            }.toMap(),
            appLimits = data.appLimits,
            whitelist = data.whitelist.toSet()
        )
        bonusRepository.replaceAll(
            data.bonuses.mapNotNull { dto ->
                runCatching { BonusGrant(LocalDate.parse(dto.date), dto.packageName, dto.minutes) }
                    .getOrNull()
            }
        )
    }

    /** Пушит локальную политику, только если она отличается от последнего синхронизированного снапшота. */
    private suspend fun pushIfChanged(childId: Int) {
        val document = currentLocalDocument()
        val snapshot = canonicalJson(document)
        if (snapshot == lastSyncedSnapshot()) return

        val response = policyApi.putPolicy(childId, PutPolicyRequest(document))
        saveSyncedState(snapshot, response.updatedAt)
        Timber.tag(TAG).d("Политика отправлена на сервер (updatedAt=%s)", response.updatedAt)
    }

    // --- Вспомогательное -------------------------------------------------------------------------

    /**
     * Активный ребёнок родителя: сохранённый выбор, если такой ребёнок ещё есть в списке;
     * иначе первый из списка (выбор при этом сохраняется — «дефолт по умолчанию»).
     */
    private suspend fun resolveParentChildId(): Int? {
        val children = childrenApi.listChildren().children
        if (children.isEmpty()) return null

        val savedId = activeChildId.first()
        if (savedId != null && children.any { it.id == savedId }) return savedId

        val fallbackId = children.first().id
        context.syncDataStore.edit { it[Keys.ACTIVE_CHILD_ID] = fallbackId }
        return fallbackId
    }

    private suspend fun currentLocalDocument(): PolicyDocumentDto = PolicyDocumentDto(
        dailyLimits = policyRepository.dailyLimits.first().minutesByDay
            .mapKeys { it.key.name },
        appLimits = policyRepository.appLimits.first(),
        whitelist = policyRepository.whitelist.first().toList(),
        // Бонусы датированы «на сегодня»: прошедшие дни в документ не тащим.
        bonuses = bonusRepository.observeAll().first()
            .filter { it.date == currentDateProvider.today() }
            .map { BonusEntryDto(it.date.toString(), it.packageName, it.minutes) }
    )

    /**
     * Стабильное строковое представление документа для сравнения содержимого: map/list
     * приводятся к отсортированному порядку, чтобы перестановка ключей не выглядела изменением.
     */
    private fun canonicalJson(document: PolicyDocumentDto): String = json.encodeToString(
        PolicyDocumentDto.serializer(),
        PolicyDocumentDto(
            dailyLimits = document.dailyLimits.toSortedMap(),
            appLimits = document.appLimits.toSortedMap(),
            whitelist = document.whitelist.sorted(),
            bonuses = document.bonuses.sortedWith(compareBy({ it.date }, { it.packageName }))
        )
    )

    private suspend fun lastSyncedSnapshot(): String? =
        context.syncDataStore.data.first()[Keys.LAST_SYNCED_SNAPSHOT]

    private suspend fun lastSyncedAt(): String? =
        context.syncDataStore.data.first()[Keys.LAST_SYNCED_AT]

    private suspend fun saveSyncedState(snapshot: String, updatedAt: String?) {
        context.syncDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNCED_SNAPSHOT] = snapshot
            if (updatedAt != null) prefs[Keys.LAST_SYNCED_AT] = updatedAt
        }
    }

    private companion object {
        const val TAG = "KidGuardSync"
        const val PUSH_DEBOUNCE_MS = 2_000L
        const val CHILD_PULL_INTERVAL_MS = 15L * 60 * 1000
    }
}
