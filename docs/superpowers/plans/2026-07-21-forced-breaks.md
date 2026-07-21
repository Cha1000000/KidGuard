# План реализации: принудительные перерывы («режим перерыва»)

> **Для агентов:** реализовывать по задачам, каждая — отдельный коммит. Шаги помечены чекбоксами.
> Спека: `docs/superpowers/specs/2026-07-21-forced-breaks-design.md`.

**Цель:** раз в N минут непрерывного залипания (или в назначенные часы) блокировать экран ребёнка
несмахиваемым замком с обратным отсчётом и сообщением родителя.

**Архитектура:** чистая логика и модель — в `:core` (юнит-тесты); хранение и синхронизация — `:data`
(Room 8→9, policy-документ); счётчик залипания, замок и плашка — `:platform`; экран родителя — `:app`.
Ночной замок и замок перерыва обслуживает **один** контроллер, чтобы два владельца не дрались за одно
окно.

**Стек:** Kotlin, Coroutines/Flow, Room, Hilt, Compose Material 3 (glassmorphism), Android
Accessibility overlays.

## Глобальные ограничения

- Ветка — `sprint/pre-oleg-week`, мёрж в `main` после теста на телефоне Олега.
- **Сервер не меняется** (policy-agnostic, документ непрозрачен).
- Destructive-миграции Room запрещены: только явная `MIGRATION_8_9`.
- Все UI-тексты — в `strings.xml`, хардкода в коде нет.
- Порог «перерывы действуют» — лимит не задан **или** > 180 минут, **бонусы не учитываются**.
- Заготовленных значений интервала/часов/длительности нет: 0 = «не задано».
- Приоритет блокировок: **сон > перерыв**; мягкие блокировки (лимит, учёба) перерыву не мешают.
- Новые id уведомлений — только через `NotificationIds` (реестр заведён после коллизии с VPN).

---

### Задача 1: Доменная модель перерывов и чистые правила

**Файлы:**
- Создать: `core/src/main/java/ru/homelab/kidguard/core/domain/model/BreakRules.kt`
- Создать: `core/src/test/java/ru/homelab/kidguard/core/domain/model/BreakRulesTest.kt`

**Интерфейсы:**
- Использует: `TimeWindow` из `core/domain/model/Schedule.kt` (уже есть, покрыт тестами).
- Отдаёт наружу: `BreakRules`, `BreakMode`, `BreakState`, `BreakRules.isConfigured`,
  `breaksApplyToday(dayLimitMinutes: Int?)`, `BreakRules.activeHoursWindow(nowMinuteOfDay: Int)`,
  `BreakRules.minutesUntilNextHour(nowMinuteOfDay: Int)`.

- [ ] **Шаг 1: Написать падающий тест**

```kotlin
package ru.homelab.kidguard.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakRulesTest {

    private fun rules(
        enabled: Boolean = true,
        mode: BreakMode = BreakMode.INTERVAL,
        intervalMinutes: Int = 45,
        hours: Set<Int> = emptySet(),
        durationMinutes: Int = 10
    ) = BreakRules(enabled, mode, intervalMinutes, hours, durationMinutes, message = "")

    @Test
    fun `перерывы действуют, когда лимит не задан`() {
        assertTrue(breaksApplyToday(dayLimitMinutes = null))
    }

    @Test
    fun `перерывы действуют, когда лимит больше трёх часов`() {
        assertTrue(breaksApplyToday(dayLimitMinutes = 181))
    }

    @Test
    fun `ровно три часа - перерывов нет`() {
        assertFalse(breaksApplyToday(dayLimitMinutes = 180))
    }

    @Test
    fun `малый лимит - перерывов нет`() {
        assertFalse(breaksApplyToday(dayLimitMinutes = 120))
    }

    @Test
    fun `не настроено, если выключено`() {
        assertFalse(rules(enabled = false).isConfigured)
    }

    @Test
    fun `не настроено, если длительность ноль`() {
        assertFalse(rules(durationMinutes = 0).isConfigured)
    }

    @Test
    fun `не настроено, если интервал ноль в режиме интервала`() {
        assertFalse(rules(intervalMinutes = 0).isConfigured)
    }

    @Test
    fun `не настроено, если часы пусты в режиме часов`() {
        assertFalse(rules(mode = BreakMode.HOURS, hours = emptySet()).isConfigured)
    }

    @Test
    fun `настроено в режиме часов, когда есть хотя бы один час`() {
        assertTrue(rules(mode = BreakMode.HOURS, hours = setOf(900)).isConfigured)
    }

    @Test
    fun `активное окно часов - внутри окна`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 15)
        assertEquals(TimeWindow(900, 915), r.activeHoursWindow(nowMinuteOfDay = 907))
    }

    @Test
    fun `активное окно часов - до начала окна null`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 15)
        assertNull(r.activeHoursWindow(nowMinuteOfDay = 899))
    }

    @Test
    fun `активное окно часов - конец окна не включается`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900), durationMinutes = 15)
        assertNull(r.activeHoursWindow(nowMinuteOfDay = 915))
    }

    @Test
    fun `минут до ближайшего часа`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900, 1080))
        assertEquals(5, r.minutesUntilNextHour(nowMinuteOfDay = 895))
    }

    @Test
    fun `минут до ближайшего часа - после последнего часа null`() {
        val r = rules(mode = BreakMode.HOURS, hours = setOf(900))
        assertNull(r.minutesUntilNextHour(nowMinuteOfDay = 901))
    }

    @Test
    fun `выключенное расписание не даёт окна`() {
        val r = rules(enabled = false, mode = BreakMode.HOURS, hours = setOf(900))
        assertNull(r.activeHoursWindow(nowMinuteOfDay = 905))
    }
}
```

