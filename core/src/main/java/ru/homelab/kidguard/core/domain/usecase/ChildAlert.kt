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
 */
data class ChildAlert(
    val childId: Int,
    val childName: String,
    val brokenPermissions: List<DevicePermission>,
    val silent: Boolean
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
    val silent = broken.isEmpty()
    val wasBroken = previous?.isControlBroken(now) == true
    val previousBroken = previous?.health?.brokenPermissions().orEmpty()
    // Повторяем, только если набор поломок расширился: то же самое родитель уже видел.
    if (wasBroken && broken.toSet().minus(previousBroken.toSet()).isEmpty()) return null

    return ChildAlert(
        childId = current.id,
        childName = current.name,
        brokenPermissions = broken,
        silent = silent
    )
}
