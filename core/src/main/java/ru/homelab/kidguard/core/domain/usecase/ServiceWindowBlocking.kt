package ru.homelab.kidguard.core.domain.usecase

/** Доля экрана, меньше которой служебное окно считается «ручкой» у края, а не панелью. */
const val MIN_SERVICE_PANEL_AREA_FRACTION = 0.05f

/** Не закрываем служебные окна чаще — иначе «Назад» полетит очередью в то, что под панелью. */
const val SERVICE_WINDOW_CLOSE_COOLDOWN_MS = 1_000L

/**
 * Закрыть ли служебное (не прикладное) окно запрещённого родителем пакета.
 *
 * Правило «запрещён пакет — запрещены все его окна». Игровая панель HiOS (`com.transsion.smartpanel`)
 * выдвигается свайпом от края прямо поверх игры и держит кнопку очистки, которая force-stop'ит весь фон,
 * включая KidGuard (телефон Олега, 17.09.2026). Это не активность, а служебное окно, и обычная
 * блокировка по активному приложению его не видела — запрет пакета не действовал. То же устройство у
 * игровых панелей других оболочек (Game Turbo, Game Booster): закрываются одним правилом, без списка.
 *
 * Не трогаем:
 * - прикладные окна — их закрывает обычный путь блокировки;
 * - клавиатуру — «Назад» по ней ломал бы набор текста в разрешённых приложениях;
 * - защищённые пакеты (KidGuard, лаунчер, системная оболочка) — даже если родитель их запретил по ошибке;
 * - окно неизвестного размера и маленькое окно: у панели есть постоянная «ручка» у края (42×246 на
 *   Tecno), и реакция на неё отправляла бы «Назад» в игру при каждом её появлении.
 *
 * @param windowAreaFraction доля площади экрана, занятая окном; `null` — размер узнать не удалось.
 * @param millisSinceLastClose сколько прошло с прошлого закрытия служебного окна.
 */
fun shouldCloseBlockedServiceWindow(
    packageName: String,
    windowKind: WindowKind?,
    windowAreaFraction: Float?,
    blockedApps: Set<String>,
    protectedPackages: Set<String>,
    millisSinceLastClose: Long
): Boolean {
    if (packageName !in blockedApps || packageName in protectedPackages) return false
    if (windowKind == WindowKind.APPLICATION || windowKind == WindowKind.INPUT_METHOD) return false
    if (millisSinceLastClose < SERVICE_WINDOW_CLOSE_COOLDOWN_MS) return false
    val area = windowAreaFraction ?: return false
    return area >= MIN_SERVICE_PANEL_AREA_FRACTION
}
