# PIN на системный экран «Дата и время» — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Закрыть системный экран «Дата и время» (`android.settings.DATE_SETTINGS`) тем же
PIN-оверлеем, что уже защищает VPN-настройки, «Специальные возможности», Device Admin и экраны
«О приложении»/«Хранилище» KidGuard — чтобы ребёнок не мог переводом часов вперёд обойти
анти-отмотку и досрочно обнулить дневной счётчик экранного времени.

**Architecture:** Чистое расширение существующего паттерна детекта критичных экранов в
`KidGuardAccessibilityService.kt` (веха 6.2/6.3): новый элемент enum `CriticalScreen` + новая
ветка `when` в `detectCriticalScreen()` + новые ключевые слова заголовка. `PinGuard`,
`PinOverlayManager`, счётчик попыток, окно повторной разблокировки — переиспользуются без
изменений, новых файлов/модулей/Hilt-биндингов нет.

**Tech Stack:** Kotlin, Android AccessibilityService, adb/uiautomator для живой проверки.

## Global Constraints

- Ветка: `feature/milestone-06v-prep` (уже существует, переключаться не нужно).
- UI-текстов нет — задача не трогает Compose/строки приложения, только kdoc-комментарии и
  системный детект (комментарии — на русском).
- Отступы — как в существующем файле (Kotlin, обычные пробелы, 4).
- Коммит на задачу, сообщение на русском, `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- **Не пушить и не мёржить** — Володя решает отдельно.
- Юнит-тестов не пишем: изменение только в `:platform`, детект по строке заголовка — та же природа,
  что и у уже покрытых веткой `detectCriticalScreen` случаев (VPN/Accessibility/Device Admin —
  тоже без юнит-тестов, только живая проверка на эмуляторе).
- Эмулятор ребёнка — `emulator-5554`. Приложение KidGuard должно быть установлено и запущено,
  accessibility-сервис включён (если недавно был `force-stop`, восстановить настройки
  accessibility перед проверкой — см. известное ограничение в `docs/known-limits-and-bypasses.md`).
- Экран координат при скриншотах: см. множитель, который вернёт инструмент показа скриншота
  (`original WxH, displayed at wxh — multiply by K`), координаты тапов — во ВСЕХ случаях в
  нативных пикселях (после умножения), либо через `uiautomator dump` для точных `bounds`.

---

### Task 1: Детект экрана «Дата и время» + kdoc

**Files:**
- Modify: `platform/src/main/java/ru/homelab/kidguard/platform/accessibility/KidGuardAccessibilityService.kt`

**Interfaces:**
- Consumes: существующие `settingsPackages: Set<String>` (свойство класса, строка 79), существующий
  приватный enum `CriticalScreen` (строка 319), существующий `when`-блок в `detectCriticalScreen()`
  (строки 211–256), существующий `private companion object` со списками ключевых слов
  (строки 328–373).
- Produces: новый элемент `CriticalScreen.DATE_TIME_SETTINGS`, который дальше течёт по уже
  существующему пути `maybeInterceptWithPin()` → `pinOverlayManager.show()` без каких-либо
  дополнительных изменений (этот путь ничего не знает о конкретных типах экрана, кроме
  спец-обработки `KIDGUARD_APP_INFO`, которая тут не нужна).

- [ ] **Step 1: Добавить константу с ключевыми словами**

В `private companion object` (файл заканчивается на строке 375, константы — строки 328–373),
добавить сразу после `APP_DETAILS_KEYWORDS` (строки 370–372):

```kotlin
        // Экран «Дата и время» (веха 6В): перевод часов ВПЕРЁД ничем не закрыт (анти-отмотка,
        // веха 2, защищает только от отката НАЗАД) — так ребёнок мог искусственно ускорить
        // наступление «нового дня» и обнулить дневной счётчик раньше срока. Заголовок снят
        // живьём с эмулятора: «Дата и время» (com.android.settings/.Settings$DateTimeSettingsActivity).
        val DATE_TIME_KEYWORDS = listOf("дата и время", "date & time", "date and time")
