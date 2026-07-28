# Кнопка «Сбросить сегодняшний лимит» — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить на экран «Дневной лимит» кнопку, которая обнуляет израсходованное сегодня время (общее + пер-app + статистику), возвращая ребёнку полный дневной лимит на сегодня.

**Architecture:** Родитель кладёт в policy-документ маркер сброса `{date, issuedAt}` (по образцу бонусов; сервер policy-agnostic, не меняется). Ребёнок в своём sync-пути `pullAndApply` применяет маркер один раз (идемпотентно по `issuedAt`), обнуляя `screen_time` и `app_screen_time` за сегодня. Кнопка на родителе — `OutlinedButton` с диалогом подтверждения.

**Tech Stack:** Kotlin, Coroutines/Flow, Room, Retrofit, Hilt, Jetpack Compose. Мультимодуль `:core`/`:data`/`:app`.

Спека: `docs/superpowers/specs/2026-07-28-daily-limit-reset-design.md`. Макет: `docs/ui-concepts/daily-limit-reset/daily-limit-reset-mockup.html`.

## Global Constraints

- Отступы: где в файле табы — табы, где пробелы — пробелы. Не смешивать.
- Комментарии/тексты — на русском. Все UI-строки — в `app/src/main/res/values/strings.xml`, хардкода в коде нет.
- Room: только не-деструктивные миграции. Текущая версия БД — **9**, поднимаем до **10**.
- Обратная совместимость policy-документа: новое поле — nullable с дефолтом `null` (старый документ без него читается как «сброса нет»).
- Сервер (`KidGuard-server`), `AndroidManifest`, `accessibility_service_config.xml` — НЕ трогать.
- `:core` не зависит от `:data`/`:platform` — новая модель и чистая логика живут в `:core`.
- Не запускать git-мутации и не устанавливать на устройство — сборку проверяет исполнитель, установку/проверку на телефоне Олега делает ведущий (Opus) после.
- Коммиты — стиль репо (conventional commits) + трейлеры:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` и
  `Claude-Session: https://claude.ai/code/session_01GAi4c3pWYp7sNEvoyqa32A`.

---

### Task 1: Модель маркера сброса + чистая логика идемпотентности (`:core`, TDD)

**Files:**
- Create: `core/src/main/java/ru/homelab/kidguard/core/domain/model/DailyUsageReset.kt`
- Test: `core/src/test/java/ru/homelab/kidguard/core/domain/model/DailyUsageResetTest.kt`

