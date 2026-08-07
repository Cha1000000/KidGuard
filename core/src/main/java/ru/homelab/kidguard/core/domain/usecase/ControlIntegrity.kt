package ru.homelab.kidguard.core.domain.usecase

/** Что делать, когда родительский контроль на детском устройстве потерял разрешение. */
enum class ControlIntegrityAction {
    /** Всё в порядке (или ещё нечего чинить) — не вмешиваемся. */
    NOTHING,

    /** Разрешение можно вернуть самим — восстанавливаем молча. */
    RESTORE,

    /** Предупредили ребёнка и родителя, ждём — идёт отсрочка. */
    WARN,

    /** Отсрочка вышла, разрешение не вернули — закрываем телефон замком. */
    LOCK
}

/**
 * Чистое правило: как реагировать на пропажу accessibility-разрешения.
 *
 * Разрешение слетает не «при обновлении» (обновление его переживает — проверено), а при
 * ПРИНУДИТЕЛЬНОЙ ОСТАНОВКЕ приложения: Android при force-stop сам вычищает сервисы пакета из
 * `ENABLED_ACCESSIBILITY_SERVICES`. Это делает и вендорский «оптимизатор» (у Tecno — PhoneMaster),
 * и кнопка «Остановить» в настройках, и — что важнее — ребёнок может просто выключить сервис
 * тумблером, сняв тем самым все лимиты.
 *
 * Поэтому реакция двухступенчатая. Если приложению выдали `WRITE_SECURE_SETTINGS` (разово, с
 * компьютера), оно чинит разрешение само и человека не беспокоит вовсе. Если нет — сначала
 * отсрочка с предупреждением (родитель уже получил сигнал, разрешение могло слететь не по злому
 * умыслу), и только потом замок: иначе выключение одного тумблера остаётся простым способом
 * пользоваться телефоном без ограничений.
 *
 * @param secondsSinceLost сколько секунд назад заметили пропажу; `null` — не терялось.
 */
fun controlIntegrityAction(
    accessibilityEnabled: Boolean,
    canSelfRestore: Boolean,
    secondsSinceLost: Long?,
    graceSeconds: Long
): ControlIntegrityAction = when {
    accessibilityEnabled -> ControlIntegrityAction.NOTHING
    canSelfRestore -> ControlIntegrityAction.RESTORE
    secondsSinceLost == null -> ControlIntegrityAction.WARN
    secondsSinceLost >= graceSeconds -> ControlIntegrityAction.LOCK
    else -> ControlIntegrityAction.WARN
}