- [ ] **Шаг 2: Запустить тест, убедиться что падает**

Выполнить: `./gradlew :core:test --tests '*BreakRulesTest*'`
Ожидается: компиляция падает — `Unresolved reference: BreakRules`.

- [ ] **Шаг 3: Написать модель и правила**

```kotlin
package ru.homelab.kidguard.core.domain.model

/** Как выбираются моменты перерывов: через интервал залипания или в назначенные часы. */
enum class BreakMode { INTERVAL, HOURS }

/**
 * Настройки принудительных перерывов. Ноль в [intervalMinutes]/[durationMinutes] и пустые [hours]
 * означают «родитель ещё не задал» — заготовленных значений у фичи нет намеренно.
 */
data class BreakRules(
    val enabled: Boolean,
    val mode: BreakMode,
    val intervalMinutes: Int,
    val hours: Set<Int>,
    val durationMinutes: Int,
    val message: String
) {

    /** Родитель задал всё необходимое и включил перерывы. */
    val isConfigured: Boolean
        get() = enabled && durationMinutes > 0 && when (mode) {
            BreakMode.INTERVAL -> intervalMinutes > 0
            BreakMode.HOURS -> hours.isNotEmpty()
        }

    /** Окно перерыва режима HOURS, внутри которого мы сейчас находимся (или null). */
    fun activeHoursWindow(nowMinuteOfDay: Int): TimeWindow? {
        if (!isConfigured || mode != BreakMode.HOURS) return null
        val start = hours.firstOrNull { start ->
            nowMinuteOfDay >= start && nowMinuteOfDay < start + durationMinutes
        } ?: return null
        return TimeWindow(start, start + durationMinutes)
    }

    /** Сколько минут до ближайшего часа перерыва сегодня (или null, если сегодня их больше нет). */
    fun minutesUntilNextHour(nowMinuteOfDay: Int): Int? {
        if (!isConfigured || mode != BreakMode.HOURS) return null
        val next = hours.filter { it > nowMinuteOfDay }.minOrNull() ?: return null
        return next - nowMinuteOfDay
    }

    companion object {
        val EMPTY = BreakRules(
            enabled = false,
            mode = BreakMode.INTERVAL,
            intervalMinutes = 0,
            hours = emptySet(),
            durationMinutes = 0,
            message = ""
        )
    }
}

/** Текущее состояние перерывов на детском устройстве. */
sealed interface BreakState {
    data object Idle : BreakState
    /** Скоро перерыв — повод показать плашку. */
    data object Warning : BreakState
    /** Идёт перерыв; [secondsLeft] питает обратный отсчёт на замке. */
    data class Active(val secondsLeft: Int) : BreakState
}

/**
 * Действуют ли перерывы в день с таким лимитом. Перерывы нужны там, где ребёнок не ограничен
 * жёстко: лимит вовсе не задан или больше [BREAKS_LIMIT_THRESHOLD_MINUTES]. Бонусы не учитываются —
 * порог описывает намерение родителя, а бонус это разовая поблажка.
 */
fun breaksApplyToday(dayLimitMinutes: Int?): Boolean =
    dayLimitMinutes == null || dayLimitMinutes > BREAKS_LIMIT_THRESHOLD_MINUTES

const val BREAKS_LIMIT_THRESHOLD_MINUTES = 180
```

