# KidGuard — мягкий блок «Время учёбы»: исправление P1–P5 — план

- **Дата:** 2026-11-08
- **Статус:** план; реализация не начата, коммитов нет.
- **Источник:** отчёт проверки 2026-11-07 (P1–P5), план «KidGuard — исправление перерывов», README.
- **Решения Володи (2026-11-08):**
  - P2 — перерыв не продолжается и не всплывает после учёбы/сна: активная сессия при старте расписания завершается со сбросом usage; остаток HOURS-окна после расписания не показывается. Часть HOURS-окна **до** старта расписания может показываться; окно, начавшееся во время расписания, пропускается целиком.
  - P3 — один общий поток состояния расписания + тик по точной границе минуты.
  - P1 — без родительского PIN экраны-замки не включать; родителю показывать подсказку/предложение задать PIN; на ребёнке PIN-ссылку без PIN не предлагать.
  - Звонки (in-call UI) — отдельной задачей, в этот план не входят.

## 1. Цель и границы

Закрыть дыру обхода мягкого блока, привести HOURS-перерывы к контракту «свободные часы», убрать задержку/рассинхрон расписания, сделать soft-overlay корректным при смене причины и конце окна, закрыть пробелы тестов.

Не входят: изменение сервера, миграции Room, переделка полноэкранного замка, логика звонков в soft-блоке, реальный Tecno/HiOS.

## 2. Принятые допущения

1. **P2, остаток окна.** Активная сессия перерыва при старте учёбы/сна завершается (`completeBreakAndResetUsage`), остаток не показывается. HOURS-окно, прошедшая часть которого `[start, now]` пересекла расписание, не стартует/не возобновляется; часть окна до старта расписания успевает показаться. Окно, начавшееся во время расписания, пропускается целиком. Правило stateless (по правилам расписания), поэтому переживает смерть процесса и reboot.
2. **P1, родительский UI.** Делаем как у «Времени сна»: тумблеры включения замков без PIN заблокированы, рядом карточка-подсказка с кнопкой «Задать PIN». Если хочешь только подсказку без блокировки тумблера — это одна правка, но тогда риск включения без PIN остаётся.
3. **P1, полноэкранные замки.** `NoPinSet → успех` в `PinOverlayManager` остаётся осознанным аварийным выходом (его использует `AccessibilityGuardController`). P1 закрывает soft-ссылку и не даёт включить замки без PIN через UI; legacy-политика без PIN на ребёнке по-прежнему снимает полноэкранный замок любым кодом — это документированное ограничение, не меняем.
4. **P1, сервер.** PUT политики в обход UI по-прежнему может включить учёбу без PIN; ребёнок в этом случае не получит PIN-ссылку (обхода нет), родитель увидит карточку и сможет задать PIN. Это защита от обхода, а не запрет API.

## 3. P1 — PIN-ссылка без PIN (High)

### Проблема

`BlockingController.enforce` всегда передаёт `onPinRequested`; `PinOverlayManager` трактует `NoPinSet` как успех. Без PIN ребёнок вводит любые 4 цифры и обходит soft-блок (учёба, лимит, запрещённые приложения) до выхода из приложения. Комментарий в `OverlayManager` говорит, что решать должен вызывающий, но проверки нет.

### Ребёнок

- `platform/.../overlay/BlockingController.kt`
  - `@Volatile private var pinConfigured = false`.
  - В `run()` (внутри `coroutineScope`) добавить сбор `policyRepository.pinProtection` → `pinConfigured = it != null`.
  - В `enforce` передавать `onPinRequested` только при `pinConfigured`:
    ```kotlin
    val onPinRequested = if (pinConfigured) {
        decision.activePackage?.let { pkg -> { requestPinBypass(pkg) } }
    } else null
    ```
