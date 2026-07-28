# План: надёжный детект переднего приложения + невычитание времени оверлеев/замков из лимита

Дата: 2026-07-28. Ветка: `sprint/pre-oleg-week`. Реализация — Sonnet 5, проверка — за ним.

## Зачем (две связанные проблемы, найдены на реальном HiOS Олега)

**Проблема 1 — обход «Времени учёбы» (и вообще мягкой блокировки).**
Некоторые тяжёлые игры (пример — Standoff 2) после первого показа оверлея «Сейчас время учёбы»
можно смахнуть и **запустить повторно уже без блокировки**. Другие приложения переблокируются
корректно. Разница — в надёжности оконных событий accessibility.

**Проблема 2 — время наших блокирующих экранов капает в дневной лимит.**
Пока показан наш полноэкранный замок (сон/перерыв) или блокирующий оверлей, система считает, что
экран включён и разблокирован (`isInteractive=true`, `isKeyguardLocked=false`), поэтому
`ScreenTimeTracker` **продолжает начислять время** — общему счётчику и приложению под замком.
Требование Володи: **время, когда на экране наши оверлеи/замки, не должно вычитаться из лимита.**

## Корень проблемы 1

Детект переднего приложения (`ForegroundAppMonitor.currentPackage`) обновляется ТОЛЬКО в
`KidGuardAccessibilityService.onAccessibilityEvent` по событию `TYPE_WINDOW_STATE_CHANGED`
(`foregroundAppMonitor.update(event.packageName)`). `currentPackage` — это `StateFlow`, он реагирует
лишь на **смену значения**.

Сценарий обхода:
1. Запуск игры → `TYPE_WINDOW_STATE_CHANGED` → `currentPackage = игра` → `BlockingController`
   показывает оверлей + `sendHome`.
2. При уходе на лаунчер и/или тёплом резюме тяжёлой игры (единственная Activity + SurfaceView,
   процесс жив) свежий `TYPE_WINDOW_STATE_CHANGED` **не всегда приходит**, и `currentPackage`
   **застревает** на прошлом значении (частая причина — событие «под» нашим оверлеем подавляется,
   либо игра резюмится без state-changed).
3. Повторный запуск той же игры = «значение не изменилось» → `flatMapLatest` в `BlockingController`
   не пересобирается → блокировка не срабатывает. → **обход.**

Приятный факт: в конфиге `accessibility_service_config.xml` УЖЕ подписаны оба типа —
`typeWindowStateChanged|typeWindowsChanged`, есть `flagRetrieveInteractiveWindows` и
`canRetrieveWindowContent="true"`. Но `onAccessibilityEvent` в самом начале делает
`if (event?.eventType != TYPE_WINDOW_STATE_CHANGED) return` — то есть `typeWindowsChanged`
**приходит, но выбрасывается**. Значит фикс — начать его обрабатывать. Правки манифеста/конфига НЕ нужны.

## Корень проблемы 2

`ScreenTimeTracker.isUserActive()` = `isInteractive && !isKeyguardLocked`. Наши замки/оверлеи — это
`TYPE_ACCESSIBILITY_OVERLAY`/`TYPE_APPLICATION_OVERLAY`, а НЕ системный keyguard, поэтому во время
их показа `isUserActive()` возвращает `true`, и тик учёта начисляет время.

## Решение

### Часть A — надёжный детект переднего приложения (событийный, без опроса «каждую секунду»)

В `KidGuardAccessibilityService.onAccessibilityEvent` разнести обработку по типу события:

- `TYPE_WINDOW_STATE_CHANGED` — **как сейчас** (быстрый путь: `event.packageName` +
  логика критичных экранов/lockdown/скрытия PIN-оверлея). Ничего не меняем.
- `TYPE_WINDOWS_CHANGED` — **новое**: вычислить верхнее прикладное окно и обновить
  `foregroundAppMonitor`. Это ловит возвраты/тёплые резюмы, которые не шлют `STATE_CHANGED`.
  Логику критичных экранов здесь НЕ трогаем (она завязана на `event.packageName`/`title` и `windowId`
  именно state-changed события).