- [ ] **Шаг 4: Запустить тесты, убедиться что проходят**

Выполнить: `./gradlew :core:test --tests '*BreakRulesTest*'`
Ожидается: BUILD SUCCESSFUL, 15 тестов пройдено.

- [ ] **Шаг 5: Коммит**

```bash
git add core/src/main/java/ru/homelab/kidguard/core/domain/model/BreakRules.kt \
        core/src/test/java/ru/homelab/kidguard/core/domain/model/BreakRulesTest.kt
git commit -m "feat(breaks): доменная модель перерывов и правило порога 3 часов"
```

---

### Задача 2: Хранение (Room 8→9) и репозиторий

**Файлы:**
- Создать: `data/src/main/java/ru/homelab/kidguard/data/db/entity/BreakRulesEntity.kt`
- Создать: `data/src/main/java/ru/homelab/kidguard/data/db/entity/BreakHourEntity.kt`
- Изменить: `data/src/main/java/ru/homelab/kidguard/data/db/Migrations.kt` (добавить `MIGRATION_8_9`)
- Изменить: `data/src/main/java/ru/homelab/kidguard/data/db/KidGuardDatabase.kt` (version 9, entities)
- Изменить: `data/src/main/java/ru/homelab/kidguard/data/db/dao/PolicyDao.kt`
- Изменить: `core/src/main/java/ru/homelab/kidguard/core/domain/repository/PolicyRepository.kt`
- Изменить: `data/src/main/java/ru/homelab/kidguard/data/policy/PolicyRepositoryImpl.kt`
- Изменить: `core/src/main/java/ru/homelab/kidguard/core/domain/model/PolicySnapshot.kt`
- Изменить (фейки): `core/src/test/java/ru/homelab/kidguard/core/domain/FakePolicyRepository.kt`,
  `core/src/test/java/ru/homelab/kidguard/core/domain/security/PinGuardTest.kt`

**Интерфейсы:**
- Использует: `BreakRules`, `BreakMode` из задачи 1.
- Отдаёт наружу: `PolicyRepository.breakRules: Flow<BreakRules>`,
  `suspend fun setBreakRules(rules: BreakRules)`, `suspend fun resetBreaks()`;
  `PolicySnapshot.breakRules: BreakRules` (дефолт `BreakRules.EMPTY`).

- [ ] **Шаг 1: Завести сущности Room**

```kotlin
// BreakRulesEntity.kt
package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Настройки перерывов — всегда одна строка (id = 0), как у policy_flags. */
@Entity(tableName = "break_rules")
data class BreakRulesEntity(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean,
    val mode: String,
    val intervalMinutes: Int,
    val durationMinutes: Int,
    val message: String
)
```

```kotlin
// BreakHourEntity.kt
package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Час перерыва режима HOURS: минуты от полуночи (15:00 = 900). */
@Entity(tableName = "break_hour")
data class BreakHourEntity(
    @PrimaryKey val minuteOfDay: Int
)
```

- [ ] **Шаг 2: Написать миграцию 8→9**

В `Migrations.kt`, по образцу `MIGRATION_7_8`:

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `break_rules` (" +
                "`id` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL DEFAULT 0, " +
                "`mode` TEXT NOT NULL DEFAULT 'INTERVAL', " +
                "`intervalMinutes` INTEGER NOT NULL DEFAULT 0, " +
                "`durationMinutes` INTEGER NOT NULL DEFAULT 0, " +
                "`message` TEXT NOT NULL DEFAULT '', " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `break_hour` (" +
                "`minuteOfDay` INTEGER NOT NULL, " +
                "PRIMARY KEY(`minuteOfDay`))"
        )
    }
}
```

Версию базы поднять до 9, зарегистрировать `MIGRATION_8_9` там же, где перечислены остальные,
и добавить обе сущности в `entities` у `@Database`.

- [ ] **Шаг 3: DAO**

В `PolicyDao.kt` — по образцу расписаний, **точечные UPDATE** вместо пересоздания строки (грабли
`setBlockGoogleSearch`, который затирал соседние флаги):

```kotlin
@Query("SELECT * FROM break_rules WHERE id = 0")
fun breakRules(): Flow<BreakRulesEntity?>

