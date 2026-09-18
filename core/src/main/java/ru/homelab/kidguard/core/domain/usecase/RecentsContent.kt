package ru.homelab.kidguard.core.domain.usecase

/**
 * Имена элементов, по которым список последних приложений узнаётся по СОДЕРЖИМОМУ окна лаунчера.
 *
 * Основной признак — заголовок события окна («recent apps»). Но HiOS иногда присылает при открытии
 * обзора заголовок главного экрана («главные экран 1 из 2»), и обзор открывался без замка — поймано
 * серией на телефоне Олега 17.09.2026 (1 промах из ~30 заходов, корзина доступна). Содержимое окна от
 * заголовка не зависит.
 *
 * - `recent_overview_panel`, `ts_btn_recents_clear` — HiOS Launcher (сняты с телефона Олега; на главном
 *   экране их нет даже среди невидимых);
 * - `overview_panel`, `clear_all` — Quickstep из AOSP, на нём основаны лаунчеры большинства оболочек.
 *   В разметке лаунчера панель есть всегда, поэтому учитываются только ВИДИМЫЕ элементы.
 */
private val RECENTS_VIEW_ID_NAMES = listOf("recent_overview_panel", "ts_btn_recents_clear", "overview_panel", "clear_all")

/** Полные id элементов обзора для пакета лаунчера — в форме, которую ждёт `findAccessibilityNodeInfosByViewId`. */
fun recentsViewIdsFor(launcherPackage: String): List<String> =
    RECENTS_VIEW_ID_NAMES.map { "$launcherPackage:id/$it" }

/**
 * Считать ли обзор открытым по проверкам содержимого: только если ДВЕ проверки подряд увидели его
 * элементы. Одна проверка ловила бы хвост анимации закрытия обзора — и лишний PIN на рабочем столе
 * после каждого «Назад».
 */
fun recentsConfirmedByContent(previousCheckSawRecents: Boolean, currentCheckSawRecents: Boolean): Boolean =
    previousCheckSawRecents && currentCheckSawRecents
