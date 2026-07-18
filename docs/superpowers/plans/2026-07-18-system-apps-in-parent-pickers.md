# Системные приложения в родительских пикерах — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дать родителю задавать лимиты/запреты/белый список и для системных приложений, которые ребёнок реально использовал; критичные помечать предупреждением; системные вынести в отдельную категорию.

**Architecture:** Детское устройство публикует объединённый список «запускаемые ∪ реально использованные» с двумя флагами на приложение (`isSystem`, `isRisky`). Флаги едут через аддитивно расширенный `ChildAppDto` и две новые колонки в серверной таблице `child_apps`. Родительские пикеры группируют по `isSystem` и рисуют значок ⚠️ на `isRisky`.

**Tech Stack:** Kotlin/Compose (app/core/data/platform), Hilt, Room; Node.js/Express + better-sqlite3 (KidGuard-server); kotlinx.serialization (Retrofit).

## Global Constraints

- **Обратная совместимость строго обязательна.** Сервер деплоим ПЕРВЫМ; изменения аддитивные. Старый детский `PUT /apps` без флагов → колонки `DEFAULT 0`. Старый родительский `GET /apps` → лишние поля игнорируются (`NetworkModule.kt:30` `ignoreUnknownKeys = true`). Обновить детскую часть на телефоне Олега можно только через ~неделю.
- Новые булевы поля DTO — с дефолтом `false` (`isSystem: Boolean = false`, `isRisky: Boolean = false`).
- Никаких хардкод-строк UI — только `strings.xml`.
- UI-этап (Task 5+) — **сперва макет, согласование с Володей, потом код** (правило проекта).
- Отступы как в файлах (пробелы, 4). SOLID/Clean.
- Сервер — в `/home/racer/projects/KidGuard-server`; мобильный — в `/home/racer/projects/KidGuard`.

---

## Часть A — Сервер (деплой первым, обратно совместимо)

### Task 1: Миграция таблицы child_apps (+ is_system, is_risky)

**Files:**
- Modify: `KidGuard-server/src/db/connection.js:124` (рядом с `ensureColumn('child_apps', 'icon', ...)`)

**Interfaces:**
- Produces: колонки `child_apps.is_system INTEGER NOT NULL DEFAULT 0`, `child_apps.is_risky INTEGER NOT NULL DEFAULT 0`.

- [ ] **Step 1: Добавить идемпотентные миграции** после строки с `icon`:

```js
ensureColumn('child_apps', 'is_system', 'is_system INTEGER NOT NULL DEFAULT 0');
ensureColumn('child_apps', 'is_risky',  'is_risky  INTEGER NOT NULL DEFAULT 0');
```

- [ ] **Step 2: Проверить старт сервера на существующей БД** — `node -e "require('./src/db/connection')"` из каталога сервера; ожидание: без ошибок, колонки добавлены (лог `schema_ensure_done`).

### Task 2: appsService — принять/хранить/вернуть флаги

**Files:**
- Modify: `KidGuard-server/src/services/appsService.js` (`replaceApps`, `getApps`, `normalizeApps`)
- Test: `KidGuard-server/test/apps.test.js`

**Interfaces:**
- Consumes: колонки из Task 1.
- Produces: `normalizeApps` возвращает элементы `{ packageName, label, icon, isSystem, isRisky }`; `getApps` возвращает те же поля (булевы).

- [ ] **Step 1: Тест — старое тело без флагов даёт дефолты; новое тело round-trip.** Дописать в `test/apps.test.js`:

```js
test('PUT без флагов → getApps возвращает isSystem/isRisky = false (обратная совместимость)', () => {
    replaceApps(childId, [{ packageName: 'com.old.app', label: 'Old' }]);
    const [app] = getApps(childId);
    assert.equal(app.isSystem, false);
    assert.equal(app.isRisky, false);
});

test('PUT с флагами → getApps их возвращает', () => {
    replaceApps(childId, [{ packageName: 'com.android.systemui', label: 'systemui', isSystem: true, isRisky: true }]);
    const [app] = getApps(childId);
    assert.equal(app.isSystem, true);
    assert.equal(app.isRisky, true);
});

test('normalizeApps: не-булев флаг → false', () => {
    const [n] = normalizeApps([{ packageName: 'p', label: 'L', isSystem: 'yes', isRisky: 1 }]);
    assert.equal(n.isSystem, false);
    assert.equal(n.isRisky, false);
});
```
(Точный синтаксис assert/раннера — как в существующем `apps.test.js`; подстроиться под него.)

