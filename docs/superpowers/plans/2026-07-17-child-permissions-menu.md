# Пункт «Разрешения» в детском меню — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Родитель может на телефоне ребёнка открыть мастер разрешений через меню «три точки», защищённое родительским PIN с защитой от перебора.

**Architecture:** Единая доменная точка `PinGuard` (core) проверяет PIN и ведёт счётчик попыток — её зовут и новый экран, и оверлей 6.2, поэтому логика не дублируется. Счётчик `totalFailures` в DataStore, момент разблокировки — в памяти по `elapsedRealtime`. Навигация: два маршрута (`CHILD_PIN`, `CHILD_PERMISSIONS`) на существующий `PermissionsWizardScreen`.

**Tech Stack:** Kotlin, Compose, Hilt, DataStore Preferences, Navigation Compose, JUnit.

**Спека:** `docs/superpowers/specs/2026-07-17-child-permissions-menu-design.md`

## Global Constraints

- Ветка: `feature/milestone-06v-prep` (уже на ней; не создавать новую).
- Все UI-тексты — только в `app/src/main/res/values/strings.xml`, хардкод запрещён.
- Комментарии и текст страниц — на русском.
- Архитектура: интерфейс в `:core/domain/repository`, реализация в `:data` или `:platform`. `:core` не зависит от Android SDK в тестируемой логике.
- `:core` — Android library, но его юнит-тесты — чистый JUnit **без Robolectric**. `SystemClock`, `Context`, `Log` в тестах `:core` НЕ работают. Поэтому время берётся через интерфейс `ElapsedTimeSource`.
- Отступы: как в соседних файлах (4 пробела).
- Коммит после каждой задачи. `git` в vault не трогать — работаем в `/home/racer/projects/KidGuard`.
- Эмуляторы: `emulator-5554` — ребёнок, `emulator-5556` — родитель.
- **Не запускать `adb shell am force-stop` на детском приложении** — это выключает accessibility-сервис. Для перезапуска: `force-stop` → заново включить accessibility (`settings put secure enabled_accessibility_services <компонент>` и `accessibility_enabled 1`) → `am start`.

## Правила механики (взяты из спеки, не менять)

| Величина | Значение |
|---|---|
| Попыток в серии | 5 |
| Блок после серии 1 | 60 с |
| Блок после серии 2 | 120 с |
| Блок после серии 3 и далее | 600 с (потолок, не растёт) |
| Верный PIN | обнуляет счётчик |
| PIN не задан | пускать без гейта (`NoPinSet`) |
| Во время блока верный PIN | всё равно `Blocked` |

---

### Task 1: Доменное ядро — ElapsedTimeSource, PinAttemptsStore, PinGuard

**Files:**
- Create: `core/src/main/java/ru/homelab/kidguard/core/domain/repository/ElapsedTimeSource.kt`
- Create: `core/src/main/java/ru/homelab/kidguard/core/domain/repository/PinAttemptsStore.kt`
- Create: `core/src/main/java/ru/homelab/kidguard/core/domain/security/PinVerifyResult.kt`
- Create: `core/src/main/java/ru/homelab/kidguard/core/domain/security/PinGuard.kt`
- Test: `core/src/test/java/ru/homelab/kidguard/core/domain/security/PinGuardTest.kt`

**Interfaces:**
- Consumes: `PolicyRepository.pinProtection: Flow<PinProtection?>` (существует), `PinHasher.verify(pin: String, salt: String, expectedHash: String): Boolean` (существует, `core/domain/security/PinHasher.kt`), `PinProtection(hash: String, salt: String)` (существует).
- Produces: `PinGuard.verify(pin: String): PinVerifyResult`; `PinVerifyResult.{Success, NoPinSet, Wrong(attemptsLeft: Int), Blocked(secondsLeft: Int)}`; `PinAttemptsStore.{totalFailures(): Int, increment(): Int, reset()}`; `ElapsedTimeSource.elapsedRealtimeMs(): Long`.

- [ ] **Step 1: Написать интерфейсы (кода логики ещё нет)**

`ElapsedTimeSource.kt`:

```kotlin
package ru.homelab.kidguard.core.domain.repository

/**
 * Монотонное время с момента загрузки устройства (Android: `SystemClock.elapsedRealtime`).
 *
 * Зачем отдельный интерфейс: системным часам верить нельзя — ребёнок переведёт время вперёд и
 * снимет блокировку PIN. Это время часам не подчиняется. Плюс `SystemClock` недоступен в
 * юнит-тестах `:core` (чистый JUnit, без Robolectric), а через интерфейс он подменяется фейком.
 */
interface ElapsedTimeSource {

    /** Миллисекунды с загрузки устройства. Только для измерения интервалов, не для дат. */
    fun elapsedRealtimeMs(): Long
}
```

`PinAttemptsStore.kt`:

```kotlin
package ru.homelab.kidguard.core.domain.repository

/**
 * Счётчик подряд идущих неудачных вводов родительского PIN (веха 6, защита от перебора).
 *
 * Хранится ОДНО число — номер серии и остаток попыток вычисляются из него ([PinGuard]).
 * Переживает перезапуск процесса и перезагрузку: иначе перебор сбрасывался бы убийством
 * приложения.
 */
interface PinAttemptsStore {

    /** Сколько неудач накоплено. 0 — чисто. */
    suspend fun totalFailures(): Int

    /** +1 к счётчику. Возвращает новое значение. */
    suspend fun increment(): Int

    /** Сброс в 0 — только после верного PIN. */
    suspend fun reset()
}
```

`PinVerifyResult.kt`:

