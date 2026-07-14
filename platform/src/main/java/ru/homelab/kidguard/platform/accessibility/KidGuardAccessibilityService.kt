package ru.homelab.kidguard.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.security.PinHasher
import ru.homelab.kidguard.platform.overlay.PinOverlayManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Accessibility-сервис KidGuard.
 *
 * 1. Определяет активное (foreground) приложение по событиям смены окна и публикует его в
 *    [ForegroundAppMonitor] — фундамент под учёт экранного времени и блокировку (вехи 2–3).
 * 2. Точечно перехватывает открытие критичных системных экранов (веха 6, шаг 6.2): настройки VPN,
 *    «Специальные возможности» (чтобы ребёнок не отключил сам этот сервис) и удаление именно
 *    приложения KidGuard (другие приложения, включая игры, ребёнок удаляет свободно — «чистит
 *    мусор»). Детект — по ЗАГОЛОВКУ окна (`event.text`), кросс-вендорно; накрывает экран
 *    PIN-оверлеем типа `TYPE_ACCESSIBILITY_OVERLAY` (обычный оверлей на этих защищённых экранах
 *    система скрывает). Верный PIN пропускает на короткое окно, «Назад» уводит.
 *
 * Защита деактивации Device Admin — следующий шаг 6.3.
 */
@AndroidEntryPoint
class KidGuardAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var foregroundAppMonitor: ForegroundAppMonitor

    @Inject
    lateinit var policyRepository: PolicyRepository

    @Inject
    lateinit var pinOverlayManager: PinOverlayManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * elapsedRealtime() последней успешной проверки PIN. Пока разница с текущим временем меньше
     * [UNLOCK_WINDOW_MS], повторный показ критичного экрана не требует PIN снова — родитель
     * успевает довести настройку до конца (например, реально отключить/включить VPN-тумблер).
     */
    @Volatile
    private var lastUnlockedAt = 0L

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

        val criticalScreen = detectCriticalScreen(event, packageName)
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
            packageName !in OVERLAY_HOST_PACKAGES
        ) {
            pinOverlayManager.hide()
        }
    }

    private fun maybeInterceptWithPin(screen: CriticalScreen) {
        if (SystemClock.elapsedRealtime() - lastUnlockedAt < UNLOCK_WINDOW_MS) {
            Timber.tag(TAG).d("Критичный экран %s в окне разблокировки — без PIN", screen)
            return
        }
        scope.launch {
            if (policyRepository.pinProtection.first() == null) {
                // PIN не задан родителем — защита не настроена, не перехватываем.
                return@launch
            }
            Timber.tag(TAG).d("Критичный экран %s — показываю PIN-оверлей", screen)
            pinOverlayManager.show(
                verifyPin = ::verifyPin,
                onUnlocked = { lastUnlockedAt = SystemClock.elapsedRealtime() },
                onCancel = { performGlobalAction(GLOBAL_ACTION_BACK) }
            )
        }
    }

    /** Сырой PIN никуда не хранится — только сравнение введённого с hash+salt из политики. */
    private suspend fun verifyPin(entered: String): Boolean {
        val protection = policyRepository.pinProtection.first() ?: return false
        return PinHasher.verify(entered, protection.salt, protection.hash)
    }

    /**
     * Детект критичных экранов — по ТЕКСТУ ЗАГОЛОВКА окна (`event.text`), а не по имени
     * класса активности. Класс у системных настроек прошивко-зависим (на Android 14+ многие
     * экраны идут через общий `SpaActivity`/`Settings`), а вот заголовок окна («Настройки VPN»,
     * «Специальные возможности») стабилен на разных устройствах и вендорах — поэтому детект
     * универсален (Tecno, Xiaomi, Samsung и т.д.), а не подогнан под одну модель.
     *
     * `event.text` для `TYPE_WINDOW_STATE_CHANGED` — это заголовок/подпись окна, а не пункты
     * списка, поэтому главный экран настроек (где «Спец. возможности» — лишь строка меню) под
     * PIN не попадает: там заголовок «Настройки».
     */
    private fun detectCriticalScreen(event: AccessibilityEvent, packageName: String): CriticalScreen? {
        val title = event.text.joinToString(" ").lowercase()
        if (title.isBlank()) return null
        return when {
            packageName == SETTINGS_PACKAGE && VPN_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.VPN_SETTINGS

            packageName == SETTINGS_PACKAGE && ACCESSIBILITY_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.ACCESSIBILITY_SETTINGS

            // Удаление приложения: окно пакет-инсталлера с названием ИМЕННО нашего приложения
            // (другие приложения ребёнок удаляет свободно — «чистит мусор»). Название берём у
            // системы, чтобы не хардкодить строку и не путать с приложениями, где «kidguard» —
            // лишь часть текста: сравниваем по точному label в заголовке диалога удаления.
            packageName in PACKAGE_INSTALLER_PACKAGES && title.contains(ownAppLabel().lowercase()) ->
                CriticalScreen.KIDGUARD_UNINSTALL

            else -> null
        }
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

    private enum class CriticalScreen { VPN_SETTINGS, ACCESSIBILITY_SETTINGS, KIDGUARD_UNINSTALL }

    private companion object {
        const val TAG = "KidGuardA11y"
        const val UNLOCK_WINDOW_MS = 60_000L
        const val SETTINGS_PACKAGE = "com.android.settings"
        val PACKAGE_INSTALLER_PACKAGES =
            setOf("com.google.android.packageinstaller", "com.android.packageinstaller")
        // Пока активный экран принадлежит настройкам/инсталлеру, PIN-оверлей держим (родитель
        // вводит PIN); их под-окна не должны его случайно закрывать.
        val OVERLAY_HOST_PACKAGES = setOf(SETTINGS_PACKAGE) + PACKAGE_INSTALLER_PACKAGES
        // Ключевые слова в заголовке окна (нижний регистр). Русский — основной язык устройств
        // ребёнка; английский — на случай другой локали. Кросс-вендорно стабильны.
        val VPN_KEYWORDS = listOf("vpn")
        val ACCESSIBILITY_KEYWORDS = listOf(
            "специальные возможности", "спец. возможности", "спец возможности", "accessibility"
        )
    }
}