```

- [ ] **Step 2: Добавить элемент enum**

В `private enum class CriticalScreen` (строки 319–326), добавить после `KIDGUARD_APP_INFO`:

```kotlin
    private enum class CriticalScreen {
        VPN_SETTINGS,
        ACCESSIBILITY_SETTINGS,
        DEVICE_ADMIN,
        KIDGUARD_UNINSTALL,
        /** «О приложении» и «Хранилище» нашего приложения: очистка данных, force-stop, удаление. */
        KIDGUARD_APP_INFO,
        /** Перевод часов вперёд — обход анти-отмотки, досрочный сброс дневного счётчика. */
        DATE_TIME_SETTINGS
    }
```

- [ ] **Step 3: Добавить ветку детекта**

В `detectCriticalScreen()` (строки 211–256), добавить новую ветку перед `else -> null` (после
блока `APP_DETAILS_KEYWORDS`, строки 251–252):

```kotlin
            packageName in settingsPackages && APP_DETAILS_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.KIDGUARD_APP_INFO

            // Экран «Дата и время»: закрываем ЦЕЛИКОМ (как VPN/Accessibility), а не только
            // переключатель «Автоматически» — детект по конкретному Switch внутри дерева окна
            // сложнее и менее переносим между прошивками, тот же компромисс уже принят для
            // остальных системных экранов. Смена часового пояса без PIN — не проблема, в детском
            // сценарии не нужна.
            packageName in settingsPackages && DATE_TIME_KEYWORDS.any { title.contains(it) } ->
                CriticalScreen.DATE_TIME_SETTINGS

            else -> null
```

- [ ] **Step 4: Обновить kdoc класса**

Заменить kdoc-абзац класса (строки 27–44) — во втором пункте (строки 32–37) добавить упоминание
нового экрана:

```kotlin
/**
 * Accessibility-сервис KidGuard.
 *
 * 1. Определяет активное (foreground) приложение по событиям смены окна и публикует его в
 *    [ForegroundAppMonitor] — фундамент под учёт экранного времени и блокировку (вехи 2–3).
 * 2. Точечно перехватывает открытие критичных системных экранов (веха 6, шаги 6.2–6.3, 6В):
 *    настройки VPN, «Специальные возможности» (чтобы ребёнок не отключил сам этот сервис), экран
 *    администратора устройства (деактивация Device Admin снимает защиту от удаления), удаление
 *    именно приложения KidGuard, его экраны «О приложении» / «Хранилище» (оттуда доступны
 *    «Очистить хранилище», «Остановить» и «Удалить» — каждый из трёх убивает контроль), и экран
 *    «Дата и время» (перевод часов вперёд обходит анти-отмотку и обнуляет дневной счётчик раньше
 *    срока). Другие приложения, включая игры, ребёнок удаляет и чистит свободно — «чистит мусор».
 *
 * Детект кросс-вендорный, без привязки к конкретной прошивке: экран узнаём по ЗАГОЛОВКУ окна
 * ([screenTitle], `AccessibilityWindowInfo.title`), а не по классу активности; пакеты настроек и
 * инсталлера спрашиваем у системы ([settingsPackages], [installerPackages]), а не хардкодим.
 * Найденный экран накрываем PIN-оверлеем типа `TYPE_ACCESSIBILITY_OVERLAY` (обычный оверлей на этих
 * защищённых экранах система скрывает). Верный PIN пропускает на короткое окно, «Назад» уводит.
 */
```

- [ ] **Step 5: Собрать проект**

```bash
cd /home/racer/projects/KidGuard
./gradlew :platform:compileDebugKotlin --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Собрать debug APK для живой проверки**