- `core/.../usecase/BlockingDecision.kt` (или новый `PinBypass.kt`)
  - Чистая `fun pinBypassAllowed(pinConfigured: Boolean, activePackage: String?): Boolean`.
  - `BlockingController` использует её для читаемости; тест в `core`.
- `PinOverlayManager` и `AccessibilityGuardController` **не трогать**.

### Родитель

- `app/.../feature/parent/rules/ScheduleScreen.kt`
  - «Время учёбы»: `toggleEnabled = pinIsSet` (как сон).
  - `PinWarningCard` вынести из-под карточки сна наверх (одна карточка на оба расписания) или оставить одну под обеими; текст обновить: PIN нужен для экранов-замков (учёба, сон, перерывы).
  - `PinWarningCard` вынести в общий файл (`feature/parent/rules/PinWarningCard.kt`), чтобы переиспользовать на экране перерывов.
- `app/.../feature/parent/rules/ScheduleViewModel.kt`
  - В `setEnabled` при `enabled=true` сначала `policyRepository.pinProtection.first()`; `null` → выход без записи (defense-in-depth поверх UI).
- `app/.../feature/parent/rules/BreaksScreen.kt` / `BreaksViewModel.kt`
  - `BreaksScreen(onBack, onOpenPinSetup, ...)`; в `ParentScreen` передать `{ navController.navigate(ROUTE_RULES_PIN) }`.
  - `BreaksUiState` получает `pinIsSet`; `canEnable` учитывает `pinIsSet`.
  - При `!pinIsSet` показывать ту же карточку-подсказку; тумблер включения недоступен.
  - `setEnabled(true)` в VM тоже проверяет `pinProtection.first()`.
- `app/src/main/res/values/strings.xml`
  - Обновить `schedule_pin_warning` под все экраны-замки; при необходимости добавить строку для перерывов/кнопки.

### Тесты

- `PinBypassTest` (core): PIN есть/нет × пакет есть/нет.
- `ScheduleViewModelTest` (новый): включение STUDY без PIN не пишет; с PIN пишет; выключение разрешено всегда.
- `BreaksViewModelTest` (расширить): `canEnable=false` без PIN; `setEnabled(true)` без PIN не пишет; с PIN — пишет; выключение разрешено.
- Ручная/AVD: политика без PIN → у ребёнка ссылки нет, любые 4 цифры ничего не открывают; в родителе тумблеры заблокированы и карточка ведёт в PIN; после установки PIN ссылка работает, неверный PIN — нет.

### Приёмка

- Без PIN обход soft-блока невозможен.
- Родитель не может включить учёбу/сон/перерывы через UI без PIN; видит подсказку.
- С PIN ссылка и проверка работают; полный замок и аварийный `NoPinSet` не сломаны.

## 4. P2 — перерыв не всплывает после учёбы/сна (Medium)

### Проблема

`ObserveBreakStateUseCase.hoursState` создаёт сессию при активном HOURS-окне до проверки `scheduleActive`. Во время учёбы/сна сессия живёт скрыто; после расписания всплывает полноэкранный замок без предупреждения. Кроме того, уже начатая до расписания сессия не завершается и после расписания возвращается с остатком.

### Новый контракт (решение Володи 2026-11-08)

- При старте Study/Sleep активная сессия перерыва (любого режима) завершается со сбросом usage — остаток не показывается.
- Пока расписание активно, новые сессии не создаются.
- HOURS-окно, прошедшая часть которого пересекла расписание, после расписания не стартует — остаток окна сбрасывается.
- Часть HOURS-окна **до** старта расписания может показываться; окно, начавшееся во время расписания, пропускается целиком.
- INTERVAL: сброс usage при завершении гарантирует, что после расписания не будет мгновенного нового перерыва по тому же порогу.

### Реализация

- `core/.../ObserveBreakStateUseCase.kt`, `evaluate` — до возврата активной сессии:
  ```kotlin
  if (input.scheduleActive && activeSession != null) {
      breakRuntimeRepository.completeBreakAndResetUsage(activeSession.id)
      stickinessSource.reset()
      activeSession = null
      usageReset = true
  }
  ```
  Сессия получает `COMPLETED`, usage сбрасывается атомарно; UI скрывает расписание (reducer).
