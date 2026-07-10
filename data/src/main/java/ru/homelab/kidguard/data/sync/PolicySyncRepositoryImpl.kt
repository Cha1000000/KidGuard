package ru.homelab.kidguard.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.PolicySyncRepository
import ru.homelab.kidguard.data.auth.AuthLocalStore
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.PolicyApi
import ru.homelab.kidguard.data.network.PolicyDocumentDto
import ru.homelab.kidguard.data.network.PutPolicyRequest
import timber.log.Timber
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "kidguard_sync")

@Singleton
class PolicySyncRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val policyApi: PolicyApi,
    private val childrenApi: ChildrenApi,
    private val policyRepository: PolicyRepository,
    private val authLocalStore: AuthLocalStore
) : PolicySyncRepository {

    private object Keys {
        /**
         * Канонизированный JSON последнего синхронизированного документа — защита от пинг-понга:
         * push уходит только когда локальная политика реально отличается от последней
         * синхронизированной; pull-apply тоже обновляет снапшот, иначе применение серверного
         * документа тут же триггерило бы обратный push того же содержимого.
         */
        val LAST_SYNCED_SNAPSHOT = stringPreferencesKey("last_synced_snapshot")
        val LAST_SYNCED_AT = stringPreferencesKey("last_synced_at")
    }

    private val json = Json

    // --- Петли ---------------------------------------------------------------------------------

    @OptIn(FlowPreview::class)
    override suspend fun parentSyncLoop() {
        // Разовый pull при входе: подхватить правки второго родителя (LWW — сервер прав).
        runCatching { pullAndApply(resolveParentChildId() ?: return@runCatching) }
            .onFailure { Timber.tag(TAG).w(it, "Стартовый pull родителя не удался") }

        // Дальше — наблюдаем локальные правки и пушим с дебаунсом. combine эмитит и после
        // pull-apply, но pushIfChanged сравнит со снапшотом и промолчит.
        combine(
            policyRepository.dailyLimits,
            policyRepository.appLimits,
            policyRepository.whitelist
        ) { _, _, _ -> Unit }
            .debounce(PUSH_DEBOUNCE_MS)
            .collect {
                runCatching {
                    val childId = resolveParentChildId() ?: return@collect
                    pushIfChanged(childId)
                }.onFailure { Timber.tag(TAG).w(it, "Push политики не удался (повторим при следующей правке)") }
            }
    }

    override suspend fun childSyncLoop() {
        while (currentCoroutineContext().isActive) {
            val childId = authLocalStore.pairedChildId()
            if (childId != null) {
                runCatching { pullAndApply(childId) }
                    .onFailure { Timber.tag(TAG).w(it, "Pull политики не удался (повторим через интервал)") }
            }
            delay(CHILD_PULL_INTERVAL_MS)
        }
    }

    // --- Pull / Push ----------------------------------------------------------------------------

    /** Забирает серверный документ и применяет в Room, если он новее уже применённого. */
    private suspend fun pullAndApply(childId: Int) {
        val response = policyApi.getPolicy(childId)
        val data = response.data ?: return // политики на сервере ещё нет
        if (response.updatedAt != null && response.updatedAt == lastSyncedAt()) return // уже применяли

        policyRepository.replaceAll(
            dailyLimits = data.dailyLimits.mapNotNull { (key, minutes) ->
                runCatching { DayOfWeek.valueOf(key) to minutes }.getOrNull()
            }.toMap(),
            appLimits = data.appLimits,
            whitelist = data.whitelist.toSet()
        )
        saveSyncedState(canonicalJson(data), response.updatedAt)
        Timber.tag(TAG).d("Политика применена из сервера (updatedAt=%s)", response.updatedAt)
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

    /** Активный ребёнок родителя — первый в списке (селектор для нескольких детей отложен). */
    private suspend fun resolveParentChildId(): Int? =
        childrenApi.listChildren().children.firstOrNull()?.id

    private suspend fun currentLocalDocument(): PolicyDocumentDto = PolicyDocumentDto(
        dailyLimits = policyRepository.dailyLimits.first().minutesByDay
            .mapKeys { it.key.name },
        appLimits = policyRepository.appLimits.first(),
        whitelist = policyRepository.whitelist.first().toList()
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
            whitelist = document.whitelist.sorted()
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
