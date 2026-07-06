package ru.homelab.kidguard.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility-сервис KidGuard.
 *
 * На шаге 1.4 это скелет: он нужен, чтобы мастер разрешений мог проверять, включён ли сервис,
 * и вести пользователя в системные настройки Accessibility. Логику определения активного
 * приложения добавим на шаге 1.5.
 */
class KidGuardAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Реализация — шаг 1.5 (детект активного приложения).
    }

    override fun onInterrupt() {
        // Нет фоновой работы, которую нужно прерывать на данном этапе.
    }
}