```kotlin
package ru.homelab.kidguard.core.domain.security

/** Исход проверки родительского PIN ([PinGuard]). */
sealed interface PinVerifyResult {

    /** PIN верный — пускаем. */
    object Success : PinVerifyResult

    /** PIN не задан родителем: защита не настроена — не мешаем (та же политика, что в 6.2). */
    object NoPinSet : PinVerifyResult

    /** PIN неверный, попытки в серии ещё остались. */
    data class Wrong(val attemptsLeft: Int) : PinVerifyResult

    /** Серия исчерпана — ввод заблокирован на [secondsLeft] секунд. */
    data class Blocked(val secondsLeft: Int) : PinVerifyResult
}
```

- [ ] **Step 2: Написать падающий тест**

`PinGuardTest.kt`:

```kotlin
package ru.homelab.kidguard.core.domain.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.PinProtection
import ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource
import ru.homelab.kidguard.core.domain.repository.PinAttemptsStore

class PinGuardTest {

    private val salt = PinHasher.generateSalt()
    private val correctPin = "1234"
    private val wrongPin = "9999"

    private class FakeAttemptsStore(private var value: Int = 0) : PinAttemptsStore {
        override suspend fun totalFailures(): Int = value
        override suspend fun increment(): Int = ++value
        override suspend fun reset() { value = 0 }
    }

    private class FakeElapsed(var nowMs: Long = 0L) : ElapsedTimeSource {
        override fun elapsedRealtimeMs(): Long = nowMs
    }

    // Минимальный PolicyRepository: PinGuard читает только pinProtection, остальное не трогает.
    private fun policyWith(protection: PinProtection?): FakePolicy = FakePolicy(protection)

    private fun guard(
        protection: PinProtection? = PinProtection(PinHasher.hash(correctPin, salt), salt),
        store: PinAttemptsStore = FakeAttemptsStore(),
        elapsed: ElapsedTimeSource = FakeElapsed()
    ) = PinGuard(policyWith(protection), store, elapsed)

    @Test
    fun `верный PIN — Success и счётчик обнулён`() = runTest {
        val store = FakeAttemptsStore(value = 3)
        val result = guard(store = store).verify(correctPin)
        assertEquals(PinVerifyResult.Success, result)
        assertEquals(0, store.totalFailures())
    }

    @Test
    fun `PIN не задан — NoPinSet, не мешаем`() = runTest {
        assertEquals(PinVerifyResult.NoPinSet, guard(protection = null).verify(wrongPin))
    }

    @Test
    fun `неверный PIN — Wrong с убывающим остатком попыток`() = runTest {
        val g = guard()
        assertEquals(PinVerifyResult.Wrong(attemptsLeft = 4), g.verify(wrongPin))
        assertEquals(PinVerifyResult.Wrong(attemptsLeft = 3), g.verify(wrongPin))
        assertEquals(PinVerifyResult.Wrong(attemptsLeft = 2), g.verify(wrongPin))
        assertEquals(PinVerifyResult.Wrong(attemptsLeft = 1), g.verify(wrongPin))
    }

    @Test
    fun `5-я неудача — блок на 60 секунд`() = runTest {
        val g = guard()
        repeat(4) { g.verify(wrongPin) }
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 60), g.verify(wrongPin))
    }

    @Test
    fun `10-я неудача — блок на 120 секунд`() = runTest {
        val g = guard(store = FakeAttemptsStore(value = 9))
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 120), g.verify(wrongPin))
    }

    @Test
    fun `15-я неудача и дальше — потолок 600 секунд`() = runTest {
        val g15 = guard(store = FakeAttemptsStore(value = 14))
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 600), g15.verify(wrongPin))

        // 4-я серия — по-прежнему 600, лестница не растёт.
        val g20 = guard(store = FakeAttemptsStore(value = 19))
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 600), g20.verify(wrongPin))
    }

    @Test
    fun `во время блокировки верный PIN тоже Blocked, с остатком времени`() = runTest {
        val elapsed = FakeElapsed(nowMs = 0L)
        val g = guard(store = FakeAttemptsStore(value = 4), elapsed = elapsed)
        assertEquals(PinVerifyResult.Blocked(60), g.verify(wrongPin))

        elapsed.nowMs = 47_000L  // прошло 47 с из 60
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 13), g.verify(correctPin))
    }

    @Test
    fun `после истечения блокировки верный PIN снова работает`() = runTest {
        val elapsed = FakeElapsed(nowMs = 0L)
        val store = FakeAttemptsStore(value = 4)
        val g = guard(store = store, elapsed = elapsed)
        g.verify(wrongPin)              // → Blocked(60)

        elapsed.nowMs = 60_000L         // блок истёк ровно
        assertEquals(PinVerifyResult.Success, g.verify(correctPin))
        assertEquals(0, store.totalFailures())
    }

    @Test
    fun `перевод системных часов не влияет — считаем по elapsedRealtime`() = runTest {
        // ElapsedTimeSource монотонен и от системных часов не зависит: если он не сдвинулся,
        // блокировка держится, сколько бы ребёнок ни крутил часы в настройках.
        val elapsed = FakeElapsed(nowMs = 1_000L)
        val g = guard(store = FakeAttemptsStore(value = 4), elapsed = elapsed)
        g.verify(wrongPin)
        assertEquals(PinVerifyResult.Blocked(secondsLeft = 60), g.verify(correctPin))
    }
}
```

Дополнительно — фейк политики в том же файле, ниже класса теста. `PinGuard` читает из
`PolicyRepository` только `pinProtection`; остальные члены заглушены намеренно — если какой-то
из них вдруг позовётся, тест упадёт с внятным сообщением, а не молча вернёт пустоту:

