package ru.homelab.kidguard.platform.accessibility

import ru.homelab.kidguard.platform.call.callAudioModeFlow
import ru.homelab.kidguard.core.domain.usecase.shouldDismissRecentsPinOnCall
import ru.homelab.kidguard.core.domain.usecase.nextStackSync
import ru.homelab.kidguard.core.domain.usecase.isRecentsButtonClick
import ru.homelab.kidguard.core.domain.usecase.isRecentsLockMenuItem
import ru.homelab.kidguard.core.domain.usecase.isRecentsUnlockMenuItem
import ru.homelab.kidguard.core.domain.usecase.recentsConfirmedByContent
import ru.homelab.kidguard.core.domain.usecase.recentsLockIconIdsFor
import ru.homelab.kidguard.core.domain.usecase.recentsViewIdsFor
import ru.homelab.kidguard.core.domain.usecase.shouldCloseBlockedServiceWindow
import ru.homelab.kidguard.core.domain.usecase.shouldDismissPinOverlayOnWindowEvent
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.homelab.kidguard.platform.R
import ru.homelab.kidguard.core.domain.repository.HealthReportTrigger
import ru.homelab.kidguard.core.domain.repository.RecentsLockAutomation
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.security.PinVerifyResult
import ru.homelab.kidguard.core.domain.usecase.WindowKind
import ru.homelab.kidguard.core.domain.usecase.WindowSnapshot
import ru.homelab.kidguard.core.domain.usecase.resolveForegroundPackage
import ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService.Companion.MAX_TREE_DEPTH
import ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService.Companion.UNLOCK_WINDOW_MS
import ru.homelab.kidguard.platform.overlay.PinOverlayManager
import ru.homelab.kidguard.platform.overlay.BreakWarningOverlay
import ru.homelab.kidguard.platform.overlay.FullScreenLockOverlayManager
import ru.homelab.kidguard.platform.overlay.WarningOverlayManager
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Accessibility-сервис KidGuard.
 *
 * 1. Определяет активное (foreground) приложение по событиям смены окна и публикует его в
 *    [ForegroundAppMonitor] — фундамент под учёт экранного времени и блокировку (вехи 2–3).
 * 2. Точечно перехватывает открытие критичных системных экранов (веха 6, шаги 6.2–6.3, 6В):
 *    настройки VPN, «Специальные возможности» (чтобы ребёнок не отключил сам этот сервис), экран
 *    администратора устройства (деактивация Device Admin снимает защиту от удаления), удаление
 *    именно приложения KidGuard, его экраны «О приложении» / «Хранилище» (оттуда доступны
 *    «Очистить хранилище», «Остановить» и «Удалить» — каждый из трёх убивает контроль), и экран
 *    «Дата и время» (перевод часов вперёд обходит анти-отмотку и обнуляет дневной счётчик раньше
 *    срока). Другие приложения, включая игры, ребёнок удаляет и чистит свободно — «чистит мусор».
 *
 * Детект кросс-вендорный, без привязки к конкретной прошивке: экран узнаём по ЗАГОЛОВКУ окна
 * ([screenTitle], `AccessibilityWindowInfo.title`), а не по классу активности; пакеты настроек и
 * инсталлера спрашиваем у системы ([settingsPackages], [installerPackages]), а не хардкодим.
 * Найденный экран накрываем PIN-оверлеем типа `TYPE_ACCESSIBILITY_OVERLAY` (обычный оверлей на этих
 * защищённых экранах система скрывает). Верный PIN пропускает на короткое окно, «Назад» уводит.
 */