- [ ] **Step 2: Прогнать — тесты падают** (`npm test`), т.к. поля ещё не поддержаны. Ожидание: FAIL на новых кейсах.

- [ ] **Step 3: `normalizeApps` — принять опциональные булевы** (после блока `icon`, перед `push`):

```js
const isSystem = item.isSystem === true;
const isRisky = item.isRisky === true;
normalized.push({ packageName, label, icon, isSystem, isRisky });
```

- [ ] **Step 4: `replaceApps` — INSERT c новыми колонками:**

```js
const insert = db.prepare(
    'INSERT INTO child_apps (child_id, package_name, label, icon, is_system, is_risky) VALUES (?, ?, ?, ?, ?, ?)'
);
// ...
insert.run(childId, app.packageName, app.label, app.icon ?? null, app.isSystem ? 1 : 0, app.isRisky ? 1 : 0);
```

- [ ] **Step 5: `getApps` — SELECT + маппинг булевых:**

```js
const rows = db.prepare(`
    SELECT package_name, label, icon, is_system, is_risky
    FROM child_apps
    WHERE child_id = ?
    ORDER BY label COLLATE NOCASE ASC
`).all(childId);

return rows.map((row) => ({
    packageName: row.package_name,
    label: row.label,
    icon: row.icon ?? null,
    isSystem: row.is_system === 1,
    isRisky: row.is_risky === 1
}));
```

- [ ] **Step 6: Прогнать все тесты — зелёные** (`npm test`). Ожидание: PASS, включая старые кейсы (обратная совместимость).

### Task 3: Деплой сервера на боевой

- [ ] **Step 1:** Убедиться, что весь набор тестов зелёный (`npm test`).
- [ ] **Step 2:** Задеплоить по штатной процедуре (`KidGuard-server/deploy/`); проверить `GET /apps/:childId` боевого — старые записи отдаются с `isSystem:false,isRisky:false`, ошибок нет. Деплой авторизован Володей заранее (при зелёных тестах). Если деплой требует интерактивной авторизации (ssh/ключи) — остановиться и запросить у Володи запуск через `! <команда>`.

---

## Часть B — Детская часть + транспорт (клиент)

### Task 4: Доменная модель + DTO + провайдер (флаги сквозняком)

**Files:**
- Modify: `core/.../domain/model/AppInfo.kt`
- Modify: `data/.../network/AppsApi.kt` (`ChildAppDto`)
- Modify: `app/.../feature/parent/rules/InstalledAppsProvider.kt` (`InstalledApp` + маппинг в `ChildAppsProvider`)
- Modify: `data/.../children/ChildRepositoryImpl.kt:86` (маппинг DTO→AppInfo)

**Interfaces:**
- Produces: `AppInfo(packageName, label, iconBase64, isSystem, isRisky)`, `InstalledApp(packageName, label, icon, isSystem, isRisky)`, `ChildAppDto(..., isSystem=false, isRisky=false)`.

- [ ] **Step 1: `AppInfo` +2 поля:**

```kotlin
data class AppInfo(
    val packageName: String,
    val label: String,
    val iconBase64: String? = null,
    val isSystem: Boolean = false,
    val isRisky: Boolean = false
)
```

- [ ] **Step 2: `ChildAppDto` +2 поля (после `icon`):**

```kotlin
@Serializable
data class ChildAppDto(
    val packageName: String,
    val label: String,
    val icon: String? = null,
    val isSystem: Boolean = false,
    val isRisky: Boolean = false
)
```

