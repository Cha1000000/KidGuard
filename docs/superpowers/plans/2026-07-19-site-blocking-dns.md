# Запрет сайтов (DNS-фильтрация) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development или superpowers:executing-plans. Шаги — чекбоксы `- [ ]`.

**Goal:** Родитель задаёт чёрный список доменов + тумблер «Блокировать поиск Google»; на детском устройстве это исполняется DNS-фильтром поверх нашего VpnService, работает везде и держится до снятия галки.

**Architecture:** Правила едут в policy-документе (сервер не трогаем). Детский `KidGuardVpnService` получает режим DNS-фильтра: split-tunnel (в tun только DNS), парсит запросы, блокирует по правилам (суффикс для списка, exact для google), остальное форвардит на upstream. UI — новый экран на «Правила».

**Tech Stack:** Kotlin/Compose, Hilt, Room; Android `VpnService` (tun, protected sockets); kotlinx.serialization (sync). Спека: `docs/superpowers/specs/2026-07-19-site-blocking-dns-design.md`.

## Global Constraints

- **Сервер не трогаем** (policy-документ непрозрачен). Синк — через существующий policy JSON.
- **Обратная совместимость политики:** новые поля с дефолтами (пустой список, `blockGoogleSearch=false`), чтобы старый детский клиент/старый документ не ломались (`ignoreUnknownKeys` уже есть).
- **Только домены** (URL→хост); голые IP вне v1. **DoH/Private DNS** не боремся — честная оговорка в UI.
- **Матчинг:** список — по суффиксу (`vk.com`→`m.vk.com`); google-тумблер — exact `google.com`/`www.google.com`.
- UI-экран — **сперва макет** в `docs/ui-concepts/site-blocking/`, затем код.
- Тестировать enforcement на **детском эмуляторе, профиль ребёнка Alina** (НЕ Олег) — чтобы не задеть синхронизацию с реальным телефоном Олега.
- Отступы как в файлах; SOLID/Clean; без хардкод-строк UI.

---

## Фаза A — Ядро-исполнение (enforcement) + спайк lockdown

> Самая рисковая часть — делаем первой и валидируем на устройстве до плумбинга политики.
> Для спайка правила берём временно захардкоженные (напр. `vk.com`), потом заменим политикой (Фаза B).

### Task A1: Чистая логика матчинга правил (core) — TDD

**Files:**
- Create: `core/src/main/java/ru/homelab/kidguard/core/domain/model/SiteBlockRules.kt`
- Create: `core/src/main/java/ru/homelab/kidguard/core/domain/usecase/DomainNormalizer.kt`
- Test: `core/src/test/java/ru/homelab/kidguard/core/domain/model/SiteBlockRulesTest.kt`
- Test: `core/src/test/java/ru/homelab/kidguard/core/domain/usecase/DomainNormalizerTest.kt`

**Interfaces (Produces):**
- `data class SiteBlockRules(val blockedDomains: Set<String>, val blockGoogleSearch: Boolean) { fun isBlocked(host: String): Boolean }`
- `object DomainNormalizer { fun normalize(input: String): String? }`