@SuppressLint("AccessibilityPolicy")
@AndroidEntryPoint
class KidGuardAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var foregroundAppMonitor: ForegroundAppMonitor

    @Inject
    lateinit var policyRepository: PolicyRepository

    @Inject
    lateinit var pinOverlayManager: PinOverlayManager

    @Inject
    lateinit var pinGuard: PinGuard

    @Inject
    lateinit var warningOverlayManager: WarningOverlayManager

    @Inject
    lateinit var fullScreenLockOverlayManager: FullScreenLockOverlayManager

    @Inject
    lateinit var breakWarningOverlay: BreakWarningOverlay

    @Inject
    lateinit var healthReportTrigger: HealthReportTrigger

    @Inject
    lateinit var accessibilityLiveness: AccessibilityLiveness

    @Inject
    lateinit var recentsLockAutomation: RecentsLockAutomation

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Цикл сверки переднего плана со стеком окон — один на сервис, см. [syncForegroundWithStack]. */
    private var stackSyncJob: Job? = null

    /** Подписки на политику и звонки текущего подключения сервиса — см. [onServiceConnected]. */
    private val connectionJobs = mutableListOf<Job>()

    /**
     * Задан ли родителем PIN — кеш в памяти, обновляется потоком в [onServiceConnected].
     *
     * Нужен ради скорости перехвата списка последних: там между открытием обзора и показом PIN
     * счёт идёт на доли секунды (ребёнок жмёт «Очистить всё» в это окно — телефон Олега,
     * 15.09.2026). Чтение `pinProtection` из DataStore — диск и корутинный хоп, слишком медленно
     * для горячего пути. Здесь — синхронная проверка volatile-поля.
     */
    @Volatile
    private var pinConfigured = false

    /**
     * Запрещённые родителем пакеты — кеш в памяти для закрытия их служебных окон (игровая панель).
     * Тот же довод, что у [pinConfigured]: до очистки из панели у ребёнка ~1,5 с, диск на этом пути нельзя.
     */
    @Volatile
    private var blockedAppsCache: Set<String> = emptySet()

    /** elapsedRealtime() последнего закрытия служебного окна — пауза против очереди «Назад». */
    private var lastServiceWindowCloseAt = 0L

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * elapsedRealtime() последней успешной проверки PIN для каждого типа критичного экрана.
     * Пока разница с текущим временем меньше [UNLOCK_WINDOW_MS], повторный показ ТОГО ЖЕ
     * типа экрана не требует PIN снова — родитель успевает довести настройку до конца.
     * Разные типы экранов (VPN, accessibility, device admin) требуют отдельного ввода PIN.
     */
    private val lastUnlockedAt = mutableMapOf<CriticalScreen, Long>()

    /**
     * Пакет каждого известного окна: `windowId -> packageName`. Наполняется из событий прикладных
     * окон, где имя приходит даром, и избавляет от похода за `root` при каждом пересчёте стека
     * (см. [windowSnapshots]). Закрытые окна вычищаются там же.
     */
    private val windowPackages = mutableMapOf<Int, String>()

    /**
     * Каким экраном вызван показанный сейчас PIN-оверлей. Нужно ровно для списка последних:
     * оверлей висит поверх ЛАУНЧЕРА, а общее правило ниже прячет его при уходе на чужое
     * приложение — и спрятало бы сразу же, приняв лаунчер за уход.
     */
    private var pinOverlayScreen: CriticalScreen? = null

    /**
     * Перехват списка последних уже идёт. Отдельный флаг, а не `pinOverlayManager.isShowing()`:
     * между сворачиванием списка и появлением оверлея есть пауза, и всё это время `isShowing()`
     * отвечает «нет». Второе событие о списке (его шлёт лаунчер) успевало в эту щель и сворачивало
     * экран повторно — уводя ребёнка уже с того приложения, куда он вернулся.
     */
    private var recentsInterceptRunning = false

    /**
     * Пакеты, чьи окна мы хотя бы раз видели служебными (шторка, навбар, AOD, клавиатура). Список
     * набирается наблюдением, а не хардкодом: на каждой прошивке оболочка своя, а ошибиться в имени
     * пакета — значит снова начать засчитывать её как приложение. Нужен для событий от окон,
     * которых в стеке уже нет, — см. [updateForeground].
     */
    private val shellPackages = mutableSetOf<String>()

    /**
     * Пакеты системных настроек и пакет-инсталлера — СПРАШИВАЕМ У СИСТЕМЫ, а не хардкодим:
     * на кастомных прошивках (HiOS/Transsion, MIUI, EMUI) инсталлер может называться по-своему
     * (`com.transsion.*` и т.п.), и тогда защита от удаления просто не сработала бы. К найденному
     * добавляем известные AOSP-значения — объединение никогда не хуже прежнего списка констант.
     *
     * `by lazy` — резолв идёт через IPC к PackageManager, на каждое событие окна его гонять нельзя;
     * набор пакетов за время жизни сервиса не меняется.
     */
    private val settingsPackages: Set<String> by lazy {
        (resolvePackageFor(Intent(Settings.ACTION_SETTINGS)) + AOSP_SETTINGS_PACKAGES)
            .also { Timber.tag(TAG).d("Пакеты настроек: %s", it) }
    }

    private val installerPackages: Set<String> by lazy {
        val uninstallIntent = Intent(
            Intent.ACTION_DELETE,
            "package:${applicationContext.packageName}".toUri()
        )
        (resolvePackageFor(uninstallIntent) + AOSP_INSTALLER_PACKAGES)
            .also { Timber.tag(TAG).d("Пакеты инсталлера: %s", it) }
    }

    /** Пока активный экран принадлежит настройкам/инсталлеру, PIN-оверлей держим (см. [onAccessibilityEvent]). */
    private val overlayHostPackages: Set<String> by lazy { settingsPackages + installerPackages }

    /**
     * Пакет домашнего лаунчера — им владеет и список последних приложений. Нужен, чтобы отличить
     * его от чужих экранов со словом «недавние» в заголовке (такое встречается в настройках).
     */
    private val launcherPackages: Set<String> by lazy {
        resolvePackageFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            .also { Timber.tag(TAG).d("Пакеты лаунчера: %s", it) }
    }

    /**
     * Пакет активности, которая обработает [intent], или пустое множество. Отсеиваем системный
     * resolver (`android`): он появляется, когда интент обрабатывают несколько приложений, и не
     * является ни настройками, ни инсталлером.
     */
    private fun resolvePackageFor(intent: Intent): Set<String> = runCatching {
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeIf { it.isNotBlank() && it != ANDROID_RESOLVER_PACKAGE }
            ?.let { setOf(it) }
            .orEmpty()
    }.onFailure { Timber.tag(TAG).w(it, "Не удалось определить пакет для %s", intent.action) }
        .getOrDefault(emptySet())

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Сторож судит о живости контроля в первую очередь по этому флагу — см. [AccessibilityLiveness].
        accessibilityLiveness.onConnected()
        // Держим наличие PIN в памяти, чтобы перехват обзора не читал DataStore на горячем пути.
        // Подписки этого подключения. Система может вызвать onServiceConnected повторно без отключения —
        // прежние отменяем, иначе с каждым переподключением копились бы живые дубли.
        connectionJobs.forEach { it.cancel() }
        connectionJobs.clear()
        connectionJobs += scope.launch { policyRepository.pinProtection.collect { pinConfigured = it != null } }
        connectionJobs += scope.launch { policyRepository.blockedApps.collect { blockedAppsCache = it } }
        // Закрепление карточки в списке последних по кнопке родителя (см. RecentsLockAutomation).
        connectionJobs += scope.launch(Dispatchers.Main) {
            recentsLockAutomation.requests.collect { recentsLockAutomation.report(lockOwnRecentsCard()) }
        }
        // Отдаём оверлею WindowManager сервиса — только окно accessibility-типа показывается
        // поверх защищённых системных экранов (см. PinOverlayManager).
        getSystemService(WindowManager::class.java)?.let {
            pinOverlayManager.attach(it)
            warningOverlayManager.attach(it)
            fullScreenLockOverlayManager.attach(it)
            breakWarningOverlay.attach(it)
        }
        // Звонок снимает PIN над списком последних: ребёнок должен иметь возможность ответить.
        // Коллектор на главном потоке — там же, где события окон меняют pinOverlayScreen и флаги
        // перехвата, чтобы им не гоняться между потоками.
        connectionJobs += scope.launch(Dispatchers.Main) {
            applicationContext.callAudioModeFlow(includeRinging = true).collect { callActive ->
                if (pinOverlayManager.isShowing() && shouldDismissRecentsPinOnCall(
                        overlayForRecents = pinOverlayScreen == CriticalScreen.RECENTS,
                        callActive = callActive
                    )
                ) {
                    pinOverlayManager.hide()
                    pinOverlayScreen = null
                    recentsInterceptRunning = false
                    Timber.tag(TAG).d("Звонок — снимаю PIN со списка последних")
                }
            }
        }
        // Щуп стека окон для контроллера блокировки: перед повтором блокировки он проверяет, что
        // приложение действительно на экране, а не только в (возможно залипшем) мониторе.
        foregroundAppMonitor.attachStackProbe { windowSnapshots() }
        // Сверка переднего плана со стеком — на главном потоке, как и события окон: кэш имён окон
        // общий и без синхронизации. Система может вызвать onServiceConnected повторно без отключения —
        // второй бесконечный цикл был бы лишней нагрузкой, поэтому прежний отменяем.
        stackSyncJob?.cancel()
        stackSyncJob = scope.launch(Dispatchers.Main) { syncForegroundWithStack() }
        // Система подключила сервис — главный кейс задержки watchdog: родитель только что выдал
        // (или переустановкой сбросил и восстановил) accessibility-разрешение. Не ждём следующий
        // 15-минутный тик, шлём heartbeat сразу.
        healthReportTrigger.requestNow()
    }

    /**
     * Периодически сверяет монитор переднего плана со стеком окон и снимает залипание.
     *
     * События окон не гарантируют правду: опоздавшее событие лаунчера перезаписывало приложение,
     * которое уже снова на экране, а приложение, которым просто пользуются, новых событий смены окна
     * не шлёт. Запрещённое приложение так оставалось открытым без блокировки минутами (эмулятор,
     * 17.09.2026). Решение, когда стеку верить, — в [nextStackSync]: два одинаковых чтения подряд.
     *
     * При выключенном экране не опрашиваем: пользоваться телефоном нельзя, а IPC зря будит процесс.
     */
    private suspend fun syncForegroundWithStack() {
        val powerManager = getSystemService(PowerManager::class.java)
        var candidate: String? = null
        while (true) {
            delay(STACK_SYNC_INTERVAL_MS)
            if (powerManager?.isInteractive != true) {
                candidate = null
                continue
            }
            val step = nextStackSync(
                current = foregroundAppMonitor.currentPackage.value,
                observed = resolveForegroundPackage(windowSnapshots()),
                candidate = candidate
            )
            candidate = step.candidate
            step.accept?.let { packageName ->
                foregroundAppMonitor.update(packageName)
                Timber.tag(TAG).d("Активное приложение (сверка со стеком, монитор залип): %s", packageName)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TYPE_WINDOWS_CHANGED — событийный фикс обхода блокировки (план 2026-07-28, проблема 1):
        // ловит возвраты/тёплые резюмы приложений (напр. Standoff 2 после смахивания оверлея),
        // которые не всегда шлют свежий TYPE_WINDOW_STATE_CHANGED, из-за чего currentPackage
        // застревал на прошлом значении и повторный запуск того же приложения не переблокировался.
        // Отдельная ветка: только обновляет foregroundAppMonitor и выходит — логика критичных
        // экранов/lockdown/PIN-оверлея ниже завязана на event.packageName/title и остаётся
        // исключительно на TYPE_WINDOW_STATE_CHANGED.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (isRelevantWindowChange(event)) {
                refreshForegroundFromWindows()
            }
            return
        }
        // Нажатие по кнопке «Обзор» на панели навигации — самый ранний сигнал: приходит в момент касания,
        // тогда как об открытии самого обзора система сообщает только к концу анимации (а при повороте
        // экрана ещё позже). Замок ставим здесь, до того как список последних появится на экране.
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val label = event.text.joinToString(" ").ifBlank { event.contentDescription?.toString().orEmpty() }
            if (isRecentsButtonClick(event.packageName?.toString(), shellPackages + SYSTEM_UI_PACKAGE, label)) {
                Timber.tag(TAG).d("Нажата кнопка «Обзор» (%s) — ставлю замок заранее", label)
                maybeInterceptWithPin(CriticalScreen.RECENTS)
            }
            return
        }
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        updateForeground(event.windowId, packageName)
        maybeCloseBlockedServiceWindow(event, packageName)

        val title = screenTitle(event)
        // Диагностический лог (только debug — Timber-дерево плантится лишь в debug-сборке): по нему
        // сверяем фактические заголовки критичных системных экранов с ключевыми словами детекта.
        // Пригодится при обкатке на реальном HiOS/других прошивках (часть 6В), где заголовки могут
        // отличаться.
        Timber.tag(TAG).d("Заголовок окна [%s]: %s", packageName, title)

        // «Защита от дурака»: попытка включить системную опцию «Блокировать соединения без VPN»
        // (lockdown) показывает диалог-подтверждение «Использовать сеть VPN?». Эта опция вместе с
        // запретом сайтов оставила бы телефон совсем без интернета, поэтому отменяем диалог
        // (GLOBAL_ACTION_BACK = «Отмена», lockdown не применяется) и показываем предупреждение.
        // Проверяем ДО detectCriticalScreen: заголовок диалога содержит «vpn» и иначе был бы
        // принят за экран VPN-настроек. isShowing-гард — чтобы не жать «Назад» повторно.
        if (isLockdownDialog(title)) {
            if (!warningOverlayManager.isShowing()) {
                Timber.tag(TAG).d("Диалог lockdown — отменяю и показываю предупреждение")
                performGlobalAction(GLOBAL_ACTION_BACK)
                warningOverlayManager.show()
            }
            return
        }

        val criticalScreen = detectCriticalScreen(packageName, title)
        if (criticalScreen != null) {
            maybeInterceptWithPin(criticalScreen)
            return
        }
        // Заголовок обзора не распознан, но событие от лаунчера — проверяем содержимое окна: HiOS иногда
        // присылает при открытии обзора заголовок главного экрана.
        if (packageName in launcherPackages) scheduleRecentsContentCheck(packageName)
        // Экран не критичный. Оверлей убираем ТОЛЬКО при реальном уходе на другое приложение
        // (лаунчер и т.п.). НЕ реагируем на: события самого оверлея (наш пакет — иначе оверлей
        // скрыл бы себя своим же window-событием) и под-окна того же хоста настроек/инсталлера
        // (например, всплывающие «значок приложения»), где родитель ещё должен ввести PIN.
        val overlayHosts = if (pinOverlayScreen == CriticalScreen.RECENTS) launcherPackages else overlayHostPackages
        val ownPackage = applicationContext.packageName
        // PIN над списком последних по событиям окон не снимается никогда — только верный PIN, отказ
        // или звонок. Иначе поворот из горизонтальной игры и панель меню спец. возможностей снимали
        // замок через секунду (обход, 15.09.2026). Решение и причины — в shouldDismissPinOverlayOnWindowEvent.
        if (pinOverlayManager.isShowing() && shouldDismissPinOverlayOnWindowEvent(
                overlayForRecents = pinOverlayScreen == CriticalScreen.RECENTS,
                eventPackage = packageName,
                ownPackage = ownPackage,
                hostPackages = overlayHosts
            )
        ) {
            pinOverlayManager.hide()
            pinOverlayScreen = null
            recentsInterceptRunning = false
        }
        // Warning-оверлей убираем при реальном уходе с настроек (напр. ребёнок нажал «Домой», не закрыв
        // предупреждение), чтобы он не завис поверх следующего экрана. Правило прежнее.
        if (packageName != ownPackage && packageName !in overlayHosts && warningOverlayManager.isShowing()) {
            warningOverlayManager.hide()
        }
    }

    private fun maybeInterceptWithPin(screen: CriticalScreen) {
        val lastUnlocked = lastUnlockedAt[screen] ?: 0L
        if (SystemClock.elapsedRealtime() - lastUnlocked < unlockWindowFor(screen)) {
            Timber.tag(TAG).d("Критичный экран %s в окне разблокировки — без PIN", screen)
            return
        }
        // Список последних — синхронно, без корутины и чтения DataStore: между открытием обзора и
        // показом PIN счёт на доли секунды, и любой лишний хоп даёт ребёнку окно на «Очистить всё».
        // Наличие PIN берём из кеша [pinConfigured]. Событие accessibility уже на главном потоке,
        // так что показ идёт настолько быстро, насколько система вообще позволяет.
        if (screen == CriticalScreen.RECENTS) {
            if (!pinConfigured) {
                Timber.tag(TAG).d("Список последних, но PIN не задан — пропускаю")
                return
            }
            Timber.tag(TAG).d("Критичный экран RECENTS — показываю PIN-оверлей")
            interceptRecents()
            return
        }
        scope.launch {
            // «О приложении»/«Хранилище» детектятся по заголовку, но он одинаков у ВСЕХ приложений
            // (и у хранилища устройства) — чей это экран, видно только по содержимому окна.
            // Опрос, а не одна проверка: содержимое может доехать чуть позже события.
            if (screen == CriticalScreen.KIDGUARD_APP_INFO && !awaitOwnAppScreen()) {
                Timber.tag(TAG).d("Экран «О приложении»/«Хранилище» не наш — пропускаю")
                return@launch
            }
            if (!pinConfigured) {
                // PIN не задан родителем — защита не настроена, не перехватываем.
                Timber.tag(TAG).d("Критичный экран %s, но PIN не задан — пропускаю", screen)
                return@launch
            }
            Timber.tag(TAG).d("Критичный экран %s — показываю PIN-оверлей", screen)
            pinOverlayManager.show(
                verifyPin = ::verifyPin,
                onUnlocked = { lastUnlockedAt[screen] = SystemClock.elapsedRealtime() },
                onCancel = { performGlobalAction(GLOBAL_ACTION_BACK) }
            )
        }
    }

    /**
     * Список последних — единственный экран, где порядок обратный: сначала уводим, потом просим
     * PIN.
     *
     * У остальных критичных экранов оверлей ложится поверх, а «Назад» уводит. Здесь так нельзя:
     * карточки приложений видны сразу, и цель ребёнка — кнопка «Очистить всё», до которой он
     * доберётся быстрее, чем прочитает оверлей. Поэтому экран сворачивается немедленно, а PIN
     * спрашивается уже поверх того, откуда ушли.
     *
     * Верный PIN открывает список заново: родителю он нужен, чтобы разбирать свёрнутые приложения.
     * Отказ не возвращает ничего — мы уже ушли, и это ровно то поведение, которого ждали.
     */
    private fun interceptRecents() {
        // Подавления «хвоста» после отказа тут больше нет (см. историю: телефон Олега 15.09.2026).
        // Любой заход в обзор — под PIN. Хвост, приходящий пока PIN на экране, гасит гард
        // isShowing() ниже; хвост уже после отказа даёт лишь короткий повторный PIN на рабочем
        // столе — безопасная косметика. Прежние таймер/флаг пропускали ОСОЗНАННЫЙ быстрый повторный
        // вход, и через него ребёнок обходил замок: лишний PIN лучше пропущенного.
        // Флаг синхронный, в отличие от pinOverlayManager.isShowing(): показ идёт через
        // mainHandler, и второе событие о списке (его шлёт лаунчер) успевало проскочить в щель.
        if (recentsInterceptRunning || pinOverlayManager.isShowing()) return
        recentsInterceptRunning = true
        pinOverlayScreen = CriticalScreen.RECENTS
        cardLockChecked = false
        // Сначала уводим с обзора, и только потом рисуем PIN. Показ нового окна во время поворота
        // экрана (обзор из горизонтальной игры) занимал до 900 мс, и ребёнок успевал нажать
        // «Очистить всё» до первого кадра замка — контроль убит на телефоне Олега 17.09.2026.
        // «Домой» не ждёт отрисовки нашего окна: лаунчер выходит из обзора, и нажатие приходится на
        // рабочий стол. Верный PIN откроет обзор заново (onUnlocked).
        performGlobalAction(GLOBAL_ACTION_HOME)
        keepAwayFromRecentsUntilLockVisible()
        pinOverlayManager.show(
            verifyPin = ::verifyPin,
            onUnlocked = {
                pinOverlayScreen = null
                recentsInterceptRunning = false
                // Окно разблокировки ставим ДО открытия обзора — иначе его же перехватим снова.
                lastUnlockedAt[CriticalScreen.RECENTS] = SystemClock.elapsedRealtime()
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                // Родитель открыл список последних — самый спокойный момент проверить закрепление.
                mainHandler.postDelayed({ checkOwnCardLock() }, CARD_LOCK_CHECK_DELAY_MS)
            },
            onCancel = {
                pinOverlayScreen = null
                recentsInterceptRunning = false
                // С обзора уже ушли до показа замка (см. начало функции); «Домой» здесь — страховка:
                // до 17.09.2026 на эмуляторе лаунчер после закрытия оверлея возвращал список, если
                // уйти только один раз. С уходом до показа и после отказа список не возвращается
                // (проверено на эмуляторе и телефоне Олега 17.09.2026).
                performGlobalAction(GLOBAL_ACTION_HOME)
                Timber.tag(TAG).d("Список последних закрыт без PIN — увожу на домашний экран")
            },
            titleRes = R.string.pin_overlay_recents_title,
            subtitleRes = R.string.pin_overlay_recents_subtitle
        )
    }

    /**
     * Сколько после верного PIN не спрашивать его снова для этого экрана.
     *
     * У списка последних окно длиннее: остальные экраны открывают, чтобы довести одну настройку, а
     * здесь родитель разбирает карточки — двадцати секунд на это мало. Плюс список мы открываем
     * сами после верного PIN, и короткое окно означало бы, что он перехватится повторно.
     */
    /**
     * Служебное окно запрещённого пакета — игровая панель с кнопкой очистки и подобные — закрываем сразу.
     * Когда именно — решает [shouldCloseBlockedServiceWindow]; здесь только размер окна и действие.
     *
     * «Назад» закрывает выдвижную панель, не трогая игру под ней. Если окно через [SERVICE_WINDOW_RECHECK_MS]
     * всё ещё на экране — уводим домой: игра прерывается, но кнопка очистки до нажатия не доживает.
     */
    private fun maybeCloseBlockedServiceWindow(event: AccessibilityEvent, packageName: String) {
        if (packageName !in blockedAppsCache) return
        val windowId = event.windowId
        val window = runCatching { windows.firstOrNull { it.id == windowId } }
            .onFailure { Timber.tag(TAG).w(it, "Не удалось прочитать окно %d служебного пакета %s", windowId, packageName) }
            .getOrNull()
        val bounds = android.graphics.Rect()
        if (window != null) window.getBoundsInScreen(bounds) else event.source?.getBoundsInScreen(bounds)
        val metrics = resources.displayMetrics
        val screenArea = metrics.widthPixels.toLong() * metrics.heightPixels
        val areaFraction = if (bounds.isEmpty || screenArea <= 0) null else {
            bounds.width().toLong() * bounds.height() / screenArea.toFloat()
        }
        val now = SystemClock.elapsedRealtime()
        val close = shouldCloseBlockedServiceWindow(
            packageName = packageName,
            windowKind = window?.let { windowKindOf(it.type) },
            windowAreaFraction = areaFraction,
            blockedApps = blockedAppsCache,
            protectedPackages = launcherPackages + applicationContext.packageName + SYSTEM_UI_PACKAGE,
            millisSinceLastClose = now - lastServiceWindowCloseAt
        )
        if (!close) return
        lastServiceWindowCloseAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        Timber.tag(TAG).d("Служебное окно запрещённого пакета %s (%.0f%% экрана) — закрываю", packageName, (areaFraction ?: 0f) * 100)
        mainHandler.postDelayed({
            // Не смогли проверить — считаем, что окно на месте: лишний уход домой дешевле нажатой очистки.
            val stillShown = runCatching { windows.any { it.id == windowId } }
                .onFailure { Timber.tag(TAG).w(it, "Не удалось проверить окно %d — увожу домой на всякий случай", windowId) }
                .getOrDefault(true)
            if (stillShown) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                Timber.tag(TAG).d("Окно %s не закрылось по «Назад» — увожу домой", packageName)
            }
        }, SERVICE_WINDOW_RECHECK_MS)
    }

    /**
     * Проверяет по значку на карточке, закреплена ли она, и запоминает результат для отчёта родителю.
     *
     * Пассивно: ничего не нажимает, только читает дерево. Карточку в списке может быть не видно (её
     * ещё не нарисовали, или она за экраном) — тогда ничего не меняем, чтобы не гасить верный ответ
     * прошлой проверки. Так же и на оболочке без закрепления: значка нет ни у кого, вывод «не знаем».
     */
    private fun checkOwnCardLock(): Boolean {
        val launcher = launcherPackages.firstOrNull() ?: return false
        val roots = allRoots()
        val title = roots.firstNotNullOfOrNull { root ->
            root.findAccessibilityNodeInfosByText(ownAppLabel()).firstOrNull { it.isVisibleToUser }
        } ?: return false
        val titleBounds = android.graphics.Rect().also { title.getBoundsInScreen(it) }
        val lockIcons = recentsLockIconIdsFor(launcher).flatMap { id ->
            roots.flatMap { it.findAccessibilityNodeInfosByViewId(id) }
        }.filter { it.isVisibleToUser }
        // Значка нет и мы его никогда не видели: либо оболочка не умеет закреплять, либо карточку
        // ещё не дорисовали. Вывода «не закреплена» в этом случае не делаем.
        if (lockIcons.isEmpty() && !cardLockIconSeenEver) return false
        val locked = lockIcons.any { icon ->
            val bounds = android.graphics.Rect().also { icon.getBoundsInScreen(it) }
            kotlin.math.abs(bounds.centerY() - titleBounds.centerY()) < CARD_LOCK_ICON_MAX_DISTANCE_PX
        }
        if (lockIcons.isNotEmpty()) cardLockIconSeenEver = true
        cardLockKnownLocked = locked
        scope.launch {
            if (settingsRepository.recentsLockConfirmed.first() != locked) {
                settingsRepository.setRecentsLockConfirmed(locked)
                healthReportTrigger.requestNow()
                Timber.tag(TAG).d("Карточка KidGuard в списке последних: %s", if (locked) "закреплена" else "НЕ закреплена")
            }
        }
        return true
    }

    /**
     * Видели ли мы значок закрепления на этой прошивке хоть раз. Пока не видели — «нет значка» может
     * означать и «оболочка не умеет закреплять», поэтому вывод «не закреплена» не делаем.
     */
    private var cardLockIconSeenEver = false

    /** Последний прочитанный со значка ответ: закреплена ли карточка. */
    private var cardLockKnownLocked = false

    /**
     * Пока окно замка не видно на экране — повторно уводим с обзора.
     *
     * При повороте (обзор из горизонтальной игры) система прячет наше окно на время поворота, и замок,
     * хотя и добавлен, ребёнку не мешает: 18.09.2026 Володя в этой щели успевал нажать «Очистить всё».
     * Поэтому «Домой» повторяется короткими попытками, пока замок не появится на экране или пока не
     * выйдет [RECENTS_KEEP_AWAY_WINDOW_MS]. Как только замок виден — прекращаем: дальше он сам держит экран.
     */
    private fun keepAwayFromRecentsUntilLockVisible() {
        mainHandler.removeCallbacks(recentsKeepAway)
        hiddenLockSteps = 0
        recentsKeepAwayUntil = SystemClock.elapsedRealtime() + RECENTS_KEEP_AWAY_WINDOW_MS
        mainHandler.postDelayed(recentsKeepAway, RECENTS_KEEP_AWAY_STEP_MS)
    }

    private var recentsKeepAwayUntil = 0L
    private var hiddenLockSteps = 0

    /** Удалось ли за это открытие обзора посмотреть, закреплена ли карточка (см. [checkOwnCardLock]). */
    private var cardLockChecked = false

    private val recentsKeepAway = object : Runnable {
        override fun run() {
            if (pinOverlayScreen != CriticalScreen.RECENTS || !recentsInterceptRunning) return
            // Следим весь поворот: система прячет окно не один раз — бывает, что замок мелькнул и снова
            // скрыт (телефон Олега, 18.09.2026). Поэтому уводим с обзора на каждой проверке, где он скрыт.
            // Пока список последних ещё на экране (мы только что с него ушли), успеваем посмотреть,
            // закреплена ли наша карточка: другого момента увидеть её у сервиса нет.
            if (!cardLockChecked) cardLockChecked = checkOwnCardLock()
            if (!pinOverlayManager.isVisibleOnScreen()) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                hiddenLockSteps++
            }
            if (SystemClock.elapsedRealtime() < recentsKeepAwayUntil) {
                mainHandler.postDelayed(this, RECENTS_KEEP_AWAY_STEP_MS)
            } else if (hiddenLockSteps > 0) {
                Timber.tag(TAG).d("Замок обзора был скрыт %d проверок подряд — уводил с обзора", hiddenLockSteps)
            }
        }
    }

    /**
     * Проверка обзора по содержимому окна лаунчера — страховка к распознаванию по заголовку (см.
     * [recentsViewIdsFor]). После события лаунчера окно опрашивается раз в [RECENTS_CONTENT_POLL_MS] в
     * течение [RECENTS_CONTENT_POLL_WINDOW_MS]; обзор признаём, когда две проверки подряд его увидели
     * ([recentsConfirmedByContent]). Новое событие опрос не перезапускает, а продлевает: иначе частые
     * события лаунчера при открытии обзора отодвигали подтверждение (замер на телефоне Олега 17.09.2026).
     */
    private fun scheduleRecentsContentCheck(launcherPackage: String) {
        recentsContentLauncher = launcherPackage
        recentsContentDeadline = SystemClock.elapsedRealtime() + RECENTS_CONTENT_POLL_WINDOW_MS
        if (recentsContentPolling) return
        recentsContentPolling = true
        recentsContentLastSaw = false
        mainHandler.postDelayed(recentsContentPoll, RECENTS_CONTENT_POLL_MS)
    }

    private var recentsContentLauncher: String? = null
    private var recentsContentDeadline = 0L
    private var recentsContentPolling = false
    private var recentsContentLastSaw = false

    private val recentsContentPoll = object : Runnable {
        override fun run() {
            val launcher = recentsContentLauncher
            if (launcher == null || pinOverlayManager.isShowing()) {
                recentsContentPolling = false
                return
            }
            val saw = launcherShowsRecents(launcher)
            if (recentsConfirmedByContent(recentsContentLastSaw, saw)) {
                recentsContentPolling = false
                Timber.tag(TAG).d("Список последних распознан по содержимому окна (заголовок не совпал)")
                maybeInterceptWithPin(CriticalScreen.RECENTS)
                return
            }
            recentsContentLastSaw = saw
            if (SystemClock.elapsedRealtime() < recentsContentDeadline) {
                mainHandler.postDelayed(this, RECENTS_CONTENT_POLL_MS)
            } else {
                recentsContentPolling = false
            }
        }
    }

    /**
     * Видны ли в окне лаунчера элементы обзора. Ошибку чтения логируем и считаем «не видно».
     *
     * Опрос идёт после каждого события лаунчера, включая обычное «Домой», поэтому обращений к системе
     * здесь минимум: корень активного окна одним вызовом (обзор — активное окно лаунчера), `root` у окна
     * читается один раз, поиск по id останавливается на первом видимом элементе.
     */
    private fun launcherShowsRecents(launcherPackage: String): Boolean = try {
        val root = rootInActiveWindow?.takeIf { it.packageName == launcherPackage }
        root != null && recentsViewIdsFor(launcherPackage).any { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Не удалось проверить содержимое лаунчера на обзор")
        false
    }

    /**
     * Закрепляет карточку KidGuard в списке последних так же, как это сделал бы родитель: открывает
     * список, долгим нажатием на нашей карточке вызывает меню и жмёт пункт замка.
     *
     * Почему «руками», а не через API: состояние закрепления вендорское, программного доступа к нему
     * нет ни на чтение, ни на запись. Зато закреплённая карточка переживает и «Очистить всё», и
     * перезагрузку (проверено на телефоне Олега 18.09.2026) — ради этого стоит потерпеть хрупкость.
     *
     * Чужой оболочке не вредим: каждый шаг ограничен таймаутом, пункты меню ищем по подписи
     * ([isRecentsLockMenuItem]), и при любой заминке просто уходим на рабочий стол. Если карточка уже
     * закреплена — в меню будет обратный пункт, это [RecentsLockAutomation.Result.AlreadyLocked].
     */
    private suspend fun lockOwnRecentsCard(): RecentsLockAutomation.Result {
        // Свой же перехват обзора на время операции выключаем — иначе закроем список себе.
        lastUnlockedAt[CriticalScreen.RECENTS] = SystemClock.elapsedRealtime()
        if (pinOverlayManager.isShowing()) pinOverlayManager.hide()
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        val launcher = launcherPackages.firstOrNull()
            ?: return RecentsLockAutomation.Result.Failed.also { goHomeAfterAutomation() }
        if (!awaitCondition { launcherShowsRecents(launcher) }) {
            return RecentsLockAutomation.Result.Failed.also { goHomeAfterAutomation() }
        }
        val label = ownAppLabel()
        // Сначала читаем состояние: если карточка уже закреплена, трогать чужое меню незачем.
        if (awaitCondition { checkOwnCardLock() } && cardLockKnownLocked) {
            Timber.tag(TAG).d("Карточка уже закреплена — меню не открываем")
            goHomeAfterAutomation()
            return RecentsLockAutomation.Result.AlreadyLocked
        }
        val card = awaitNode { roots ->
            roots.firstNotNullOfOrNull { root ->
                root.findAccessibilityNodeInfosByText(label).firstOrNull { it.isVisibleToUser }
            }
        } ?: return RecentsLockAutomation.Result.Failed.also { goHomeAfterAutomation() }
        if (!openCardMenu(launcher, card)) return RecentsLockAutomation.Result.Failed.also { goHomeAfterAutomation() }

        var result = RecentsLockAutomation.Result.Failed
        awaitCondition {
            val items = allRoots().flatMap(::menuItems)
            val unlock = items.firstOrNull { isRecentsUnlockMenuItem(it.second) }
            val lock = items.firstOrNull { isRecentsLockMenuItem(it.second) }
            when {
                unlock != null -> { result = RecentsLockAutomation.Result.AlreadyLocked; true }
                lock != null -> {
                    val clicked = lock.first.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    result = if (clicked) RecentsLockAutomation.Result.Success else RecentsLockAutomation.Result.Failed
                    true
                }
                else -> false
            }
        }
        Timber.tag(TAG).d("Автозакрепление карточки в списке последних: %s", result)
        goHomeAfterAutomation()
        return result
    }

    /**
     * Корни всех окон на экране. Меню карточки в списке последних открывается ОТДЕЛЬНЫМ окном, и в
     * активном окне его узлов нет (телефон Олега, 18.09.2026) — поэтому ищем по всем окнам сразу.
     */
    private fun allRoots(): List<AccessibilityNodeInfo> =
        runCatching { windows.mapNotNull { it.root } }
            .onFailure { Timber.tag(TAG).w(it, "Не удалось прочитать окна для автозакрепления") }
            .getOrDefault(emptyList())

    /** Видимые подписи пунктов меню карточки: текст либо описание. */
    private fun menuItems(root: AccessibilityNodeInfo): List<Pair<AccessibilityNodeInfo, String>> {
        val items = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > MAX_TREE_DEPTH) return
            val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (label != null && node.isVisibleToUser) items += node to label
            repeat(node.childCount) { walk(node.getChild(it), depth + 1) }
        }
        walk(root, 0)
        return items
    }

    /**
     * Открывает меню карточки. На HiOS оно вызывается стрелкой рядом с названием приложения
     * (`task_arrow`), а не долгим нажатием — проверено на телефоне Олега 18.09.2026: после долгого
     * нажатия в дереве не появлялось ни одного пункта. Долгое нажатие оставлено запасным путём:
     * на других оболочках меню вызывается именно им.
     */
    private suspend fun openCardMenu(launcherPackage: String, card: AccessibilityNodeInfo): Boolean {
        val cardBounds = android.graphics.Rect().also { card.getBoundsInScreen(it) }
        fun nearestById(idName: String): AccessibilityNodeInfo? = allRoots()
            .flatMap { it.findAccessibilityNodeInfosByViewId("$launcherPackage:id/$idName") }
            .filter { it.isVisibleToUser }
            .minByOrNull { node ->
                val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                kotlin.math.abs(bounds.centerY() - cardBounds.centerY())
            }

        // Способ открытия меню карточки у каждой оболочки свой: на HiOS это строка с названием и
        // стрелкой над карточкой, на AOSP-подобных — долгое нажатие по самой карточке. Пробуем по
        // очереди и после каждой попытки смотрим, появились ли пункты меню.
        val attempts: List<Pair<String, () -> Boolean>> = listOf(
            "стрелка" to { nearestById(RECENTS_CARD_ARROW_ID)?.let(::clickSelfOrParent) == true },
            "строка названия" to { nearestById(RECENTS_CARD_TITLE_ROW_ID)?.let(::clickSelfOrParent) == true },
            // Оболочка может не принимать программные нажатия — тогда шлём НАСТОЯЩЕЕ касание.
            "касание по стрелке" to { nearestById(RECENTS_CARD_ARROW_ID)?.let { tapOn(it) } == true },
            "касание по названию" to { nearestById(RECENTS_CARD_TITLE_ROW_ID)?.let { tapOn(it) } == true },
            "долгое нажатие" to { longClick(card) },
            "долгое касание по карточке" to { longPressOn(card) }
        )
        for ((name, attempt) in attempts) {
            if (!attempt()) continue
            if (awaitCondition { allRoots().flatMap(::menuItems).any { isRecentsLockMenuItem(it.second) || isRecentsUnlockMenuItem(it.second) } }) {
                Timber.tag(TAG).d("Меню карточки открылось: %s", name)
                return true
            }
        }
        return false
    }

    /** Настоящее касание в центр узла — когда оболочка не принимает программные нажатия. */
    private fun tapOn(node: AccessibilityNodeInfo): Boolean = dispatchTouch(node, TAP_DURATION_MS)

    /** Настоящее долгое касание в центр узла. */
    private fun longPressOn(node: AccessibilityNodeInfo): Boolean = dispatchTouch(node, LONG_PRESS_DURATION_MS)

    private fun dispatchTouch(node: AccessibilityNodeInfo, durationMs: Long): Boolean {
        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        if (bounds.isEmpty) return false
        val path = android.graphics.Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Клик по узлу или ближайшему кликабельному предку. */
    private fun clickSelfOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < LONG_CLICK_PARENT_DEPTH) {
            if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
            depth++
        }
        return false
    }

    /** Долгое нажатие по карточке: сам узел может быть не кликабельным — поднимаемся к предку. */
    private fun longClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < LONG_CLICK_PARENT_DEPTH) {
            if (current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true
            current = current.parent
            depth++
        }
        return false
    }

    private suspend fun awaitNode(find: (List<AccessibilityNodeInfo>) -> AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        awaitCondition {
            found = find(allRoots())
            found != null
        }
        return found
    }

    /** Ждёт условие, опрашивая раз в [AUTOMATION_POLL_MS], не дольше [AUTOMATION_STEP_TIMEOUT_MS]. */
    private suspend fun awaitCondition(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + AUTOMATION_STEP_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return true
            delay(AUTOMATION_POLL_MS)
        }
        return false
    }

    private fun goHomeAfterAutomation() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        lastUnlockedAt.remove(CriticalScreen.RECENTS)
    }

    private fun unlockWindowFor(screen: CriticalScreen): Long =
        if (screen == CriticalScreen.RECENTS) RECENTS_UNLOCK_WINDOW_MS else UNLOCK_WINDOW_MS

    /** Сырой PIN никуда не хранится. Проверка и счётчик попыток — в общем [PinGuard]. */
    private suspend fun verifyPin(entered: String): PinVerifyResult = pinGuard.verify(entered)

    /**
     * Передний план по событию смены окна.
     *
     * `event.packageName` — это пакет ОКНА, а не приложения на экране, и доверять ему можно только
     * когда окно прикладное. Событие `TYPE_WINDOW_STATE_CHANGED` шлёт каждое окно: шторка
     * уведомлений, панель громкости, навбар, always-on display, клавиатура, тост. Раньше монитор
     * обновлялся безусловно — и оболочка подменяла собой приложение, а значение залипало до
     * следующего события. За неделю так набежало 459 минут на `com.android.systemui`, которого
     * система вообще не считает бывавшим на переднем плане (см. [resolveForegroundPackage]).
     *
     * Событие от служебного окна не выбрасываем: оно как раз повод пересчитать, что осталось под
     * шторкой — иначе после её закрытия приложение вернулось бы в монитор только со следующим
     * своим событием, которого может и не быть.
     */
    private fun updateForeground(windowId: Int, packageName: String) {
        when (windowKind(windowId)) {
            WindowKind.APPLICATION -> {
                windowPackages[windowId] = packageName
                foregroundAppMonitor.update(packageName)
                Timber.tag(TAG).d("Активное приложение: %s", packageName)
            }
            // Окна события в стеке нет — судить не по чему, решаем по прошлому опыту с этим
            // пакетом. Случая два, и они требуют противоположного: окно нового приложения ещё
            // не доехало до getWindows() (верить событию, иначе блокировка запоздает) либо окно
            // оболочки уже закрылось (верить стеку, иначе шторка на миг подменит приложение —
            // ровно это и ловилось в логе при закрытии шторки).
            null -> if (packageName in shellPackages) {
                refreshForegroundFromWindows()
            } else {
                foregroundAppMonitor.update(packageName)
                Timber.tag(TAG).d("Активное приложение: %s", packageName)
            }
            else -> {
                // Пакет опознан как оболочка на факте, а не по списку констант: мы своими глазами
                // видели его окно служебным. Так это работает на любой прошивке.
                shellPackages += packageName
                Timber.tag(TAG).d("Событие служебного окна [%s] — пересчитываю передний план", packageName)
                refreshForegroundFromWindows()
            }
        }
    }

    /**
     * Пересчёт переднего плана по текущему стеку окон.
     *
     * `null` (прикладных окон нет — экран блокировки, AOD) намеренно НЕ сбрасывает монитор:
     * пустой ответ бывает и в момент переключения между экранами, а обнулённый передний план на
     * мгновение снял бы блокировку. Учёт времени в такие моменты и так стоит — `ScreenTimeTracker`
     * проверяет, что экран включён и разблокирован.
     */
    private fun refreshForegroundFromWindows() {
        val foreground = resolveForegroundPackage(windowSnapshots()) ?: return
        foregroundAppMonitor.update(foreground)
        Timber.tag(TAG).d("Активное приложение (по стеку окон): %s", foreground)
    }

    /**
     * Снимок стека окон для [resolveForegroundPackage].
     *
     * Имя пакета у окна берём из кэша [windowPackages], и только при промахе дёргаем `root` —
     * он создаёт `AccessibilityNodeInfo` через IPC, а пересчёт идёт на каждое релевантное событие.
     * Кэш заодно чинит старую дыру: `root` бывает `null` у окна без извлекаемого содержимого, и
     * тогда верхнее прикладное окно считалось безымянным, а монитор оставался залипшим.
     */
    private fun windowSnapshots(): List<WindowSnapshot> = try {
        val live = windows
        // Закрытые окна из кэша убираем сразу: id переиспользуются, и чужое имя было бы хуже,
        // чем его отсутствие.
        windowPackages.keys.retainAll(live.mapTo(mutableSetOf()) { it.id })
        live.map { window ->
            val kind = windowKindOf(window.type)
            val packageName = if (kind == WindowKind.APPLICATION) {
                windowPackages[window.id]
                    ?: window.root?.packageName?.toString()?.takeIf { it.isNotBlank() }
                        ?.also { windowPackages[window.id] = it }
            } else {
                null
            }
            WindowSnapshot(id = window.id, kind = kind, layer = window.layer, packageName = packageName)
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Не удалось прочитать стек окон")
        emptyList()
    }

    /**
     * Род окна, к которому относится событие, или `null` — окна в стеке нет (ещё не появилось или
     * уже закрылось) и судить не по чему. Разница важна: «неизвестно» и «служебное» ведут к разным
     * решениям в [updateForeground].
     */
    private fun windowKind(windowId: Int): WindowKind? = try {
        windows.firstOrNull { it.id == windowId }?.let { windowKindOf(it.type) }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Не удалось определить род окна %d", windowId)
        null
    }

    private fun windowKindOf(type: Int): WindowKind = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.APPLICATION
        AccessibilityWindowInfo.TYPE_SYSTEM -> WindowKind.SYSTEM
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.INPUT_METHOD
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WindowKind.OVERLAY
        else -> WindowKind.OTHER
    }

    /**
     * Фильтр против «дребезга» `TYPE_WINDOWS_CHANGED` (он шумный — летит и на изменения
     * содержимого окна, не только стека окон). Реагируем только на изменения СТЕКА окон:
     * добавление/удаление/смену активного или сфокусированного окна, вход-выход в PIP.
     * Флаги нулевые — считаем релевантным (безопасный фолбэк: пересчёт лёгкий, дедуп всё равно
     * происходит на уровне `currentPackage: StateFlow`).
     */
    private fun isRelevantWindowChange(event: AccessibilityEvent): Boolean {
        val relevantFlags = AccessibilityEvent.WINDOWS_CHANGE_ADDED or
            AccessibilityEvent.WINDOWS_CHANGE_REMOVED or
            AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or
            AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or
            AccessibilityEvent.WINDOWS_CHANGE_PIP
        val changes = event.windowChanges
        return changes == 0 || (changes and relevantFlags) != 0
    }

    /**
     * Заголовок текущего экрана из НАДЁЖНОГО источника — `title` окна, к которому относится
     * событие (`getWindows()` по `event.windowId`, фолбэк — активное окно), ПЛЮС `event.text`
     * как дополнение.
     *
     * Почему не только `event.text`: для части системных экранов `event.text` возвращает не
     * заголовок, а подпись случайного элемента (на экране «Администраторы устройства» приходило
     * «Значок приложения»), и детект по нему промахивался. `AccessibilityWindowInfo.getTitle()`
     * же отдаёт стабильный заголовок окна («Приложения администратора устройства», «Настройки
     * VPN», «Специальные возможности») — кросс-вендорно, без привязки к классу активности.
     * Требует `canRetrieveWindowContent` + `flagRetrieveInteractiveWindows` (заданы в конфиге).
     */
    private fun screenTitle(event: AccessibilityEvent): String {
        val fromWindow = try {
            val ws = windows
            (ws.firstOrNull { it.id == event.windowId } ?: ws.firstOrNull { it.isActive })
                ?.title?.toString().orEmpty()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Не удалось прочитать заголовок окна")
            ""
        }
        val fromEvent = event.text.joinToString(" ")
        return "$fromWindow $fromEvent".trim().lowercase()
    }

    /**
     * Детект критичных экранов — по ЗАГОЛОВКУ окна (см. [screenTitle]), а не по имени класса
     * активности. Класс у системных настроек прошивко-зависим (на Android 14+ многие экраны идут
     * через общий `SpaActivity`/`Settings`), а заголовок окна стабилен на разных устройствах и
     * вендорах — поэтому детект универсален (Tecno, Xiaomi, Samsung и т.д.), а не подогнан под
     * одну модель. Главный экран настроек под PIN не попадает: там заголовок «Настройки».
     */
    private fun detectCriticalScreen(packageName: String, title: String): CriticalScreen? {
        if (title.isBlank()) return null
        return when (packageName) {
            in settingsPackages if VPN_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.VPN_SETTINGS

            in settingsPackages if ACCESSIBILITY_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.ACCESSIBILITY_SETTINGS

            // Экран администратора устройства (веха 6.3): деактивация Device Admin снимает
            // системную защиту от удаления — под PIN. Заголовок стабилен кросс-вендорно.
            in settingsPackages if DEVICE_ADMIN_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.DEVICE_ADMIN

            // Диалог-подтверждение деактивации Device Admin (Android показывает перед
            // «Активировать приложение администратора?»). Заголовок НЕ содержит «администратор» —
            // содержит текст вида «Отключение защитит телефон от KidGuard-контроля. Для отключения
            // нужен родительский PIN». Ловим по «kidguard» в заголовке + «отключ» или «disable».
            in settingsPackages if title.contains("kidguard") &&
                    DEACTIVATION_DIALOG_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.DEVICE_ADMIN

            // Удаление приложения: окно пакет-инсталлера с названием ИМЕННО нашего приложения
            // (другие приложения ребёнок удаляет свободно — «чистит мусор»). Название берём у
            // системы, чтобы не хардкодить строку и не путать с приложениями, где «kidguard» —
            // лишь часть текста: сравниваем по точному label в заголовке диалога удаления.
            in installerPackages if title.contains(ownAppLabel().lowercase()) ->
                CriticalScreen.KIDGUARD_UNINSTALL

            // «О приложении» / «Хранилище» ИМЕННО нашего приложения. Оттуда доступны три способа
            // убить контроль: «Очистить хранилище» (сбрасывает роль, политику И выданный
            // accessibility), «Остановить» (Android отключает accessibility-сервис при force-stop)
            // и «Удалить». Плановый шаг 6.2 этот экран перечислял, но в код не попал.
            //
            // Заголовок сам по себе НЕ годится: он одинаков для всех приложений («о приложении»,
            // «хранилище») и не содержит имени. Матчить по нему одному — значит запереть ребёнку
            // чужие приложения, а по концепции он их «чистит» свободно. Хуже: у экрана хранилища
            // УСТРОЙСТВА заголовок тоже «хранилище». Поэтому здесь — только КАНДИДАТ; чей это
            // экран, решает [awaitOwnAppScreen] по содержимому окна (см. maybeInterceptWithPin).
            //
            // Вторая половина условия — HiOS (снято с реального Tecno KL6, 2026-07-18): там
            // заголовок экрана «О приложении» — НЕ «о приложении», а label самого приложения
            // («KidGuard»). Без этого условия экран проскакивал без PIN вместе со своими
            // «Удалить»/«Остановить»/«Очистить». Двухфакторность сохраняется: label в заголовке —
            // тоже лишь кандидат, окончательно решает awaitOwnAppScreen.
            in settingsPackages if (APP_DETAILS_KEYWORDS.any { title.contains(it) } ||
                    title.contains(ownAppLabel().lowercase())) ->
                CriticalScreen.KIDGUARD_APP_INFO

            // Экран «Дата и время»: закрываем ЦЕЛИКОМ (как VPN/Accessibility), а не только
            // переключатель «Автоматически» — детект по-конкретному Switch внутри дерева окна
            // сложнее и менее переносим между прошивками, тот же компромисс уже принят для
            // остальных системных экранов. Смена часового пояса без PIN — не проблема, в детском
            // сценарии не нужна.
            in settingsPackages if DATE_TIME_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.DATE_TIME_SETTINGS

            // Список последних живёт в лаунчере и НЕ является отдельной активностью: на HiOS
            // mCurrentFocus остаётся QuickstepLauncher и в режиме списка, и на домашнем экране.
            // Отличает их только заголовок окна («hios launcher recent apps» — снято с телефона
            // Олега 06.09.2026).
            in launcherPackages if RECENTS_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.RECENTS

            else -> null
        }
    }

    /**
     * Диалог-подтверждение включения системного lockdown («Блокировать соединения без VPN»):
     * Android показывает «Использовать сеть VPN? …доступ в Интернет … отсутствует». Ловим по
     * «vpn» в заголовке + характерным фразам (RU/EN). Обычный always-on такого диалога не даёт —
     * значит ложных срабатываний на нём нет.
     */
    private fun isLockdownDialog(title: String): Boolean {
        if (title.isBlank() || !title.contains("vpn")) return false
        return LOCKDOWN_DIALOG_KEYWORDS.any { title.contains(it) }
    }

    /**
     * Дожидается, пока станет видно, что открытый экран — про НАШЕ приложение.
     *
     * Содержимое окна доезжает позже `TYPE_WINDOW_STATE_CHANGED`, поэтому опрашиваем несколько раз.
     * Не нашли за отведённое время — считаем, что экран чужой, и не мешаем: ребёнок должен свободно
     * открывать «О приложении» своих игр. Цена ошибки в эту сторону мала — если он всё-таки полезет
     * дальше (в «Хранилище» нашего приложения), там будет своё событие и своя проверка.
     */
    private suspend fun awaitOwnAppScreen(): Boolean {
        repeat(CONTENT_POLL_ATTEMPTS) {
            if (windowMentionsOwnApp()) return true
            delay(CONTENT_POLL_DELAY_MS.milliseconds)
        }
        return false
    }

    /**
     * Есть ли в дереве активного окна узел с названием нашего приложения — «этот экран про нас».
     * Второй фактор детекта [CriticalScreen.KIDGUARD_APP_INFO] (первый — заголовок).
     *
     * Обходим дерево САМИ, хотя для этого есть штатный `findAccessibilityNodeInfosByText`: на
     * экранах настроек Android 14+ (`SpaActivity`, Compose) он стабильно возвращает 0 совпадений,
     * хотя нужный узел в дереве есть — проверено на эмуляторе (ручной обход находит `KidGuard`
     * с первой попытки, тот же вызов `byText` — ноль). Ручной обход работает.
     *
     * Требует `canRetrieveWindowContent` (задан в конфиге). Recycle узлов не нужен: с API 33
     * (наш minSdk) `AccessibilityNodeInfo.recycle()` — no-op и помечен deprecated.
     */
    private fun windowMentionsOwnApp(): Boolean = try {
        nodeTreeContainsText(rootInActiveWindow, ownAppLabel(), depth = 0)
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Не удалось прочитать содержимое окна")
        false
    }

    /** Рекурсивный поиск текста по дереву. [MAX_TREE_DEPTH] — страховка от глубоких/битых деревьев. */
    private fun nodeTreeContainsText(node: AccessibilityNodeInfo?, text: String, depth: Int): Boolean {
        if (node == null || depth > MAX_TREE_DEPTH) return false
        if (node.text?.contains(text, ignoreCase = true) == true) return true
        for (i in 0 until node.childCount) {
            if (nodeTreeContainsText(node.getChild(i), text, depth + 1)) return true
        }
        return false
    }

    private fun ownAppLabel(): String = packageManager.getApplicationLabel(applicationInfo).toString()

    override fun onInterrupt() {
        // Нет фоновой работы, которую нужно прерывать.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        accessibilityLiveness.onDisconnected()
        mainHandler.removeCallbacksAndMessages(null)
        recentsContentPolling = false
        foregroundAppMonitor.detachStackProbe()
        scope.cancel()
        detachOverlays()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        accessibilityLiveness.onDisconnected()
        mainHandler.removeCallbacksAndMessages(null)
        recentsContentPolling = false
        foregroundAppMonitor.detachStackProbe()
        scope.cancel()
        detachOverlays()
        super.onDestroy()
    }

    /**
     * Отдаём оверлеям обратно «ничего»: окна сервиса больше нет. Ссылка на мёртвый WindowManager
     * переживала отключение сервиса, и попытка показать оверлей падала с `BadTokenException` —
     * а именно в этот момент нужен замок «контроль отключён» (он умеет рисоваться окном приложения).
     */
    private fun detachOverlays() {
        pinOverlayManager.detach()
        warningOverlayManager.detach()
        fullScreenLockOverlayManager.detach()
        breakWarningOverlay.detach()
    }

    private enum class CriticalScreen {
        VPN_SETTINGS,
        ACCESSIBILITY_SETTINGS,
        DEVICE_ADMIN,
        KIDGUARD_UNINSTALL,
        /** «О приложении» и «Хранилище» нашего приложения: очистка данных, force-stop, удаление. */
        KIDGUARD_APP_INFO,
        /** Перевод часов вперёд — обход анти-отмотки, досрочный сброс дневного счётчика. */
        DATE_TIME_SETTINGS,
        /** Список последних приложений: оттуда «Очистить всё» останавливает KidGuard. */
        RECENTS
    }

    private companion object {
        const val TAG = "KidGuardA11y"
        const val UNLOCK_WINDOW_MS = 20_000L

        /**
         * Шаг сверки переднего плана со стеком окон. Залипание снимается за два шага (~3 с): быстрее
         * ребёнок в запрещённом приложении ничего не успеет, а IPC раз в 1,5 с при включённом
         * экране — копейки на фоне потока событий окон.
         */
        const val STACK_SYNC_INTERVAL_MS = 1_500L

        /** Через сколько проверить, закрылось ли служебное окно по «Назад». */
        const val SERVICE_WINDOW_RECHECK_MS = 400L

        /** Шаг опроса окна лаунчера на обзор и сколько опрашивать после последнего события лаунчера. */
        /** Шаг и предел повторного увода с обзора, пока окно замка скрыто поворотом экрана. */
        /** Опрос и таймаут одного шага автозакрепления карточки. */
        /** Пауза перед проверкой значка закрепления и допуск по расстоянию до названия карточки. */
        const val CARD_LOCK_CHECK_DELAY_MS = 350L
        const val CARD_LOCK_ICON_MAX_DISTANCE_PX = 400

        const val AUTOMATION_POLL_MS = 120L
        const val AUTOMATION_STEP_TIMEOUT_MS = 2_500L

        /** На сколько предков подниматься в поисках того, кто примет долгое нажатие. */
        const val LONG_CLICK_PARENT_DEPTH = 4

        /** Длительности настоящих касаний для открытия меню карточки. */
        const val TAP_DURATION_MS = 60L
        const val LONG_PRESS_DURATION_MS = 700L

        /** Стрелка меню карточки в списке последних (HiOS). На других оболочках её просто не найдём. */
        const val RECENTS_CARD_ARROW_ID = "task_arrow"

        /** Строка с названием над карточкой (HiOS) — второй способ открыть меню карточки. */
        const val RECENTS_CARD_TITLE_ROW_ID = "task_top_title_layout"

        const val RECENTS_KEEP_AWAY_STEP_MS = 200L
        const val RECENTS_KEEP_AWAY_WINDOW_MS = 2_500L

        const val RECENTS_CONTENT_POLL_MS = 150L
        const val RECENTS_CONTENT_POLL_WINDOW_MS = 1_200L

        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        // Окно разблокировки списка последних приравнено к остальным экранам (20 с, решение Володи
        // 15.09.2026). Прежние 2 минуты давали ребёнку слишком много времени на «Очистить всё»,
        // если родитель ввёл PIN и передал телефон. 20 с хватает разобрать карточки; после этого
        // обзор снова под PIN. Ниже нуля нельзя: мы сами открываем обзор после верного PIN, и
        // мгновенное окно тут же выбросило бы PIN родителю обратно.
        const val RECENTS_UNLOCK_WINDOW_MS = 20_000L

        // Опрос содержимого окна для [awaitOwnAppScreen]: до ~600 мс. Содержимое доезжает позже
        // события смены окна; за это время ребёнок физически не успеет ничего нажать, а чужие
        // экраны столько не задерживают (просто не совпадут и уйдут).
        const val CONTENT_POLL_ATTEMPTS = 10
        const val CONTENT_POLL_DELAY_MS = 100L
        /** Страховка от зацикливания на битом/глубоком дереве узлов. */
        const val MAX_TREE_DEPTH = 30
        /** Системный resolver — не настройки и не инсталлер, отсеиваем при резолве. */
        const val ANDROID_RESOLVER_PACKAGE = "android"
        // AOSP-значения как ФОЛБЭК к резолву через PackageManager (см. settingsPackages /
        // installerPackages): если резолв почему-то не отработал, детект остаётся на прежнем
        // уровне, а не ломается.
        val AOSP_SETTINGS_PACKAGES = setOf("com.android.settings")
        val AOSP_INSTALLER_PACKAGES =
            setOf("com.google.android.packageinstaller", "com.android.packageinstaller")
        // Ключевые слова в заголовке окна (нижний регистр). Русский — основной язык устройств
        // ребёнка; английский — на случай другой локали. Кросс-вендорно стабильны.
        /**
         * Заголовки списка последних. Набор заведомо неполон и дополняется по факту: у каждого
         * вендора своя формулировка. Проверенное на HiOS 14 — «hios launcher recent apps».
         */
        val RECENTS_KEYWORDS = listOf(
            "recent apps", "recents", "overview", "недавние", "последние приложения"
        )
        val VPN_KEYWORDS = listOf("vpn")
        // «доступность» — заголовок этого экрана на HiOS (снят с реального Tecno KL6, 2026-07-18):
        // Transsion переводит Accessibility иначе, чем AOSP, и без этого ключа экран проскакивал
        // без PIN — ребёнок мог выключить сам сервис.
        val ACCESSIBILITY_KEYWORDS = listOf(
            "специальные возможности", "спец. возможности", "спец возможности",
            "доступность", "accessibility"
        )
        // Экран Device Admin в разных прошивках и падежах называется по-разному:
        // «Администратор устройства», «Приложение администратор‑А устройства» (детальный экран,
        // родительный падеж), «Приложения для администрир‑ОВАНИЯ устройства» (список). Раньше
        // ключом было «администратор устройства» — оно НЕ ловится как подстрока в «администратора
        // устройства» (после «администратор» идёт «а», а не пробел), поэтому детальный экран
        // деактивации проскакивал без PIN. Матчим по ОСНОВАМ слова, а не по точным фразам —
        // так ловятся все падежи и кросс-вендорные варианты.
        val DEVICE_ADMIN_KEYWORDS = listOf(
            "администратор", "администрир", "device admin", "device administrator"
        )
        // Диалог-подтверждение деактивации KidGuard Device Admin.
        // Заголовок: «Отключение защитит телефон от KidGuard-контроля. Для отключения нужен
        // родительский PIN. Отмена ОК». Не содержит «администратор».
        // Требуется ОДНОВРЕМЕННО «kidguard» в заголовке (проверяется отдельно).
        val DEACTIVATION_DIALOG_KEYWORDS = listOf("отключ", "disable")
        // Экраны «О приложении» и «Хранилище» (заголовки сняты с эмулятора: «о приложении»,
        // «хранилище»; с реального Tecno KL6/HiOS: «память» — так там называется экран хранилища
        // приложения). Работают ТОЛЬКО в паре с [windowMentionsOwnApp] — сами по себе эти
        // заголовки одинаковы у всех приложений и у хранилища/памяти устройства.
        val APP_DETAILS_KEYWORDS = listOf(
            "о приложении", "сведения о приложении", "app info", "хранилище", "память", "storage"
        )
        // Экран «Дата и время» (веха 6В): перевод часов ВПЕРЁД ничем не закрыт (анти-отмотка,
        // веха 2, защищает только от отката НАЗАД) — так ребёнок мог искусственно ускорить
        // наступление «нового дня» и обнулить дневной счётчик раньше срока. Заголовок снят
        // живьём с эмулятора: «Дата и время» (com.android.settings/.Settings$DateTimeSettingsActivity).
        val DATE_TIME_KEYWORDS = listOf("дата и время", "date & time", "date and time")
        // Диалог включения lockdown («Использовать сеть VPN?»): заголовок + тело
        // «…доступ в Интернет … отсутствует». Снято живьём с эмулятора (2026-07-19).
        val LOCKDOWN_DIALOG_KEYWORDS = listOf(
            "использовать сеть vpn", "доступ в интернет", "won't have internet", "no internet"
        )
    }
}