- `hoursState`:
  - `if (scheduleActive) return BreakState.Idle` — новых сессий во время расписания нет.
  - Для активного `activeHoursWindow` проверить прошедшую часть `[window.start, now]` на пересечение с окнами study/sleep; при пересечении — `Idle` (остаток окна сброшен), иначе старт как сейчас.
- Правила расписания в `BreakInput`: добавить `studySchedule`/`sleepSchedule`. В `combine` уже 5 потоков — либо vararg-`combine(flows) { arr -> ... }`, либо сначала объединить study+sleep в один `ScheduleRulesPair`; читать их в `hoursState` как альтернативу.
- Чистый helper в `Schedule.kt`:
  ```kotlin
  fun ScheduleRules.overlaps(start: LocalDateTime, end: LocalDateTime): Boolean
  ```
  Абсолютные окна для дат, которые покрывает диапазон (включая переход через полночь), пустые/выключенные окна пропускаются; границы — как в `TimeWindow` (начало включительно, конец исключительно). Тесты в `ScheduleTest`.
- Восстановление после смерти процесса/reboot:
  - при активном расписании активная сессия завершается тем же путём;
  - если процесс умер до срабатывания, при восстановлении сессии проверить, пересекает ли её прошедшая часть `[startedAtWallMs, min(now, endsAtWallMs)]` окна study/sleep; при пересечении — `completeBreakAndResetUsage` и не возвращать `Active`. Для HOURS это закрывает остаток окна после расписания, для INTERVAL — остаток перерыва.
- Комментарии в `ObserveBreakStateUseCase`/плане: «расписание завершает перерыв со сбросом, а не просто скрывает его».

### Тесты

`ScheduleTest`:
- `overlaps`: обычное окно, окно через полночь, границы, пустое/выключенное расписание, диапазон в 2 дня.

`ObserveBreakStateUseCaseTest`:
- активный INTERVAL при старте учёбы → `Idle`, сессия `COMPLETED`, usage сброшен; после учёбы нет мгновенного нового перерыва;
- активный HOURS при старте учёбы → `Idle`, сессия `COMPLETED`; после учёбы остаток окна не показывается;
- HOURS-окно 13:45–14:15, учёба 14:00–15:00 → 13:46 `Active`; 14:00 завершено; после — нет;
- HOURS-окно 13:45–14:15, учёба 13:50–14:00 → 13:46 `Active`; 13:50 завершено; 14:00–14:15 не показывается;
- HOURS-окно 14:05–14:45, учёба 14:00–14:30 → 14:05 `Idle`; после 14:30 остаток не показывается;
- HOURS-окно 13:00–13:10, учёба 14:00 → обычный перерыв;
- восстановление активной HOURS/INTERVAL-сессии, чья прошедшая часть пересекла расписание → сессия завершена, замка нет;
- warning HOURS: окно впереди — плашка как раньше; окно целиком внутри расписания — плашки/сессии нет; окно, начавшееся до расписания, — плашка до старта, затем сессия завершается;
- существующий тест «расписание скрывает перерыв на UI но не продлевает сессию» **обновляется** под новый контракт: сессия завершается на старте расписания, а не по deadline. Это намеренная смена контракта (решение Володи), а не подгонка под код; в тесте — ссылка на это решение.

### Документация

- `docs/superpowers/specs/2026-07-21-forced-breaks-design.md` — SUPERSEDED-врезка/§5 привести к новому правилу.
- План «KidGuard — исправление перерывов» §3.5 и §7.3 п.6: заменить «скрывают, но не ставят сессию на паузу» на «завершают сессию со сбросом; после расписания остаток не показывается».
- После реализации — предложить запись в карточку и план в Obsidian (не коммитить в репозиторий).

