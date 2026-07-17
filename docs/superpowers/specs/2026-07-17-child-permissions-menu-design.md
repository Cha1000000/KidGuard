# Пункт «Разрешения» в детском меню — дизайн

**Дата:** 2026-07-17
**Веха:** 6 (защита от обхода), подготовка к 6В
**Статус:** согласовано Владимиром

## Зачем

Родителю нужно проверить на телефоне ребёнка, какие разрешения выданы, а какие
слетели. Сейчас это невозможно: мастер разрешений показывается один раз после
привязки, и попасть в него снова нельзя ничем, кроме переустановки.

Повод — реальный случай 17.07.2026. Родитель видел на карточке ребёнка плашку
«⚠ Не работает: Доступ к статистике использования», открывал детское приложение —
и там не было ничего: ни статуса, ни входа в мастер. Проверить причину было нечем.
(Та конкретная плашка оказалась ложной — разрешение было мёртвым, убрано коммитом
`ccdff76`. Но дыра в диагностике осталась и никуда не делась.)

## Почему под PIN

Экран со статусами разрешений — это карта дыр в защите. Строка «Специальные
возможности: не выдано» читается ребёнком как «контроль не работает, можно гулять».

Это не новое соображение, оно уже зафиксировано в kdoc `DeviceHealth`:

> Ребёнку не показывается никогда: «контроль не активен» — прямая подсказка
> «можно гулять».

Поэтому вход — только по родительскому PIN.

## Решения (согласовано)

| Вопрос | Решение |
|---|---|
| Что открываем | Существующий `PermissionsWizardScreen`, не новый экран |
| PIN не задан | Пускать без гейта — как в 6.2 |
| Точка входа | Три точки в `GreetingRow` → `DropdownMenu` → «Разрешения» |
| PIN-гейт | Отдельный экран-маршрут `CHILD_PIN` |
| Защита от перебора | Сразу для обоих мест: новый экран + оверлей 6.2 |
| Механика | 5 попыток → 60 с → 5 попыток → 120 с → 5 попыток → 600 с (потолок) |
| Разблокировка | Не нужна: лестница упирается в потолок, тупика нет |

## Архитектура защиты PIN

### PinGuard — единственная точка проверки

Логика попыток НЕ дублируется. Одна доменная точка, которую зовут оба места:
`ChildPinViewModel` (новый экран) и `KidGuardAccessibilityService` (оверлей 6.2).

```kotlin
// core/domain/security/PinGuard.kt
class PinGuard @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val attemptsStore: PinAttemptsStore
) {
    suspend fun verify(pin: String): PinVerifyResult
}

sealed interface PinVerifyResult {
    object Success : PinVerifyResult
    object NoPinSet : PinVerifyResult
    data class Wrong(val attemptsLeft: Int) : PinVerifyResult
    data class Blocked(val secondsLeft: Int) : PinVerifyResult
}
```

`NoPinSet` — повторение политики 6.2 (`KidGuardAccessibilityService:158`): PIN не
задан → защита не настроена → не мешаем.

### Один счётчик вместо двух

В DataStore хранится только `totalFailures`. Остальное вычисляется:

| Величина | Формула |
|---|---|
| Попытка в текущей серии | `totalFailures % 5` |
| Номер серии | `totalFailures / 5` |
| Длительность блока | серия 1 → 60 с, серия 2 → 120 с, серия ≥3 → 600 с |

Верный PIN обнуляет `totalFailures`.

### Где что хранится — и почему

| Данные | Где | Почему именно так |
|---|---|---|
| `totalFailures` | DataStore | Должен переживать перезапуск и перезагрузку |
| `blockedUntil` | В памяти, `elapsedRealtime` | См. ниже — два ограничения сразу |

`elapsedRealtime` считает от загрузки и не подчиняется системным часам: перевести
время вперёд и снять блок нельзя.

`blockedUntil` **нельзя** хранить на диске. После перезагрузки `elapsedRealtime`
обнуляется, сохранённый момент оказывается далеко в будущем, и устройство
запирается навсегда. Поэтому — только в памяти.

