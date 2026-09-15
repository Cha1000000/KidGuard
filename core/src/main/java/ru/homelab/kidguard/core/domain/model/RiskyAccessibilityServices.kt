package ru.homelab.kidguard.core.domain.model

/**
 * Посторонние службы доступности, опасные для контроля.
 *
 * Сейчас это «Меню спец. возможностей» в любом варианте: системное (AOSP/HiOS,
 * `com.android.systemui.accessibility.accessibilitymenu`) и из Android Accessibility Suite
 * (`com.google.android.accessibility.accessibilitymenu`). В его меню есть действие «Недавние
 * приложения», которым 15.09.2026 на телефоне Олега открывали список последних в обход PIN-замка и
 * останавливали KidGuard кнопкой «Очистить всё».
 *
 * Прочие службы (TalkBack, «Переключение доступа» и т.п.) опасными не считаются: ребёнку они могут
 * быть нужны, о них только сообщаем родителю, но не выключаем.
 */
object RiskyAccessibilityServices {

    /**
     * Опасна ли служба. [component] — как она записана в настройках: `пакет/класс` или
     * `пакет/.Класс`. Сверяем по фрагменту пакета и имени класса, а не по точной строке: у вендоров
     * пакет меню отличается префиксом, а класс называется одинаково.
     */
    fun isRisky(component: String): Boolean {
        val normalized = component.trim()
        if (normalized.isEmpty()) return false
        val pkg = normalized.substringBefore('/')
        val cls = normalized.substringAfter('/', missingDelimiterValue = "")
        return pkg.endsWith(".accessibilitymenu") || cls.endsWith("AccessibilityMenuService")
    }

    /**
     * Службы из строковой настройки (список через `:`, как `enabled_accessibility_services`),
     * кроме собственной службы KidGuard (её пакет — [ownPackage]).
     */
    fun foreignIn(settingValue: String?, ownPackage: String): List<String> =
        settingValue.orEmpty()
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.substringBefore('/') != ownPackage }

    /**
     * Та же строковая настройка без опасных служб; `null`, если убирать нечего (чтобы не писать в
     * настройки без нужды). Порядок и остальные службы — включая KidGuard — сохраняются.
     */
    fun withoutRisky(settingValue: String?): String? {
        val parts = settingValue.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }
        val kept = parts.filterNot { isRisky(it) }
        return if (kept.size == parts.size) null else kept.joinToString(":")
    }
}