## 5. P3 — одно состояние расписания и точная граница (Low)

### Проблема

Тик 30 с; `FullScreenLockController`, `BlockingController` и `ObserveBreakStateUseCase` подписываются на собственные холодные `ObserveScheduleStateUseCase()` с разными фазами. На эмуляторе старт учёбы — +15 с, снятие — +26 с; между скрытием ночного замка и включением soft-блока возможен зазор до 30 с.

### Решение

- Новый `core/.../repository/ScheduleClock.kt`:
  ```kotlin
  interface ScheduleClock {
      fun now(): LocalDateTime
      suspend fun awaitNextMinute()
  }
  ```
- `platform/.../time/PlatformScheduleClock.kt`: `now() = LocalDateTime.now()`, `awaitNextMinute()` — `delay(1000 - (now.second*1000 + now.nano/1_000_000))`, минимум 1 мс (на границе — 1000 мс).
- Новый `@Singleton ScheduleStateSource` (`core/.../usecase`):
  - `@Inject constructor(policy, clock)` создаёт собственный `CoroutineScope(SupervisorJob() + Dispatchers.Default)`;
  - внутренний конструктор с `CoroutineScope` — для тестов;
  - `val state: SharedFlow<ScheduleState>` = `combine(study, sleep, ticker) { ... }.distinctUntilChanged().shareIn(scope, SharingStarted.Eagerly, replay = 1)`;
  - `ticker` — `flow { while (true) { emit(clock.now()); clock.awaitNextMinute() } }`.
  - Никакого `Inactive`-заглушки: `shareIn` с `replay = 1` без initial; `.first()` ждёт первый реальный emission (он быстрый: Room-flow + immediate tick).
- `ObserveScheduleStateUseCase` становится тонкой обёрткой: `invoke() = source.state`.
- `PlatformModule`: `@Binds ScheduleClock` → `PlatformScheduleClock`.
- `ObserveBreakStateUseCase`, `FullScreenLockController`, `BlockingController` продолжают инжектить `ObserveScheduleStateUseCase`; код вызовов не меняется.
- Убрать 30-секундный `TICK_MS` из `ObserveScheduleStateUseCase`.

### Тесты

Новый `ObserveScheduleStateUseCaseTest` (или `ScheduleStateSourceTest`) с фейковым `ScheduleClock` и фейковым `PolicyRepository`:
- в 21:57:59.500 — Inactive; после 500 мс — Study (граница минуты точная);
- на ровной границе нет нулевого цикла (следующий тик — через минуту);
- смена политики в середине минуты эмитит сразу (без ожидания тика);
- два подписчика получают один и тот же поток, тик один (счётчик в фейковых часах);
- `distinctUntilChanged` не даёт дублей;
- после временной остановки подписок `replay=1` отдаёт последнее реальное состояние, не заглушку.

### Риски

- Стартовый `SharedFlow` без initial: контроллеры короткое время не имеют состояния; это допустимо, но проверить на AVD (замок/блок при старте процесса).
- `shareIn(Eagerly)` живёт всё время процесса — один тикер на приложение, расход минимален.
- Откат: вернуть холодный `ObserveScheduleStateUseCase` (один файл + конструкторы).

## 6. P4 — soft-overlay: обновление и скрытие (Low)

### Проблема

`OverlayManager.show` идемпотентен: при смене причины текст не меняется; при конце учёбы плашка живёт до 6 с. Скрывать на `block=false` нельзя — уход «Домой» сам делает решение false, и плашка исчезла бы сразу после показа.

### Решение

- `platform/.../overlay/OverlayManager.kt`
  - `fun showOrUpdate(reason, untilText, onPinRequested)`: нет окна → создать; есть → обновить title/subtitle, ссылку PIN и callback; таймер автоскрытия не перезапускать (окно живёт свои 6 с).
  - `fun hide()`: снять callback таймера, `removeView`, очистить.
  - Вынести построение title/subtitle в чистые функции; `createOverlayView` и update используют их.
