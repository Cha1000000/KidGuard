package ru.homelab.kidguard.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.security.PinVerifyResult
import ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService.Companion.MAX_TREE_DEPTH
import ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService.Companion.UNLOCK_WINDOW_MS
import ru.homelab.kidguard.platform.overlay.PinOverlayManager
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * elapsedRealtime() последней успешной проверки PIN для каждого типа критичного экрана.
     * Пока разница с текущим временем меньше [UNLOCK_WINDOW_MS], повторный показ ТОГО ЖЕ
     * типа экрана не требует PIN снова — родитель успевает довести настройку до конца.
     * Разные типы экранов (VPN, accessibility, device admin) требуют отдельного ввода PIN.
     */
    private val lastUnlockedAt = mutableMapOf<CriticalScreen, Long>()

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
        // Отдаём оверлею WindowManager сервиса — только окно accessibility-типа показывается
        // поверх защищённых системных экранов (см. PinOverlayManager).
        getSystemService(WindowManager::class.java)?.let { pinOverlayManager.attach(it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        foregroundAppMonitor.update(packageName)
        Timber.tag(TAG).d("Активное приложение: %s", packageName)

        val title = screenTitle(event)
        // Диагностический лог (только debug — Timber-дерево плантится лишь в debug-сборке): по нему
        // сверяем фактические заголовки критичных системных экранов с ключевыми словами детекта.
        // Пригодится при обкатке на реальном HiOS/других прошивках (часть 6В), где заголовки могут
        // отличаться.
        Timber.tag(TAG).d("Заголовок окна [%s]: %s", packageName, title)

        val criticalScreen = detectCriticalScreen(packageName, title)
        if (criticalScreen != null) {
            maybeInterceptWithPin(criticalScreen)
            return
        }
        // Экран не критичный. Оверлей убираем ТОЛЬКО при реальном уходе на другое приложение
        // (лаунчер и т.п.). НЕ реагируем на: события самого оверлея (наш пакет — иначе оверлей
        // скрыл бы себя своим же window-событием) и под-окна того же хоста настроек/инсталлера
        // (например, всплывающие «значок приложения»), где родитель ещё должен ввести PIN.
        if (pinOverlayManager.isShowing() &&
            packageName != applicationContext.packageName &&
            packageName !in overlayHostPackages
        ) {
            pinOverlayManager.hide()
        }
    }

    private fun maybeInterceptWithPin(screen: CriticalScreen) {
        val lastUnlocked = lastUnlockedAt[screen] ?: 0L
        if (SystemClock.elapsedRealtime() - lastUnlocked < UNLOCK_WINDOW_MS) {
            Timber.tag(TAG).d("Критичный экран %s в окне разблокировки — без PIN", screen)
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
            if (policyRepository.pinProtection.first() == null) {
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

    /** Сырой PIN никуда не хранится. Проверка и счётчик попыток — в общем [PinGuard]. */
    private suspend fun verifyPin(entered: String): PinVerifyResult = pinGuard.verify(entered)

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
        return when {
            packageName in settingsPackages && VPN_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.VPN_SETTINGS

            packageName in settingsPackages && ACCESSIBILITY_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.ACCESSIBILITY_SETTINGS

            // Экран администратора устройства (веха 6.3): деактивация Device Admin снимает
            // системную защиту от удаления — под PIN. Заголовок стабилен кросс-вендорно.
            packageName in settingsPackages && DEVICE_ADMIN_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.DEVICE_ADMIN

            // Диалог-подтверждение деактивации Device Admin (Android показывает перед
            // «Активировать приложение администратора?»). Заголовок НЕ содержит «администратор» —
            // содержит текст вида «Отключение защитит телефон от KidGuard-контроля. Для отключения
            // нужен родительский PIN». Ловим по «kidguard» в заголовке + «отключ» или «disable».
            packageName in settingsPackages &&
                title.contains("kidguard") &&
                DEACTIVATION_DIALOG_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.DEVICE_ADMIN

            // Удаление приложения: окно пакет-инсталлера с названием ИМЕННО нашего приложения
            // (другие приложения ребёнок удаляет свободно — «чистит мусор»). Название берём у
            // системы, чтобы не хардкодить строку и не путать с приложениями, где «kidguard» —
            // лишь часть текста: сравниваем по точному label в заголовке диалога удаления.
            packageName in installerPackages && title.contains(ownAppLabel().lowercase()) ->
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
            packageName in settingsPackages && APP_DETAILS_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.KIDGUARD_APP_INFO

            // Экран «Дата и время»: закрываем ЦЕЛИКОМ (как VPN/Accessibility), а не только
            // переключатель «Автоматически» — детект по-конкретному Switch внутри дерева окна
            // сложнее и менее переносим между прошивками, тот же компромисс уже принят для
            // остальных системных экранов. Смена часового пояса без PIN — не проблема, в детском
            // сценарии не нужна.
            packageName in settingsPackages && DATE_TIME_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.DATE_TIME_SETTINGS

            else -> null
        }
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
        scope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private enum class CriticalScreen {
        VPN_SETTINGS,
        ACCESSIBILITY_SETTINGS,
        DEVICE_ADMIN,
        KIDGUARD_UNINSTALL,
        /** «О приложении» и «Хранилище» нашего приложения: очистка данных, force-stop, удаление. */
        KIDGUARD_APP_INFO,
        /** Перевод часов вперёд — обход анти-отмотки, досрочный сброс дневного счётчика. */
        DATE_TIME_SETTINGS
    }

    private companion object {
        const val TAG = "KidGuardA11y"
        const val UNLOCK_WINDOW_MS = 20_000L
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
        val VPN_KEYWORDS = listOf("vpn")
        val ACCESSIBILITY_KEYWORDS = listOf(
            "специальные возможности", "спец. возможности", "спец возможности", "accessibility"
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
        // «хранилище»). Работают ТОЛЬКО в паре с [windowMentionsOwnApp] — сами по себе эти
        // заголовки одинаковы у всех приложений и у хранилища устройства.
        val APP_DETAILS_KEYWORDS = listOf(
            "о приложении", "сведения о приложении", "app info", "хранилище", "storage"
        )
        // Экран «Дата и время» (веха 6В): перевод часов ВПЕРЁД ничем не закрыт (анти-отмотка,
        // веха 2, защищает только от отката НАЗАД) — так ребёнок мог искусственно ускорить
        // наступление «нового дня» и обнулить дневной счётчик раньше срока. Заголовок снят
        // живьём с эмулятора: «Дата и время» (com.android.settings/.Settings$DateTimeSettingsActivity).
        val DATE_TIME_KEYWORDS = listOf("дата и время", "date & time", "date and time")
    }
}
