package ru.homelab.kidguard.platform.permissions

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.domain.repository.HealthReportTrigger
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.security.PinVerifyResult
import ru.homelab.kidguard.core.domain.usecase.ControlIntegrityAction
import ru.homelab.kidguard.core.domain.usecase.controlIntegrityAction
import ru.homelab.kidguard.platform.R
import ru.homelab.kidguard.platform.overlay.PinOverlayManager
import ru.homelab.kidguard.platform.warning.WarningNotifier
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Следит за тем, что родительский контроль вообще жив: без разрешения «Специальные возможности»
 * KidGuard не видит активное приложение, а значит не блокирует ничего.
 *
 * Разрешение слетает при ПРИНУДИТЕЛЬНОЙ ОСТАНОВКЕ приложения — Android сам вычищает сервисы пакета
 * из списка включённых (обновление приложения, вопреки ожиданиям, разрешение переживает: проверено).
 * Останавливает приложение и вендорский «оптимизатор», и кнопка «Остановить» в настройках. Отдельно
 * стоит случай, ради которого всё это и делается: ребёнок выключает сервис тумблером и спокойно
 * пользуется телефоном без лимитов.
 *
 * Реакция — [controlIntegrityAction]: сначала пробуем вернуть разрешение сами (если выдано
 * `WRITE_SECURE_SETTINGS`), иначе предупреждаем и через [GRACE_SECONDS] блокируем телефон замком.
 *
 * Замок рисуется тем же [PinOverlayManager], что и перехват настроек, но окном приложения:
 * accessibility-сервиса в этот момент нет, и его окно недоступно. Конфликта между двумя ролями
 * оверлея быть не может — перехват работает только при живом сервисе, замок только при мёртвом.
 */
@Singleton
class AccessibilityGuardController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val pinOverlayManager: PinOverlayManager,
    private val settingsRepository: SettingsRepository,
    private val healthReportTrigger: HealthReportTrigger,
    private val warningNotifier: WarningNotifier,
    private val pinGuard: PinGuard
) {

    /** Момент (elapsedRealtime), когда заметили пропажу; null — разрешение на месте. */
    private var lostAtMillis: Long? = null

    /** До этого момента замок не показываем — ребёнок ушёл включать разрешение или родитель ввёл PIN. */
    private var snoozeUntilMillis: Long = 0

    suspend fun run() {
        Timber.tag(TAG).d("Сторож разрешения контроля запущен")
        while (currentCoroutineContext().isActive) {
            evaluate()
            delay(TICK_SECONDS * 1000L)
        }
    }

    private suspend fun evaluate() {
        val enabled = permissionsManager.isGranted(DevicePermission.ACCESSIBILITY)
        if (enabled) {
            onAccessibilityAlive()
            return
        }
        // Разрешение ещё ни разу не выдавали — идёт первичная настройка, вмешиваться не в чем.
        // `setupCompleted` для этого не подходит: он ставится уже при выборе роли.
        if (!settingsRepository.controlEverConfigured.first()) return

        val now = SystemClock.elapsedRealtime()
        val secondsSinceLost = lostAtMillis?.let { (now - it) / 1000 }
        val action = controlIntegrityAction(
            accessibilityEnabled = false,
            canSelfRestore = permissionsManager.canRestoreAccessibility(),
            secondsSinceLost = secondsSinceLost,
            graceSeconds = GRACE_SECONDS
        )
        if (lostAtMillis == null) {
            lostAtMillis = now
            Timber.tag(TAG).w("Разрешение контроля пропало — сообщаю родителю")
            // Не ждём очередного 15-минутного heartbeat: родитель должен узнать сразу.
            healthReportTrigger.requestNow()
        }

        when (action) {
            ControlIntegrityAction.RESTORE -> restore()
            ControlIntegrityAction.WARN -> warn(secondsSinceLost ?: 0)
            ControlIntegrityAction.LOCK -> lock(now)
            ControlIntegrityAction.NOTHING -> Unit
        }
    }

    /** Разрешение на месте: снимаем всё, что успели показать, и запоминаем, что контроль настроен. */
    private suspend fun onAccessibilityAlive() {
        if (!settingsRepository.controlEverConfigured.first()) {
            settingsRepository.markControlConfigured()
        }
        if (lostAtMillis == null) return

        Timber.tag(TAG).i("Разрешение контроля восстановлено")
        lostAtMillis = null
        snoozeUntilMillis = 0
        warningNotifier.clearControlLostWarning()
        pinOverlayManager.hide()
        healthReportTrigger.requestNow()
    }

    private fun restore() {
        if (permissionsManager.restoreAccessibility()) {
            Timber.tag(TAG).i("Разрешение контроля возвращено автоматически")
        } else {
            // Разрешение на запись есть, но система записать не дала — ведём себя как без него.
            Timber.tag(TAG).w("Автовосстановление не удалось — остаётся обычный путь")
            warn(0)
        }
    }

    private fun warn(secondsSinceLost: Long) {
        val minutesLeft = ((GRACE_SECONDS - secondsSinceLost) / 60).toInt().coerceAtLeast(1)
        warningNotifier.showControlLostWarning(minutesLeft)
    }

    /**
     * Замок. Снимается тремя способами: разрешение вернули (проверка на следующем тике), ребёнок
     * ушёл включать его по ссылке, либо родитель ввёл PIN. Если PIN не задан, [PinVerifyResult]
     * вернёт `NoPinSet` и оверлей пропустит любой код — это осознанный аварийный выход, чтобы
     * телефон нельзя было запереть навсегда.
     */
    private fun lock(now: Long) {
        if (now < snoozeUntilMillis || pinOverlayManager.isShowing()) return
        Timber.tag(TAG).w("Отсрочка вышла, разрешение не вернули — показываю замок")
        warningNotifier.clearControlLostWarning()
        pinOverlayManager.show(
            verifyPin = { entered -> pinGuard.verify(entered) },
            onUnlocked = { snoozeUntilMillis = SystemClock.elapsedRealtime() + PIN_SNOOZE_MILLIS },
            titleRes = R.string.control_lock_title,
            subtitleRes = R.string.control_lock_subtitle,
            action = PinOverlayManager.OverlayAction(R.string.control_lock_action) {
                snoozeUntilMillis = SystemClock.elapsedRealtime() + SETTINGS_SNOOZE_MILLIS
                openAccessibilitySettings()
            }
        )
    }

    /**
     * Уводит на системный экран «Специальные возможности». Запуск активности из фона нам разрешён
     * благодаря `SYSTEM_ALERT_WINDOW` — то же исключение, на котором держатся блокирующие оверлеи.
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.tag(TAG).w(it, "Не удалось открыть настройки специальных возможностей") }
    }

    private companion object {
        const val TAG = "KidGuardControlGuard"
        const val TICK_SECONDS = 15

        /** Сколько ждём после пропажи разрешения, прежде чем заблокировать телефон. */
        const val GRACE_SECONDS = 5L * 60

        /** Пауза после верного PIN — родителю дают время починить телефон без мигающего замка. */
        const val PIN_SNOOZE_MILLIS = 10L * 60 * 1000

        /** Пауза после ухода в настройки: замок не должен перекрывать дорогу к тумблеру. */
        const val SETTINGS_SNOOZE_MILLIS = 2L * 60 * 1000
    }
}