```kotlin
private class FakePolicy(private val protection: PinProtection?) : PolicyRepository {

    override val pinProtection: Flow<PinProtection?> = flowOf(protection)

    private fun unused(): Nothing = error("PinGuard не должен обращаться к этому члену политики")

    override val dailyLimits: Flow<DailyLimits> get() = unused()
    override val whitelist: Flow<Set<String>> get() = unused()
    override val appLimits: Flow<Map<String, Int>> get() = unused()
    override val blockedApps: Flow<Set<String>> get() = unused()

    override suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?) = unused()
    override suspend fun setAppLimit(packageName: String, minutes: Int?) = unused()
    override suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) = unused()
    override suspend fun setBlocked(packageName: String, blocked: Boolean) = unused()
    override suspend fun setPin(hash: String, salt: String) = unused()
    override suspend fun clearPin() = unused()
    override suspend fun replaceAll(
        dailyLimits: Map<DayOfWeek, Int>,
        appLimits: Map<String, Int>,
        whitelist: Set<String>,
        blockedApps: Set<String>,
        pinHash: String?,
        pinSalt: String?
    ) = unused()
}
```

Импорты для теста:

```kotlin
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.DayOfWeek
```

- [ ] **Step 3: Запустить тест — убедиться, что падает**

Run: `cd /home/racer/projects/KidGuard && ./gradlew :core:testDebugUnitTest --tests '*PinGuardTest*'`
Expected: FAIL — компиляция не проходит, `PinGuard` не существует.

- [ ] **Step 4: Реализовать PinGuard**

`PinGuard.kt`:

```kotlin
package ru.homelab.kidguard.core.domain.security

import kotlinx.coroutines.flow.first
import ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource
import ru.homelab.kidguard.core.domain.repository.PinAttemptsStore
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственная точка проверки родительского PIN (веха 6). Её зовут и экран разрешений детского
 * меню, и PIN-оверлей системных экранов (6.2) — так логика попыток существует в одном
 * экземпляре и не может разъехаться между двумя дверьми.
 *
 * Защита от перебора — лестница: 5 попыток, затем блок 60 с, ещё 5 — 120 с, дальше 600 с
 * потолком. Полный перебор 10000 комбинаций становится многодневным.
 *
 * Момент разблокировки живёт ТОЛЬКО в памяти и считается по [ElapsedTimeSource]:
 * * системные часы не годятся — ребёнок переведёт время вперёд и снимет блок;
 * * на диск класть нельзя — после перезагрузки `elapsedRealtime` обнуляется, сохранённый момент
 *   окажется «в будущем», и устройство запрётся навсегда.
 *
 * Цена решения: перезагрузка снимает таймер. Но счётчик неудач переживает её, поэтому ребёнок
 * быстро упирается в потолок 600 с (записано в `docs/known-limits-and-bypasses.md`).
 */
@Singleton
class PinGuard @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val attemptsStore: PinAttemptsStore,
    private val elapsedTimeSource: ElapsedTimeSource
) {

    private var blockedUntilElapsedMs: Long = 0L

    suspend fun verify(pin: String): PinVerifyResult {
        val protection = policyRepository.pinProtection.first() ?: return PinVerifyResult.NoPinSet

        val now = elapsedTimeSource.elapsedRealtimeMs()
        if (now < blockedUntilElapsedMs) {
            return PinVerifyResult.Blocked(secondsLeft = secondsLeft(now))
        }

        if (PinHasher.verify(pin, protection.salt, protection.hash)) {
            attemptsStore.reset()
            blockedUntilElapsedMs = 0L
            return PinVerifyResult.Success
        }

        val failures = attemptsStore.increment()
        val attemptInSeries = failures % ATTEMPTS_PER_SERIES
        if (attemptInSeries != 0) {
            return PinVerifyResult.Wrong(attemptsLeft = ATTEMPTS_PER_SERIES - attemptInSeries)
        }

        val blockMs = blockMsForSeries(failures / ATTEMPTS_PER_SERIES)
        blockedUntilElapsedMs = now + blockMs
        return PinVerifyResult.Blocked(secondsLeft = (blockMs / 1000).toInt())
    }

    /** Округляем вверх: показывать «0 с» и не пускать выглядело бы поломкой. */
    private fun secondsLeft(now: Long): Int {
        val leftMs = blockedUntilElapsedMs - now
        return ((leftMs + 999) / 1000).toInt()
    }

    private fun blockMsForSeries(series: Int): Long = when (series) {
        1 -> FIRST_BLOCK_MS
        2 -> SECOND_BLOCK_MS
        else -> MAX_BLOCK_MS
    }

    private companion object {
        const val ATTEMPTS_PER_SERIES = 5
        const val FIRST_BLOCK_MS = 60_000L
        const val SECOND_BLOCK_MS = 120_000L
        const val MAX_BLOCK_MS = 600_000L
    }
}
```

- [ ] **Step 5: Запустить тесты — убедиться, что проходят**

Run: `cd /home/racer/projects/KidGuard && ./gradlew :core:testDebugUnitTest --tests '*PinGuardTest*'`
Expected: PASS, 9 тестов.

Если `UP-TO-DATE` — добавь `--rerun-tasks`, Gradle кэширует. Проверь фактический прогон:
`grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' core/build/test-results/testDebugUnitTest/TEST-*PinGuardTest.xml`

- [ ] **Step 6: Прогнать все тесты core — ничего не сломано**

