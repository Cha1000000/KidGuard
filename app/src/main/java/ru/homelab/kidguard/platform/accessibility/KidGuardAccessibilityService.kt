package ru.homelab.kidguard.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Accessibility-сервис KidGuard: определяет активное (foreground) приложение по событиям смены
 * окна и публикует его в [ForegroundAppMonitor]. Это фундамент под учёт экранного времени и
 * блокировку (вехи 2–3). Определяет любые приложения, включая системные и Play Market.
 */
@AndroidEntryPoint
class KidGuardAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var foregroundAppMonitor: ForegroundAppMonitor

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        foregroundAppMonitor.update(packageName)
        Timber.tag(TAG).d("Активное приложение: %s", packageName)
    }

    override fun onInterrupt() {
        // Нет фоновой работы, которую нужно прерывать.
    }

    private companion object {
        const val TAG = "KidGuardA11y"
    }
}