- [ ] **Step 1: Тесты матчинга.**
```kotlin
class SiteBlockRulesTest {
    @Test fun `суффиксный матч блокирует домен и поддомены`() {
        val r = SiteBlockRules(setOf("vk.com"), blockGoogleSearch = false)
        assertTrue(r.isBlocked("vk.com"))
        assertTrue(r.isBlocked("m.vk.com"))
        assertTrue(r.isBlocked("api.vk.com"))
        assertFalse(r.isBlocked("myvk.com"))   // не суффикс по точке
        assertFalse(r.isBlocked("vk.com.evil.com"))
    }
    @Test fun `google-тумблер — только точный google_com и www`() {
        val r = SiteBlockRules(emptySet(), blockGoogleSearch = true)
        assertTrue(r.isBlocked("google.com"))
        assertTrue(r.isBlocked("www.google.com"))
        assertFalse(r.isBlocked("mail.google.com")) // Gmail не трогаем
        assertFalse(r.isBlocked("play.google.com"))
    }
    @Test fun `регистр и завершающая точка нормализуются`() {
        val r = SiteBlockRules(setOf("vk.com"), false)
        assertTrue(r.isBlocked("M.VK.COM."))
    }
    @Test fun `пустые правила ничего не блокируют`() {
        assertFalse(SiteBlockRules(emptySet(), false).isBlocked("vk.com"))
    }
}
```
- [ ] **Step 2: Прогнать — падают** (`./gradlew :core:test`).
- [ ] **Step 3: Реализация `SiteBlockRules`.**
```kotlin
data class SiteBlockRules(
    val blockedDomains: Set<String>,   // только enabled-домены, уже нормализованные
    val blockGoogleSearch: Boolean
) {
    fun isBlocked(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (blockGoogleSearch && (h == "google.com" || h == "www.google.com")) return true
        return blockedDomains.any { e -> h == e || h.endsWith(".$e") }
    }
    val isActive: Boolean get() = blockGoogleSearch || blockedDomains.isNotEmpty()
    companion object { val NONE = SiteBlockRules(emptySet(), false) }
}
```
- [ ] **Step 4: Тесты нормализатора.**
```kotlin
class DomainNormalizerTest {
    @Test fun `срезает схему путь порт, нижний регистр`() {
        assertEquals("vk.com", DomainNormalizer.normalize("HTTPS://VK.com/feed?x=1"))
        assertEquals("m.vk.com", DomainNormalizer.normalize("m.vk.com:443"))
        assertEquals("vk.com", DomainNormalizer.normalize("  vk.com.  "))
    }
    @Test fun `невалидное — null`() {
        assertNull(DomainNormalizer.normalize(""))
        assertNull(DomainNormalizer.normalize("no-dot"))
        assertNull(DomainNormalizer.normalize("bad space.com"))
    }
}
```
- [ ] **Step 5: Реализация `DomainNormalizer`** (strip scheme `substringAfter("://")` при наличии, `substringBefore('/')`, `substringBefore('?')`, `substringBefore(':')`, `trim().trim('.')`; валидность: содержит '.', только `a-z0-9.-`).
- [ ] **Step 6: `./gradlew :core:test` — зелёные.** Commit.

### Task A2: Парсер DNS-пакетов и сборка NXDOMAIN (platform) — TDD где можно

**Files:**
- Create: `platform/src/main/java/ru/homelab/kidguard/platform/vpn/dns/DnsPacket.kt` (парс QNAME из DNS-payload; сборка ответа NXDOMAIN по запросу)
- Create: `platform/src/main/java/ru/homelab/kidguard/platform/vpn/dns/IpUdpPacket.kt` (разбор IPv4+UDP заголовков; сборка ответного IPv4+UDP пакета со swapped src/dst)

**Interfaces (Produces):**
- `DnsPacket.parseQName(dnsPayload: ByteArray): String?`
- `DnsPacket.buildResponse(query: ByteArray, rcode: Int): ByteArray` (rcode=3 NXDOMAIN: копия заголовка запроса с QR=1, RCODE=3, ANCOUNT=0, вопрос сохранён)
- `IpUdpPacket`: разбор `version/ihl`, protocol==17 (UDP), src/dst IP+port, udp payload; `buildIpv4Udp(srcIp,srcPort,dstIp,dstPort,payload)` с корректными длинами и чек-суммами (IP-checksum обязателен; UDP-checksum допустимо 0 для IPv4).