- [ ] **Step 3: `ChildRepositoryImpl` маппинг DTO→AppInfo (`:86`):**

```kotlin
Result.success(response.apps.map { AppInfo(it.packageName, it.label, it.icon, it.isSystem, it.isRisky) })
```

- [ ] **Step 4: `InstalledApp` +2 поля и маппинг в `ChildAppsProvider.loadActiveChildApps`:**

```kotlin
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean = false,
    val isRisky: Boolean = false
)
```
```kotlin
InstalledApp(
    packageName = it.packageName,
    label = it.label,
    icon = decodeIcon(it.iconBase64) ?: localIcon(it.packageName),
    isSystem = it.isSystem,
    isRisky = it.isRisky
)
```

- [ ] **Step 5: Собрать `:app:assembleDebug`** — компилируется.

### Task 5: Источник объединённого списка с флагами (детское устройство)

**Files:**
- Modify: `core/.../domain/repository/InstalledAppsSource.kt` (+метод)
- Modify: `platform/.../apps/PlatformInstalledAppsSource.kt` (реализация)

**Interfaces:**
- Consumes: `AppInfo` c флагами (Task 4).
- Produces: `suspend fun publishableApps(usedPackages: Set<String>): List<AppInfo>` — запускаемые ∪ переданные использованные, каждый с `isSystem`/`isRisky`.

- [ ] **Step 1: Интерфейс — новый метод** (рядом с `launchableApps`):

```kotlin
/**
 * Список для публикации родителю: запускаемые приложения ∪ переданные `usedPackages`
 * (реально использованные, включая системные без launcher-иконки). На каждом — флаги
 * isSystem (FLAG_SYSTEM) и isRisky (критичные для устройства: сам KidGuard, лаунчер, systemui).
 */
suspend fun publishableApps(usedPackages: Set<String>): List<AppInfo>
```

- [ ] **Step 2: Реализация в `PlatformInstalledAppsSource`.** Добавить helper и метод:

```kotlin
override suspend fun publishableApps(usedPackages: Set<String>): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val risky = resolveRiskyPackages()

    // Запускаемые (как раньше) + флаги.
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        .distinctBy { it.activityInfo.packageName }
        .map { ri ->
            val pkg = ri.activityInfo.packageName
            AppInfo(
                packageName = pkg,
                label = ri.loadLabel(pm).toString(),
                iconBase64 = runCatching { ri.loadIcon(pm).toIconBase64() }.getOrNull(),
                isSystem = isSystemPackage(pkg),
                isRisky = pkg in risky
            )
        }

    val launchablePkgs = launchable.mapTo(mutableSetOf()) { it.packageName }
    // Использованные, которых нет среди запускаемых (обычно системные без launcher-иконки).
    val extra = (usedPackages - launchablePkgs).mapNotNull { pkg ->
        runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            AppInfo(
                packageName = pkg,
                label = pm.getApplicationLabel(ai).toString(),
                iconBase64 = runCatching { pm.getApplicationIcon(ai).toIconBase64() }.getOrNull(),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isRisky = pkg in risky
            )
        }.getOrNull()
    }

    (launchable + extra).sortedBy { it.label.lowercase() }
}

private fun isSystemPackage(pkg: String): Boolean = runCatching {
    val ai = context.packageManager.getApplicationInfo(pkg, 0)
    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
}.getOrDefault(false)

/** Критичные для устройства: сам KidGuard + дефолтный лаунчер + systemui. */
private fun resolveRiskyPackages(): Set<String> = buildSet {
    add(context.packageName)
    add(SYSTEM_UI_PACKAGE)
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    context.packageManager
        .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
        ?.let { add(it) }
}
```
Добавить импорт `android.content.pm.ApplicationInfo` и константу `const val SYSTEM_UI_PACKAGE = "com.android.systemui"` в companion.

- [ ] **Step 3: Собрать `:platform` / `:app:assembleDebug`** — компилируется.

### Task 6: Публикация объединённого списка (SyncRepositoryImpl)