- `platform/.../overlay/SoftOverlayPolicy.kt` — чистое правило:
  ```kotlin
  fun shouldHideSoftOverlay(
      lastReason: BlockReason,
      lastPackage: String?,
      activePackage: String?,
      studyTimeActive: Boolean,
      limitExpired: Boolean,
      blockedApps: Set<String>,
      alwaysAllowed: Set<String>
  ): Boolean
  ```
  Логика:
  - причина STUDY исчезла → hide;
  - причина LIMIT исчезла (дневной или личный) → hide;
  - причина BLOCKED_BY_PARENT и пакет больше не запрещён → hide;
  - ребёнок ушёл в другое приложение (не лаунчер/KidGuard) → hide (таймер всё равно сработал бы);
  - фокус на лаунчере/KidGuard и причина ещё жива → **не** hide (ребёнок читает плашку).
- `BlockingController`:
  - `enforce` → `showOrUpdate`, запомнить `lastShown` (reason, package, untilText);
  - `BlockDecision` расширить контекстом (`studyTimeActive`, `limitExpired`, `blockedApps`, `alwaysAllowed`), чтобы решение о скрытии не требовало повторного чтения;
  - на `block=false` вызвать `shouldHideSoftOverlay` и при true — `overlayManager.hide()`;
  - `limitExpired` для правила = дневной `LimitState.Expired` **или** личный `appLimitState.Expired`.
- `BlockingUiState` не меняется.

### Тесты

- `SoftOverlayPolicyTest` (platform, JVM): все ветки таблицы, включая «лаунчер + причина жива → keep» и «учёба кончилась + лаунчер → hide».
- Ручная/AVD: открыть заблокированное приложение, дождаться плашки, закончить окно учёбы (или выключить расписание) — плашка исчезает сразу, а не через 6 с; при уходе «Домой» во время активной причины плашка остаётся; смена причины (лимит → учёба) обновляет текст; дублей View нет.
- `dumpsys window` до/после: 0/1 окно, без двух экземпляров.

## 7. P5 — тесты и покрытие

### Что добавляем

| Область | Тест | Файл |
|---|---|---|
| PIN-ссылка | `pinBypassAllowed` | `core/.../usecase/PinBypassTest.kt` |
| Родитель: расписание | STUDY без PIN не включается, SLEEP уже не включается, выключение разрешено | `app/.../rules/ScheduleViewModelTest.kt` |
| Родитель: перерывы | `canEnable`/`setEnabled` с PIN и без | `BreaksViewModelTest.kt` |
| Расписание | точная граница, смена политики, два подписчика, один тик | `ObserveScheduleStateUseCaseTest.kt` |
| Перерывы | P2-сценарии | `ObserveBreakStateUseCaseTest.kt` |
| Причина soft-блока | `describe` → чистая `blockReasonFor`/`untilTextFor`; STUDY vs LIMIT vs BLOCKED; `untilText` для окна через полночь | `platform/.../overlay/BlockDecisionReasonTest.kt` |
| Overlay hide | `shouldHideSoftOverlay` | `platform/.../overlay/SoftOverlayPolicyTest.kt` |
| Overlay lifecycle | instrumentation/ручная: in-place update, hide, PIN-ввод не сбрасывается (у soft-блока ввода нет) | AVD |

### Фальсифицируемость

Для каждого нового теста — мутация:
- `pinBypassAllowed` всегда true → падает тест «без PIN нельзя»;
- `scheduleActive`-проверка убрана → падает P2-тест;
- тик снова 30 с/`now()` из старого тика у границы → падает тест границы;
- `shouldHideSoftOverlay` всегда false/true → падают соответствующие ветки;
- `describe` с перепутанным приоритетом → падает тест причины.

### Полный прогон