Время берётся через интерфейс `ElapsedTimeSource` (по образцу существующего
`CurrentDateProvider`): `SystemClock` недоступен в юнит-тестах `:core` — там чистый
JUnit без Robolectric. Реализация `PlatformElapsedTimeSource` — в `:platform`.

### Устойчивость к обходу

* **force-stop** — уже перекрыт вехой 6.2: чтобы его сделать, надо зайти в «О
  приложении», а тот экран сам под PIN-оверлеем (`CriticalScreen.KIDGUARD_APP_INFO`).
* **Перезагрузка** — снимает таймер, но не счётчик. Ребёнок быстро упирается в
  потолок 600 с. Записать в `known-limits-and-bypasses.md`.
* **Полный перебор** — 10000 комбинаций при 5 попытках на 600 с ≈ 14 дней
  непрерывного тыканья.

## Экраны

### Три точки на TodayScreen

В `GreetingRow`: `Spacer(weight=1f)` + `IconButton(Icons.Filled.MoreVert)` →
`DropdownMenu` с пунктом «Разрешения».

`TopAppBar` не заводим: проект его не использует нигде, у нас glass-дизайн с
крупными заголовками.

### ChildPinScreen (новый)

`feature/child/permissions/ChildPinScreen.kt` + `ChildPinViewModel`.

Устроен как `PinSetupScreen` — существующий паттерн проекта: `CompactTopBar`
(«Назад») + `PinPad`. ViewModel зовёт `PinGuard.verify` (PBKDF2 120k итераций —
не на главном потоке).

| Результат | UI |
|---|---|
| `Success` | Переход в мастер |
| `NoPinSet` | Сразу в мастер, PIN не спрашиваем |
| `Wrong` | Точки краснеют, «Осталось попыток: N» |
| `Blocked` | PinPad неактивен, «Повторите через 0:47», живой отсчёт |

### PermissionsWizardScreen (правка)

Переиспользуем как есть: он идемпотентен, сам перепроверяет статусы при возврате
из системных настроек (`LifecycleResumeEffect`), содержит карточки автозапуска и VPN.

Добавляются два опциональных параметра:

* `onBack: (() -> Unit)? = null` — показывать `CompactTopBar`. Нужен только при
  входе из меню; в онбординге назад идти некуда.
* подпись главной кнопки: «Продолжить» (онбординг) / «Готово» (меню).

Статусы показываются ребёнку как есть, включая «не выдано»: смысл экрана —
диагностика, а вход уже защищён PIN.

## Навигация

### Проблема

Существующий `onFinished` маршрута `PERMISSIONS`:

```kotlin
navController.navigate(Destinations.CHILD) {
    popUpTo(Destinations.PERMISSIONS) { inclusive = true }
}
```

Из онбординга стек `[PAIRING → PERMISSIONS]` — корректно. Из меню стек
`[CHILD → CHILD_PIN → …]`, и тот же код создал бы **второй** `CHILD`:
`popUpTo(PERMISSIONS)` снимает только мастер, нижний `CHILD` остаётся.

### Решение — два маршрута на один composable

Маршрут `PERMISSIONS` (онбординг) не трогаем. Добавляем в `Destinations`:

```kotlin
const val CHILD_PIN = "child_pin"
const val CHILD_PERMISSIONS = "child_permissions"
```

```kotlin
composable(Destinations.CHILD_PIN) {
    ChildPinScreen(
        onBack = { navController.popBackStack() },
        onUnlocked = {
            navController.navigate(Destinations.CHILD_PERMISSIONS) {
                popUpTo(Destinations.CHILD_PIN) { inclusive = true }
            }
        }
    )
}
composable(Destinations.CHILD_PERMISSIONS) {
    PermissionsWizardScreen(
        onBack = { navController.popBackStack() },
        onFinished = { navController.popBackStack() }
    )
}
```