**Files:**
- Modify: `data/.../sync/SyncRepositoryImpl.kt:225` (`pushInstalledApps`)

**Interfaces:**
- Consumes: `installedAppsSource.publishableApps(usedPackages)` (Task 5), `usageRepository.appScreenTimeByPackage` (существует).

- [ ] **Step 1: Собрать использованные пакеты за сегодня и вчера и опубликовать union:**

```kotlin
private suspend fun pushInstalledApps(childId: Int) {
    val today = currentDateProvider.today()
    val usedPackages = buildSet {
        for (date in listOf(today.minusDays(1), today)) {
            usageRepository.appScreenTimeByPackage(date).first().forEach { (pkg, seconds) ->
                if (seconds > 0) add(pkg)
            }
        }
    }
    val apps = installedAppsSource.publishableApps(usedPackages)
        .map { ChildAppDto(it.packageName, it.label, it.iconBase64, it.isSystem, it.isRisky) }
    val snapshot = json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(ChildAppDto.serializer()),
        apps.sortedBy { it.packageName }
    )
    if (snapshot == context.syncDataStore.data.first()[Keys.LAST_SENT_APPS]) return
    appsApi.putApps(childId, PutAppsRequest(apps))
    context.syncDataStore.edit { it[Keys.LAST_SENT_APPS] = snapshot }
    Timber.tag(TAG).d("Список приложений отправлен (%d)", apps.size)
}
```
(Снапшот теперь включает флаги → смена флага вызовет переотправку, что верно. `currentDateProvider` в классе уже есть — используется в `pushUsage`.)

- [ ] **Step 2: Собрать `:app:assembleDebug`** — компилируется.

- [ ] **Step 3: Прогнать юнит-тесты модулей** (`./gradlew :core:test :data:test`) — зелёные (правок логики лимитов нет; проверяем, что ничего не сломали).

- [ ] **Step 4: E2E-проверка на эмуляторе ребёнка против боевого сервера:** запустить пару системных приложений (накопить время), дождаться синка, проверить в БД сервера/`GET /apps`, что системные использованные пакеты опубликованы с `is_system=1`, критичные — `is_risky=1`.

---

## Часть C — Родительский UI (после согласования макета)

> **Гейт:** сперва макет группировки «Системные» + вид значка ⚠️ → согласование с Володей → затем код. Детализируется отдельным под-планом после согласования, чтобы не писать UI-код вслепую.

### Task 7 (набросок): Сгруппированный список + значок-предупреждение

**Files (ожидаемо):**
- `app/.../feature/parent/rules/BlockedAppsScreen.kt`, `AppLimitsScreen.kt`, `WhitelistScreen.kt`
- Возможный общий composable `AppPickerList`/`AppPickerRow` в `feature/parent/rules/`
- `app/src/main/res/values/strings.xml` (заголовок «Системные», подпись значка)

**Суть:** разбить список на `!isSystem` (сверху) и `isSystem` (ниже, под заголовком «Системные»); на `isRisky` — иконка ⚠️ (жёлтый треугольник) рядом с названием, строка остаётся выбираемой. Проверка на родительском эмуляторе.

---

## Self-Review (проведён)

- **Покрытие спеки:** источник (Task 5/6), флаги isSystem/isRisky (Task 4/5), транспорт (Task 4), сервер+миграция+тесты (Task 1–3), обратная совместимость (Global Constraints + Task 2 Step 1 + Task 3), UI-группировка+значок (Task 7, макет-гейт). Всё покрыто.
- **Плейсхолдеры:** Task 7 намеренно набросок (UI-гейт по правилу «сперва макет»); части A/B — конкретный код. Допустимо.
- **Согласованность типов:** `publishableApps(Set<String>): List<AppInfo>`, поля `isSystem`/`isRisky` одинаковы во всех слоях; `ChildAppDto` порядок полей packageName,label,icon,isSystem,isRisky — единообразно в Task 2/4/6.

## Порядок выполнения

A (сервер, деплой) → B (детская часть + транспорт, e2e) → C (макет → согласование → UI).
