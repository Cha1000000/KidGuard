package ru.homelab.kidguard.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.homelab.kidguard.core.domain.model.BlockedSite
import ru.homelab.kidguard.core.domain.model.BonusGrant
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakRules
import ru.homelab.kidguard.core.domain.model.DailyUsageBlock
import ru.homelab.kidguard.core.domain.model.DailyUsageReset
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.PolicySnapshot
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.TimeWindow
import ru.homelab.kidguard.core.domain.model.shouldApplyBlock
import ru.homelab.kidguard.core.domain.model.shouldApplyReset
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.DeviceHealthSource
import ru.homelab.kidguard.core.domain.repository.HealthReportTrigger
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.data.auth.AuthLocalStore
import ru.homelab.kidguard.data.network.AppsApi
import ru.homelab.kidguard.data.network.BlockedSiteDto
import ru.homelab.kidguard.data.network.BonusEntryDto
import ru.homelab.kidguard.data.network.BreakRulesDto
import ru.homelab.kidguard.data.network.ChildAppDto
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.DeviceHealthApi
import ru.homelab.kidguard.data.network.EmergencyContactDto
import ru.homelab.kidguard.data.network.TimeWindowDto
import ru.homelab.kidguard.data.network.DailyUsageBlockDto
import ru.homelab.kidguard.data.network.DailyUsageResetDto
import ru.homelab.kidguard.data.network.DeviceHealthDto
import ru.homelab.kidguard.data.network.DeviceHealthRequest
import ru.homelab.kidguard.data.network.PolicyApi
import ru.homelab.kidguard.data.network.PolicyDocumentDto
import ru.homelab.kidguard.data.network.PutAppsRequest
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
    private val appsApi: AppsApi,
    private val deviceHealthApi: DeviceHealthApi,
    private val installedAppsSource: InstalledAppsSource,
    private val deviceHealthSource: DeviceHealthSource,
    private val policyRepository: PolicyRepository,
    private val bonusRepository: BonusRepository,
    private val usageRepository: UsageRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val authLocalStore: AuthLocalStore,
    private val policySocket: PolicySocket,
    private val healthReportTrigger: HealthReportTrigger
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

        /** Снапшот последнего отправленного списка приложений устройства (веха 4.1). */
        val LAST_SENT_APPS = stringPreferencesKey("last_sent_apps")

        /**
         * `issuedAt` последнего применённого на ребёнке маркера сброса дневного лимита —
         * идемпотентный ключ, чтобы тот же маркер не обнулял usage повторно на каждом pull.
         */
        val LAST_USAGE_RESET_AT = longPreferencesKey("last_usage_reset_at")

        /** `issuedAt` последнего применённого маркера блокировки на сегодня — идемпотентный ключ. */
        val LAST_USAGE_BLOCK_AT = longPreferencesKey("last_usage_block_at")
    }

    private val json = Json

    // --- Петли ---------------------------------------------------------------------------------

    @OptIn(FlowPreview::class)
    override suspend fun parentSyncLoop() = coroutineScope {
        // Разовый pull при входе: подхватить правки второго родителя (LWW — сервер прав).
        runCatching { pullAndApply(resolveParentChildId() ?: return@runCatching) }
            .onFailure { Timber.tag(TAG).w(it, "Стартовый pull родителя не удался") }

        // Push-канал: правка вторым родителем и привязка устройства прилетают без перезахода.
        launch {
            policySocket.events().collect { event ->
                when (event) {
                    is WsEvent.PolicyChanged -> runCatching {
                        if (event.childId == activeChildId.first()) pullAndApply(event.childId)
                    }.onFailure { Timber.tag(TAG).w(it, "Pull по WS-сигналу не удался") }

                    is WsEvent.ChildPaired -> childPairedEvents.tryEmit(event.childId)
                }
            }
        }

        // Наблюдаем локальные правки (включая бонусы и сайты) и пушим с дебаунсом. combine эмитит и
        // после pull-apply, но pushIfChanged сравнит со снапшотом и промолчит.
        // combine() с типизированными флоу ограничен 5 аргументами — остальные (bonuses,
        // blockedSites, blockGoogleSearch) добавляем отдельными combine() поверх, чтобы не
        // переходить на нетипизированный vararg-Array вариант.
        combine(
            policyRepository.dailyLimits,
            policyRepository.appLimits,
            policyRepository.whitelist,
            policyRepository.blockedApps,
            policyRepository.pinProtection
        ) { _, _, _, _, _ -> Unit }
            .combine(bonusRepository.observeAll()) { _, _ -> Unit }
            .combine(policyRepository.blockedSites) { _, _ -> Unit }
            .combine(policyRepository.blockGoogleSearch) { _, _ -> Unit }
            .combine(policyRepository.studySchedule) { _, _ -> Unit }
            .combine(policyRepository.sleepSchedule) { _, _ -> Unit }
            .combine(policyRepository.emergencyContacts) { _, _ -> Unit }
            .combine(policyRepository.breakRules) { _, _ -> Unit }
            .combine(policyRepository.dailyUsageReset) { _, _ -> Unit }
            .combine(policyRepository.dailyUsageBlock) { _, _ -> Unit }
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

    // replay=0: событие интересно только открытым сейчас подписчикам (вкладка «Дети»),
    // extraBufferCapacity — чтобы tryEmit из петли не терялся при медленном подписчике.
    private val childPairedEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    override val childPaired: Flow<Int> = childPairedEvents

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
            whitelist = emptyList(),
            blockedApps = emptyList(),
            blockedSites = emptyList(),
            blockGoogleSearch = false
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
            policySocket.events().collect { event ->
                if (event !is WsEvent.PolicyChanged) return@collect
                runCatching {
                    if (event.childId == authLocalStore.pairedChildId()) pullAndApply(event.childId)
                }.onFailure { Timber.tag(TAG).w(it, "Pull по WS-сигналу не удался") }
            }
        }

        // Немедленный heartbeat по сигналу (веха 6, задержка до 15 мин на реальном телефоне):
        // accessibility-сервис и мастер разрешений дёргают HealthReportTrigger при восстановлении
        // разрешения, чтобы родитель не ждал следующего тика while-цикла ниже. Сам 15-минутный
        // цикл остаётся как есть — страховка на случай пропущенного сигнала.
        launch {
            healthReportTrigger.requests.collect {
                if (authLocalStore.pairedChildId() == null) return@collect
                runCatching { pushHealth() }
                    .onFailure { Timber.tag(TAG).w(it, "Немедленный heartbeat не удался") }
            }
        }

        while (currentCoroutineContext().isActive) {
            val childId = authLocalStore.pairedChildId()
            if (childId != null) {
                runCatching { pullAndApply(childId) }
                    .onFailure { Timber.tag(TAG).w(it, "Pull политики не удался (повторим через интервал)") }
                runCatching { pushUsage(childId) }
                    .onFailure { Timber.tag(TAG).w(it, "Отправка статистики не удалась (повторим через интервал)") }
                runCatching { pushInstalledApps(childId) }
                    .onFailure { Timber.tag(TAG).w(it, "Отправка списка приложений не удалась (повторим через интервал)") }
                runCatching { pushHealth() }
                    .onFailure { Timber.tag(TAG).w(it, "Отправка heartbeat не удалась (повторим через интервал)") }
            }
            delay(CHILD_PULL_INTERVAL_MS)
        }
    }

    /**
     * Heartbeat: «я жив + вот моё здоровье» (watchdog, веха 6). Шлём на КАЖДОМ тике, в отличие от
     * списка приложений: важна не только смена флагов, но и сам факт доставки — по молчанию
     * родитель узнаёт, что сервис убит целиком (вендором, очисткой данных, force-stop) и доложить
     * о себе уже не может. Поэтому «отправлять только при изменении» здесь было бы ошибкой.
     *
     * childId не нужен — сервер берёт его из device-токена.
     */
    private suspend fun pushHealth() {
        val health = deviceHealthSource.current()
        deviceHealthApi.sendHealth(
            DeviceHealthRequest(
                DeviceHealthDto(
                    accessibility = health.accessibility,
                    overlay = health.overlay,
                    deviceAdmin = health.deviceAdmin,
                    vpn = health.vpn,
                    batteryOptimization = health.batteryOptimization
                )
            )
        )
        Timber.tag(TAG).d("Heartbeat отправлен, всё в порядке: %s", health.isHealthy)
    }

    /**
     * Публикует объединённый список приложений устройства (веха «системные приложения в пикерах»):
     * запускаемые ∪ реально использованные (включая системные без launcher-иконки за сегодня/вчера) —
     * родитель выбирает из него лимиты/белый список/запреты. Отправка только при изменении списка
     * (снапшот в DataStore); снапшот включает флаги isSystem/isRisky, поэтому их смена тоже
     * триггерит переотправку.
     */
    private suspend fun pushInstalledApps(childId: Int) {
        val today = currentDateProvider.today()
        val usedPackages = buildSet {
            for (date in listOf(today.minusDays(1), today)) {
                usageRepository.appScreenTimeByPackage(date).first().forEach { (pkg, seconds) ->
                    if (seconds > 0) add(pkg)
                }
            }
        }
        val apps = installedAppsSource.publishableApps(usedPackages)
            .map { ChildAppDto(it.packageName, it.label, it.iconBase64, it.isSystem, it.isRisky) }
        val snapshot = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ChildAppDto.serializer()),
            apps.sortedBy { it.packageName }
        )
        if (snapshot == context.syncDataStore.data.first()[Keys.LAST_SENT_APPS]) return
        appsApi.putApps(childId, PutAppsRequest(apps))
        context.syncDataStore.edit { it[Keys.LAST_SENT_APPS] = snapshot }
        Timber.tag(TAG).d("Список приложений отправлен (%d)", apps.size)
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

    /**
     * Стирает `kidguard_sync` целиком (выход/удаление аккаунта): активного ребёнка, снапшот и
     * метки последнего синка — иначе после повторного входа под другим родителем pull-сравнение
     * опиралось бы на чужое состояние.
     */
    override suspend fun clearLocalSyncState() {
        context.syncDataStore.edit { it.clear() }
    }

    // --- Pull / Push ----------------------------------------------------------------------------

    /** Забирает серверный документ и применяет в Room, если он новее уже применённого. */
    private suspend fun pullAndApply(childId: Int) {
        val response = policyApi.getPolicy(childId)
        val data = response.data ?: return // политики на сервере ещё нет
        if (response.updatedAt != null && response.updatedAt == lastSyncedAt()) return // уже применяли

        applyDocument(data)
        applyDailyUsageReset(childId, data)
        applyDailyBlock(data)
        saveSyncedState(canonicalJson(data), response.updatedAt)
        Timber.tag(TAG).d("Политика применена из сервера (updatedAt=%s)", response.updatedAt)
    }

    /**
     * Применяет маркер сброса дневного лимита — ТОЛЬКО в детском пути [pullAndApply], не в
     * [switchActiveChild]: обнулять usage должен ребёнок, применивший команду родителя, а не
     * родительское устройство при переключении между детьми. Идемпотентно — тот же маркер
     * (issuedAt не новее последнего применённого) повторно usage не трогает.
     */
    private suspend fun applyDailyUsageReset(childId: Int, data: PolicyDocumentDto) {
        val marker = data.dailyUsageReset
            ?.let { runCatching { DailyUsageReset(LocalDate.parse(it.date), it.issuedAt) }.getOrNull() }
        val today = currentDateProvider.today()
        val lastApplied = context.syncDataStore.data.first()[Keys.LAST_USAGE_RESET_AT] ?: 0L
        if (shouldApplyReset(marker, today, lastApplied)) {
            usageRepository.resetScreenTime(today)
            usageRepository.resetAppScreenTime(today)
            // Сервер хранит usage через UPSERT и сам старые строки не удаляет — говорим ему явно
            // очистить сегодня, иначе экран «Статистика» у родителя покажет доисбросные цифры.
            runCatching { usageApi.clearUsage(childId, today.toString()) }
                .onFailure { Timber.tag(TAG).w(it, "Не удалось очистить серверную статистику за день") }
            context.syncDataStore.edit { it[Keys.LAST_USAGE_RESET_AT] = marker!!.issuedAt }
            Timber.tag(TAG).d("Дневной лимит сброшен родителем (issuedAt=%d)", marker!!.issuedAt)
        }
    }

    /**
     * Применяет маркер блокировки на сегодня — ТОЛЬКО в детском пути (как [applyDailyUsageReset]).
     * Обнуляет доступное время: выставляет расход = дневному лимиту + бонусу за сегодня (остаток
     * становится 0). Идемпотентно по `issuedAt`. Отменяется обычным сбросом (обнулит расход) или
     * бонусом (добавит время сверху). Серверный DELETE не нужен: `pushUsage` сам дошлёт новое
     * (большее) значение обычным UPSERT.
     */
    private suspend fun applyDailyBlock(data: PolicyDocumentDto) {
        val marker = data.dailyUsageBlock
            ?.let { runCatching { DailyUsageBlock(LocalDate.parse(it.date), it.issuedAt) }.getOrNull() }
        val today = currentDateProvider.today()
        val lastApplied = context.syncDataStore.data.first()[Keys.LAST_USAGE_BLOCK_AT] ?: 0L
        if (shouldApplyBlock(marker, today, lastApplied)) {
            val limitMinutes = policyRepository.dailyLimits.first().limitFor(today.dayOfWeek)
            if (limitMinutes != null) {
                val bonusMinutes = bonusRepository.phoneBonusMinutes(today).first()
                usageRepository.setScreenTime(today, (limitMinutes + bonusMinutes) * 60)
            }
            context.syncDataStore.edit { it[Keys.LAST_USAGE_BLOCK_AT] = marker!!.issuedAt }
            Timber.tag(TAG).d("Заблокировано на сегодня родителем (issuedAt=%d)", marker!!.issuedAt)
        }
    }

    /** Целиком заменяет локальную политику (включая бонусы) содержимым серверного документа. */
    private suspend fun applyDocument(data: PolicyDocumentDto) {
        policyRepository.replaceAll(
            PolicySnapshot(
                dailyLimits = data.dailyLimits.mapNotNull { (key, minutes) ->
                    runCatching { DayOfWeek.valueOf(key) to minutes }.getOrNull()
                }.toMap(),
                appLimits = data.appLimits,
                whitelist = data.whitelist.toSet(),
                blockedApps = data.blockedApps.toSet(),
                blockedSites = data.blockedSites.map { BlockedSite(it.domain, it.enabled) },
                blockGoogleSearch = data.blockGoogleSearch,
                studySchedule = data.studySchedule.toRules(data.studyScheduleEnabled),
                sleepSchedule = data.sleepSchedule.toRules(data.sleepScheduleEnabled),
                emergencyContacts = data.emergencyContacts.map { EmergencyContact(it.name, it.phone) },
                pinHash = data.pinHash,
                pinSalt = data.pinSalt,
                breakRules = data.breaks.toDomain(),
                dailyUsageReset = data.dailyUsageReset?.let {
                    runCatching { DailyUsageReset(LocalDate.parse(it.date), it.issuedAt) }.getOrNull()
                },
                dailyUsageBlock = data.dailyUsageBlock?.let {
                    runCatching { DailyUsageBlock(LocalDate.parse(it.date), it.issuedAt) }.getOrNull()
                }
            )
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

    private suspend fun currentLocalDocument(): PolicyDocumentDto {
        val pin = policyRepository.pinProtection.first()
        val study = policyRepository.studySchedule.first()
        val sleep = policyRepository.sleepSchedule.first()
        return PolicyDocumentDto(
            dailyLimits = policyRepository.dailyLimits.first().minutesByDay
                .mapKeys { it.key.name },
            appLimits = policyRepository.appLimits.first(),
            whitelist = policyRepository.whitelist.first().toList(),
            blockedApps = policyRepository.blockedApps.first().toList(),
            // Бонусы датированы «на сегодня»: прошедшие дни в документ не тащим.
            bonuses = bonusRepository.observeAll().first()
                .filter { it.date == currentDateProvider.today() }
                .map { BonusEntryDto(it.date.toString(), it.packageName, it.minutes) },
            pinHash = pin?.hash,
            pinSalt = pin?.salt,
            blockedSites = policyRepository.blockedSites.first().map { BlockedSiteDto(it.domain, it.enabled) },
            blockGoogleSearch = policyRepository.blockGoogleSearch.first(),
            studySchedule = study.toDto(),
            sleepSchedule = sleep.toDto(),
            studyScheduleEnabled = study.enabled,
            sleepScheduleEnabled = sleep.enabled,
            emergencyContacts = policyRepository.emergencyContacts.first()
                .map { EmergencyContactDto(it.name, it.phone) },
            breaks = policyRepository.breakRules.first().toDto(),
            dailyUsageReset = policyRepository.dailyUsageReset.first()
                ?.let { DailyUsageResetDto(it.date.toString(), it.issuedAt) },
            dailyUsageBlock = policyRepository.dailyUsageBlock.first()
                ?.let { DailyUsageBlockDto(it.date.toString(), it.issuedAt) }
        )
    }

    private fun Map<String, TimeWindowDto>.toRules(enabled: Boolean) = ScheduleRules(
        windowsByDay = mapNotNull { (key, window) ->
            runCatching { DayOfWeek.valueOf(key) to TimeWindow(window.startMinute, window.endMinute) }
                .getOrNull()
        }.toMap(),
        enabled = enabled
    )

    private fun ScheduleRules.toDto(): Map<String, TimeWindowDto> =
        windowsByDay.entries.associate { (day, window) ->
            day.name to TimeWindowDto(window.startMinute, window.endMinute)
        }

    /**
     * mode — строка в документе (не enum.ordinal), чтобы будущее добавление режима не сдвигало
     * старые значения. Незнакомое/битое значение (ручная правка документа) не должно ронять pull —
     * откатываемся на INTERVAL, как BreakRules.EMPTY.
     */
    private fun BreakRulesDto.toDomain(): BreakRules = BreakRules(
        enabled = enabled,
        mode = runCatching { BreakMode.valueOf(mode) }.getOrDefault(BreakMode.INTERVAL),
        intervalMinutes = intervalMinutes,
        hours = hours.toSet(),
        durationMinutes = durationMinutes,
        message = message
    )

    private fun BreakRules.toDto(): BreakRulesDto = BreakRulesDto(
        enabled = enabled,
        mode = mode.name,
        intervalMinutes = intervalMinutes,
        hours = hours.sorted(),
        durationMinutes = durationMinutes,
        message = message
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
            blockedApps = document.blockedApps.sorted(),
            bonuses = document.bonuses.sortedWith(compareBy({ it.date }, { it.packageName })),
            // Скаляры — сортировать нечего, но включаем как есть, иначе разница в PIN не попадёт
            // в снапшот сравнения и push/pull будут пинг-понговать.
            pinHash = document.pinHash,
            pinSalt = document.pinSalt,
            blockedSites = document.blockedSites.sortedBy { it.domain },
            blockGoogleSearch = document.blockGoogleSearch,
            studySchedule = document.studySchedule.toSortedMap(),
            sleepSchedule = document.sleepSchedule.toSortedMap(),
            studyScheduleEnabled = document.studyScheduleEnabled,
            sleepScheduleEnabled = document.sleepScheduleEnabled,
            emergencyContacts = document.emergencyContacts.sortedBy { it.phone },
            // hours сортируем по той же причине, что и остальные списки выше: порядок элементов в
            // множестве часов не несёт смысла, а без сортировки перестановка выглядела бы правкой.
            breaks = document.breaks.copy(hours = document.breaks.hours.sorted()),
            // Скаляр (как pinHash/blockGoogleSearch выше) — сортировать нечего, включаем как есть.
            dailyUsageReset = document.dailyUsageReset,
            dailyUsageBlock = document.dailyUsageBlock
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