`onFinished` — `popBackStack()`, а не `navigate`: снимаем мастер и открываем тот
`CHILD`, что лежал внизу. Новый экран не создаётся, потому что мы возвращаемся,
а не идём.

### Стек

```
[CHILD]                      главный экран
   ↓ тап по ⋮ → «Разрешения»
[CHILD, CHILD_PIN]           ввод PIN
   ↓ PIN верный (popUpTo CHILD_PIN inclusive)
[CHILD, CHILD_PERMISSIONS]   мастер — PIN-экран убран из стека
   ↓ «Готово» / «Назад» / системный «Назад»
[CHILD]                      тот же самый главный экран
```

`popUpTo(CHILD_PIN) { inclusive = true }` убирает PIN-экран сразу после успеха —
иначе «Назад» из мастера вернул бы на ввод PIN.

Все три способа выйти ведут в одно место, потому что все три — `popBackStack()`.

## Файлы

**Новые:**

| Файл | Назначение |
|---|---|
| `core/domain/security/PinGuard.kt` | Проверка PIN + throttle, единая точка |
| `core/domain/security/PinVerifyResult.kt` | Результат проверки |
| `core/domain/repository/PinAttemptsStore.kt` | Интерфейс счётчика |
| `core/domain/repository/ElapsedTimeSource.kt` | Интерфейс монотонного времени |
| `data/pin/PinAttemptsStoreImpl.kt` | DataStore-реализация счётчика |
| `platform/time/PlatformElapsedTimeSource.kt` | `SystemClock.elapsedRealtime` |
| `feature/child/permissions/ChildPinScreen.kt` | Экран ввода PIN |
| `feature/child/permissions/ChildPinViewModel.kt` | Состояние экрана |
| `core/src/test/.../PinGuardTest.kt` | Юнит-тесты |

**Правятся:**

| Файл | Что |
|---|---|
| `feature/child/today/TodayScreen.kt` | Три точки + `DropdownMenu` в `GreetingRow` |
| `core/ui/KidGuardApp.kt` | Маршруты `CHILD_PIN`, `CHILD_PERMISSIONS` |
| `core/ui/navigation/Destinations.kt` | Две константы |
| `feature/onboarding/permissions/PermissionsWizardScreen.kt` | `onBack`, подпись кнопки |
| `platform/.../KidGuardAccessibilityService.kt` | `verifyPin` → `PinGuard` |
| `platform/.../PinOverlayManager.kt` | Показ «Заблокировано на N с» |
| `app/src/main/res/values/strings.xml` | Новые строки |
| `docs/known-limits-and-bypasses.md` | Перезагрузка снимает таймер блокировки |

## Тестирование

**Юнит (`PinGuardTest`, без Android):**

* верный PIN → `Success`, счётчик обнулён;
* неверный → `Wrong` с убывающим `attemptsLeft`;
* 5-я неудача → `Blocked(60)`;
* 10-я → `Blocked(120)`;
* 15-я и далее → `Blocked(600)` (потолок не растёт);
* PIN не задан → `NoPinSet`;
* верный PIN после серии неудач → `Success` и сброс счётчика;
* во время блокировки верный PIN → `Blocked`, а не `Success`.

**На эмуляторе:**

* три точки → меню → PIN → мастер;
* неверный PIN 5 раз → блок 60 с, отсчёт живой, PinPad неактивен;
* «Готово» → возврат на существующий главный экран. Проверить **фактический стек**
  через `adb shell dumpsys activity`, а не на глаз;
* «Назад» из мастера и системный «Назад» → туда же;
* PIN не задан → мастер открывается сразу;
* перевод системных часов вперёд во время блокировки → блок НЕ снимается.

## Вне скоупа

* Разблокировка с родительского устройства — лестница упирается в потолок, тупика нет.
* Одноразовые коды разблокировки — отдельная веха.
* `isAccessibilityEnabled()` игнорирует мастер-тумблер `accessibility_enabled`
  (найдено 17.07.2026) — отдельная задача, к этому пункту меню не относится.