**Interfaces:**
- Produces: `data class DailyUsageReset(val date: LocalDate, val issuedAt: Long)` и
  `fun shouldApplyReset(marker: DailyUsageReset?, today: LocalDate, lastAppliedAt: Long): Boolean`.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyUsageResetTest {
    private val today = LocalDate.of(2026, 7, 28)

    @Test fun `null маркер — не применяем`() {
        assertEquals(false, shouldApplyReset(null, today, 0L))
    }

    @Test fun `маркер за вчера — не применяем`() {
        val marker = DailyUsageReset(today.minusDays(1), issuedAt = 100L)
        assertEquals(false, shouldApplyReset(marker, today, 0L))
    }

    @Test fun `маркер сегодня, но не новее применённого — не применяем`() {
        val marker = DailyUsageReset(today, issuedAt = 100L)
        assertEquals(false, shouldApplyReset(marker, today, lastAppliedAt = 100L))
    }

    @Test fun `маркер сегодня и новее применённого — применяем`() {
        val marker = DailyUsageReset(today, issuedAt = 101L)
        assertEquals(true, shouldApplyReset(marker, today, lastAppliedAt = 100L))
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает (не компилируется)**

Run: `./gradlew :core:test --tests "ru.homelab.kidguard.core.domain.model.DailyUsageResetTest"`
Expected: FAIL — `DailyUsageReset`/`shouldApplyReset` не существуют.

- [ ] **Step 3: Написать модель и функцию**

```kotlin
package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate

/**
 * Маркер сброса дневного лимита: родитель обнуляет израсходованное сегодня время ребёнку.
 * Едет в policy-документе (по образцу бонусов). [issuedAt] — метка времени нажатия (epoch-ms),
 * идемпотентный ключ: ребёнок применяет только маркер новее уже применённого.
 */
data class DailyUsageReset(val date: LocalDate, val issuedAt: Long)

/**
 * Пора ли применить сброс: маркер есть, он на сегодня и новее последнего применённого.
 * Вчерашний маркер после полуночи игнорируется; повторный тот же — тоже (idempotent).
 */
fun shouldApplyReset(marker: DailyUsageReset?, today: LocalDate, lastAppliedAt: Long): Boolean =
    marker != null && marker.date == today && marker.issuedAt > lastAppliedAt
```

- [ ] **Step 4: Запустить тест — зелёный**

Run: `./gradlew :core:test --tests "ru.homelab.kidguard.core.domain.model.DailyUsageResetTest"`
Expected: PASS (4 теста).

- [ ] **Step 5: Коммит**

```bash
git add core/src/main/java/ru/homelab/kidguard/core/domain/model/DailyUsageReset.kt \
        core/src/test/java/ru/homelab/kidguard/core/domain/model/DailyUsageResetTest.kt
git commit -m "feat(reset): модель маркера сброса + идемпотентная логика применения"
```

---

### Task 2: Порты в `:core` — сброс usage + маркер в политике

**Files:**
- Modify: `core/src/main/java/ru/homelab/kidguard/core/domain/repository/UsageRepository.kt`
- Modify: `core/src/main/java/ru/homelab/kidguard/core/domain/repository/PolicyRepository.kt`
- Modify: `core/.../PolicySnapshot.kt` (найти файл с `data class PolicySnapshot`; используется в `PolicyRepository.replaceAll`)
- Modify (test-фейки): `core/src/test/java/ru/homelab/kidguard/core/domain/FakePolicyRepository.kt` и любые фейки `UsageRepository` в `core/src/test` — добавить новые члены, чтобы тесты компилировались.

**Interfaces:**
- Consumes: `DailyUsageReset` (Task 1).
- Produces:
  - `UsageRepository.resetScreenTime(date: LocalDate)` и `resetAppScreenTime(date: LocalDate)` (suspend).
  - `PolicyRepository.dailyUsageReset: Flow<DailyUsageReset?>` и `suspend fun setDailyUsageReset(date: LocalDate, issuedAt: Long)`.
  - Поле `dailyUsageReset: DailyUsageReset?` в `PolicySnapshot`.

- [ ] **Step 1: `UsageRepository` — методы сброса**

Добавить в интерфейс (рядом с `addScreenTime`/`addAppScreenTime`):
```kotlin
/** Обнулить общий экранный расход за день (сброс сегодняшнего лимита). */
suspend fun resetScreenTime(date: LocalDate)

/** Обнулить пер-app расход всех приложений за день (сброс сегодняшнего лимита). */
suspend fun resetAppScreenTime(date: LocalDate)
```

- [ ] **Step 2: `PolicySnapshot` — поле маркера**

Добавить в `data class PolicySnapshot` поле (по образцу соседних, с дефолтом):
```kotlin
val dailyUsageReset: DailyUsageReset? = null,
```
Импортировать `DailyUsageReset`.

- [ ] **Step 3: `PolicyRepository` — поток и сеттер маркера**

Добавить в интерфейс (по образцу `dailyLimits` + сеттеров вроде `setDailyLimit`):
```kotlin
/** Маркер сброса дневного лимита из текущей политики (null — сброса нет). */
val dailyUsageReset: Flow<DailyUsageReset?>

/** Родитель: выставить маркер сброса на день с меткой времени нажатия. */
suspend fun setDailyUsageReset(date: LocalDate, issuedAt: Long)
```

- [ ] **Step 4: Обновить тест-фейки, чтобы `:core` компилировался**

В `FakePolicyRepository` добавить реализацию `dailyUsageReset` (например `MutableStateFlow(null)`) и
`setDailyUsageReset { … }`; если `replaceAll` принимает `PolicySnapshot` — учесть новое поле (можно
игнорировать в фейке). Аналогично любому фейку `UsageRepository` в `core/src/test` добавить пустые
`resetScreenTime`/`resetAppScreenTime`.

- [ ] **Step 5: Собрать core и прогнать тесты**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, все тесты зелёные (интерфейсные изменения не ломают существующие).

- [ ] **Step 6: Коммит**

```bash
git add core/src/main core/src/test
git commit -m "feat(reset): порты сброса usage и маркер сброса в PolicyRepository/PolicySnapshot"
```

---

### Task 3: `:data` — DAO-удаление, реализация сброса, Room 9→10, хранение маркера у родителя

**Files:**
- Modify: DAO экранного времени (`data/.../db/dao/…Dao.kt` для таблиц `screen_time` и `app_screen_time` — найти по `@Dao` + этим таблицам).
- Modify: `data/src/main/java/ru/homelab/kidguard/data/usage/UsageRepositoryImpl.kt`
- Modify: `data/.../db/KidGuardDatabase.kt` (version 9 → 10, зарегистрировать миграцию).
- Modify: `data/.../db/Migrations.kt` (добавить `MIGRATION_9_10`).
- Modify: сущность/DAO `policy_flags` и `data/.../policy/PolicyRepositoryImpl.kt` (хранение и чтение маркера).

**Interfaces:**
- Consumes: `UsageRepository.resetScreenTime/resetAppScreenTime`, `PolicyRepository.dailyUsageReset/setDailyUsageReset`, `DailyUsageReset` (Task 2).

- [ ] **Step 1: DAO — удаление за дату**

В DAO таблицы `screen_time`:
```kotlin
@Query("DELETE FROM screen_time WHERE date = :date")
suspend fun deleteForDate(date: String)
```
В DAO таблицы `app_screen_time`:
```kotlin
@Query("DELETE FROM app_screen_time WHERE date = :date")
suspend fun deleteForDate(date: String)
```
(Дата хранится как строка — сверить с тем, как её пишут существующие `add…`-методы; использовать тот же формат `date.toString()`.)

- [ ] **Step 2: `UsageRepositoryImpl` — реализовать сброс**

```kotlin
override suspend fun resetScreenTime(date: LocalDate) {
    screenTimeDao.deleteForDate(date.toString())
}

override suspend fun resetAppScreenTime(date: LocalDate) {
    appScreenTimeDao.deleteForDate(date.toString())
}
```
(Имена DAO-полей — как уже используются в этом классе для `add…`.)

- [ ] **Step 3: Хранение маркера у родителя (для включения в push)**

Маркер должен пережить перезапуск и попасть в `currentLocalDocument`. Хранить в строке `policy_flags`
(там уже лежат одиночные флаги вроде `blockGoogleSearch`): добавить два nullable-столбца
`dailyUsageResetDate TEXT` и `dailyUsageResetAt INTEGER`. Обновить Entity `policy_flags` и его DAO
(upsert/чтение). В `PolicyRepositoryImpl`:
```kotlin
override val dailyUsageReset: Flow<DailyUsageReset?> =
    policyFlagsDao.observe().map { flags ->
        val date = flags?.dailyUsageResetDate
        val at = flags?.dailyUsageResetAt
        if (date != null && at != null) DailyUsageReset(LocalDate.parse(date), at) else null
    }

override suspend fun setDailyUsageReset(date: LocalDate, issuedAt: Long) {
    policyFlagsDao.setDailyUsageReset(date.toString(), issuedAt) // upsert только этих полей
}
```
Учесть маркер в `replaceAll(PolicySnapshot)` (пишем `snapshot.dailyUsageReset` в `policy_flags`, null — очищаем оба столбца) — по образцу того, как `replaceAll` пишет `blockGoogleSearch`.

- [ ] **Step 4: Room-миграция 9 → 10**

В `Migrations.kt`:
```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE policy_flags ADD COLUMN dailyUsageResetDate TEXT")
        db.execSQL("ALTER TABLE policy_flags ADD COLUMN dailyUsageResetAt INTEGER")
    }
}
```
В `KidGuardDatabase.kt`: `version = 10`, добавить `MIGRATION_9_10` в список миграций.
(Сверить точное имя столбца-состояния и как `policy_flags` объявлен — следовать существующему стилю.)

- [ ] **Step 5: Сборка data**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL. Если Room ругается на схему — проверить соответствие Entity ↔ миграции.

- [ ] **Step 6: Коммит**

```bash
git add data/src/main
git commit -m "feat(reset): сброс usage в Room, хранение маркера, миграция 9->10"
```

---

### Task 4: `:data` sync — DTO, применение маркера на ребёнке, канонизация

**Files:**
- Modify: `data/src/main/java/ru/homelab/kidguard/data/network/PolicyApi.kt` (DTO).
- Modify: `data/src/main/java/ru/homelab/kidguard/data/sync/SyncRepositoryImpl.kt` (`applyDocument`, `currentLocalDocument`, `canonicalJson`, `pullAndApply`).
- Modify: sync-DataStore ключи (там же, где `LAST_SYNCED_AT`/`LAST_SENT_APPS`).

**Interfaces:**
- Consumes: `shouldApplyReset`, `DailyUsageReset`, `UsageRepository.resetScreenTime/resetAppScreenTime`, `PolicyRepository.dailyUsageReset`.

- [ ] **Step 1: DTO маркера в policy-документе**

В `PolicyApi.kt`:
```kotlin
@Serializable
data class DailyUsageResetDto(val date: String, val issuedAt: Long)
```
В `PolicyDocumentDto` добавить поле с дефолтом (обратная совместимость):
```kotlin
val dailyUsageReset: DailyUsageResetDto? = null,
```

- [ ] **Step 2: `applyDocument` — маппинг маркера в снапшот**

В `SyncRepositoryImpl.applyDocument`, в конструкторе `PolicySnapshot(...)` добавить (по образцу
соседних полей):
```kotlin
dailyUsageReset = data.dailyUsageReset?.let {
    runCatching { DailyUsageReset(LocalDate.parse(it.date), it.issuedAt) }.getOrNull()
},
```

- [ ] **Step 3: `currentLocalDocument` — включить маркер в исходящий документ**

Там, где собирается `PolicyDocumentDto` для push (родитель), добавить поле из `policyRepository.dailyUsageReset.first()`:
```kotlin
dailyUsageReset = policyRepository.dailyUsageReset.first()
    ?.let { DailyUsageResetDto(it.date.toString(), it.issuedAt) },
```

- [ ] **Step 4: `canonicalJson` — включить маркер**

В функции канонизации (которая сериализует документ для сравнения против пинг-понга) добавить то же
поле `dailyUsageReset` в тот же порядок ключей, что и в `currentLocalDocument`. Иначе push/pull будут
считать документ изменившимся и зациклятся.

- [ ] **Step 5: `pullAndApply` — применить сброс на ребёнке (идемпотентно)**

Сразу после `applyDocument(data)` (там же, где ребёнок применяет политику):
```kotlin
val marker = data.dailyUsageReset
    ?.let { runCatching { DailyUsageReset(LocalDate.parse(it.date), it.issuedAt) }.getOrNull() }
val today = currentDateProvider.today()
val lastApplied = context.syncDataStore.data.first()[Keys.LAST_USAGE_RESET_AT] ?: 0L
if (shouldApplyReset(marker, today, lastApplied)) {
    usageRepository.resetScreenTime(today)
    usageRepository.resetAppScreenTime(today)
    context.syncDataStore.edit { it[Keys.LAST_USAGE_RESET_AT] = marker!!.issuedAt }
    Timber.tag(TAG).d("Дневной лимит сброшен родителем (issuedAt=%d)", marker.issuedAt)
}
```
Добавить `Keys.LAST_USAGE_RESET_AT` (longPreferencesKey) рядом с существующими ключами sync-DataStore.
Убедиться, что `usageRepository` и `currentDateProvider` доступны в `SyncRepositoryImpl` (при
необходимости — добавить в конструктор; `currentDateProvider` уже используется в `pushInstalledApps`).
Применение только здесь (`pullAndApply` — детский путь), в `switchActiveChild` НЕ трогаем.

- [ ] **Step 6: Сборка**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Коммит**

```bash
git add data/src/main
git commit -m "feat(reset): маркер сброса в sync — применение на ребёнке + канонизация"
```

---

### Task 5: `:app` — кнопка «Сбросить сегодняшний лимит» + диалог + ViewModel

**Files:**
- Modify: `app/src/main/java/ru/homelab/kidguard/feature/parent/rules/DailyLimitViewModel.kt`
- Modify: `app/src/main/java/ru/homelab/kidguard/feature/parent/rules/DailyLimitScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PolicyRepository.setDailyUsageReset` (через `DailyLimitViewModel`).

- [ ] **Step 1: ViewModel — действие сброса**

В `DailyLimitViewModel` (у него уже есть `policyRepository` и `currentDateProvider`):
```kotlin
/** Сбросить израсходованное сегодня время: ставим маркер сброса с меткой времени нажатия. */
fun resetTodayUsage() {
    viewModelScope.launch {
        policyRepository.setDailyUsageReset(currentDateProvider.today(), System.currentTimeMillis())
    }
}
```

- [ ] **Step 2: Строки**

В `strings.xml`:
```xml
<string name="daily_limit_reset_today">Сбросить сегодняшний лимит</string>
<string name="daily_limit_reset_today_title">Сбросить сегодняшний лимит?</string>
<string name="daily_limit_reset_today_message">Израсходованное сегодня время обнулится — ребёнок получит на сегодня полный лимит заново. Бонусы сохранятся.</string>
<string name="daily_limit_reset_today_action">Сбросить</string>
```

- [ ] **Step 3: Кнопка + диалог на экране**

В `DailyLimitScreen`, МЕЖДУ `GlassCard` с `BonusSection` и `GlassCard` со списком дней, добавить
`OutlinedButton` (по образцу кнопки «Перерывы», которая уже есть ниже) и состояние диалога:
```kotlin
var showResetTodayConfirm by remember { mutableStateOf(false) }
// today уже вычислен в экране как remember { LocalDate.now().dayOfWeek }; лимит на сегодня:
val todayHasLimit = limits.limitFor(today) != null

OutlinedButton(
    onClick = { showResetTodayConfirm = true },
    enabled = todayHasLimit,
    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
) {
    Text("↻ " + stringResource(R.string.daily_limit_reset_today))
}
```
Диалог (по образцу существующего `showResetConfirm` для «Сбросить все лимиты»):
```kotlin
if (showResetTodayConfirm) {
    AlertDialog(
        onDismissRequest = { showResetTodayConfirm = false },
        title = { Text(stringResource(R.string.daily_limit_reset_today_title)) },
        text = { Text(stringResource(R.string.daily_limit_reset_today_message)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.resetTodayUsage()
                showResetTodayConfirm = false
            }) { Text(stringResource(R.string.daily_limit_reset_today_action)) }
        },
        dismissButton = {
            TextButton(onClick = { showResetTodayConfirm = false }) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
```
(«↻» как текстовый префикс достаточно для MVP; при желании — иконка `Icons.Filled.Refresh` из
material-icons. `R.string.common_cancel` уже используется на этом экране.)

- [ ] **Step 4: Сборка приложения**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main
git commit -m "feat(reset): кнопка «Сбросить сегодняшний лимит» с подтверждением"
```

---

## Проверка (за исполнителем — сборка; на телефоне Олега — ведущий Opus)

1. `./gradlew :core:test` — зелёные (в т.ч. `DailyUsageResetTest`).
2. `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
3. На эмуляторе-родителе: на дне с лимитом кнопка активна, без лимита — неактивна; диалог; тап →
   маркер в `GET /policy` (поле `dailyUsageReset`).
4. На ребёнке: «потратить» немного лимита → сброс от родителя → `screen_time` и `app_screen_time` за
   сегодня = 0 (читать БД **с `-wal`/`-shm`**), кольцо на детском экране снова полное, статистика за
   сегодня 0.
5. Идемпотентность: следующий pull того же маркера не обнуляет накопленное повторно.
6. Разблокировка: исчерпать лимит (блок) → сброс → ребёнок разблокирован автоматически.
7. Обратная совместимость: применение старого документа без поля не падает (миграция 9→10).

## Self-review (сверка с спекой)

- Расположение (в), активность по наличию лимита, диалог — Task 5. ✓
- Обнуление общего + пер-app + статистики — Task 3 (DELETE обеих таблиц) + Task 4 (вызов при маркере). ✓
- Механизм маркер/идемпотентность/только-ребёнок — Task 1 (логика) + Task 4 (pullAndApply). ✓
- Бонусы не трогаем — нигде не обнуляем bonus_grants. ✓
- Обратная совместимость + миграция 9→10 — Task 3/4. ✓
- Крайние случаи (полночь, офлайн, разблокировка, повторные нажатия) — покрыты логикой `shouldApplyReset` + реактивным `LimitState`. ✓