Run: `cd /home/racer/projects/KidGuard && ./gradlew :core:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add core/src/main/java/ru/homelab/kidguard/core/domain/repository/ElapsedTimeSource.kt \
        core/src/main/java/ru/homelab/kidguard/core/domain/repository/PinAttemptsStore.kt \
        core/src/main/java/ru/homelab/kidguard/core/domain/security/PinVerifyResult.kt \
        core/src/main/java/ru/homelab/kidguard/core/domain/security/PinGuard.kt \
        core/src/test/java/ru/homelab/kidguard/core/domain/security/PinGuardTest.kt
git commit -m "feat(veha-6): PinGuard — единая проверка PIN с защитой от перебора

Лестница 5 попыток → 60с → 120с → 600с (потолок). Счётчик неудач вынесен в
PinAttemptsStore (переживает перезапуск), момент разблокировки — в памяти по
elapsedRealtime: системным часам верить нельзя, а на диске после перезагрузки
он запер бы устройство навсегда.

9 юнит-тестов, включая перевод часов и верный PIN во время блокировки.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Реализации — DataStore-счётчик и системное время

**Files:**
- Create: `data/src/main/java/ru/homelab/kidguard/data/pin/PinAttemptsStoreImpl.kt`
- Create: `platform/src/main/java/ru/homelab/kidguard/platform/time/PlatformElapsedTimeSource.kt`
- Modify: `data/src/main/java/ru/homelab/kidguard/data/di/RepositoryModule.kt`
- Modify: `platform/src/main/java/ru/homelab/kidguard/platform/di/PlatformModule.kt`

**Interfaces:**
- Consumes: `PinAttemptsStore`, `ElapsedTimeSource` (Task 1).
- Produces: Hilt-биндинги обоих интерфейсов — с этого момента `PinGuard` инжектится куда угодно.

- [ ] **Step 1: Реализовать PinAttemptsStoreImpl**

Паттерн — как в `SettingsRepositoryImpl` (отдельный `preferencesDataStore` на файл).

```kotlin
package ru.homelab.kidguard.data.pin

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.repository.PinAttemptsStore
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pinAttemptsDataStore by preferencesDataStore(name = "kidguard_pin_attempts")

/**
 * Счётчик неудачных вводов PIN на диске: должен переживать перезапуск процесса и перезагрузку,
 * иначе перебор сбрасывался бы убийством приложения.
 */
@Singleton
class PinAttemptsStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PinAttemptsStore {

    private object Keys {
        val TOTAL_FAILURES = intPreferencesKey("total_failures")
    }

    override suspend fun totalFailures(): Int =
        context.pinAttemptsDataStore.data.map { prefs -> prefs[Keys.TOTAL_FAILURES] ?: 0 }.first()

    override suspend fun increment(): Int {
        var updated = 0
        context.pinAttemptsDataStore.edit { prefs ->
            updated = (prefs[Keys.TOTAL_FAILURES] ?: 0) + 1
            prefs[Keys.TOTAL_FAILURES] = updated
        }
        return updated
    }

    override suspend fun reset() {
        context.pinAttemptsDataStore.edit { prefs -> prefs[Keys.TOTAL_FAILURES] = 0 }
    }
}
```

- [ ] **Step 2: Реализовать PlatformElapsedTimeSource**

```kotlin
package ru.homelab.kidguard.platform.time

import android.os.SystemClock
import ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource
import javax.inject.Inject
import javax.inject.Singleton

/** `SystemClock.elapsedRealtime` — монотонен, идёт в глубоком сне, не подчиняется системным часам. */
@Singleton
class PlatformElapsedTimeSource @Inject constructor() : ElapsedTimeSource {

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
```

- [ ] **Step 3: Добавить биндинги**

В `RepositoryModule.kt` — импорт `ru.homelab.kidguard.core.domain.repository.PinAttemptsStore`, импорт `ru.homelab.kidguard.data.pin.PinAttemptsStoreImpl` и метод:

```kotlin
    @Binds
    @Singleton
    abstract fun bindPinAttemptsStore(impl: PinAttemptsStoreImpl): PinAttemptsStore
```

В `PlatformModule.kt` — импорт `ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource`, импорт `ru.homelab.kidguard.platform.time.PlatformElapsedTimeSource` и метод:

```kotlin
    @Binds
    @Singleton
    abstract fun bindElapsedTimeSource(impl: PlatformElapsedTimeSource): ElapsedTimeSource
```

- [ ] **Step 4: Собрать — проверить, что Hilt-граф сходится**

Run: `cd /home/racer/projects/KidGuard && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Ошибка вида «PinAttemptsStore cannot be provided» означает забытый биндинг.

- [ ] **Step 5: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add data/src/main/java/ru/homelab/kidguard/data/pin/PinAttemptsStoreImpl.kt \
        platform/src/main/java/ru/homelab/kidguard/platform/time/PlatformElapsedTimeSource.kt \
        data/src/main/java/ru/homelab/kidguard/data/di/RepositoryModule.kt \
        platform/src/main/java/ru/homelab/kidguard/platform/di/PlatformModule.kt
git commit -m "feat(veha-6): реализации PinAttemptsStore (DataStore) и ElapsedTimeSource

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: PermissionsWizardScreen — кнопка «Назад» и подпись главной кнопки

**Files:**
- Modify: `app/src/main/java/ru/homelab/kidguard/feature/onboarding/permissions/PermissionsWizardScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `PermissionsWizardScreen(onFinished: () -> Unit, onBack: (() -> Unit)? = null, finishLabelRes: Int = R.string.permissions_continue, modifier: Modifier, viewModel: PermissionsViewModel)`. Task 5 зовёт её с `onBack`.

- [ ] **Step 1: Добавить строку**

В `strings.xml`, рядом с `permissions_continue`:

```xml
    <string name="permissions_done">Готово</string>
```

- [ ] **Step 2: Изменить сигнатуру и добавить TopBar**

В `PermissionsWizardScreen.kt` заменить сигнатуру и обёртку. Новый код функции (шапка + существующий `LazyColumn` без изменений внутри):

```kotlin
@Composable
fun PermissionsWizardScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    finishLabelRes: Int = R.string.permissions_continue,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(StartActivityForResult()) {
        viewModel.refresh()
    }

