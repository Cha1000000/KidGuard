package ru.homelab.kidguard.platform.overlay

import ru.homelab.kidguard.core.domain.model.BlockCause

/**
 * Скрывать ли уже показанную мягкую плашку.
 *
 * Плашка показывается по НАБОРУ причин (см. [BlockCause]): «Время учёбы», общий лимит, личный
 * лимит пакета, запрет родителя. Пока причина жива, плашка нужна; исчезла — снимаем.
 *
 * Наивное «скрывать при `block = false`» ломает показ: `enforce` уводит ребёнка «Домой», лаунчер
 * всегда разрешён, решение тут же становится false — и плашка исчезла бы, не дав прочитать текст.
 * Поэтому решение принимается по двум вопросам:
 *
 * 1. **Жива ли ещё причина, по которой плашку показали?** Если нет — показывать нечего.
 * 2. **Заблокирован ли активный пакет сейчас?** Если да — блокировка реальна, плашка нужна
 *    (возможно, с другим текстом — его обновит `showOrUpdate`).
 *
 * И лишь когда оба ответа «нет» — плашку снимаем. Отдельно учитываем, что ребёнок ушёл в ДРУГОЕ
 * разрешённое приложение: плашка не должна накрывать экран, который открыли нарочно. Лаунчер и
 * сам KidGuard — исключение: туда мы уводим ребёнка САМИ, и плашка нужна, чтобы он её прочитал.
 *
 * @param shownCauses причины, по которым плашка показана (для пакета [lastPackage])
 * @param currentShownCauses те же причины, посчитанные для [lastPackage] СЕЙЧАС
 * @param activeCauses причины, посчитанные для текущего активного пакета
 * @param activePackage активный пакет сейчас
 * @param lastPackage пакет, которому показывали плашку
 * @param alwaysAllowed пакеты, которые не блокируются никогда (лаунчер и сам KidGuard)
 */
fun shouldHideSoftOverlay(
    shownCauses: Set<BlockCause>,
    currentShownCauses: Set<BlockCause>,
    activeCauses: Set<BlockCause>,
    activePackage: String?,
    lastPackage: String?,
    alwaysAllowed: Set<String>
): Boolean {
    val activeBlocked = activeCauses.isNotEmpty()
    if (activeBlocked) return false

    // Плашки нет — и скрывать нечего (её и не показывали).
    if (shownCauses.isEmpty()) return false

    // Личный лимит относится к КОНКРЕТНОМУ пакету, поэтому сравнивать надо по имени пакета:
    // причина «личный лимит com.app» не должна погасаться из-за лимита com.other.
    val shownCauseAlive = shownCauses.any { shown ->
        currentShownCauses.any { current ->
            when {
                shown is BlockCause.PersonalLimitExpired && current is BlockCause.PersonalLimitExpired ->
                    shown.packageName == current.packageName
                else -> shown == current
            }
        }
    }
    if (!shownCauseAlive) return true

    // Причина показа жива, но ребёнок смотрит на другое разрешённое приложение: плашка накрывает
    // чужой экран. Лаунчер и сам KidGuard — исключение (туда мы уводим ребёнка сами).
    val movedToAnotherApp = activePackage != null &&
        activePackage != lastPackage &&
        activePackage !in alwaysAllowed
    return movedToAnotherApp
}
