package ru.homelab.kidguard.data.network

import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.DeviceHealth
import ru.homelab.kidguard.core.domain.model.ProcessExitKind
import ru.homelab.kidguard.core.domain.model.ProcessExitRecord
import timber.log.Timber
import java.time.Instant

/**
 * Разбор [ChildDto] в доменного [Child] — раньше жил приватным методом внутри `ChildRepositoryImpl`,
 * но с экраном «Оповещения» тот же список детей понадобился второй раз (`AlertSettingsRepositoryImpl`
 * достаёт детей вместе с настройками уведомлений, и ему тоже нужны lastSeenAt/health для карточки
 * ребёнка). Держать два места, разбирающих один и тот же DTO, означало бы чинить парсинг дважды при
 * следующей правке контракта — поэтому маппер вынесен сюда и `internal`, чтобы им мог пользоваться
 * любой репозиторий модуля `:data`, не привязываясь к конкретной реализации ChildRepository.
 */
internal fun ChildDto.toDomain(): Child = Child(
    id = id,
    name = name,
    avatar = avatar,
    paired = paired,
    lastSeenAt = parseLastSeen(lastSeenAt),
    health = health?.toDomain(),
    hasCoParent = hasCoParent
)

/**
 * Кривую метку времени от сервера глотаем: список детей — основной экран родителя, ронять его
 * из-за необязательного watchdog-поля нельзя. Родитель увидит «нет данных», а не ошибку.
 */
private fun parseLastSeen(raw: String?): Instant? = raw?.let {
    runCatching { Instant.parse(it) }
        .onFailure { e -> Timber.w(e, "Не разобрал lastSeenAt: %s", raw) }
        .getOrNull()
}

private fun DeviceHealthDto.toDomain() = DeviceHealth(
    accessibility = accessibility,
    overlay = overlay,
    deviceAdmin = deviceAdmin,
    vpn = vpn,
    batteryOptimization = batteryOptimization,
    lastExit = toLastExit()
)

/**
 * Причина смерти нужна целиком или никак: без времени её нечего показать родителю, а незнакомое
 * имя вида нового ProcessExitKind (ребёнок обновился раньше родителя) не должно ронять список
 * детей — в этом случае просто считаем, что данных нет.
 */
private fun DeviceHealthDto.toLastExit(): ProcessExitRecord? {
    val kindName = lastExitKind ?: return null
    val at = lastExitAt?.let { raw ->
        runCatching { Instant.parse(raw) }
            .onFailure { e -> Timber.w(e, "Не разобрал lastExitAt: %s", raw) }
            .getOrNull()
    } ?: return null
    val kind = runCatching { ProcessExitKind.valueOf(kindName) }.getOrNull() ?: return null
    return ProcessExitRecord(
        at = at,
        kind = kind,
        description = lastExitDescription.orEmpty(),
        // Сырые коды по сети не возим: родителю они не нужны, а для диагностики есть description.
        rawReason = 0,
        rawSubreason = 0
    )
}