@Query("SELECT minuteOfDay FROM break_hour ORDER BY minuteOfDay")
fun breakHours(): Flow<List<Int>>

@Upsert
suspend fun upsertBreakRules(entity: BreakRulesEntity)

@Query("DELETE FROM break_hour")
suspend fun deleteAllBreakHours()

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertBreakHours(entities: List<BreakHourEntity>)

@Transaction
suspend fun replaceBreakHours(minutes: Collection<Int>) {
    deleteAllBreakHours()
    insertBreakHours(minutes.map(::BreakHourEntity))
}
```

`replaceAllPolicy` дополнить очисткой `break_rules` и `break_hour`, а `PolicyEntities` — полями
`breakRules: BreakRulesEntity?` и `breakHours: List<Int>`.

- [ ] **Шаг 4: Репозиторий**

В `PolicyRepository` добавить:

```kotlin
/** Настройки принудительных перерывов; BreakRules.EMPTY — родитель ничего не задал. */
val breakRules: Flow<BreakRules>

/** Сохранить настройки перерывов целиком (экран сохраняет их одним действием). */
suspend fun setBreakRules(rules: BreakRules)

/** Общий сброс: обнуляет и интервал, и часы, и длительность, выключает перерывы. */
suspend fun resetBreaks()
```

В `PolicyRepositoryImpl` — склейка строки и часов:

```kotlin
override val breakRules: Flow<BreakRules> =
    policyDao.breakRules().combine(policyDao.breakHours()) { row, hours ->
        if (row == null) BreakRules.EMPTY else BreakRules(
            enabled = row.enabled,
            mode = BreakMode.valueOf(row.mode),
            intervalMinutes = row.intervalMinutes,
            hours = hours.toSet(),
            durationMinutes = row.durationMinutes,
            message = row.message
        )
    }
