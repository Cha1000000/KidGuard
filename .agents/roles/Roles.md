# Roles Routing Rules (KidGuard)

Назначение: авто-выбор роли и соответствующих skills для задач проекта KidGuard
(нативный Android: Kotlin + Jetpack Compose + Room + Hilt + Retrofit + бэкенд-синхронизация;
контроль без Device Owner — Accessibility / Overlay / VpnService / Device Admin).

Набор ролей и skills адаптирован под этот проект: убраны web/React/командно-оркестрационные
роли из исходного шаблона (проект нативный Android и разрабатывается в основном соло).

## Базовые правила

1. Сначала определить интент задачи, затем роль(и), затем skills.
2. На один шаг роли использовать 2-3 primary skills.
3. При явном указании роли/skills пользователем это имеет приоритет над авто-роутингом.
4. Если интент неочевиден, fallback: `fullstack-guardian` + `debugging-strategies`.

## Роли и skills

### 1) Architect
Для: архитектура, границы слоёв, sync-подсистема, механизм контроля, data flow, ADR.
Primary:
- `architecture-designer`
- `architecture-patterns`
Secondary:
- `api-designer`
- `api-design-principles`
- `openapi-spec-generation`

### 2) Coder
Для: реализация фич и фиксов в Android/Kotlin/Compose, data-слой, сервисы контроля, sync.
Primary:
- `kotlin-specialist`
- `fullstack-guardian`
Secondary:
- `auth-implementation-patterns`
- `sql-optimization-patterns`

### 3) Analyst
Для: root cause, анализ логов/метрик, производительность фоновых сервисов, проблемы синхронизации.
Primary:
- `debugging-wizard`
- `debugging-strategies`
- `monitoring-expert`
Secondary:
- `sql-optimization-patterns`

### 4) Designer
Для: UI/UX детского и родительского экранов, визуальная система, вёрстка Compose, consistency.
Primary:
- `mobile-android-design`
- `visual-design-foundations`
- `interaction-design`
Secondary:
- `design-system-patterns`

### 5) Reviewer
Для: review изменений, регрессии, безопасность (чувствительные разрешения!), риски в контроле/данных.
Primary:
- `code-review-excellence`
- `code-reviewer`
- `security-reviewer`
Secondary:
- `auth-implementation-patterns`
- `accessibility-compliance`

### 6) QA / Tester
Для: test strategy, сценарии, flaky-кейсы, smoke/regression.
Primary:
- `e2e-testing-patterns`
Secondary:
- `code-reviewer`

### 7) DevOps
Для: CI/CD, сборка/подпись APK, автоматизация в GitHub Actions.
Primary:
- `github-actions-templates`

## Intents -> Roles

- "архитектура", "перестроить слой", "как спроектировать" -> Architect
- "реализуй", "добавь", "пофикси" -> Coder
- "проанализируй", "почему сломалось", "найди причину" -> Analyst
- "улучши дизайн", "сверстай экран", "UX" -> Designer
- "сделай ревью", "проверь безопасность", "аудит" -> Reviewer
- "напиши тесты", "проверь сценарии" -> QA / Tester
- "настрой CI", "сборка/подпись", "GitHub Actions" -> DevOps

## Multi-role pipeline

Для сложных задач использовать цепочку:
1. Architect (дизайн решения + ограничения)
2. Coder (изменения)
3. Reviewer или QA (проверка)

Обязательно подключать Reviewer для критичных зон:
- механизм контроля (Accessibility / Overlay / VpnService / Device Admin) и обход детьми
- учёт экранного времени и лимиты
- Google Sign-In, связывание аккаунтов, доступ к данным
- синхронизация единой политики между родителями (конфликты, LWW)
- миграции/изменения схем Room и серверной БД