Функция определения верхнего приложения:
```kotlin
private fun topApplicationPackage(): String? = try {
    windows
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }  // наши оверлеи не TYPE_APPLICATION → исключаются
        .maxByOrNull { it.layer }                                        // самое верхнее прикладное окно
        ?.root?.packageName?.toString()
        ?.takeIf { it.isNotBlank() }
} catch (e: Exception) { null }
```
Обновление:
```kotlin
TYPE_WINDOWS_CHANGED -> {
    if (isRelevantWindowChange(event)) {
        topApplicationPackage()?.let { foregroundAppMonitor.update(it) }
    }
    return
}
```
`isRelevantWindowChange` — оптимизация против «дребезга»: реагируем только на изменения стека окон,
а не на изменения содержимого. Через `event.windowChanges` (API 28+, у нас minSdk 33):
реагируем, если есть любой из флагов `WINDOWS_CHANGE_ADDED | WINDOWS_CHANGE_REMOVED |
WINDOWS_CHANGE_ACTIVE | WINDOWS_CHANGE_FOCUSED | WINDOWS_CHANGE_PIP` (при нуле флагов — считать
релевантным, на всякий случай). Это резко снижает число пересчётов.

Почему не «опрос экрана каждую секунду» (ответ на вопрос Володи про батарею):
- Решение **чисто событийное** — работаем на уже приходящем событии, никакого фонового таймера.
- Пересчёт идёт только при **реальном изменении стека окон**, да ещё и отфильтрованном по флагам;
  при погашенном экране окна почти не меняются.
- Один вызов `windows`/`root` — лёгкая внутрипроцессная операция сервиса (не будит CPU из сна:
  события окон и так приходят только когда что-то происходит). Периодических wakeup’ов нет.
- Итог: заметного расхода батареи не добавляется (в отличие от polling-таймера, который мы
  СОЗНАТЕЛЬНО не берём).

Дедуп: `currentPackage` — `StateFlow`, повтор того же значения не пере-эмитится сам. `null`/пусто не
пишем (сохраняем последнее известное). `BlockingController.distinctUntilChanged` уже корректен —
менять его не нужно; при аккуратной смене `currentPackage` (игра→лаунчер→игра) переблокировка
срабатывает.

### Часть B — исключить время наших блокирующих экранов из учёта

Добавить признак «сейчас показан блокирующий UI» и учитывать его в `ScreenTimeTracker`.

Единый сигнал (SOLID: трекер не должен знать про конкретные оверлеи):
```kotlin
@Singleton
class BlockingUiState @Inject constructor(
    private val fullScreenLockOverlayManager: FullScreenLockOverlayManager, // замок сна/перерыва
    private val overlayManager: OverlayManager,                             // мягкий блок (лимит/учёба)
    private val pinOverlayManager: PinOverlayManager,                       // перехват настроек
    private val warningOverlayManager: WarningOverlayManager                // предупреждение lockdown
) {
    /** Показан ли сейчас блокирующий экран (ребёнок не пользуется приложением под ним). */
    fun blockingVisible(): Boolean =
        fullScreenLockOverlayManager.isShowing() ||
        overlayManager.isShowing() ||
        pinOverlayManager.isShowing() ||
        warningOverlayManager.isShowing()
}
```
Важно: **`BreakWarningOverlay` НЕ включаем** — он `FLAG_NOT_TOUCHABLE` (тапы проходят насквозь),
ребёнок в этот момент реально играет, это время — законная нагрузка.

`ScreenTimeTracker`:
```kotlin
// + конструкторный параметр: private val blockingUiState: BlockingUiState
private fun isUserActive(): Boolean {
    val interactive = powerManager?.isInteractive == true
    val unlocked = keyguardManager?.isKeyguardLocked == false
    return interactive && unlocked && !blockingUiState.blockingVisible()
}
```
Эффект: пока показан замок/блок-оверлей/PIN-оверлей — тик учёта пропускается, время не начисляется
ни общему счётчику, ни приложению под ним.

## Список правок (файлы)

1. `platform/.../overlay/OverlayManager.kt` — добавить публичный `fun isShowing(): Boolean =
   overlayView != null` (сейчас есть только приватное поле `overlayView`).
2. `platform/.../accessibility/BlockingUiState.kt` — новый `@Singleton` (Часть B).
3. `platform/.../tracking/ScreenTimeTracker.kt` — инжект `BlockingUiState`, учёт в `isUserActive()`.
4. `platform/.../accessibility/KidGuardAccessibilityService.kt` — обработка `TYPE_WINDOWS_CHANGED`
   + `topApplicationPackage()` + `isRelevantWindowChange()`. Путь `TYPE_WINDOW_STATE_CHANGED` не менять.
5. Импорты: `android.view.accessibility.AccessibilityWindowInfo` в сервисе.

Прочие компоненты (`BlockingController`, `FullScreenLockController`, конфиг accessibility, манифест,
сервер, БД, миграции) — НЕ трогаем.