```

`setBreakRules` пишет строку через `upsertBreakRules` и часы через `replaceBreakHours`;
`resetBreaks` сохраняет `BreakRules.EMPTY`.

- [ ] **Шаг 5: Дополнить фейки в тестах**

В обоих фейках (`FakePolicyRepository`, фейк внутри `PinGuardTest`) добавить:

```kotlin
override val breakRules: Flow<BreakRules> = flowOf(BreakRules.EMPTY)
override suspend fun setBreakRules(rules: BreakRules) = Unit
override suspend fun resetBreaks() = Unit
```

(в `PinGuardTest` вместо `Unit` — `unused()`, как у соседних методов).

- [ ] **Шаг 6: Собрать и прогнать тесты**

Выполнить: `./gradlew :core:test :data:assembleDebug`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 7: Коммит**

```bash
git add core data
git commit -m "feat(breaks): хранение настроек перерывов, миграция Room 8-9"
```

---

### Задача 3: Синхронизация через policy-документ

**Файлы:**
- Изменить: `data/src/main/java/ru/homelab/kidguard/data/network/PolicyApi.kt`
- Изменить: `data/src/main/java/ru/homelab/kidguard/data/sync/SyncRepositoryImpl.kt`

**Интерфейсы:**
- Использует: `PolicyRepository.breakRules`, `PolicySnapshot.breakRules` из задачи 2.
- Отдаёт наружу: поле `breaks` в `PolicyDocumentDto`.

- [ ] **Шаг 1: DTO с дефолтами**

В `PolicyApi.kt` добавить (дефолты обязательны — старые документы без поля должны применяться):

```kotlin
@JsonClass(generateAdapter = true)
data class BreakRulesDto(
    val enabled: Boolean = false,
    val mode: String = "INTERVAL",
    val intervalMinutes: Int = 0,
    val hours: List<Int> = emptyList(),
    val durationMinutes: Int = 0,
    val message: String = ""
)
```

и в `PolicyDocumentDto` — `val breaks: BreakRulesDto = BreakRulesDto()`.

- [ ] **Шаг 2: applyDocument и currentLocalDocument**

В `SyncRepositoryImpl` смапить в обе стороны, добавив `breakRules` в `PolicySnapshot`, который
уходит в `replaceAll`, и в собираемый локальный документ.

- [ ] **Шаг 3: canonicalJson**

Дописать поля перерывов в `canonicalJson` **в том же порядке**, что и в документе.
Без этого push и pull начнут пинг-понговать (уже наступали на это в вехе 4).

- [ ] **Шаг 4: Добавить поток в наблюдатель родительской петли**

В `combine`-цепочке родительского наблюдателя (лимит 5 типизированных аргументов — цепочка уже
разбита) добавить `policyRepository.breakRules`, чтобы правка перерывов запускала push.

- [ ] **Шаг 5: Собрать**

Выполнить: `./gradlew :data:assembleDebug`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 6: Коммит**

```bash
git add data
git commit -m "feat(breaks): синхронизация настроек перерывов в policy-документе"
```

---

### Задача 4: Макеты экрана и оверлея (стоп-точка, согласование с Володей)

**Файлы:**
- Создать: `docs/ui-concepts/breaks/breaks-mockup.html`

Правило проекта: UI сперва макеты, потом код. Макеты кладём **в папку проекта**, не в Artifact.

- [ ] **Шаг 1: Собрать макет одной HTML-страницей**

По образцу `docs/ui-concepts/schedule/schedule-mockup.html`: переключатель светлой/тёмной темы,
glassmorphism, реальные тексты. Показать три экрана рядом:
1. «Дневной лимит» с кнопкой «Перерывы» под списком дней;
2. экран «Перерывы» в обоих режимах (интервал / часы), с незаполненными слайдерами, полем текста
   с подсказкой и подписью «действуют в дни …»;
3. замок перерыва (индиго-градиент **без луны и звёзд**, заголовок, текст родителя, обратный
   отсчёт, PIN-клавиатура, кнопки контактов) и плашка-предупреждение поверх игры.

- [ ] **Шаг 2: Показать Володе и дождаться правок**

Не переходить к задаче 5 без явного одобрения.

- [ ] **Шаг 3: Коммит**

```bash
git add docs/ui-concepts/breaks/
git commit -m "docs(breaks): макет экрана «Перерывы» и замка перерыва"
```

---

### Задача 5: Родительский UI

**Файлы:**
- Создать: `app/src/main/java/ru/homelab/kidguard/feature/parent/rules/BreaksScreen.kt`
- Создать: `app/src/main/java/ru/homelab/kidguard/feature/parent/rules/BreaksViewModel.kt`
- Изменить: `app/src/main/java/ru/homelab/kidguard/feature/parent/rules/DailyLimitScreen.kt`
- Изменить: `app/src/main/java/ru/homelab/kidguard/feature/parent/ParentScreen.kt` (маршрут)
- Изменить: `app/src/main/res/values/strings.xml`

**Интерфейсы:**
- Использует: `PolicyRepository.breakRules`, `setBreakRules`, `resetBreaks` (задача 2),
  `breaksApplyToday` (задача 1), `dailyLimits` для подписи «в какие дни действуют».
- Отдаёт наружу: маршрут `ROUTE_RULES_BREAKS`, кнопка на `DailyLimitScreen`.

- [ ] **Шаг 1: ViewModel**

Состояние экрана (`BreaksUiState`) с полями `enabled`, `mode`, `intervalMinutes`,
`durationMinutes`, `hours`, `message` и вычисляемым `activeDays: List<DayOfWeek>` —
дни, где `breaksApplyToday(dailyLimits.limitFor(day))`. Методы: `setMode`, `setInterval`,
`setDuration`, `addHour`, `removeHour`, `setMessage`, `setEnabled`, `reset`.

Тумблер `enabled` доступен только когда всё необходимое задано — то же правило, что
`BreakRules.isConfigured`, но без самого `enabled`:

```kotlin
val canEnable: Boolean
    get() = durationMinutes > 0 && when (mode) {
        BreakMode.INTERVAL -> intervalMinutes > 0
        BreakMode.HOURS -> hours.isNotEmpty()
    }