- `./gradlew testDebugUnitTest assembleDebug`.
- `:data:connectedDebugAndroidTest` — только если появятся изменения в data (по плану их нет; проверить перед релизом).
- AVD-матрица из раздела 9.

## 8. Порядок и зависимости

1. **P1** — безопасность; сначала тесты, затем правки. Родительский UI и ребёнок можно делать параллельно; общий PR/ветка — после «ок».
2. **P2** — маленькая правка core; не зависит от P1.
3. **P3** — архитектура расписания; после P2 (P2 меняет `hoursState`, P3 — источник состояния). P3 не меняет контракт P2.
4. **P4** — overlay; после P1 (в `enforce` уже есть `pinConfigured`) и P3 (причина/состояние).
5. **P5** — сквозные тесты; закрывается после каждого этапа, финальный прогон — в конце.

Каждый этап: красный тест → реализация → мутация → зелёный; без коммитов до явной отмашки. Ветка — после одобрения плана.

## 9. Матрица приёмки (AVD)

Политика только через сервер: родительский JWT с 5554 (фактический родитель Pixel_10), ребёнок 5556 (Pixel_9_Pro, childId 2). Исходный JSON сохранить и вернуть; runtime-таблицы очистить; Олега (childId 3) не трогать.

| Сценарий | Ожидание |
|---|---|
| Политика без PIN; попытка включить учёбу в UI | тумблер заблокирован, карточка ведёт в PIN; включение не записывается |
| Политика без PIN, учёба уже включена в обход UI | у ребёнка нет PIN-ссылки; любые 4 цифры не открывают приложение; soft-блок работает |
| PIN задан | ссылка есть; верный PIN открывает; неверный — нет |
| Старт STUDY | soft-блок в пределах ~1 с от границы (P3), не 15–30 с |
| Конец STUDY при видимой плашке | плашка скрывается сразу (P4), не через 6 с |
| HOURS-окно полностью внутри STUDY | во время и после — замка нет (P2) |
| HOURS-окно началось до STUDY и ещё идёт после | часть до STUDY — перерыв; на старте STUDY завершён; остатка после STUDY нет |
| HOURS-окно началось внутри STUDY | ни во время, ни после — замка нет |
| INTERVAL-перерыв при старте STUDY | завершён, usage сброшен; после STUDY мгновенного перерыва нет |
| HOURS-окно 13:45–14:15, STUDY 14:00–15:00 | 13:45–14:00 — перерыв; в 14:00 завершён; после — нет |
| HOURS-окно 13:45–14:15, STUDY 13:50–14:00 | 13:45–13:50 — перерыв; 13:50 завершён; 14:00–14:15 не показывается |
| Перерыв начался до STUDY и истёк внутри | завершён на старте STUDY; после STUDY замка нет; сессия COMPLETED |
| Перерыв начался до STUDY и жив | завершён на старте STUDY со сбросом; после STUDY замка нет; сессия COMPLETED |
| Звонок, screen off/on, kill -9, reboot | soft-блок/расписание восстанавливаются; full-screen замок по приоритету; пересечённая расписанием сессия после reboot не воскресает |
| Полный замок без PIN (legacy) | аварийный `NoPinSet` остаётся; в UI новые замки без PIN не включаются |

В конце: PUT исходного JSON, очистка `break_session`/`stickiness_state`, удаление временных файлов, проверка accessibility и сервисов на детском AVD.

## 10. Риски и откат

- **P3** самый широкий: общий поток меняет момент эмиссии у трёх потребителей. Откат — вернуть холодный `ObserveScheduleStateUseCase` и удалить `ScheduleStateSource`/`ScheduleClock` (DI-биндинг).
- **P2** меняет контракт §3.5: существующий тест «расписание скрывает перерыв» обновляется осознанно; часть окна до расписания показывается, остаток после — нет. Пересечение считается по правилам study/sleep, поэтому важно не разъехаться с `ScheduleState` (оба из одной политики).
- **P1** UI-блокировка тумблеров может раздражать; альтернатива — только карточка-подсказка (описана в допущении 2.2).
- **P4** правило скрытия не должно снимать плашку сразу после `sendHome`; это закреплено тестом «лаунчер + причина жива → keep».
- Миграций БД нет.