    // Перепроверяем статусы при каждом возврате на экран (в т.ч. из системных настроек).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    GlassBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // «Назад» есть только при входе из детского меню: в онбординге возвращаться некуда.
            if (onBack != null) {
                CompactTopBar(
                    title = stringResource(R.string.permissions_title),
                    onBack = onBack
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Заголовок в шапке уже есть — в списке он был бы вторым.
                if (onBack == null) {
                    item {
                        Text(
                            text = stringResource(R.string.permissions_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.permissions_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(R.string.permissions_subtitle),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(DevicePermission.entries) { permission ->
                    PermissionRow(
                        permission = permission,
                        granted = statuses[permission] == true,
                        onGrant = { viewModel.grantIntent(permission)?.let(launcher::launch) }
                    )
                }
                item {
                    AutostartCard(
                        onOpenSettings = { launcher.launch(viewModel.autostartIntent()) }
                    )
                }
                item {
                    AlwaysOnVpnCard(
                        onOpenSettings = { launcher.launch(Intent(Settings.ACTION_VPN_SETTINGS)) }
                    )
                }
                item {
                    Button(
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(finishLabelRes))
                    }
                }
            }
        }
    }
}
```

Добавить импорт: `import ru.homelab.kidguard.core.ui.components.CompactTopBar`.

- [ ] **Step 3: Собрать**

Run: `cd /home/racer/projects/KidGuard && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add app/src/main/java/ru/homelab/kidguard/feature/onboarding/permissions/PermissionsWizardScreen.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(veha-6): мастер разрешений умеет показывать «Назад» и свою подпись кнопки

Нужно для входа из детского меню: в онбординге возвращаться некуда и кнопка
«Продолжить», а из меню — «Назад» в шапке и «Готово».

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: ChildPinScreen — экран ввода PIN

**Files:**
- Create: `app/src/main/java/ru/homelab/kidguard/feature/child/permissions/ChildPinViewModel.kt`
- Create: `app/src/main/java/ru/homelab/kidguard/feature/child/permissions/ChildPinScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PinGuard.verify` (Task 1), `PinPad(enteredLength, onDigit, onBackspace, modifier, maxLength, isError)` (существует, `core/ui/components/PinPad.kt`), `CompactTopBar(title, onBack, modifier)` (существует).
- Produces: `ChildPinScreen(onBack: () -> Unit, onUnlocked: () -> Unit, modifier: Modifier, viewModel: ChildPinViewModel)`. Task 5 зовёт её из `NavHost`.

- [ ] **Step 1: Добавить строки**

В `strings.xml`:

```xml
    <string name="child_pin_title">Разрешения</string>
    <string name="child_pin_prompt">Введите PIN родителя</string>
    <string name="child_pin_attempts_left">Неверный PIN. Осталось попыток: %1$d</string>
    <string name="child_pin_blocked">Слишком много попыток. Повторите через %1$s</string>
    <string name="child_menu_permissions">Разрешения</string>
    <string name="child_menu_open_cd">Меню</string>
```

- [ ] **Step 2: Написать ViewModel**

```kotlin
package ru.homelab.kidguard.feature.child.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.security.PinVerifyResult
import javax.inject.Inject

/** Состояние экрана ввода PIN. */
data class ChildPinUiState(
    val entered: String = "",
    val attemptsLeft: Int? = null,
    val blockedSecondsLeft: Int = 0,
    val unlocked: Boolean = false
) {
    val isError: Boolean get() = attemptsLeft != null
    val isBlocked: Boolean get() = blockedSecondsLeft > 0
}

@HiltViewModel
class ChildPinViewModel @Inject constructor(
    private val pinGuard: PinGuard
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildPinUiState())
    val uiState: StateFlow<ChildPinUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun onDigit(digit: Int) {
        val state = _uiState.value
        if (state.isBlocked || state.entered.length >= PIN_LENGTH) return
        val entered = state.entered + digit
        _uiState.update { it.copy(entered = entered, attemptsLeft = null) }
        if (entered.length == PIN_LENGTH) verify(entered)
    }

    fun onBackspace() {
        val state = _uiState.value
        if (state.isBlocked || state.entered.isEmpty()) return
        _uiState.update { it.copy(entered = it.entered.dropLast(1), attemptsLeft = null) }
    }

    private fun verify(pin: String) {
        viewModelScope.launch {
            when (val result = pinGuard.verify(pin)) {
                is PinVerifyResult.Success -> _uiState.update { it.copy(unlocked = true) }
                is PinVerifyResult.NoPinSet -> _uiState.update { it.copy(unlocked = true) }
                is PinVerifyResult.Wrong -> _uiState.update {
                    it.copy(entered = "", attemptsLeft = result.attemptsLeft)
                }
                is PinVerifyResult.Blocked -> startCountdown(result.secondsLeft)
            }
        }
    }

    /** Живой отсчёт: цифра на экране должна убывать, иначе выглядит как зависание. */
    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        _uiState.update { it.copy(entered = "", attemptsLeft = null, blockedSecondsLeft = seconds) }
        countdownJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                delay(1000L)
                left--
                _uiState.update { it.copy(blockedSecondsLeft = left) }
            }
        }
    }

    private companion object {
        const val PIN_LENGTH = 4
    }
}
```

- [ ] **Step 3: Написать экран**

```kotlin
package ru.homelab.kidguard.feature.child.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.core.ui.components.PinPad

/**
 * PIN-гейт перед мастером разрешений в детском режиме (веха 6).
 *
 * Зачем PIN: экран статусов разрешений — карта дыр в защите. «Специальные возможности: не
 * выдано» читается ребёнком как «контроль не работает, можно гулять» (см. kdoc DeviceHealth).
 *
 * Это Compose-экран, а НЕ PinOverlayManager: оверлей рисуется через WindowManager
 * accessibility-сервиса, а этот экран нужен как раз тогда, когда accessibility сломан и сервис
 * мёртв.
 */
@Composable
fun ChildPinScreen(
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildPinViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }

    GlassBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactTopBar(title = stringResource(R.string.child_pin_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusText(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.isError || state.isBlocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 20.dp)
                )
                PinPad(
                    enteredLength = state.entered.length,
                    isError = state.isError,
                    onDigit = { if (!state.isBlocked) viewModel.onDigit(it) },
                    onBackspace = { if (!state.isBlocked) viewModel.onBackspace() }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun statusText(state: ChildPinUiState): String = when {
    state.isBlocked -> stringResource(R.string.child_pin_blocked, formatCountdown(state.blockedSecondsLeft))
    state.attemptsLeft != null -> stringResource(R.string.child_pin_attempts_left, state.attemptsLeft)
    else -> stringResource(R.string.child_pin_prompt)
}

/** «0:47» — родителю нужен наглядный отсчёт, а не «47 секунд». */
private fun formatCountdown(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
```

- [ ] **Step 4: Собрать**

Run: `cd /home/racer/projects/KidGuard && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add app/src/main/java/ru/homelab/kidguard/feature/child/permissions/ \
        app/src/main/res/values/strings.xml
git commit -m "feat(veha-6): экран ввода родительского PIN в детском режиме

Compose-экран, а не PinOverlayManager: оверлей рисуется через WindowManager
accessibility-сервиса, а этот экран нужен именно тогда, когда accessibility
сломан и сервис мёртв.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Навигация и меню «три точки»

**Files:**
- Modify: `app/src/main/java/ru/homelab/kidguard/core/ui/navigation/Destinations.kt`
- Modify: `app/src/main/java/ru/homelab/kidguard/core/ui/KidGuardApp.kt`
- Modify: `app/src/main/java/ru/homelab/kidguard/feature/child/ChildScreen.kt`
- Modify: `app/src/main/java/ru/homelab/kidguard/feature/child/today/TodayScreen.kt`

**Interfaces:**
- Consumes: `ChildPinScreen(onBack, onUnlocked, …)` (Task 4), `PermissionsWizardScreen(onFinished, modifier, onBack, finishLabelRes, …)` (Task 3).
- Produces: рабочая цепочка «три точки → PIN → мастер → назад на тот же главный экран».

- [ ] **Step 1: Добавить маршруты**

В `Destinations.kt`:

```kotlin
    const val CHILD_PIN = "child_pin"
    const val CHILD_PERMISSIONS = "child_permissions"
```

- [ ] **Step 2: Прокинуть колбэк в ChildScreen**

`ChildScreen.kt` целиком:

```kotlin
package ru.homelab.kidguard.feature.child

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.homelab.kidguard.feature.child.today.TodayScreen
import ru.homelab.kidguard.platform.foreground.KidGuardForegroundService

/**
 * Точка входа детского режима. При входе запускает foreground-сервис контроля и показывает
 * главный экран «Сегодня» (веха 4.1.3).
 */
@Composable
fun ChildScreen(
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        KidGuardForegroundService.start(context)
    }
    TodayScreen(onOpenPermissions = onOpenPermissions, modifier = modifier)
}
```

- [ ] **Step 3: Добавить маршруты в NavHost**

В `KidGuardApp.kt` заменить строку `composable(Destinations.CHILD) { ChildScreen() }` на:

```kotlin
                composable(Destinations.CHILD) {
                    ChildScreen(
                        onOpenPermissions = { navController.navigate(Destinations.CHILD_PIN) }
                    )
                }
                composable(Destinations.CHILD_PIN) {
                    ChildPinScreen(
                        onBack = { navController.popBackStack() },
                        onUnlocked = {
                            // PIN-экран убираем из стека сразу: иначе «Назад» из мастера
                            // вернул бы на ввод PIN, что выглядит как баг.
                            navController.navigate(Destinations.CHILD_PERMISSIONS) {
                                popUpTo(Destinations.CHILD_PIN) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Destinations.CHILD_PERMISSIONS) {
                    // onFinished здесь — popBackStack, а НЕ navigate(CHILD): CHILD уже лежит в
                    // стеке снизу, и navigate создал бы его второй экземпляр (в отличие от
                    // маршрута PERMISSIONS, куда попадают из онбординга с пустым стеком).
                    PermissionsWizardScreen(
                        onFinished = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                        finishLabelRes = R.string.permissions_done
                    )
                }
```

Добавить импорты: `import ru.homelab.kidguard.R`, `import ru.homelab.kidguard.feature.child.permissions.ChildPinScreen`.

- [ ] **Step 4: Три точки в TodayScreen**

В `TodayScreen.kt`:

1. Сигнатуру заменить на:

```kotlin
@Composable
fun TodayScreen(
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel()
) {
```

2. Вызов `GreetingRow` заменить на:

```kotlin
            GreetingRow(
                name = ui.childName,
                avatar = ui.childAvatar,
                onAvatarClick = { showAvatarPicker = true },
                onOpenPermissions = onOpenPermissions
            )
```

3. `GreetingRow` заменить целиком:

```kotlin
@Composable
private fun GreetingRow(
    name: String,
    avatar: Int,
    onAvatarClick: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Аватар кликабелен (тап → выбор своего аватара, веха 4.1.5); визуального бейджа нет.
        Image(
            painter = painterResource(ChildAvatars.resFor(avatar)),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.child_avatar_edit_cd), onClick = onAvatarClick)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.child_greeting_hello),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.child_greeting_name,
                    name.ifBlank { stringResource(R.string.child_greeting_name_fallback) }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        ChildMenu(onOpenPermissions = onOpenPermissions)
    }
}

/** Меню детского режима. Пункты ведут под родительский PIN — сами по себе ничего не открывают. */
@Composable
private fun ChildMenu(onOpenPermissions: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.child_menu_open_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.child_menu_permissions)) },
                onClick = {
                    expanded = false
                    onOpenPermissions()
                }
            )
        }
    }
}
```

Добавить импорты в `TodayScreen.kt`:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
```

