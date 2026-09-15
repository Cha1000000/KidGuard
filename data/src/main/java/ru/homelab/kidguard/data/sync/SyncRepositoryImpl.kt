package ru.homelab.kidguard.data.sync

import java.time.Instant
import androidx.datastore.preferences.core.MutablePreferences
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
import ru.homelab.kidguard.core.domain.model.PenaltyGrant
import ru.homelab.kidguard.core.domain.model.dayBudgetMinutes
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakRules
import ru.homelab.kidguard.core.domain.model.DailyUsageBlock
import ru.homelab.kidguard.core.domain.model.DailyUsageReset
import ru.homelab.kidguard.core.domain.model.shouldApplyUnblock
import ru.homelab.kidguard.core.domain.model.AppliedDayBlock
import ru.homelab.kidguard.core.domain.model.DailyUsageUnblock
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.BONUS_SPENT_PREFIX
import ru.homelab.kidguard.core.domain.model.OVERRUN_PACKAGE
import ru.homelab.kidguard.core.domain.model.PolicySnapshot
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.TimeWindow
import ru.homelab.kidguard.core.domain.model.shouldApplyBlock
import ru.homelab.kidguard.core.domain.model.shouldApplyReset
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.PenaltyRepository
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
import ru.homelab.kidguard.data.network.PenaltyEntryDto
import ru.homelab.kidguard.data.network.BreakRulesDto
import ru.homelab.kidguard.data.network.ChildAppDto
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.DeviceHealthApi
import ru.homelab.kidguard.data.network.EmergencyContactDto
import ru.homelab.kidguard.data.network.TimeWindowDto
import ru.homelab.kidguard.data.network.DailyUsageBlockDto
import ru.homelab.kidguard.data.network.DailyUsageUnblockDto
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
    private val penaltyRepository: PenaltyRepository,
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
        val LAST_USAGE_UNBLOCK_AT = longPreferencesKey("last_usage_unblock_at")

        // Снимок момента, когда телефон применил блокировку дня: сколько бюджета было израсходовано
        // (к этому значению разблокировка возвращает расход) и сколько времени оставалось (это видит
        // родитель). Живёт до разблокировки, сброса или полуночи.
        val BLOCK_SNAPSHOT_DATE = stringPreferencesKey("block_snapshot_date")
        val BLOCK_SNAPSHOT_ISSUED_AT = longPreferencesKey("block_snapshot_issued_at")
        val BLOCK_SNAPSHOT_APPLIED_AT = stringPreferencesKey("block_snapshot_applied_at")
        val BLOCK_SNAPSHOT_SECONDS = intPreferencesKey("block_snapshot_seconds")
        val BLOCK_SNAPSHOT_MINUTES_LEFT = intPreferencesKey("block_snapshot_minutes_left")
    }

    private val json = Json

    // --- Петли ---------------------------------------------------------------------------------

    @OptIn(FlowPreview::class)
    override suspend fun parentSyncLoop() = coroutineScope {
        // Отработавшая история бонусов: держим ровно тот период, который возим в документе.
        // Чистим до первого push, чтобы старые записи не уехали на сервер.
        runCatching {
            val cutoff = bonusHistoryCutoff()
            bonusRepository.deleteOlderThan(cutoff)
            penaltyRepository.deleteOlderThan(cutoff)
        }.onFailure { Timber.tag(TAG).w(it, "Не удалось почистить старые бонусы и штрафы") }

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

                    is WsEvent.ChildHealthChanged -> childHealthEvents.tryEmit(event.childId)

                    // Переподключение WS — подтянуть свежее состояние активного ребёнка, чтобы
                    // не ждать следующего 15-минутного пула, если правка второго родителя
                    // прилетела в момент разрыва.
                    WsEvent.Reconnected -> {
                        val childId = activeChildId.first() ?: return@collect
                        runCatching { pullAndApply(childId) }
                            .onFailure { Timber.tag(TAG).w(it, "Pull после переподключения не удался") }
                    }
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
            .combine(penaltyRepository.observeAll()) { _, _ -> Unit }
            .combine(policyRepository.blockedSites) { _, _ -> Unit }
            .combine(policyRepository.blockGoogleSearch) { _, _ -> Unit }
            .combine(policyRepository.studySchedule) { _, _ -> Unit }
            .combine(policyRepository.sleepSchedule) { _, _ -> Unit }
            .combine(policyRepository.emergencyContacts) { _, _ -> Unit }
            .combine(policyRepository.breakRules) { _, _ -> Unit }
            .combine(policyRepository.dailyUsageReset) { _, _ -> Unit }
            .combine(policyRepository.dailyUsageBlock) { _, _ -> Unit }
            .combine(policyRepository.dailyUsageUnblock) { _, _ -> Unit }
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

    // replay=0 по той же причине, что и у childPaired: событие интересно только тем, кто слушает
    // прямо сейчас (открытое родительское приложение).
    private val childHealthEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    override val childHealthChanged: Flow<Int> = childHealthEvents

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
                // Лог обязателен: без него незапривязанное устройство молча глотает ВСЕ события
                // push-канала, и в логах это неотличимо от «сервер ничего не слал» — на разбор
                // такой тишины уходит несоразмерно много времени.
                val childId = authLocalStore.pairedChildId() ?: run {
                    Timber.tag(TAG).d("WS-событие пришло, но устройство не привязано — пропускаю")
                    return@collect
                }
                when (event) {
                    is WsEvent.PolicyChanged -> {
                        if (event.childId == childId) {
                            runCatching { pullAndApply(event.childId) }
                                .onFailure { Timber.tag(TAG).w(it, "Pull по WS-сигналу не удался") }
                        }
                    }
                    // Переподключились после разрыва — подтянуть всё, что могло прийти
                    // в офлайне (например, отключение расписания родителем), не дожидаясь
                    // очередного 15-минутного пула.
                    WsEvent.Reconnected -> {
                        runCatching { pullAndApply(childId) }
                            .onFailure { Timber.tag(TAG).w(it, "Pull после переподключения не удался") }
                    }

                    // Перечислены явно, а не через `else`: when остаётся исчерпывающим, и новый
                    // тип события компилятор заставит осознанно разобрать и здесь, а не даст
                    // молча проглотить его на детском устройстве.
                    is WsEvent.ChildPaired, is WsEvent.ChildHealthChanged -> Unit
                }
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
        val dayBlock = appliedDayBlockToday()
        deviceHealthApi.sendHealth(
            DeviceHealthRequest(
                DeviceHealthDto(
                    accessibility = health.accessibility,
                    overlay = health.overlay,
                    deviceAdmin = health.deviceAdmin,
                    vpn = health.vpn,
                    batteryOptimization = health.batteryOptimization,
                    lastExitKind = health.lastExit?.kind?.name,
                    lastExitAt = health.lastExit?.at?.toString(),
                    lastExitDescription = health.lastExit?.description?.takeIf { it.isNotBlank() },
                    dayBlockDate = dayBlock?.date?.toString(),
                    dayBlockIssuedAt = dayBlock?.issuedAt,
                    dayBlockAppliedAt = dayBlock?.appliedAt?.toString(),
                    dayBlockMinutesLeft = dayBlock?.minutesLeftBefore
                )
            )
        )
        Timber.tag(TAG).d(
            "Heartbeat отправлен, всё в порядке: %s, прошлая смерть: %s",
            health.isHealthy,
            health.lastExit?.kind ?: "нет данных"
        )
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
                // Фактическое время: приложение, чьё время целиком ушло в перерасход, тоже
                // «использовалось» — иначе оно выпало бы из публикуемого списка.
                usageRepository.appTotalScreenTimeByPackage(date).first().forEach { (pkg, seconds) ->
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
     *
     * Расход бюджета и перерасход уезжают разными записями (вторая — под маркером
     * [OVERRUN_PACKAGE]): родителю нужно и то, сколько бюджета израсходовано, и сколько ребёнок
     * пробыл в телефоне сверх него. По приложениям, наоборот, отправляется сумма — там интересно
     * фактическое время в приложении, а не бюджетная его часть.
     *
     * Отдельно уезжает израсходованное дополнительное время ([BONUS_SPENT_PREFIX] + пакет) —
     * иначе родитель не увидит, сколько осталось от выданного им пропуска.
     */
    private suspend fun pushUsage(childId: Int) {
        val today = currentDateProvider.today()
        val entries = buildList {
            for (date in listOf(today.minusDays(1), today)) {
                val total = usageRepository.screenTimeSeconds(date).first()
                if (total > 0) add(UsageEntryDto(date.toString(), packageName = "", seconds = total))
                val overrun = usageRepository.overrunSeconds(date).first()
                if (overrun > 0) {
                    add(UsageEntryDto(date.toString(), packageName = OVERRUN_PACKAGE, seconds = overrun))
                }
                usageRepository.appTotalScreenTimeByPackage(date).first().forEach { (pkg, seconds) ->
                    if (seconds > 0) add(UsageEntryDto(date.toString(), packageName = pkg, seconds = seconds))
                }
                // Израсходованное дополнительное время — отдельными записями под префиксом:
                // родителю нужно показать остаток выданного, а сам расход считается здесь.
                usageRepository.appBonusSpentByPackage(date).first().forEach { (pkg, seconds) ->
                    if (seconds > 0) {
                        add(
                            UsageEntryDto(
                                date.toString(),
                                packageName = BONUS_SPENT_PREFIX + pkg,
                                seconds = seconds
                            )
                        )
                    }
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
        applyDailyUnblock(data)
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
            context.syncDataStore.edit {
                it[Keys.LAST_USAGE_RESET_AT] = marker!!.issuedAt
                // Сброс снимает и блокировку — подтверждение о ней родителю больше не нужно.
                clearBlockSnapshot(it)
            }
            Timber.tag(TAG).d("Дневной лимит сброшен родителем (issuedAt=%d)", marker!!.issuedAt)
            healthReportTrigger.requestNow()
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
            // Снимок берём ДО выставления расхода: после него остаток всегда нулевой.
            val usedSeconds = usageRepository.screenTimeSeconds(today).first()
            var minutesLeft = 0
            val limitMinutes = policyRepository.dailyLimits.first().limitFor(today.dayOfWeek)
            if (limitMinutes != null) {
                val bonusMinutes = bonusRepository.phoneBonusMinutes(today).first()
                val penaltyMinutes = penaltyRepository.phonePenalty(today).first()?.minutes ?: 0
                val budgetMinutes = dayBudgetMinutes(limitMinutes, bonusMinutes, penaltyMinutes)
                minutesLeft = ((budgetMinutes * 60 - usedSeconds).coerceAtLeast(0)) / 60
                usageRepository.setScreenTime(today, budgetMinutes * 60)
            }
            context.syncDataStore.edit {
                it[Keys.LAST_USAGE_BLOCK_AT] = marker!!.issuedAt
                it[Keys.BLOCK_SNAPSHOT_DATE] = today.toString()
                it[Keys.BLOCK_SNAPSHOT_ISSUED_AT] = marker.issuedAt
                it[Keys.BLOCK_SNAPSHOT_APPLIED_AT] = Instant.now().toString()
                it[Keys.BLOCK_SNAPSHOT_SECONDS] = usedSeconds
                it[Keys.BLOCK_SNAPSHOT_MINUTES_LEFT] = minutesLeft
            }
            Timber.tag(TAG).d(
                "Заблокировано на сегодня родителем (issuedAt=%d, оставалось %d мин)",
                marker!!.issuedAt, minutesLeft
            )
            // Родитель ждёт подтверждения — не держим его до следующего 15-минутного тика.
            healthReportTrigger.requestNow()
        }
    }

    /**
     * Применяет разблокировку дня — после [applyDailyBlock], чтобы блокировка и разблокировка,
     * приехавшие одним pull (телефон был офлайн), дали честный итог «ничего не поменялось».
     *
     * С возвратом остатка расход дня откатывается к снимку, сделанному в момент блокировки.
     * Перерасход, накопленный за время блокировки (ребёнок смахивал оверлей), лежит в отдельном
     * счётчике и возвращённое время не съедает. Возврат выполняется, только если снимок относится
     * именно к снимаемой блокировке: иначе мы откатили бы расход к чужому моменту.
     *
     * Без возврата (разблокировка выдачей бонуса) расход не трогаем — время даёт сам бонус.
     *
     * На родительском телефоне этот путь тоже выполняется (он зовёт [pullAndApply] по WS-сигналу),
     * но безвреден: своего расхода и снимка там нет.
     */
    private suspend fun applyDailyUnblock(data: PolicyDocumentDto) {
        val marker = data.dailyUsageUnblock?.let {
            runCatching { DailyUsageUnblock(LocalDate.parse(it.date), it.issuedAt, it.restoreRemaining) }.getOrNull()
        }
        val block = data.dailyUsageBlock
            ?.let { runCatching { DailyUsageBlock(LocalDate.parse(it.date), it.issuedAt) }.getOrNull() }
        val today = currentDateProvider.today()
        val prefs = context.syncDataStore.data.first()
        if (!shouldApplyUnblock(marker, block, today, prefs[Keys.LAST_USAGE_UNBLOCK_AT] ?: 0L)) return

        val savedSeconds = prefs[Keys.BLOCK_SNAPSHOT_SECONDS]
        val snapshotMatches = prefs[Keys.BLOCK_SNAPSHOT_DATE] == today.toString() &&
            prefs[Keys.BLOCK_SNAPSHOT_ISSUED_AT] == block!!.issuedAt
        if (marker!!.restoreRemaining && snapshotMatches && savedSeconds != null) {
            usageRepository.setScreenTime(today, savedSeconds)
        }
        context.syncDataStore.edit {
            it[Keys.LAST_USAGE_UNBLOCK_AT] = marker.issuedAt
            clearBlockSnapshot(it)
        }
        Timber.tag(TAG).d(
            "Разблокировано родителем (issuedAt=%d, возврат остатка: %s)",
            marker.issuedAt, marker.restoreRemaining && snapshotMatches
        )
        healthReportTrigger.requestNow()
    }

    private fun clearBlockSnapshot(prefs: MutablePreferences) {
        prefs.remove(Keys.BLOCK_SNAPSHOT_DATE)
        prefs.remove(Keys.BLOCK_SNAPSHOT_ISSUED_AT)
        prefs.remove(Keys.BLOCK_SNAPSHOT_APPLIED_AT)
        prefs.remove(Keys.BLOCK_SNAPSHOT_SECONDS)
        prefs.remove(Keys.BLOCK_SNAPSHOT_MINUTES_LEFT)
    }

    /** Применённая сегодня блокировка дня — для отчёта родителю; null — сегодня не блокирован. */
    private suspend fun appliedDayBlockToday(): AppliedDayBlock? {
        val prefs = context.syncDataStore.data.first()
        val date = prefs[Keys.BLOCK_SNAPSHOT_DATE] ?: return null
        // После полуночи вчерашняя блокировка уже не действует, хотя снимок ещё не стёрт.
        if (date != currentDateProvider.today().toString()) return null
        return AppliedDayBlock(
            date = LocalDate.parse(date),
            issuedAt = prefs[Keys.BLOCK_SNAPSHOT_ISSUED_AT] ?: return null,
            appliedAt = prefs[Keys.BLOCK_SNAPSHOT_APPLIED_AT]
                ?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null,
            minutesLeftBefore = prefs[Keys.BLOCK_SNAPSHOT_MINUTES_LEFT] ?: 0
        )
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
                },
                dailyUsageUnblock = data.dailyUsageUnblock?.let {
                    runCatching {
                        DailyUsageUnblock(LocalDate.parse(it.date), it.issuedAt, it.restoreRemaining)
                    }.getOrNull()
                }
            )
        )
        val incomingBonuses = data.bonuses.mapNotNull { dto ->
            runCatching { BonusGrant(LocalDate.parse(dto.date), dto.packageName, dto.minutes) }
                .getOrNull()
        }
        resetBonusSpentWhereReduced(incomingBonuses)
        bonusRepository.replaceAll(incomingBonuses)
        penaltyRepository.replaceAll(
            data.penalties.mapNotNull { dto ->
                runCatching {
                    PenaltyGrant(LocalDate.parse(dto.date), dto.packageName, dto.minutes, dto.comment)
                }.getOrNull()
            }
        )
    }

    /**
     * Обнуляет израсходованное дополнительное время тех приложений, которым его **уменьшили или
     * отменили**.
     *
     * Отмена бонуса происходит у родителя и доезжает сюда обычным `replaceAll` — запись просто
     * исчезает из документа. Без этого сброса расход пережил бы отмену, и следующая выдача
     * погасла бы об него мгновенно: выдали 15 → израсходованы → отменили → выдали снова 15,
     * остаток `15 − 15 = 0`, приложение не открылось бы.
     *
     * Увеличение минут — обычное продление, расход при нём сохраняется: именно на нём держится
     * суммирование повторных выдач (`15 + 15 = 30` против потраченных 15 → остаётся 15).
     *
     * На родительском устройстве метод безвреден: расход приложений ребёнка там не ведётся.
     */
    private suspend fun resetBonusSpentWhereReduced(incoming: List<BonusGrant>) {
        val today = currentDateProvider.today()
        val before = bonusRepository.appBonusMinutes(today).first()
        if (before.isEmpty()) return
        val after = incoming
            .filter { it.date == today && it.packageName.isNotEmpty() }
            .associate { it.packageName to it.minutes }
        before.forEach { (pkg, minutesBefore) ->
            if ((after[pkg] ?: 0) < minutesBefore) {
                usageRepository.resetAppBonusSpent(today, pkg)
            }
        }
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

    /** Самый ранний день, бонусы и штрафы за который ещё храним и синхронизируем. */
    private suspend fun bonusHistoryCutoff(): LocalDate =
        currentDateProvider.today().minusDays((BonusRepository.HISTORY_DAYS - 1).toLong())

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
            // История бонусов за последние BonusRepository.HISTORY_DAYS дней: график «Последние
            // 7 дней» у родителя рисует риску бюджета по бонусу, выданному в тот день, а раньше
            // в документ уходили только сегодняшние — и после pull история пропадала совсем.
            bonuses = bonusRepository.observeAll().first()
                .filter { it.date >= bonusHistoryCutoff() }
                .map { BonusEntryDto(it.date.toString(), it.packageName, it.minutes) },
            // Штрафы возим тем же периодом и тем же отсечением, что бонусы: они складываются в
            // один бюджет дня, и разъехавшаяся глубина истории рисовала бы кривые риски в графике.
            penalties = penaltyRepository.observeAll().first()
                .filter { it.date >= bonusHistoryCutoff() }
                .map { PenaltyEntryDto(it.date.toString(), it.packageName, it.minutes, it.comment) },
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
                ?.let { DailyUsageBlockDto(it.date.toString(), it.issuedAt) },
            dailyUsageUnblock = policyRepository.dailyUsageUnblock.first()
                ?.let { DailyUsageUnblockDto(it.date.toString(), it.issuedAt, it.restoreRemaining) }
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

    /** Стабильное представление документа для сравнения — см. [canonicalPolicyJson]. */
    private fun canonicalJson(document: PolicyDocumentDto): String = canonicalPolicyJson(json, document)

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