- [ ] **Step 1: Тест QNAME-парсинга** (собрать байты запроса `vk.com` вручную, распарсить → "vk.com"). Прогнать (`:platform` юнит-тесты в JVM, без Android API — DnsPacket/IpUdpPacket чистые на ByteArray).
- [ ] **Step 2: Реализация `DnsPacket`** (QNAME: последовательность label-length+label до нулевого байта; ответ: заголовок 12 байт, флаги `0x8183`, ANCOUNT=0, копировать секцию вопроса).
- [ ] **Step 3: Тест сборки IPv4/UDP** (IP-checksum корректный; round-trip разбор). Реализация `IpUdpPacket`.
- [ ] **Step 4: `./gradlew :platform:testDebugUnitTest` (или :platform:test) — зелёные.** Commit.

### Task A3: Режим DNS-фильтра в KidGuardVpnService

**Files:**
- Modify: `platform/src/main/java/ru/homelab/kidguard/platform/vpn/KidGuardVpnService.kt`
- Create: `platform/src/main/java/ru/homelab/kidguard/platform/vpn/dns/DnsProxyLoop.kt` (цикл чтения tun + форвардинг)

**Суть:** добавить второй режим tun — DNS-фильтр (split-tunnel):
- Новый action/extra: `ACTION_START` с режимом `MODE_DNS_FILTER` + сериализованные правила (`blockedDomains`, `blockGoogleSearch`); режим `MODE_BLACKHOLE` — прежнее поведение (лимит исчерпан).
- В DNS-режиме `Builder`:
  - `addAddress(TUN_ADDRESS,32)`, `addDnsServer(DNS_VIRTUAL_IP)` (напр. `10.111.222.2`),
    `addRoute(DNS_VIRTUAL_IP, 32)` — **только DNS в tun**, без default route (остальное — напрямую).
- Поднять поток `DnsProxyLoop`:
  - читать пакеты из `FileInputStream(tunFd.fileDescriptor)`;
  - `IpUdpPacket` разобрать; если UDP dstPort==53:
    - `DnsPacket.parseQName` → `SiteBlockRules.isBlocked(host)`:
      - blocked → собрать NXDOMAIN-ответ (`DnsPacket.buildResponse(query,3)`), обернуть в
        IPv4/UDP со swapped адресами (dst→src), записать в `FileOutputStream(tunFd)`;
      - иначе → отправить оригинальный udp payload на **реальный upstream** через
        `DatagramSocket` c `protect(socket)` (upstream: системный DNS или `8.8.8.8`), прочитать
        ответ, обернуть обратно в tun.
  - не-DNS пакеты в этом режиме в tun не попадают (маршрут только на DNS_VIRTUAL_IP).
- Корректно останавливать поток при смене режима/закрытии tun.

- [ ] **Step 1:** Реализовать переключение режимов и DNS-Builder в `KidGuardVpnService`.
- [ ] **Step 2:** Реализовать `DnsProxyLoop` (чтение/запись tun, форвардинг через protected-сокет, NXDOMAIN для блокированных). Правила передавать в конструктор/через `@Volatile` для горячей замены.
- [ ] **Step 3: Сборка `:app:assembleDebug` зелёная.**

### Task A4: 🔬 СПАЙК на детском эмуляторе (критично, до Фазы B)

**Цель:** валидировать подход и главный риск (lockdown) ДО плумбинга политики.

- [ ] **Step 1:** Временно захардкодить в `VpnController` запуск DNS-режима с правилом `vk.com` + `blockGoogleSearch=true` (когда время доступно), вместо pass-through. Собрать, поставить на **детский эмулятор**.
- [ ] **Step 2:** Проверить (adb/скриншот/браузер на эмуляторе): `vk.com` не резолвится/недоступен, `google.com` недоступен, `mail.google.com` и прочий интернет — работают. Логи `DnsProxyLoop`.
- [ ] **Step 3: lockdown ON/OFF.** Проверить поведение при системном always-on lockdown вкл и выкл (интернет для не-DNS трафика).
- [ ] **Step 4: РЕШЕНИЕ.** Если split-tunnel работает (хотя бы при lockdown OFF, и приемлемо при ON) → продолжаем Фазу B. **Если при lockdown ON интернет ломается и это критично** → ОСТАНОВИТЬСЯ, зафиксировать вывод, доложить Володе (не строить tun2socks молча). Откатить хардкод после спайка.

