package ru.homelab.kidguard.core.domain.model

/**
 * Подписка родителя на тревоги об одном ребёнке.
 *
 * Настраивается здесь **не ребёнок, а сам родитель**: у ребёнка может быть двое равноправных
 * родителей, и каждый решает за себя, каким каналом его звать. Поэтому это отдельная модель, а не
 * пара полей в [Child] — иначе настройка выглядела бы свойством ребёнка, каким она не является, и
 * рано или поздно уехала бы в общую политику, где отписка одного родителя отписала бы и второго.
 *
 * Каналы независимы: выключенная шторка не отменяет письма и наоборот.
 */
data class ChildAlertPrefs(
    val childId: Int,
    /** Уведомление в шторку на этом телефоне. */
    val push: Boolean = true,
    /** Письмо с сервера — единственный канал, работающий при закрытом приложении. */
    val email: Boolean = true
)

/** Ребёнок вместе с подпиской текущего родителя на него — строка списка на экране «Оповещения». */
data class ChildAlertRow(
    val child: Child,
    val prefs: ChildAlertPrefs
)

/**
 * Всё, что показывает экран «Оповещения».
 *
 * [accountEmail] — адрес из Google-аккаунта, он же идентификатор родителя; поменять его нельзя.
 * [alertEmail] — куда родитель попросил слать письма вместо него; `null` означает «на адрес
 * аккаунта», и тогда экран показывает [accountEmail] серым плейсхолдером.
 */
data class AlertSettings(
    val accountEmail: String,
    val alertEmail: String?,
    val children: List<ChildAlertRow>
) {
    /** Адрес, на который письма уходят фактически. */
    val effectiveEmail: String get() = alertEmail ?: accountEmail

    /** Ни один ребёнок не оповещается письмом — секцию адреса показывать смысла нет. */
    val emailChannelUnused: Boolean get() = children.isNotEmpty() && children.none { it.prefs.email }
}

/** Итог сохранения адреса: ушло ли на него проверочное письмо (см. `alertEmailService` на сервере). */
data class AlertEmailSaved(
    val alertEmail: String?,
    val verificationSent: Boolean
)