```

- [ ] **Шаг 2: Экран «Перерывы»**

Согласно утверждённому макету: `CompactTopBar` + `LazyColumn` в `GlassBackground`. Блоки —
тумблер, выбор режима галочками (взаимоисключающе), слайдер интервала (20–180 мин, шаг 5),
список часов (шторка с барабанами часы/минуты — переиспользовать компонент из `ScheduleScreen`),
слайдер длительности (5–30 мин), поле текста с подсказкой-примером, кнопка «Сбросить перерывы»
с диалогом подтверждения, подпись про дни.

- [ ] **Шаг 3: Кнопка на «Дневном лимите»**

`OutlinedButton` под списком дней недели, видна всегда, ведёт на `ROUTE_RULES_BREAKS`.

- [ ] **Шаг 4: Строки**

Все тексты в `strings.xml`, включая фразу-шаблон:

```xml
<string name="breaks_default_message">Сделай перерыв! Дай глазам отдохнуть</string>
```

- [ ] **Шаг 5: Собрать и проверить на родительском эмуляторе**

Выполнить: `./gradlew :app:assembleDebug` и установить на `emulator-5556`.
Проверить: кнопка видна при любых лимитах; режимы переключаются; тумблер недоступен, пока не
задано; сброс очищает всё; подпись про дни пересчитывается при смене лимитов.

- [ ] **Шаг 6: Коммит**

```bash
git add app
git commit -m "feat(breaks): родительский экран «Перерывы» и кнопка на дневном лимите"
```

---

### Задача 6: Счётчик залипания и состояние перерыва

**Файлы:**
- Создать: `core/src/main/java/ru/homelab/kidguard/core/domain/repository/StickinessSource.kt`
- Создать: `platform/src/main/java/ru/homelab/kidguard/platform/tracking/StickinessTracker.kt`
- Изменить: `platform/src/main/java/ru/homelab/kidguard/platform/di/PlatformModule.kt` (биндинг)
- Создать: `core/src/main/java/ru/homelab/kidguard/core/domain/usecase/ObserveBreakStateUseCase.kt`
- Создать: `core/src/test/java/ru/homelab/kidguard/core/domain/usecase/ObserveBreakStateUseCaseTest.kt`

**Интерфейсы:**
- Использует: `BreakRules`, `BreakState`, `breaksApplyToday` (задача 1);
  `PolicyRepository.breakRules`, `dailyLimits`, `sleepSchedule`; `DailyLimits.limitFor(day)`.
- Отдаёт наружу: `StickinessSource.stickySeconds: Flow<Int>`, `StickinessSource.reset()`,
  `ObserveBreakStateUseCase(): Flow<BreakState>`.

- [ ] **Шаг 0: Порт в `:core`**

`:core` не может зависеть от `:platform`, поэтому счётчик заходит в домен через интерфейс — ровно
как `ElapsedTimeSource` и `CurrentDateProvider`:

```kotlin
package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Непрерывное «залипание» — сколько секунд подряд ребёнок смотрит в экран. Реализация живёт в
 * `:platform` (нужны PowerManager/KeyguardManager), домен видит только этот порт: так правило
 * перерывов остаётся чистым и тестируется без Robolectric.
 */
interface StickinessSource {

    /** Секунды непрерывного использования; обнуляется достаточно долгой паузой. */
    val stickySeconds: Flow<Int>