(`remember`, `mutableStateOf`, `getValue`, `setValue` уже импортированы — проверить; если нет, добавить.)

- [ ] **Step 5: Собрать**

Run: `cd /home/racer/projects/KidGuard && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add app/src/main/java/ru/homelab/kidguard/core/ui/navigation/Destinations.kt \
        app/src/main/java/ru/homelab/kidguard/core/ui/KidGuardApp.kt \
        app/src/main/java/ru/homelab/kidguard/feature/child/ChildScreen.kt \
        app/src/main/java/ru/homelab/kidguard/feature/child/today/TodayScreen.kt
git commit -m "feat(veha-6): три точки в детском меню → PIN → мастер разрешений

Два маршрута на один composable вместо флага. onFinished из меню делает
popBackStack, а не navigate(CHILD): главный экран уже лежит в стеке снизу, и
navigate создал бы его второй экземпляр.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Оверлей 6.2 переводится на PinGuard

**Files:**
- Modify: `platform/src/main/java/ru/homelab/kidguard/platform/accessibility/KidGuardAccessibilityService.kt:173-176`
- Modify: `platform/src/main/java/ru/homelab/kidguard/platform/overlay/PinOverlayManager.kt`
- Modify: `platform/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PinGuard.verify(pin): PinVerifyResult` (Task 1).
- Produces: обе двери (экран и оверлей) используют один счётчик попыток.

- [ ] **Step 1: Добавить строку в platform-ресурсы**

Файл `platform/src/main/res/values/strings.xml` — рядом с `pin_overlay_wrong`:

```xml
    <string name="pin_overlay_blocked">Слишком много попыток. Повторите через %1$d с</string>
