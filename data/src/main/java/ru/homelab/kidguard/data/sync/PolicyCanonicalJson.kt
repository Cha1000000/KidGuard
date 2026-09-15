package ru.homelab.kidguard.data.sync

import kotlinx.serialization.json.Json
import ru.homelab.kidguard.data.network.PolicyDocumentDto

/**
 * Стабильное строковое представление policy-документа для сравнения содержимого: списки и карты
 * приводятся к отсортированному порядку, чтобы перестановка элементов не выглядела изменением.
 *
 * **Строится через `copy()`, а не новым документом с перечислением полей.** Раньше поля
 * перечислялись вручную, и каждое новое поле политики, которое забыли сюда дописать, молча
 * выпадало из сравнения: документ с правкой давал тот же JSON, что и без неё, и `pushIfChanged`
 * решал, что отправлять нечего. Так 15.09.2026 не уезжала на сервер разблокировка дня — родитель
 * нажимал «Разблокировать», у себя видел результат, а до телефона ребёнка команда не доходила.
 * Та же грабля до этого уже случалась с PIN. С `copy()` новое поле попадает в сравнение само;
 * здесь перечисляется только то, что нужно нормализовать.
 */
internal fun canonicalPolicyJson(json: Json, document: PolicyDocumentDto): String = json.encodeToString(
    PolicyDocumentDto.serializer(),
    document.copy(
        dailyLimits = document.dailyLimits.toSortedMap(),
        appLimits = document.appLimits.toSortedMap(),
        whitelist = document.whitelist.sorted(),
        blockedApps = document.blockedApps.sorted(),
        bonuses = document.bonuses.sortedWith(compareBy({ it.date }, { it.packageName })),
        penalties = document.penalties.sortedWith(compareBy({ it.date }, { it.packageName })),
        blockedSites = document.blockedSites.sortedBy { it.domain },
        studySchedule = document.studySchedule.toSortedMap(),
        sleepSchedule = document.sleepSchedule.toSortedMap(),
        emergencyContacts = document.emergencyContacts.sortedBy { it.phone },
        // Порядок часов в множестве смысла не несёт — без сортировки перестановка выглядела бы правкой.
        breaks = document.breaks.copy(hours = document.breaks.hours.sorted())
    )
)