---

## Фаза B — Политика: модель, хранение, синк

> Плумбинг по образцу существующего `blockedApps` (веха 4.1.2). Мест — как у blockedApps.

### Task B1: Доменная модель + PolicyRepository

**Files:**
- Modify: `core/.../domain/repository/PolicyRepository.kt` (+ `val blockedSites: Flow<List<BlockedSite>>`, `val blockGoogleSearch: Flow<Boolean>`, сеттеры `setSiteBlocked(domain, enabled)`/`addSite(domain)`/`removeSite(domain)`/`setBlockGoogleSearch(Boolean)`; расширить `replaceAll`)
- Create: `core/.../domain/model/BlockedSite.kt` — `data class BlockedSite(val domain: String, val enabled: Boolean)`

- [ ] Реализовать интерфейсные изменения; собрать core.

### Task B2: Хранение (Room) + маппинг в SiteBlockRules

**Files:**
- Modify: `data/.../policy/PolicyRepositoryImpl.kt` — хранить список сайтов (Room: таблица `blocked_sites(domain PK, enabled)` или JSON-поле в policy-entity — выбрать по образцу текущего хранения blockedApps; следовать существующему паттерну в этом файле/`PolicyDao`)
- Modify: `data/.../db/...` (entity/dao + миграция Room, идемпотентно) при необходимости отдельной таблицы
- Экспонировать flow `SiteBlockRules` (enabled-домены + google-флаг) для VpnController — можно как derived flow в core/usecase.

- [ ] Реализовать; юнит-тесты data при наличии паттерна. Собрать.

### Task B3: Синхронизация (policy-документ, без сервера)

**Files:**
- Modify: `data/.../network/PolicyApi.kt` — policy-DTO: `blockedSites: List<BlockedSiteDto> = emptyList()`, `blockGoogleSearch: Boolean = false` (дефолты → обратная совместимость)
- Modify: `data/.../sync/SyncRepositoryImpl.kt` — включить новые поля в push/pull/`replaceAll`/canonical snapshot
- Modify: `PolicyRepository.replaceAll` вызовы

- [ ] Реализовать; проверить, что старый документ без полей парсится (дефолты). Собрать. Commit фазы B.

### Task B4: Подключить enforcement к политике

**Files:**
- Modify: `platform/.../vpn/VpnController.kt` — вместо хардкода (спайк) брать `SiteBlockRules` из политики; выбор режима: время доступно + `rules.isActive` → DNS-фильтр с правилами; время доступно + не active → pass-through (как сейчас); лимит исчерпан → blackhole. Горячая пересборка при смене правил.

- [ ] Реализовать; убрать хардкод спайка. E2E на детском эмуляторе: правило из «политики» (пока задаём через БД/синк) реально блокирует. Собрать. Commit.

---

## Фаза C — UI (после макета)

### Task C1: Макет экрана «Запрет сайтов»

**Files:**
- Create: `docs/ui-concepts/site-blocking/site-blocking-mockup.html` (standalone, по образцу `docs/ui-concepts/system-apps-in-pickers/...`; тёмный glass; тумблер «Блокировать поиск Google» сверху + «Чёрный список»: ввод+Добавить, список с галкой/удалением, оговорка про DoH).

- [ ] Сделать макет. (Согласование с Володей — по возвращении; т.к. он просил приступать, UI-код пишем по макету, визуальные правки — по его возврату.)

### Task C2: Карточка на Правила + навигация

**Files:**
- Modify: `app/.../feature/parent/rules/RulesScreen.kt` — карточка «Запрет сайтов» под «Запрет приложений»; навигация на новый экран (по образцу существующих карточек/навигации).
- Modify: строки: `rules_blocked_sites_title`/`_subtitle`.

