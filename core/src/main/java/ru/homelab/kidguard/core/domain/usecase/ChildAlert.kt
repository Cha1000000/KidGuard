package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.DevicePermission
import java.time.Instant

/**
 * Повод потревожить родителя уведомлением: с телефоном ребёнка что-то не так прямо сейчас.
 *
 * @param brokenPermissions что именно отвалилось; пусто при [silent] — устройство молчит и
 *   рассказать о себе не может.
 * @param silent устройство не выходит на связь дольше порога: контроль убит целиком (приложение
 *   остановлено, удалено или очищены данные), либо телефон просто выключен.
 * @param riskyAccessibilityMenu на телефоне включено «Меню спец. возможностей» — через его действие
 *   «Недавние приложения» открывали список последних в обход PIN-замка (15.09.2026).
 */
data class ChildAlert(
    val childId: Int,
    val childName: String,
    val brokenPermissions: List<DevicePermission>,
    val silent: Boolean,
    val riskyAccessibilityMenu: Boolean = false
)

/**
 * О чём стоит уведомить родителя, сравнивая прошлое состояние ребёнка с текущим.
 *
 * Уведомляем только на ПЕРЕХОДЕ «было в порядке → сломалось», иначе фоновая проверка сыпала бы
 * одинаковыми уведомлениями каждые 15 минут, и родитель отключил бы их через день. Обратный
 * переход (починили) уведомления не требует — родитель и так узнает, открыв приложение.
 *
 * Отдельный повод — если сломалось ЕЩЁ ОДНО разрешение поверх уже сломанного: набор изменился,
 * значит на телефоне ребёнка происходит что-то новое, и молчать об этом неправильно.
 *
 * @param previous состояние с прошлой проверки; `null` — проверяем впервые (после установки или
 *   входа), тогда о текущих поломках сообщаем сразу: родитель их ещё не видел.
 */
fun childAlert(previous: Child?, current: Child, now: Instant): ChildAlert? {
    if (!current.paired) return null
    if (!current.isControlBroken(now)) return null

    val broken = current.health?.brokenPermissions().orEmpty()
    val risky = current.health?.riskyAccessibilityServices().orEmpty().isNotEmpty()
    // «Молчит» — только когда нет ни сломанных разрешений, ни опасного меню: включённое меню
    // означает, что телефон на связи и сам о нём доложил.
    val silent = broken.isEmpty() && !risky
    val wasBroken = previous?.isControlBroken(now) == true
    val previousBroken = previous?.health?.brokenPermissions().orEmpty()
    val wasRisky = previous?.health?.riskyAccessibilityServices().orEmpty().isNotEmpty()
    // Повторяем, только если набор поломок расширился: то же самое родитель уже видел. Появившееся
    // меню — тоже расширение набора.
    val newBroken = broken.toSet().minus(previousBroken.toSet()).isNotEmpty()
    val newRisky = risky && !wasRisky
    if (wasBroken && !newBroken && !newRisky) return null

    return ChildAlert(
        childId = current.id,
        childName = current.name,
        brokenPermissions = broken,
        silent = silent,
        riskyAccessibilityMenu = risky
    )
}
