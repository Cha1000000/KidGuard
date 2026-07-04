# Roles Quick Start (KidGuard)

Используйте `Roles.md` для авто-роутинга ролей и skills под задачи KidGuard.

## Базовый шаблон промпта

`Role: <RoleName>. Skills: <skill1>, <skill2>. Task: <что сделать>. Output: <результат>. Constraints: <ограничения>.`

Пример:
`Role: Architect. Skills: architecture-designer, architecture-patterns. Task: спроектировать модуль child (Accessibility + учёт экранного времени). Output: дизайн + границы + риски обхода. Constraints: без Device Owner.`

## Шаблоны по ролям

### Architect
`Role: Architect. Skills: architecture-designer, architecture-patterns. Спроектируй решение для <feature>.`

### Coder
`Role: Coder. Skills: kotlin-specialist, fullstack-guardian. Реализуй <feature>. Output: код + тесты + краткий changelog.`

### Analyst
`Role: Analyst. Skills: debugging-wizard, debugging-strategies, monitoring-expert. Найди root cause для <issue>.`

### Designer
`Role: Designer. Skills: mobile-android-design, visual-design-foundations, interaction-design. Обнови UX/UI экрана <screen>.`

### Reviewer
`Role: Reviewer. Skills: code-review-excellence, code-reviewer, security-reviewer. Проверь diff на регрессии и риски (особенно разрешения и обход контроля).`

### QA / Tester
`Role: QA. Skills: e2e-testing-patterns. Подготовь и пройди test-сценарии для <flow>.`

### DevOps
`Role: DevOps. Skills: github-actions-templates. Настрой CI для <сборка/тесты/подпись APK>.`

## Multi-role цепочка

`Use pipeline: Architect -> Coder -> Reviewer.`

## Override

Если роль/skills явно указаны в промпте, это приоритетнее авто-роутинга.