    /** Сбросить счётчик — вызывается, когда перерыв доиграл свой таймер. */
    fun reset()
}
```

Биндинг в `PlatformModule` рядом с `bindElapsedTimeSource`:

```kotlin
@Binds
abstract fun bindStickinessSource(impl: StickinessTracker): StickinessSource
```

- [ ] **Шаг 1: StickinessTracker (реализация порта в `:platform`)**

Наблюдает то же состояние, что `ScreenTimeTracker` (экран включён и разблокирован), своим тиком
раз в 15 секунд:

Проверка активности — та же, что у `ScreenTimeTracker` (экран включён И разблокирован); свой тик
раз в 15 секунд. Порог паузы читается лямбдой, потому что зависит от текущей длительности перерыва
(её родитель может поменять на лету).

```kotlin
@Singleton
class StickinessTracker @Inject constructor(
    @param:ApplicationContext private val context: Context
) : StickinessSource {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    private val _stickySeconds = MutableStateFlow(0)
    override val stickySeconds: Flow<Int> = _stickySeconds.asStateFlow()

    private var idleSeconds = 0

    /** [resetAfterIdleSeconds] — сколько паузы засчитываем за состоявшийся перерыв. */
    suspend fun run(resetAfterIdleSeconds: () -> Int) {
        Timber.tag(TAG).d("Счётчик залипания запущен")
        while (currentCoroutineContext().isActive) {
            delay(TICK_SECONDS * 1000L)
            if (isUserActive()) {
                idleSeconds = 0
                _stickySeconds.value += TICK_SECONDS
            } else {
                idleSeconds += TICK_SECONDS
                val threshold = resetAfterIdleSeconds()
                if (threshold > 0 && idleSeconds >= threshold) reset()
            }
        }
    }

    override fun reset() {
        _stickySeconds.value = 0
        idleSeconds = 0
    }

    private fun isUserActive(): Boolean =
        powerManager?.isInteractive == true && keyguardManager?.isKeyguardLocked == false

    private companion object {
        const val TAG = "KidGuardStickiness"
        const val TICK_SECONDS = 15
    }
}
```

- [ ] **Шаг 2: Тест на состояние перерыва**

Тест проверяет три вещи на фейковых потоках: перерыв не наступает в день с лимитом ≤ 3 ч; перерыв
наступает по достижении интервала; внутри окна сна состояние всегда `Idle`.

- [ ] **Шаг 3: ObserveBreakStateUseCase**

`combine` из `breakRules`, `dailyLimits`, `sleepSchedule`, `stickySeconds` и тика раз в 15 секунд.
Логика: если идёт сон, или `!breaksApplyToday(dailyLimits.limitFor(today))`, или
`!rules.isConfigured` → `Idle`.
Иначе для `INTERVAL` сравниваем `stickySeconds` с порогом (`Active` с остатком, `Warning` за 5 минут
до порога), для `HOURS` — `activeHoursWindow` и `minutesUntilNextHour`.

- [ ] **Шаг 4: Запустить тесты**

Выполнить: `./gradlew :core:test --tests '*ObserveBreakStateUseCaseTest*'`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 5: Коммит**

```bash
git add core platform
git commit -m "feat(breaks): счётчик залипания и состояние перерыва"
```

---

### Задача 7: Один владелец полноэкранных замков

**Файлы:**
- Переименовать/изменить: `platform/.../overlay/SleepLockOverlayManager.kt` →
  `FullScreenLockOverlayManager.kt`
- Переименовать/изменить: `platform/.../schedule/SleepLockController.kt` →
  `FullScreenLockController.kt`
- Изменить: `platform/src/main/java/ru/homelab/kidguard/platform/accessibility/KidGuardAccessibilityService.kt`
- Изменить: `platform/src/main/java/ru/homelab/kidguard/platform/foreground/KidGuardForegroundService.kt`
- Изменить: `platform/src/main/res/values/strings.xml`

**Интерфейсы:**
- Использует: `ObserveBreakStateUseCase`, `StickinessTracker` (задача 6),
  `ObserveScheduleStateUseCase` (уже есть).
- Отдаёт наружу: `FullScreenLockOverlayManager.show(appearance: LockAppearance, …)`,
  `FullScreenLockController.run()`.

- [ ] **Шаг 1: Параметризовать оверлей**

Ввести `enum class LockAppearance { NIGHT, BREAK }`. `NightSkyView` рисует луну и звёзды только
для `NIGHT`; для `BREAK` — тот же индиго-градиент без них. Вторую строку сделать изменяемой
снаружи: у сна это «Телефон откроется в 07:00», у перерыва — текст родителя плюс отсчёт.

- [ ] **Шаг 2: Обратный отсчёт**

Тикает внутри оверлея раз в секунду через `mainHandler.postDelayed`, обновляя `TextView`. При
`hide()` цикл останавливается (снять коллбэки в `dismiss`).

- [ ] **Шаг 3: Объединить контроллеры**

`FullScreenLockController.run()` собирает `combine(scheduleState, breakState, contacts, foreground,
ticker)`. Решение по приоритету:

```kotlin
when {
    sleep != null -> showSleepLock(sleep)
    breakState is BreakState.Active && !unlockedUntilScreenOff -> showBreakLock(breakState)
    else -> hideIfShowing("нечего показывать")
}
```

Снятие PIN на замке перерыва ставит флаг `unlockedUntilScreenOff = true`, который сбрасывается по
`ACTION_SCREEN_OFF` (BroadcastReceiver, регистрируется на время работы контроллера) и по окончании
перерыва. У ночного замка остаётся прежнее окно 15 минут — механику ему не меняем.

- [ ] **Шаг 4: Фраза-шаблон при пустом тексте родителя**

Текст берётся из политики, но если родитель поле не заполнил, показываем фразу из ресурсов —
иначе ребёнок увидит голый отсчёт без объяснения, зачем его прервали:

```kotlin
val message = rules.message.ifBlank { context.getString(R.string.break_lock_default_message) }
```

Строку завести в `platform/src/main/res/values/strings.xml` с тем же текстом, что подсказка на
родительском экране: «Сделай перерыв! Дай глазам отдохнуть».

- [ ] **Шаг 5: Подключить в сервисах**

В `KidGuardAccessibilityService.onServiceConnected` заменить вызов `sleepLockOverlayManager.attach`
на новый менеджер. В `KidGuardForegroundService` переименовать `sleepLockJob` → `fullScreenLockJob`
и добавить job счётчика залипания.

- [ ] **Шаг 6: Собрать**

Выполнить: `./gradlew :app:assembleDebug`
Ожидается: BUILD SUCCESSFUL.

- [ ] **Шаг 7: Коммит**

```bash
git add platform
git commit -m "refactor(locks): один контроллер для ночного замка и замка перерыва"
```

---

### Задача 8: Плашка-предупреждение

**Файлы:**
- Создать: `platform/src/main/java/ru/homelab/kidguard/platform/overlay/BreakWarningOverlay.kt`
- Изменить: `platform/src/main/java/ru/homelab/kidguard/platform/schedule/FullScreenLockController.kt`
- Изменить: `platform/src/main/res/values/strings.xml`

**Интерфейсы:**
- Использует: `BreakState.Warning` (задача 6), WindowManager от accessibility-сервиса.
- Отдаёт наружу: `BreakWarningOverlay.show(text: String)`.

- [ ] **Шаг 1: Неблокирующая плашка**

Ключевое отличие от замков — флаги: тапы должны проходить насквозь в игру.

```kotlin
private fun buildLayoutParams() = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    android.graphics.PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP
    fitInsetsTypes = 0
}
```

Показ на 4 секунды с затуханием, затем `removeView`. Повторный вызов, пока плашка висит, ничего
не делает.

- [ ] **Шаг 2: Показ один раз за перерыв**

В контроллере хранить `warnedForBreakAt: Int?` — метку перерыва, для которого плашку уже показали
(для HOURS это минута начала окна, для INTERVAL — номер перерыва). Повторно не показывать.

- [ ] **Шаг 3: Проверить на детском эмуляторе поверх игры**

Открыть любую игру/полноэкранное приложение, дождаться плашки, **тапнуть в область плашки** —
нажатие должно уйти в приложение под ней, а не съесться оверлеем.

- [ ] **Шаг 4: Коммит**

```bash
git add platform
git commit -m "feat(breaks): неблокирующая плашка-предупреждение за 5 минут"
```

---

### Задача 9: Сквозная проверка на эмуляторах

**Файлы:** изменений нет, только прогон.

- [ ] **Шаг 1: Прогнать чек-лист спеки**

Все 13 пунктов раздела «Verification» из спеки, в первую очередь:
интервальный режим с коротким интервалом; сброс счётчика паузой; режим часов с пропуском окна;
приоритет сна; день с лимитом 2 часа; пустой текст → фраза-шаблон; миграция 8→9 на живой БД.

- [ ] **Шаг 2: Вернуть эмуляторы в исходное состояние**

Выключить перерывы, сбросить настройки, вернуть лимиты и расписания как были у Володи.

- [ ] **Шаг 3: Обновить карточку вики**

Дописать запись в `projects/KidGuard.md` и `log.md` во «втором мозге».