```

- [ ] **Step 2: Поменять тип verifyPin в PinOverlayManager**

В `show(...)` и `createOverlayView(...)` заменить тип параметра
`verifyPin: suspend (String) -> Boolean` на `verifyPin: suspend (String) -> PinVerifyResult`.

Добавить импорт: `import ru.homelab.kidguard.core.domain.security.PinVerifyResult`.

В `handleDigit` заменить блок проверки:

```kotlin
            val pin = enteredDigits.toString()
            verifyJob = verifyScope.launch {
                val result = verifyPin(pin)
                withContext(Dispatchers.Main) {
                    when (result) {
                        // PIN не задан родителем сюда не доходит: сервис не показывает оверлей
                        // без PIN (см. maybeInterceptWithPin), но NoPinSet трактуем как проход.
                        is PinVerifyResult.Success, is PinVerifyResult.NoPinSet -> {
                            dismiss(container)
                            onUnlocked()
                        }
                        is PinVerifyResult.Wrong -> {
                            enteredDigits.clear()
                            updateDots(dots, filledCount = PIN_LENGTH, isError = true)
                            subtitle.text = context.getString(R.string.pin_overlay_wrong)
                            subtitle.setTextColor(Color.parseColor(ERROR_COLOR))
                        }
                        is PinVerifyResult.Blocked -> {
                            enteredDigits.clear()
                            updateDots(dots, filledCount = PIN_LENGTH, isError = true)
                            subtitle.text = context.getString(
                                R.string.pin_overlay_blocked,
                                result.secondsLeft
                            )
                            subtitle.setTextColor(Color.parseColor(ERROR_COLOR))
                        }
                    }
                }
            }
```

- [ ] **Step 3: Перевести сервис на PinGuard**

В `KidGuardAccessibilityService.kt` заменить `verifyPin` (строки ~173-176):

```kotlin
    /** Сырой PIN никуда не хранится. Проверка и счётчик попыток — в общем [PinGuard]. */
    private suspend fun verifyPin(entered: String): PinVerifyResult = pinGuard.verify(entered)
```

Добавить поле рядом с другими `@Inject lateinit var`:

```kotlin
    @Inject
    lateinit var pinGuard: PinGuard
```

Добавить импорты: `import ru.homelab.kidguard.core.domain.security.PinGuard`, `import ru.homelab.kidguard.core.domain.security.PinVerifyResult`.

`policyRepository` в сервисе остаётся — он используется в `maybeInterceptWithPin` для проверки «PIN не задан».

- [ ] **Step 4: Собрать**

Run: `cd /home/racer/projects/KidGuard && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add platform/src/main/java/ru/homelab/kidguard/platform/accessibility/KidGuardAccessibilityService.kt \
        platform/src/main/java/ru/homelab/kidguard/platform/overlay/PinOverlayManager.kt \
        platform/src/main/res/values/strings.xml