- [ ] Реализовать; собрать.

### Task C3: Экран BlockedSitesScreen + ViewModel

**Files:**
- Create: `app/.../feature/parent/rules/BlockedSitesScreen.kt`
- Create: `app/.../feature/parent/rules/BlockedSitesViewModel.kt`
- Modify: `strings.xml` — тумблер google (+пояснение с оговоркой Play/Gmail), заголовок «Чёрный список», плейсхолдер ввода, кнопки, оговорка DoH/CDN, пустое состояние.

**Суть:** VM отдаёт `blockGoogleSearch` + список `BlockedSite`; действия: toggle google, добавить (нормализовать через `DomainNormalizer`, отвергать невалидное/дубли), toggle enabled записи, удалить. Экран — glass: строка-тумблер сверху, поле ввода + «Добавить», `LazyColumn` записей (галка + домен + «−»), подсказки.

- [ ] Реализовать; собрать `:app:assembleDebug` зелёная. Проверить на родительском эмуляторе (экран, добавление/галка/удаление, синк в политику). Commit фазы C.

---

## Self-Review (проведён)

- **Покрытие спеки:** DNS-механизм (A2–A3), матчинг суффикс+google (A1), только домены/нормализация (A1/C3), DoH-оговорка (C3 строки), хранение в политике без сервера (B), UI на Правила (C), риск lockdown (спайк A4). Всё покрыто.
- **Плейсхолдеры:** B2/C — «по образцу blockedApps»: паттерн существует в репо, конкретные файлы указаны; это ссылка на реальный код, не заглушка. Низкоуровневый код A2/A3 расписан.
- **Согласованность типов:** `SiteBlockRules(blockedDomains:Set<String>, blockGoogleSearch:Boolean)`, `BlockedSite(domain,enabled)`, `DomainNormalizer.normalize` — едины во всех фазах.

## Порядок и точки остановки

A (ядро+спайк) → **если спайк ок** → B (политика) → C (макет→UI). Спайк A4 — обязательная точка принятия решения: при фатальной несовместимости с lockdown остановиться и доложить.

## Итоги реализации (2026-07-19)

**Фича реализована и проверена end-to-end на детском эмуляторе (профиль Alina).** Все фазы A–C
влиты в ветку `sprint/pre-oleg-week` (7 коммитов), сборка и `:core:test` (89 тестов) зелёные.

**Спайк A4 (главный риск — lockdown):**
- Split-tunnel DNS-фильтр **работает штатно**: заблокированные домены → NXDOMAIN, остальное
  резолвится, не-DNS трафик идёт напрямую (ICMP/HTTP проходят).
- **lockdown ON:** через `settings put global always_on_vpn_lockdown 1` трафик НЕ ломался, но
  `dumpsys` показал пустые «Lockdown filtering rules» — полноценный enforcement, вероятно, не
  задействовался (нужна регистрация always-on через системный UI/DPM). **Вывод неоднозначен →
  проверить на реальном телефоне Олега.** Не блокер: без lockdown всё работает, lockdown —
  опциональная ручная настройка.

**E2E-проверка (Alina):** родитель добавил `vk.com` + включил тумблер Google → синк → детское
устройство подтянуло → VPN перешёл в DNS-фильтр. Итог пингов на Alina:
- `vk.com`, `m.vk.com` → заблокированы (суффикс);
- `google.com`, `www.google.com` → заблокированы (тумблер), но `mail.google.com` (Gmail) →
  работает (узкий матч сохранил Gmail);
- `example.com` → работает.

**Осталось / на потом:**
- Проверить lockdown ON на реальном устройстве Олега (через неделю).
- Голые IP, TCP:53, противодействие DoH/DoT — вне scope v1 (задокументировано).
- Мелочь: `DnsProxyLoop.rules` (@Volatile hot-swap) фактически не используется — смена правил
  пересоздаёт tun+loop через `VpnController`; работает, но горячую замену можно упростить/убрать.