```bash
cd /home/racer/projects/KidGuard
./gradlew assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add platform/src/main/java/ru/homelab/kidguard/platform/accessibility/KidGuardAccessibilityService.kt
git commit -m "$(cat <<'EOF'
feat(veha-6В): PIN на системный экран «Дата и время»

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Живая проверка на эмуляторе

**Files:** нет изменений кода — только verification.

**Interfaces:** не применимо.

- [ ] **Step 1: Установить свежий APK на детский эмулятор**

```bash
adb -s emulator-5554 install -r /home/racer/projects/KidGuard/app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Success`.

Если после переустановки accessibility-сервис отключился (проверить —
`adb -s emulator-5554 shell settings get secure enabled_accessibility_services`), включить заново:
```bash
adb -s emulator-5554 shell settings put secure enabled_accessibility_services ru.homelab.kidguard/ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService
adb -s emulator-5554 shell settings put secure accessibility_enabled 1
```

- [ ] **Step 2: Открыть экран «Дата и время», убедиться, что появился PIN-оверлей**

```bash
adb -s emulator-5554 shell am start -a android.settings.DATE_SETTINGS
sleep 1.5
adb -s emulator-5554 exec-out screencap -p > /tmp/date_pin_check.png
```
Expected на скриншоте: клавиатура PIN-оверлея («Введите PIN», подпись «Эту настройку защитил
родитель»), НЕ содержимое системного экрана «Дата и время».

- [ ] **Step 3: Неверный PIN — оверлей остаётся, счётчик попыток срабатывает**

Ввести любые 4 цифры, заведомо не совпадающие с текущим PIN (например `0000`, если реальный PIN —
`1357`, установленный в предыдущей подзадаче). Снять скриншот.
Expected: «Неверный PIN. Осталось попыток: N» — тот же текст и та же логика, что на других
защищённых экранах (общий `PinGuard`, отдельный тест не нужен — уже покрыт `PinGuardTest`).

- [ ] **Step 4: Верный PIN — оверлей исчезает, экран «Дата и время» доступен**

Ввести верный PIN. Снять скриншот.
Expected: виден реальный системный экран «Дата и время» (часовой пояс, формат времени,
переключатель «Автоматически»).

- [ ] **Step 5: Проверить окно повторной разблокировки (20 с)**

Уйти с экрана «Дата и время» (кнопка «Назад») и открыть его снова в течение 20 секунд после
Step 4.
```bash
adb -s emulator-5554 shell input keyevent KEYCODE_BACK
sleep 1
adb -s emulator-5554 shell am start -a android.settings.DATE_SETTINGS
sleep 1.5
adb -s emulator-5554 exec-out screencap -p > /tmp/date_pin_recheck.png
```
Expected: PIN НЕ запрашивается повторно — сразу виден системный экран (окно `UNLOCK_WINDOW_MS`
ещё активно для `CriticalScreen.DATE_TIME_SETTINGS`).

- [ ] **Step 6: Убедиться, что главный экран «Настройки» не перехватывается**

```bash
adb -s emulator-5554 shell am start -a android.settings.SETTINGS
sleep 1.5
adb -s emulator-5554 exec-out screencap -p > /tmp/settings_main_check.png
```
Expected: обычный список настроек, PIN-оверлея нет (заголовок «Настройки» не содержит ни одного
ключевого слова ни одной из веток `detectCriticalScreen`).

- [ ] **Step 7: Зафиксировать результат**

Если все 5 живых проверок (Step 2–6) прошли успешно — задача считается верифицированной, отдельный
коммит для Task 2 не нужен (изменений кода нет). Если что-то не совпало с Expected — вернуться к
Task 1 и разобраться (систематическая отладка, не догадки).

---

### Task 3: Документация — `known-limits-and-bypasses.md`

**Files:**
- Modify: `docs/known-limits-and-bypasses.md`

**Interfaces:** не применимо (документ, не код).

- [ ] **Step 1: Обновить пункт про откат времени в разделе «Что закрыто (веха 6)»**

Найти существующий пункт (строки 30–31):
```markdown
- **Откат системного времени** (сброс дневного счётчика) — анти-отмотка (веха 2): дата не «откатывается»
  назад относительно ранее виденной.
```

Заменить на:
```markdown
- **Манипуляция системным временем** (сброс дневного счётчика) — закрыта с обеих сторон:
  анти-отмотка (веха 2) не даёт откатить дату НАЗАД относительно ранее виденной; экран «Дата и
  время» под PIN-оверлеем (веха 6В, 2026-07-17) не даёт перевести время ВПЕРЁД, чтобы искусственно
  ускорить наступление «нового дня» и обнулить дневной счётчик раньше срока.
```

- [ ] **Step 2: Коммит**

```bash
cd /home/racer/projects/KidGuard
git add docs/known-limits-and-bypasses.md
git commit -m "$(cat <<'EOF'
docs(veha-6В): обновить known-limits — перевод времени вперёд теперь тоже закрыт

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Проверка по завершении

```bash
cd /home/racer/projects/KidGuard
./gradlew assembleDebug --console=plain
git log --oneline -5
git status
```
Expected: `BUILD SUCCESSFUL`; три новых коммита (Task 1, Task 3 — Task 2 без коммита) поверх
`a5cfdcf`; чистое рабочее дерево; ветка `feature/milestone-06v-prep`.

**Не пушить и не мёржить** — Володя решает отдельно.