git commit -m "feat(veha-6): PIN-оверлей 6.2 переведён на общий PinGuard

Обе двери — экран разрешений и оверлей системных экранов — теперь используют
один счётчик попыток. Защита в одной двери из двух давала бы ложное чувство
безопасности.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: Проверка на эмуляторе и known-limits

**Files:**
- Modify: `docs/known-limits-and-bypasses.md`

- [ ] **Step 1: Установить сборку на эмулятор ребёнка**

```bash
cd /home/racer/projects/KidGuard
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell settings get secure enabled_accessibility_services
```
Expected: `Success`, и accessibility-сервис на месте (переустановка его не сбрасывает).

- [ ] **Step 2: Проверить цепочку «три точки → PIN → мастер»**

Открыть детское приложение, тапнуть три точки, выбрать «Разрешения», ввести верный PIN.
Скриншот: `adb -s emulator-5554 exec-out screencap -p > /tmp/child_wizard.png`
Expected: мастер разрешений с шапкой «Разрешения», кнопкой «Назад» и кнопкой «Готово» внизу.

- [ ] **Step 3: Проверить возврат — фактический стек, а не на глаз**

Нажать «Готово». Затем:

```bash
adb -s emulator-5554 shell dumpsys activity activities | grep -A 3 "ru.homelab.kidguard"
```
Expected: **одна** активность KidGuard (у нас single-activity + Compose Navigation). Экран — детский «Сегодня».
Повторить с кнопкой «Назад» в шапке и с системной кнопкой «Назад» — результат тот же.

- [ ] **Step 4: Проверить блокировку после 5 неудач**

Ввести неверный PIN 5 раз подряд.
Expected: после 1-4 — «Неверный PIN. Осталось попыток: N» (убывает 4→1). После 5-й — «Слишком много попыток. Повторите через 0:59», цифра убывает каждую секунду, PinPad не реагирует на тапы.

- [ ] **Step 5: Проверить, что перевод часов не снимает блокировку**

Во время блокировки:

```bash
adb -s emulator-5554 shell su 0 date +%s -s @$(( $(date +%s) + 3600 ))
```
(если `su` недоступен — перевести время через «Настройки → Система → Дата и время», отключив автоматическое)
Expected: отсчёт продолжается, блокировка НЕ снимается. Вернуть время обратно после проверки.

- [ ] **Step 6: Проверить, что счётчик переживает перезапуск**

Дождаться конца блокировки, ввести неверный PIN ещё 5 раз (это 2-я серия) — ожидается блок 120 с.
Затем перезапустить приложение (с восстановлением accessibility):

```bash
SVC="ru.homelab.kidguard/ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService"
adb -s emulator-5554 shell am force-stop ru.homelab.kidguard
adb -s emulator-5554 shell settings put secure enabled_accessibility_services "$SVC"
adb -s emulator-5554 shell settings put secure accessibility_enabled 1
adb -s emulator-5554 shell am start -n ru.homelab.kidguard/.MainActivity
```
Ввести неверный PIN 5 раз.
Expected: блок **600 с** (3-я серия), а не 60 с. Это доказывает, что счётчик пережил убийство процесса.

- [ ] **Step 7: Проверить верный PIN после неудач**

Дождаться конца блокировки (или переустановить приложение, чтобы сбросить DataStore), ввести верный PIN.
Expected: мастер открывается. Затем выйти, снова зайти, ввести неверный PIN один раз.
Expected: «Осталось попыток: 4» — счётчик обнулился верным PIN.

- [ ] **Step 8: Дописать known-limits**

В `docs/known-limits-and-bypasses.md` добавить раздел:

```markdown
## Перезагрузка снимает таймер блокировки PIN

**Что:** после 5 неудачных вводов PIN ввод блокируется (60/120/600 с). Момент разблокировки
хранится в памяти по `SystemClock.elapsedRealtime()` — перезагрузка устройства обнуляет таймер.

**Почему так:** на диск его класть нельзя. После перезагрузки `elapsedRealtime` начинается
с нуля, сохранённый момент оказался бы «далеко в будущем» — и устройство заперлось бы
навсегда. Системные часы не годятся: их ребёнок переводит вперёд.

**Насколько опасно:** счётчик неудач (`PinAttemptsStore`) перезагрузку переживает, поэтому
ребёнок быстро упирается в потолок 600 с. Цикл «5 попыток → перезагрузка» даёт ~5 попыток
за ~40 с, то есть полный перебор 10000 комбинаций — около суток непрерывного занятия.

**Обход через force-stop** закрыт вехой 6.2: чтобы принудительно остановить приложение, надо
открыть «О приложении», а этот экран сам под PIN-оверлеем.
```

- [ ] **Step 9: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add docs/known-limits-and-bypasses.md
git commit -m "docs(veha-6): перезагрузка снимает таймер блокировки PIN — известное ограничение

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Проверка по завершении

- [ ] `./gradlew assembleDebug` — BUILD SUCCESSFUL
- [ ] `./gradlew :core:testDebugUnitTest` — все тесты проходят (было 11 в ChildHealthTest + 9 новых в PinGuardTest)
- [ ] `git log --oneline` — 7 коммитов задач
- [ ] Ветка `feature/milestone-06v-prep`, рабочее дерево чистое
- [ ] **Не пушить и не мёржить** — Володя решает отдельно