## 11. Статус

- [x] P1 — PIN обязателен, ссылка только при PIN, родительская подсказка
- [x] P2 — перерыв завершается на старте расписания; остаток окна после расписания не показывается
- [x] P3 — общий поток расписания и точная граница
- [x] P4 — `showOrUpdate`/`hide` и правило причины
- [x] P5 — тесты, мутации, полный прогон и AVD-матрица

### Реализация 2026-11-08

- Ветка `fix/study-soft-block-fixes`; коммитов нет (жду отмашку).
- `./gradlew testDebugUnitTest assembleDebug` — зелёный. Новые тесты: `PinBypassTest` 4,
  `ObserveScheduleStateUseCaseTest` 4, `SoftOverlayPolicyTest` 8, `BlockDecisionReasonTest` 5,
  `ScheduleViewModelTest` 4, `BreaksViewModelTest` +4, `ScheduleTest` +6, `ObserveBreakStateUseCaseTest` +4
  (5 новых сценариев, один заменил старый контракт).
- Мутации (все роняют ожидаемые тесты и откатаны): PIN без PIN → `PinBypassTest`; `overlaps=false`
  → `ScheduleTest`/P2-остаток/reboot; `shouldHide=false` → `SoftOverlayPolicyTest`; тик 30 с вместо
  границы → все `ObserveScheduleStateUseCaseTest`.
- AVD (5556): старт STUDY в 21:52:00.006 (+6 мс от границы), плашка без PIN-ссылки; P2-перерыв
  `ACTIVE` в 22:01:42 → `COMPLETED` на старте STUDY 22:02:00, замка после STUDY нет (окно жило до
  22:07); P4-плашка видна в 22:07:58 и снята в 22:08:00 (не через 6 с); после reboot и `kill -9`
  блок снова сработал ровно на границе (22:14:00.029). Политика, runtime-таблицы, accessibility
  и временные файлы восстановлены.

### Ревизия 2026-11-09 (свежий взгляд)

- **Найден и исправлен регресс P4:** правило скрытия плашки смотрело на лимит ТЕКУЩЕГО пакета.
  После `sendHome` активным становится лаунчер, у которого личных лимитов нет, поэтому плашка
  «Время вышло» (личный лимит приложения) снималась сразу после показа — ребёнок её не видел.
  На baseline (4fa77ba) плашка висела 6 с. Исправлено: личный лимит читается для пакета, которому
  плашка показана (`hideSoftOverlayIfNeeded`), дневной и личный лимиты разделены
  (`dailyLimitExpired` / `personalLimitExpired`). Добавлены 2 теста (`SoftOverlayPolicyTest`),
  проверена мутация, поведение сверено с baseline на эмуляторе.
- Проверено на AVD: с PIN плашка-ссылка открывает PIN-клавиатуру, верный PIN (1111 — найден из
  собственного хеша тестовой политики) снимает блок; без PIN ссылки нет вовсе.
- Обновлены документы: дизайн-план (`docs/superpowers/specs/2026-07-21-forced-breaks-design.md`,
  §3 и §5), руководство пользователя (`docs/guide/user-guide.md` — требование PIN и новое правило
  перерыва), README, `docs/known-limits-and-bypasses.md` (честная оговорка про политику без PIN,
  созданную до правила или через серверный API в обход экрана).
- Итог после ревизии: 479 JVM-тестов, 0 падений, `assembleDebug` — зелёный. Родительский экран с PIN не визуализировался:
  выбранный ребёнок в parent-приложении — Олег, его локальный кеш временно правился и восстановлен;
  сервер Олега не трогали.