## Нюансы и подводные камни (учесть при реализации)

- **Не сломать детект критичных экранов / перехват настроек.** `TYPE_WINDOWS_CHANGED` обрабатываем
  ОТДЕЛЬНОЙ веткой, которая только обновляет передний план и делает `return`; вся логика
  `isLockdownDialog`/`detectCriticalScreen`/скрытия PIN-оверлея остаётся исключительно на
  `TYPE_WINDOW_STATE_CHANGED` (она читает `event.packageName`, `title`, `windowId`).
- **`root` может быть `null`** (окно без извлекаемого содержимого) → пакет `null` → просто не
  обновляем (держим последнее известное значение).
- **Наш собственный пакет.** `topApplicationPackage()` может вернуть `ru.homelab.kidguard`, если
  открыта наша Activity (напр. детский экран) — это нормальная нынешняя ситуация (тот же результат
  даёт и старый путь). Отдельной фильтрации не требуется. Существующая проверка «скрыть PIN-оверлей
  только при уходе на чужой пакет» (строка ~186) остаётся на state-changed пути.
- **Потокобезопасность `isShowing()`.** Трекер читает из своей корутины (`Dispatchers.Default` в
  foreground-сервисе), а `overlayView` меняется на main-потоке. Это чтение ссылки при гранулярности
  15 сек — допустимо. Для чистоты можно пометить поля `overlayView` во всех менеджерах `@Volatile`
  (не обязательно, но желательно).
- **Гранулярность учёта 15 сек** сохраняется: если замок закрыл экран лишь часть тика, тик либо
  учтётся целиком, либо пропустится (по состоянию в момент проверки). Приемлемо, как и сейчас.
- **`StickinessTracker` (счётчик залипания для перерывов).** Для INTERVAL-перерыва длительность
  перерыва отсчитывается по ТОМУ ЖЕ счётчику залипания, и он ДОЛЖЕН продолжать идти под замком
  перерыва (так перерыв и заканчивается). Наши правки на стикинес не влияют — но при проверке
  убедиться, что INTERVAL-перерыв по-прежнему корректно доигрывает и снимается.
- **`typeWindowsChanged` шумный.** Обязательно фильтровать по `windowChanges`, иначе пересчёт будет
  дёргаться на каждое изменение содержимого. Если на каком-то экране флаги приходят нулевыми —
  фолбэк «считать релевантным» безопасен (пересчёт лёгкий, дедуп в StateFlow).

## Проверка (за Sonnet, на реальном телефоне Олега)

Сборка: `./gradlew :app:assembleDebug`; установка `-r` (in-place, пейринг сохранить).
Перед тестами: восстановить accessibility (после переустановки слетает), задать через сервер
временное окно «Время учёбы»/лимит, по завершении — вернуть конфиг из бэкапа.

1. **Обход закрыт (главное).** «Время учёбы» активно → запустить Standoff 2 → блок-оверлей →
   смахнуть → запустить Standoff 2 ПОВТОРНО → **должен снова заблокироваться**. Повторить 3–4 раза.
   Проверить ещё пару тяжёлых игр (Roblox, Tool Evolution) и обычные приложения.
2. **Белый список не задет.** В «Время учёбы» звонилка/SMS (из «Всегда доступных») открываются.
3. **Учёт: время замка не капает (Часть B).** Прочитать `screen_time` из БД (обязательно с
   `-wal`/`-shm`!). Поднять замок сна/перерыва поверх игры, подержать ~1–2 минуты, снова прочитать —
   `screen_time` за сегодня **не должен вырасти** за время замка. Аналогично для мягкого блок-оверлея.
4. **Обычный учёт цел.** Без оверлеев время начисляется как раньше (проверить +15 сек за тик на
   активном приложении).
5. **Регрессии.** Мягкий блок по исчерпанию лимита; замок сна (несмахиваемость, PIN до гашения
   экрана, экстренный звонок скрывает/возвращает замок); перехват настроек PIN-оверлеем;
   предупреждение lockdown — всё работает.
6. **Батарея (санити).** Убедиться, что нет фонового таймера/busy-loop; детект чисто событийный.

## Вне рамок

- Начисление времени лаунчера в общий лимит (существующее поведение, не меняем).
- Точность учёта мельче 15 сек.
- Опрос переднего приложения по таймеру (сознательно не берём — ради батареи; событийного детекта
  достаточно, а если на проверке всплывёт дыра — вернёмся к идее опроса, но с гейтом «экран включён
  + активна какая-то блокировка»).
